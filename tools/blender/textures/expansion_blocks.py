"""Expansion (1.2 -> 2.0) block textures, art/SPEC_EXPANSION.md section A.

Same recipe as blocks.py / blocks2.py: every stone surface starts from a Blender-baked tileable field (Cycles emission
bake on a Clifford torus), is quantised to the place's palette, lit from the top-left and cleaned of single-pixel
static; furniture and plants are laid out in numpy on top of those bases. Glowing pixels go to `_emissive` layers."""
import math
import numpy as np

from . import pix, bk
from .tex import (save, want, F, stone, bricks, brick_layout, bevel_frame, frame_mask, disc, line_pts, grow,
                  edge_of, spikes, f_noise, f_cluster, f_bands, f_vein, f_cells)
from .blocks import (P, R, END, PURPUR, WPURP, PURP_DARK, STILL, SHALE, DRIFT, crystal_index, stars, crack_paths)
from .blocks2 import (put, rect, box_frame, endstone_base, purpur_base, drift_base, stillstone_base)

YY, XX = np.mgrid[0:16, 0:16]

# ------------------------------------------------------------------------------------------------- palettes
# Warden (dragon)
OBS = P('#0A0710', '#120C18', '#1E1828', '#2B2635', '#3D3749', '#524B60')
GOLD = P('#6E5420', '#9A7A30', '#C9A24A', '#E6C778', '#F6E3A8')
SILV = P('#5E6474', '#848B9C', '#AEB6C8', '#D6DCE8')
# Zenith
SKY = P('#56678A', '#6E7FA0', '#93A6C4', '#A8B8D2', '#B9C7DD', '#D0DBEB')
SKYGOLD = pix.hx('#E8D38A')
SKYGOLD_D = pix.hx('#A8904A')
HALO = P('#9FD8FF', '#C4E6FF', '#E6F4FF', '#FFFFFF')
# Undertow
HAZE = pix.hx('#CFF3EC')
NEAR_BLACK = pix.hx('#0B1416')
NSILV = pix.hx('#8F9AA6')
CAUSTIC = pix.hx('#7FF2E2')
CORAL = P('#07080A', '#0E1114', '#171B20', '#22282E', '#2E353D')
CORAL_S = P('#5E6872', '#8F9AA6', '#C9D2DC')
# Gap / causeway
LMAG = P('#C2479E', '#FF8BE0', '#FFD0F2')
CALM = pix.hx('#DDE6F2')
VROOT = P('#241A30', '#3A2A4A', '#55406A')
CHORUS = P('#6E5482', '#8C6EA0', '#B493C4')
MEMB = P('#141A36', '#21446B', '#2E5C88', '#4A7AA8')
TEALB = P('#1F3F3A', '#2E5E55', '#3E7F74', '#5FA594', '#8CC9B4')


def px(img, x, y, c, a=255):
    if 0 <= x < img.shape[1] and 0 <= y < img.shape[0]:
        img[y, x, :3] = c
        img[y, x, 3] = a


def out(rel, img):
    if want('block/' + rel):
        save('block/' + rel, img)


def emit(rel, img, mask):
    if want('block/' + rel):
        save('block/' + rel, pix.emissive_from(img, mask))


def shade_lr(img, mask, light, dark):
    """Bevel a shape: its top/left inner edge lighter, bottom/right inner edge darker."""
    Pm = np.pad(mask, 1)
    tl = mask & (~Pm[:-2, 1:-1] | ~Pm[1:-1, :-2])
    br = mask & (~Pm[2:, 1:-1] | ~Pm[1:-1, 2:]) & ~tl
    img[tl, :3] = light
    img[br, :3] = dark


# =============================================================================================== lantern sheets
def lantern_sheet(cap_hi, cap_lo, band_hi, band_lo, base_hi, base_lo, frame_l, frame_r, glass, core, top_edge,
                  top_fill, cap_top, cap_centre, hook_a, hook_b):
    """Same sheet layout as block/spire_lantern (vanilla lantern template): cap side (1,0)-(5,2), body side
    (0,2)-(6,9), body top/bottom (0,9)-(6,15) with the cap top in (1,10)-(5,14), hooks at (11,1) and (11,10).
    `core` is a dict {(x, y): colour} drawn over the glass; returns (img, core mask)."""
    img = np.zeros((16, 16, 4), np.uint8)
    for x in range(1, 5):
        px(img, x, 0, cap_hi if x < 3 else cap_lo)
        px(img, x, 1, band_hi if x < 4 else band_lo)
    for x in range(0, 6):
        px(img, x, 2, band_hi if x < 3 else band_lo)
        px(img, x, 8, base_hi if x < 3 else base_lo)
    for y in range(3, 8):
        px(img, 0, y, frame_l)
        px(img, 5, y, frame_r)
        for x in range(1, 5):
            px(img, x, y, glass[0] if x == 1 else glass[2] if x == 4 else glass[1])
    cm = np.zeros((16, 16), bool)
    for (x, y), c in core.items():
        px(img, x, y, c)
        cm[y, x] = True
    for y in range(9, 15):
        for x in range(0, 6):
            e = x in (0, 5) or y in (9, 14)
            px(img, x, y, top_edge if e else top_fill)
    for y in range(10, 14):
        for x in range(1, 5):
            px(img, x, y, cap_top)
    for x, y in [(2, 11), (3, 11), (2, 12), (3, 12)]:
        px(img, x, y, cap_centre)
    for x in range(11, 14):
        for y in (1, 2, 10, 11):
            px(img, x, y, hook_a if (x + y) % 2 else hook_b)
    return img, cm


def wardens_lantern():
    g = {3: '#F6E3A8', 4: '#FFF1C8', 5: '#FFFFFF', 6: '#FFF1C8', 7: '#E6C778'}
    core = {}
    for y in range(3, 8):
        core[(2, y)] = pix.hx(g[y])
        core[(3, y)] = pix.hx('#F6E3A8' if y in (4, 5, 6) else '#E6C778')
    img, cm = lantern_sheet(GOLD[3], GOLD[2], OBS[4], OBS[3], OBS[3], OBS[2], OBS[4], OBS[1],
                            [SILV[3], SILV[2], SILV[1]], core, OBS[1], OBS[3], GOLD[2], OBS[2], OBS[1], GOLD[1])
    # gold banding on the body: a gold row under the cap and gold corner studs on the base
    for x in range(0, 6):
        px(img, x, 2, GOLD[3] if x < 3 else GOLD[2])
    px(img, 0, 8, GOLD[2])
    px(img, 5, 8, GOLD[1])
    for x in range(1, 5):  # cap top: gold ring rim
        px(img, x, 10, GOLD[3])
        px(img, 1, 9 + x, GOLD[3] if x < 4 else GOLD[2])
    out('wardens_lantern', img)
    emit('wardens_lantern_emissive', img, cm)


def drift_buoy():
    lil = {4: ('#D9B8F5', '#F4E4FF', '#FFFFFF', '#D9B8F5'), 5: ('#C39BEF', '#F4E4FF', '#F4E4FF', '#C39BEF'),
           6: ('#C39BEF', '#E2C4FF', '#E2C4FF', '#A983D6')}
    core = {}
    for y, row in lil.items():
        for i, c in enumerate(row):
            core[(1 + i, y)] = pix.hx(c)
    img, cm = lantern_sheet(DRIFT[5], DRIFT[4], DRIFT[4], DRIFT[3], DRIFT[2], DRIFT[1], DRIFT[4], DRIFT[1],
                            [VROOT[2], VROOT[1], VROOT[0]], core, DRIFT[1], DRIFT[3], DRIFT[4], VROOT[1],
                            VROOT[0], VROOT[2])
    # voidroot-fibre ties wrapping the float above and below the glowbulb
    for x in range(1, 5):
        px(img, x, 3, VROOT[2] if x % 2 else VROOT[1])
        px(img, x, 7, VROOT[1] if x % 2 else VROOT[0])
    for x, y in [(0, 5), (5, 5), (0, 3), (5, 7)]:
        px(img, x, y, VROOT[1])
    for x, y in [(2, 12), (3, 11)]:
        px(img, x, y, VROOT[2])
    out('drift_buoy', img)
    emit('drift_buoy_emissive', img, cm)


def lantern_moth_jar():
    """Same layout as block/tidewisp_jar: grey lid, translucent glass, a small magenta moth glowing inside."""
    img = np.zeros((16, 16, 4), np.uint8)
    body = (XX >= 4) & (XX <= 11) & (YY >= 5) & (YY <= 14)
    body &= ~(((XX == 4) | (XX == 11)) & ((YY == 5) | (YY == 14)))
    img[body, :3] = pix.hx('#3E2E4A')
    img[body, 3] = 90
    e = edge_of(body)
    img[e, :3] = pix.hx('#C7AFCF')
    img[e, 3] = 200
    img[e & (XX > 9), :3] = pix.hx('#937A9E')
    rect(img, 5, 3, 10, 4, pix.hx('#7A7A88'))
    rect(img, 5, 2, 10, 2, pix.hx('#B0B0BE'))
    put(img, [(10, 3), (10, 4)], pix.hx('#55555F'))
    put(img, [(5, 2)], pix.hx('#D6D6E0'))
    put(img, [(5, 7), (5, 8), (5, 9)], pix.hx('#F4E4F6'))
    moth = ["w....w",
            "wW..Ww",
            "wwbbww",
            ".wbbw.",
            "..bb..",
            ".w..w."]
    cols = {'w': '#FF8BE0', 'W': '#FFF0FA', 'b': '#FFD0F2'}
    glow = np.zeros((16, 16), bool)
    for j, r in enumerate(moth):
        for i, ch in enumerate(r):
            if ch in cols:
                px(img, 5 + i, 7 + j, pix.hx(cols[ch]))
                glow[7 + j, 5 + i] = True
    # dimmer lower wing tips (hindwings) and antennae
    for x, y in [(5, 12), (10, 12)]:
        px(img, x, y, LMAG[0])
    out('lantern_moth_jar', img)
    emit('lantern_moth_jar_emissive', img, glow)


# ================================================================================================== the causeway
def causeway_socket():
    top = purpur_base(1501, WPURP)
    box_frame(top, DRIFT[5], DRIFT[1])
    box_frame(top, DRIFT[3], DRIFT[4], inset=1)
    slot = np.zeros((16, 16), bool)
    for j, y in enumerate(range(4, 12)):  # keystone wedge, wider at the top
        half = 4 - j * 0.3
        slot[y] = np.abs(XX[0] + 0.5 - 8) <= half
    rim = grow(slot, 1) & ~slot
    top[rim & (XX + YY < 16), :3] = WPURP[0]
    top[rim & (XX + YY >= 16), :3] = WPURP[4]
    edge = edge_of(slot)
    top[slot, :3] = PURP_DARK
    top[slot & ~edge & (YY > 6), :3] = pix.hx('#1A0D20')
    top[edge, :3] = LMAG[1]
    top[edge & ((YY == 4) | (XX < 8)) , :3] = LMAG[2]
    top[edge & (YY == 11), :3] = LMAG[0]
    out('causeway_socket_top', top)
    emit('causeway_socket_top_emissive', top, edge)
    side = purpur_base(1502, WPURP)
    shaft = (YY >= 4) & (YY <= 11) & ((XX < 3) | (XX > 12))
    side[shaft, :3] = np.round(side[shaft, :3] * 0.62)
    db = drift_base(1503)
    for rows in ((0, 3), (12, 16)):
        side[rows[0]:rows[1]] = db[rows[0]:rows[1]]
    side[0, :, :3] = DRIFT[5]
    side[3, :, :3] = DRIFT[1]
    side[12, :, :3] = DRIFT[4]
    side[15, :, :3] = DRIFT[1]
    side[4:12, 3, :3] = WPURP[5]
    side[4:12, 12, :3] = WPURP[0]
    put(side, [(7, 1), (8, 1)], LMAG[0])
    out('causeway_socket_side', side)


def causeway_plaque():
    img = purpur_base(1511, WPURP)
    box_frame(img, WPURP[5], WPURP[0])
    panel = (XX >= 2) & (XX <= 13) & (YY >= 3) & (YY <= 12)
    n = F(f_noise, fx=5.0, seed=1512, det=1.0)
    img[panel, :3] = pix.hx('#1E1426')
    img[panel & (n > np.quantile(n, 0.72)), :3] = pix.hx('#261B30')
    pe = edge_of(panel)
    img[pe, :3] = GOLD[2]
    img[pe & ((YY == 3) | (XX == 2)), :3] = GOLD[3]
    img[pe & ((YY == 12) | (XX == 13)) & ~((YY == 3) | (XX == 2)), :3] = GOLD[1]
    rim = grow(panel, 1) & ~panel
    img[rim & ((YY == 2) | (XX == 1)), :3] = WPURP[0]
    img[rim & ((YY == 13) | (XX == 14)), :3] = WPURP[4]
    out('causeway_plaque', img)


def drift_helm():
    """Ship's wheel on a transparent ground: driftstone hub and rim, eight membrane-wrapped spokes, handle knobs."""
    img = np.zeros((16, 16, 4), np.uint8)
    cx = cy = 8.0
    d = np.hypot(XX + 0.5 - cx, YY + 0.5 - cy)
    ang = np.arctan2(YY + 0.5 - cy, XX + 0.5 - cx)
    lit = -np.cos(ang + math.pi / 4)  # +1 toward the top-left
    spoke = np.zeros((16, 16), bool)
    knob = np.zeros((16, 16), bool)
    for k in range(8):
        a = k * math.pi / 4
        for t in np.linspace(1.5, 7.6, 40):
            x, y = int(math.floor(cx + math.cos(a) * t)), int(math.floor(cy + math.sin(a) * t))
            if 0 <= x < 16 and 0 <= y < 16:
                (knob if t > 6.4 else spoke)[y, x] = True
    rim = (d >= 4.8) & (d <= 6.2)
    hub = d <= 2.4
    sp = spoke & ~rim & ~hub
    wrap = np.floor(d) % 2 == 0
    for y, x in zip(*np.nonzero(sp)):
        c = MEMB[3] if wrap[y, x] else MEMB[2]
        if lit[y, x] < -0.3:
            c = MEMB[2] if wrap[y, x] else MEMB[1]
        px(img, x, y, c)
    for y, x in zip(*np.nonzero(knob & ~rim)):
        px(img, x, y, DRIFT[5] if lit[y, x] > 0.2 else DRIFT[3])
    for y, x in zip(*np.nonzero(rim)):
        k = int(np.clip(round(3.6 + lit[y, x] * 1.5), 2, 5))
        if d[y, x] > 5.75 and lit[y, x] < 0.3:
            k -= 1
        px(img, x, y, DRIFT[k])
    for y, x in zip(*np.nonzero(hub)):
        k = int(np.clip(round(4.0 + lit[y, x] * 1.3 - (d[y, x] > 1.8) * 0.8), 2, 5))
        px(img, x, y, DRIFT[k])
    put(img, [(7, 7), (8, 7), (7, 8), (8, 8)], DRIFT[1])
    put(img, [(7, 7)], pix.hx('#3AE0D0'))
    out('drift_helm', img)


def ships_bell():
    """Aged purpur-bronze bell face lit from the left like a cylinder: a gold highlight streak, one raised shoulder
    ridge, sparse darker patina, a gold lip and a dark mouth band at the bottom."""
    pl = P('#24161F', '#4A2E44', '#6E4A66', '#8E6482', '#B88EA8', '#C9A24A', '#E6C778')
    col = np.array([3, 4, 4, 4, 4, 3, 3, 3, 3, 2, 2, 2, 2, 1, 1, 1])
    n = F(f_cluster, fx=4.0, seed=1521, vmix=0.4)
    nn = (n - n.mean()) / (n.std() + 1e-9)
    idx = col[None, :].repeat(16, 0)
    patina = (nn < -1.1) & (idx >= 2) & (idx <= 4)
    idx = np.where(patina, idx - 1, idx)
    idx = pix.despeckle(idx, 1, wrap=False)
    idx[0] = np.clip(col - 1, 1, 6)
    idx[1] = np.clip(col - 2, 1, 6)
    idx[3] = np.where(XX[0] < 7, 6, np.where(XX[0] < 11, 5, 3))
    idx[4] = 1
    idx[12] = np.where(XX[0] < 6, 6, np.where(XX[0] < 12, 5, 3))
    idx[13] = np.clip(col - 1, 1, 6)
    idx[14] = 0
    idx[15] = 0
    out('ships_bell', R(idx, pl))


def courier_cache():
    dk = END[0]
    eye = ["..####..",
           ".#oooo#.",
           "#oo..oo#",
           ".#oooo#.",
           "..####.."]
    front = endstone_base(1531)
    box_frame(front, END[4], dk)
    front[5, 1:15, :3] = dk
    front[6, 1:15, :3] = END[2]
    glow = np.zeros((16, 16), bool)
    sock = np.zeros((16, 16), bool)
    for j, r in enumerate(eye):
        for i, ch in enumerate(r):
            x, y = 4 + i, 8 + j
            if ch == '#':
                px(front, x, y, pix.hx('#B46CFF'))
                glow[y, x] = True
            elif ch == 'o':
                px(front, x, y, pix.hx('#E6CCFF'))
                glow[y, x] = True
            elif ch == '.':
                sock[y, x] = True
    # the pupil and the carved socket corners stay dark
    put(front, [(7, 10), (8, 10)], pix.hx('#2A0F40'))
    mask = glow | ((XX >= 7) & (XX <= 8) & (YY == 10))
    rim = grow(mask, 1) & ~mask
    front[rim & (YY > 6), :3] = END[1]
    # neat purpur hasp across the lid seam
    rect(front, 7, 3, 8, 6, PURPUR[3])
    put(front, [(7, 3)], PURPUR[5])
    put(front, [(8, 6), (8, 5)], PURPUR[1])
    for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        put(front, [(x, y)], PURPUR[3])
    out('courier_cache_front', front)
    emit('courier_cache_front_emissive', front, glow)
    side = endstone_base(1532)
    box_frame(side, END[4], dk)
    side[5, 1:15, :3] = dk
    side[6, 1:15, :3] = END[2]
    for (x0, y0) in [(1, 1), (13, 1), (1, 13), (13, 13)]:
        rect(side, x0, y0, x0 + 1, y0 + 1, PURPUR[3])
        put(side, [(x0, y0)], PURPUR[5])
        put(side, [(x0 + 1, y0 + 1)], PURPUR[1])
    out('courier_cache_side', side)
    top = endstone_base(1533)
    box_frame(top, END[4], dk)
    box_frame(top, END[2], END[5], inset=1)
    plate = (XX >= 5) & (XX <= 10) & (YY >= 5) & (YY <= 10)
    top[plate, :3] = PURPUR[3]
    shade_lr(top, plate, PURPUR[5], PURPUR[1])
    put(top, [(7, 7), (8, 8)], PURPUR[1])
    put(top, [(8, 7), (7, 8)], pix.hx('#B46CFF'))
    out('courier_cache_top', top)


def tide_bell():
    img = np.zeros((16, 16, 4), np.uint8)
    db = drift_base(1541)
    img[0:4] = db[0:4]
    img[0, :, :3] = DRIFT[5]
    img[3, :, :3] = DRIFT[0]
    put(img, [(2, 1), (13, 1)], DRIFT[0])
    put(img, [(2, 2), (13, 2)], DRIFT[4])
    n = F(f_cluster, fx=4.0, seed=1542, vmix=0.4)
    col = np.array([4, 4, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1])
    nn = (n - n.mean()) / (n.std() + 1e-9)
    idx = np.clip(col[None, :] + np.where(nn > 1.0, 1, np.where(nn < -1.1, -1, 0)), 0, 4)
    idx = pix.despeckle(idx, 1, wrap=False)
    plate = R(idx, TEALB)
    img[4:16] = plate[4:16]
    img[4, :, :3] = TEALB[0]          # shadow under the yoke
    img[5, :, :3] = TEALB[4]
    for x in range(16):                # tide-wave engraving
        y = int(round(9.5 + 1.6 * math.sin((x + 1) / 16 * 2 * math.pi * 1.5)))
        px(img, x, y, TEALB[0])
        px(img, x, y + 1, TEALB[4] if x < 12 else TEALB[3])
    for x in (3, 9, 14):
        px(img, x, 12, pix.hx('#7FF2E2'))
    img[14, :, :3] = TEALB[3]
    img[15, :, :3] = TEALB[0]
    out('tide_bell', img)


def roost_post():
    side = purpur_base(1551, WPURP)
    for x in (3, 7, 11):
        side[:, x, :3] = WPURP[1]
        side[:, x + 1, :3] = WPURP[4]
    side[0, :, :3] = WPURP[5]
    side[15, :, :3] = WPURP[0]
    for y0 in (3, 10):  # two lashings of voidroot fibre, twisted rope look
        for y in range(y0, y0 + 3):
            for x in range(16):
                k = (x + y) % 3
                c = VROOT[2] if k == 0 else VROOT[1]
                if y == y0 + 2:
                    c = VROOT[1] if k == 0 else VROOT[0]
                px(side, x, y, c)
        side[y0 + 3, :, :3] = np.round(side[y0 + 3, :, :3] * 0.72)
    out('roost_post_side', side)
    top = purpur_base(1552, WPURP)
    box_frame(top, WPURP[5], WPURP[0])
    pad = (XX >= 2) & (XX <= 13) & (YY >= 2) & (YY <= 13)
    pad &= ~(((XX == 2) | (XX == 13)) & ((YY == 2) | (YY == 13)))
    d = np.hypot(XX + 0.5 - 7, YY + 0.5 - 7)
    idx = np.clip(np.round(3 - d * 0.32), 1, 3).astype(int)
    m = R(idx, MEMB)
    top[pad] = m[pad]
    shade_lr(top, pad, MEMB[2], MEMB[0])
    st = edge_of(pad & ~edge_of(pad)) & ((XX + YY) % 2 == 0)
    top[st, :3] = pix.hx('#9E96B4')
    put(top, [(7, 7), (8, 8)], MEMB[0])
    put(top, [(7, 8), (8, 7)], MEMB[1])
    rim = grow(pad, 1) & ~pad
    top[rim & ((XX < 3) | (YY < 3)), :3] = WPURP[0]
    out('roost_post_top', top)


def gravity_plate():
    img = stillstone_base(1561)
    box_frame(img, STILL[4], STILL[0])
    d = np.hypot(XX + 0.5 - 8, YY + 0.5 - 8)
    ring = (d >= 3.6) & (d <= 4.8)
    inner = d < 3.6
    img[inner, :3] = STILL[1]
    img[inner & (d < 1.6), :3] = STILL[2]
    tl = (XX + YY) < 15
    img[ring, :3] = pix.hx('#3AE0D0')
    img[ring & tl, :3] = pix.hx('#8AF0E6')
    img[ring & (XX + YY > 17), :3] = pix.hx('#1E8E86')
    groove = (grow(ring, 1) & ~ring)
    img[groove & (d > 4.8) & tl, :3] = STILL[0]
    img[groove & (d < 3.6) & ~tl, :3] = STILL[0]
    out('gravity_plate', img)
    emit('gravity_plate_emissive', img, ring)


def anchor_frame_lamp():
    """block/anchor_frame's purpur border with the open lattice replaced by a glowing teal core panel."""
    img = np.zeros((16, 16, 4), np.uint8)
    bar = frame_mask(16, 2)
    base = purpur_base(203)
    img[bar] = base[bar]
    img[bar & ((XX == 0) | (YY == 0)), :3] = PURPUR[5]
    img[bar & ((XX == 15) | (YY == 15)), :3] = PURPUR[0]
    panel = ~bar
    d = np.abs(XX + 0.5 - 8) + np.abs(YY + 0.5 - 8)
    pl = P('#0E5F59', '#1E8E86', '#2FC9BB', '#8AF0E6', '#E6FFFB')
    idx = np.clip(4 - np.floor(d / 2.4), 0, 4).astype(int)
    idx = np.where(edge_of(panel) & ((XX == 2) | (YY == 2)), 1, idx)
    idx = np.where(edge_of(panel) & ((XX == 13) | (YY == 13)), 0, idx)
    p = R(idx, pl)
    img[panel] = p[panel]
    teal = grow(panel, 1) & bar & ~((XX == 0) | (YY == 0) | (XX == 15) | (YY == 15))
    img[teal, :3] = pix.hx('#2A9C93')
    img[teal & ((XX + YY) % 4 == 0), :3] = pix.hx('#5FD9CC')
    out('anchor_frame_lamp', img)
    # the glowing core: the bright middle of the panel and the bright glints of its teal rim
    emit('anchor_frame_lamp_emissive', img, (panel & (idx >= 2)) | (teal & ((XX + YY) % 4 == 0)))


# ===================================================================================================== wyrm nest
def wyrm_roost():
    """Nest of woven voidroot and chorus twigs: on top the twigs run in rings around a hollow holding pale shell
    fragments; on the side they lie in rough horizontal courses."""
    gap = pix.hx('#22182C')
    shell = P('#9A8EAE', '#C9C0D6', '#EDE6F4')
    rng = np.random.RandomState(1571)
    top = np.zeros((16, 16, 4), np.uint8)
    top[..., :3] = gap
    top[..., 3] = 255
    for k in range(64):
        r = rng.uniform(2.6, 11.0)
        a0 = rng.uniform(0, 2 * math.pi)
        span = rng.uniform(0.8, 1.8)
        cols = CHORUS if k % 3 == 1 else VROOT
        prev = None
        for t in np.linspace(0, span, 30):
            a = a0 + t
            x = int(math.floor(7.5 + math.cos(a) * r))
            y = int(math.floor(7.5 + math.sin(a) * r * 0.95))
            if (x, y) == prev or not (0 <= x < 16 and 0 <= y < 16):
                continue
            prev = (x, y)
            lit = math.cos(a + math.pi / 4) < 0
            px(top, x, y, cols[2] if lit else cols[1])
            if y + 1 < 16 and not lit:
                px(top, x, y + 1, cols[0])
    hollow = disc(16, 8, 8, 2.6)
    top[hollow, :3] = pix.hx('#1A1222')
    for (x, y, s) in [(6, 6, 0), (8, 8, 1), (5, 9, 2), (10, 6, 2)]:
        shp = [[(0, 0), (1, 0), (0, 1)], [(0, 0), (1, 0), (2, 0)], [(1, 0), (0, 1), (1, 1)]][s]
        for i, (dx, dy) in enumerate(shp):
            px(top, x + dx, y + dy, shell[2] if i == 0 else shell[1])
        px(top, x + shp[-1][0], y + shp[-1][1] + 1, shell[0])
    out('wyrm_roost_top', top)
    side = np.zeros((16, 16, 4), np.uint8)
    side[..., :3] = gap
    side[..., 3] = 255
    rng = np.random.RandomState(1572)
    for k in range(40):
        cols = CHORUS if k % 3 == 1 else VROOT
        y0 = rng.uniform(0, 16)
        x0 = rng.uniform(0, 16)
        L = rng.randint(5, 11)
        slope = rng.uniform(-0.35, 0.35)
        for i in range(L):
            x = int(x0 + i) % 16
            y = int(math.floor(y0 + slope * i)) % 16
            px(side, x, y, cols[2] if i % 4 else cols[1])
            px(side, x, (y + 1) % 16, cols[0])
    side[0, :, :3] = np.where(((XX[0] % 5) < 3)[:, None], CHORUS[2], VROOT[2])
    out('wyrm_roost_side', side)


def tidewrack():
    a = F(f_cells, ss=4, ch=None, fx=4.6, seed=1581, rnd=0.9)
    cellv, edge, f1 = a[..., 0], a[..., 1], a[..., 2]
    tone = np.floor(pix.norm(cellv) * 2.999).astype(int)
    idx = tone + 3
    g = np.roll(np.roll(f1, 1, 0), 1, 1) - np.roll(np.roll(f1, -1, 0), -1, 1)
    idx = np.where(g > 0.04, idx - 1, idx)
    idx = np.where(g < -0.04, idx + 1, idx)
    idx = np.clip(idx, 2, 5)
    silt = edge < np.quantile(edge, 0.22)
    sn = F(f_noise, fx=6.0, seed=1582, det=1.0)
    idx = np.where(silt, np.where(sn > 0.5, 1, 0), idx)
    idx = pix.despeckle(idx, 2)
    pl = [pix.hx('#3E6A66'), pix.hx('#5E928A'), DRIFT[3], DRIFT[4], DRIFT[5], pix.hx('#8676A6')]
    img = R(idx, pl)
    for path in crack_paths(1583, 3, length=(6, 10)):  # tangled voidroot
        for i, (x, y) in enumerate(path):
            px(img, x % 16, y % 16, VROOT[2] if i % 4 == 0 else VROOT[0])
    out('tidewrack', img)


# ======================================================================================================== zenith
def skystone_field(seed):
    a = F(f_bands, ch=0, nb=3, seed=seed, wob=0.10, grain=0.5)
    c = F(f_cluster, fx=4.0, seed=seed + 1, vmix=0.45)
    return 0.55 * pix.norm(a) + 0.45 * pix.norm(c)


def gold_specks(img, seed, n, avoid=None):
    for x, y, b in stars(seed, n, avoid=avoid, big=0.3):
        px(img, x, y, SKYGOLD if b else SKYGOLD_D)


def skystone_family():
    f = skystone_field(1601)
    idx = stone(f, 6, [0.06, 0.16, 0.30, 0.26, 0.16, 0.06], emb=0.8)
    img = R(idx, SKY)
    gold_specks(img, 1601, 4)
    out('skystone', img)

    f2 = F(f_noise, fx=4.0, fy=2.0, seed=1602, det=1.0)
    idx = np.clip(pix.quantile_index(f2, [0.3, 0.5, 0.2]) + 2, 0, 5)
    idx = pix.despeckle(idx, 2)
    idx = bevel_frame(idx, 6, 1, light=1, dark=-2)
    inner = (np.maximum(np.abs(XX - 7.5), np.abs(YY - 7.5)) == 5.5)
    idx = np.where(inner & (XX + YY < 15), 2, np.where(inner, 5, idx))
    img = R(idx, SKY)
    px(img, 4, 4, SKYGOLD)
    px(img, 11, 11, SKYGOLD_D)
    out('polished_skystone', img)

    lay = brick_layout([4, 4, 4, 4], [[0, 8], [4, 12], [2, 10], [6, 14]])
    fb = skystone_field(1603)
    idx = bricks(fb, 6, lay, [0.15, 0.3, 0.35, 0.2], seed=1603)
    img = R(idx, SKY)
    gold_specks(img, 1603, 2, avoid=lay[0])
    out('skystone_bricks', img)

    lay = brick_layout([8, 8], [[0, 8]])
    ft = skystone_field(1604)
    idx = bricks(ft, 6, lay, [0.2, 0.35, 0.3, 0.15], per_brick=0.7, seed=1604)
    img = R(idx, SKY)
    for (x, y) in [(3, 3), (11, 12)]:
        px(img, x, y, SKYGOLD)
    out('skystone_tiles', img)

    fc = skystone_field(1605)
    idx = stone(fc, 4, [0.25, 0.45, 0.30], emb=0.6) + 2
    idx = bevel_frame(idx, 6, 1, light=1, dark=-2)
    fr = np.zeros((16, 16), bool)
    fr[2:14, 2] = fr[2:14, 13] = fr[2, 2:14] = fr[13, 2:14] = True
    idx = np.where(fr, 1, idx)
    img = R(idx, SKY)
    motif = ["....+...",
             "...+*+..",
             "....+...",
             "........",
             "..#..#..",
             "..####..",
             "..#..#..",
             "..####..",
             "..#..#..",
             "..####..",
             "..#..#.."]
    for j, r in enumerate(motif):
        for i, ch in enumerate(r):
            x, y = 4 + i, 3 + j
            if ch in '#+':
                px(img, x, y, SKYGOLD)
                if x + 1 < 13 and y + 1 < 13 and motif[min(j + 1, len(motif) - 1)][min(i + 1, 7)] == '.':
                    px(img, x + 1, y + 1, SKY[1])
            elif ch == '*':
                px(img, x, y, pix.hx('#FFF6D8'))
    out('chiseled_skystone', img)


def halo_glass():
    f = F(f_noise, fx=2.0, seed=1611, det=1.0, shear=1)
    idx = pix.despeckle(pix.quantile_index(f, [0.3, 0.45, 0.25]), 2)
    img = R(idx, P('#BFDDF2', '#CFE6F7', '#E6F4FF'), 140)
    fm = frame_mask()
    img[fm, :3] = pix.hx('#9FB8D6')
    img[fm & ((XX == 0) | (YY == 0)), :3] = pix.hx('#E6F4FF')
    img[fm, 3] = 255
    d = np.hypot(XX + 0.5 - 8, YY + 0.5 - 8)
    ring = np.abs(d - 4.6) < 0.62
    img[ring, :3] = HALO[0]
    img[ring & (XX + YY < 14), :3] = HALO[2]
    img[ring, 3] = 255
    for (x, y) in [(3, 2), (2, 3), (12, 13)]:
        px(img, x, y, HALO[3], 200)
    out('halo_glass', img)
    emit('halo_glass_emissive', img, ring)


def orrery_ring():
    pl = [PURPUR[0], PURPUR[2], PURPUR[3], PURPUR[5], pix.hx('#CFE6F7'), pix.hx('#E6F4FF'), GOLD[1], GOLD[3]]
    f = F(f_cluster, fx=4.0, seed=1621, vmix=0.3)
    idx = stone(f, 3, [0.3, 0.45, 0.25], emb=0.5) + 1
    for y0 in (2, 11):
        idx[y0 - 1] = 0
        idx[y0:y0 + 3] = 4
        idx[y0] = 5
        idx[y0 + 3] = 3
    idx[0] = 3
    idx[15] = 0
    for y in (7,):
        for x in (1, 5, 9, 13):
            idx[y, x] = 7
            idx[y + 1, x + 1] = 6
            idx[y, x + 1] = 7
            idx[y + 1, x] = 6
    for y0 in (2, 11):
        for x in range(0, 16, 4):
            idx[y0 + 1, x] = 3
    out('orrery_ring', R(idx, pl))


def orrery_lens():
    d = np.hypot(XX + 0.5 - 8, YY + 0.5 - 8)
    tl = (XX + YY) < 15
    pl = P('#06060A', '#0E0E16', '#2B2635', '#5E6474', '#AEB6C8', '#9A7A30', '#C9A24A', '#F4F8FF')
    idx = np.where(d < 7.6, 1, 2)
    idx = np.where((d >= 6.6) & (d < 7.6), np.where(tl, 4, 3), idx)
    idx = np.where(np.abs(d - 4.9) < 0.55, np.where(tl, 6, 5), idx)
    idx = np.where(np.abs(d - 2.9) < 0.5, np.where(tl, 4, 3), idx)
    idx = np.where(d < 2.2, 0, idx)
    pin = (XX >= 7) & (XX <= 8) & (YY >= 7) & (YY <= 8)
    idx = np.where(pin, 7, idx)
    corner = d >= 7.6
    n = F(f_noise, fx=4.0, seed=1631, det=1.0)
    idx = np.where(corner & (n > 0.55), 3, idx)
    img = R(idx, pl)
    px(img, 4, 5, pl[4])
    px(img, 5, 4, pl[4])
    out('orrery_lens', img)
    emit('orrery_lens_emissive', img, pin)


def orrery_socket():
    top = R(stone(skystone_field(1641), 4, [0.25, 0.45, 0.3], emb=0.6) + 2, SKY)
    box_frame(top, SKY[5], SKY[0])
    box_frame(top, SKY[1], SKY[4], inset=1)
    slot = disc(16, 8, 6, 2.0)
    slot |= (XX >= 7) & (XX <= 8) & (YY >= 6) & (YY <= 12)
    slot |= (XX >= 6) & (XX <= 9) & (YY >= 11) & (YY <= 12)
    rim = grow(slot, 1) & ~slot
    top[rim & (XX + YY < 15), :3] = SKY[0]
    top[rim & (XX + YY >= 15), :3] = SKY[5]
    e = edge_of(slot)
    top[slot, :3] = pix.hx('#1E2638')
    top[e, :3] = SKYGOLD
    top[e & ((XX < 8) | (YY <= 4)), :3] = pix.hx('#FFF2C0')
    out('orrery_socket_top', top)
    emit('orrery_socket_top_emissive', top, e)
    side = R(stone(skystone_field(1642), 4, [0.25, 0.45, 0.3], emb=0.6) + 2, SKY)
    shaft = (YY >= 4) & (YY <= 11) & ((XX < 3) | (XX > 12))
    side[shaft, :3] = np.round(side[shaft, :3] * 0.72)
    side[0, :, :3] = SKY[5]
    side[3, :, :3] = SKY[0]
    side[12, :, :3] = SKY[5]
    side[15, :, :3] = SKY[0]
    side[4:12, 3, :3] = SKY[5]
    side[4:12, 12, :3] = SKY[1]
    put(side, [(7, 1), (8, 1)], SKYGOLD)
    put(side, [(7, 14), (8, 14)], SKYGOLD_D)
    out('orrery_socket_side', side)


def starfall_shard():
    pl = P('#5E4A1E', '#8A6E2C', '#B8963E', '#D9BE6A', '#EFDCA0', '#FBF0CC', '#FFFFFF')
    defs = [(4.5, 15.8, 1.2, 8.5, 1.3, 1.2, 0.36), (11.5, 15.8, 14.6, 9.5, 1.3, 1.2, 0.36),
            (7.0, 15.8, 5.0, 3.0, 1.6, 1.5, 0.34), (9.5, 15.8, 11.5, 5.5, 1.4, 1.3, 0.34),
            (8.2, 15.9, 8.6, 0.6, 1.9, 1.7, 0.3)]
    m, f, s, sid = spikes(defs, cover=0.42)
    idx = crystal_index(m, f, s, 7, base=(4, 3, 2), grad=-1.4)
    idx = np.where((sid >= 0) & (sid < 2), np.maximum(idx - 1, 0), idx)
    tips = m & (s > 0.72)
    idx = np.where(tips, np.maximum(idx, 5), idx)
    idx = np.where(m & (s > 0.88), 6, idx)
    img = R(idx, pl)
    out('starfall_shard', img)
    emit('starfall_shard_emissive', img, tips)


# ====================================================================================================== undertow
def coral_stage(defs, name, fan=False):
    """Branching black coral modelled as recursive tubes in Blender, toon-rendered (top-left light) and point
    sampled to 16x16; silver tips are small spheres."""
    st = bk.Stage(view=(-8, 0, 0), ortho=1.0, light_cam=(-0.6, 0.6, 0.5))
    st.mat('c', bk.Toon(P('#0E1114', '#1A1F25', '#2E353D', '#4A535E'), cuts=[-1, -0.35, 0.2, 0.62]))
    st.mat('tip', bk.Toon(P('#8F9AA6', '#C9D2DC'), cuts=[-1, 0.1]))
    rng = np.random.RandomState(sum(map(ord, name)))

    def grow_branch(p, d, L, r, depth):
        q = (p[0] + d[0] * L, p[1] + d[1] * L, p[2] + d[2] * L)
        mid = ((p[0] + q[0]) / 2 + rng.uniform(-0.02, 0.02), (p[1] + q[1]) / 2, (p[2] + q[2]) / 2)
        st.tube('c', [p, mid, q], r=r)
        if depth == 0:
            st.sphere('tip', loc=q, r=r * 1.45, seg=8)
            return
        n = 2 if fan or rng.rand() < 0.6 else 3
        for i in range(n):
            ax = (i - (n - 1) / 2) * (0.75 if not fan else 0.6) + rng.uniform(-0.15, 0.15)
            ay = 0.0 if fan else rng.uniform(-0.5, 0.5)
            dx = d[0] * math.cos(ax) - d[2] * math.sin(ax)
            dz = d[0] * math.sin(ax) + d[2] * math.cos(ax)
            dy = d[1] + ay * 0.5
            nrm = math.sqrt(dx * dx + dy * dy + dz * dz)
            grow_branch(q, (dx / nrm, dy / nrm, dz / nrm), L * rng.uniform(0.62, 0.78), r * 0.82, depth - 1)
    for (x0, ang, L, r, depth) in defs:
        a = math.radians(ang)
        grow_branch((x0, 0.0, -0.5), (math.sin(a), 0.0, math.cos(a)), L, r, depth)
    return bk.downsample_sprite(st.render(64, name), 16, cover=0.4)


def null_coral():
    img = coral_stage([(0.0, 0, 0.30, 0.04, 3), (-0.2, -30, 0.24, 0.034, 2), (0.2, 32, 0.24, 0.034, 2)],
                      'null_coral')
    out('null_coral', img)
    fan = coral_stage([(0.0, a, 0.2, 0.03, 2) for a in (-72, -40, -12, 14, 42, 74)], 'null_coral_fan', fan=True)
    out('null_coral_fan', fan)


def null_coral_family():
    f = F(f_cluster, fx=5.0, seed=1661, vmix=0.4)
    idx = stone(f, 4, [0.2, 0.35, 0.3, 0.15], emb=0.8)
    pl = CORAL[:4] + [CORAL_S[0], CORAL_S[1], CORAL_S[2]]
    vm = np.zeros((16, 16), bool)
    hi = np.zeros((16, 16), bool)
    for k, path in enumerate(crack_paths(1662, 3, length=(9, 14))):  # silver branch veins
        for i, (x, y) in enumerate(path):
            vm[y % 16, x % 16] = True
            if i % 5 == 2:
                hi[y % 16, x % 16] = True
    idx = np.where(vm, 4, idx)
    idx = np.where(hi, 5, idx)
    out('null_coral_block', R(idx, pl))

    lay = brick_layout([4, 4, 4, 4], [[0, 8], [4, 12], [0, 8], [4, 12]])
    fb = F(f_cluster, fx=6.0, seed=1664, vmix=0.4)
    idx = bricks(fb, 4, lay, [0.35, 0.4, 0.25], seed=1664)
    img = R(idx, CORAL[:4])
    for (x, y) in [(2, 1), (10, 6), (5, 9), (13, 13)]:
        px(img, x, y, CORAL_S[1])
    for (x, y) in [(3, 1), (6, 9)]:
        px(img, x, y, CORAL_S[0])
    out('null_coral_bricks', img)

    f2 = F(f_noise, fx=3.0, seed=1665, det=1.0)
    idx = np.clip(pix.quantile_index(f2, [0.35, 0.45, 0.2]) + 1, 1, 3)
    idx = pix.despeckle(idx, 2)
    idx = bevel_frame(idx, 4, 1, light=1, dark=-1)
    img = R(idx, CORAL[:4])
    for (x, y, c) in [(4, 4, 2), (11, 6, 1), (6, 11, 1), (12, 12, 0), (8, 8, 0)]:
        px(img, x, y, CORAL_S[c])
    out('polished_null_coral', img)


def archive_shelf():
    pl = {'fr_l': DRIFT[4], 'fr_d': DRIFT[2], 'bg': pix.hx('#0A080E'), 'tab': pix.hx('#C9D6D2'),
          'tab_d': pix.hx('#9FB3AE'), 'cod': pix.hx('#4A3A6E'), 'cod_d': pix.hx('#2E2448'), 'gly': CAUSTIC}
    img = np.zeros((16, 16, 4), np.uint8)
    img[..., 3] = 255
    img[..., :3] = pl['bg']
    for y in (0, 7, 8, 15):
        img[y, :, :3] = pl['fr_l'] if y in (0, 8) else pl['fr_d']
    img[:, 0, :3] = pl['fr_l']
    img[:, 15, :3] = pl['fr_d']
    glow = np.zeros((16, 16), bool)
    # (x, width, height, kind) per shelf row; kind t = pale tablet, c = dark codex, g = codex with a glyph
    rows = [(1, [(1, 2, 6, 'c'), (3, 1, 5, 't'), (4, 2, 6, 'g'), (7, 1, 4, 't'), (9, 2, 6, 'c'), (11, 2, 5, 't'),
                 (13, 2, 6, 'c')]),
            (9, [(1, 2, 6, 't'), (3, 2, 6, 'c'), (5, 1, 5, 't'), (6, 2, 6, 'c'), (8, 2, 4, 't'), (11, 2, 6, 'g'),
                 (13, 1, 5, 't')])]
    for y0, books in rows:
        for x0, w, h, kind in books:
            for x in range(x0, x0 + w):
                for y in range(y0 + 6 - h, y0 + 6):
                    if kind == 't':
                        c = pl['tab'] if x == x0 else pl['tab_d']
                    else:
                        c = pl['cod'] if x == x0 else pl['cod_d']
                    px(img, x, y, c)
            if kind in 'cg':
                for x in range(x0, x0 + w):  # spine bands
                    for y in (y0 + 6 - h + 1, y0 + 4):
                        px(img, x, y, pl['fr_l'] if kind == 'c' else pl['cod_d'])
            if kind == 'g':
                for y in (y0 + 6 - h + 2, y0 + 6 - h + 3):
                    px(img, x0, y, pl['gly'])
                    glow[y, x0] = True
    out('archive_shelf_front', img)
    emit('archive_shelf_front_emissive', img, glow)
    side = drift_base(1671)
    box_frame(side, DRIFT[5], DRIFT[1])
    side[7, 1:15, :3] = DRIFT[1]
    side[8, 1:15, :3] = DRIFT[5]
    out('archive_shelf_side', side)
    top = drift_base(1672)
    box_frame(top, DRIFT[5], DRIFT[1])
    box_frame(top, DRIFT[2], DRIFT[4], inset=2)
    out('archive_shelf_top', top)


def bell_winch():
    iron = P('#26262E', '#3E3E48', '#6A6A78', '#9C9CAA')
    side = drift_base(1681)
    drum = (XX >= 2) & (XX <= 13) & (YY >= 3) & (YY <= 12)
    rowshade = np.array([0, 0, 0, 4, 5, 5, 4, 4, 3, 3, 3, 2, 1, 0, 0, 0])
    dr = R(np.clip(rowshade[:, None].repeat(16, 1), 0, 5), DRIFT)
    side[drum] = dr[drum]
    chain = drum & (XX % 3 != 1) & (YY >= 4) & (YY <= 11)
    side[chain & (YY % 2 == 0), :3] = iron[2]
    side[chain & (YY % 2 == 1), :3] = iron[1]
    side[chain & (YY == 4), :3] = iron[3]
    side[chain & (YY == 11), :3] = iron[0]
    box_frame(side, DRIFT[5], DRIFT[1])
    side[2, 2:14, :3] = DRIFT[0]
    side[13, 2:14, :3] = DRIFT[0]
    # iron crank on the axle (right upright)
    put(side, [(14, 7), (14, 8), (15, 8)], iron[2])
    put(side, [(14, 9), (14, 10)], iron[1])
    put(side, [(13, 11), (14, 11)], iron[3])
    put(side, [(13, 12)], iron[0])
    out('bell_winch_side', side)
    top = drift_base(1682)
    box_frame(top, DRIFT[5], DRIFT[1])
    band = (YY >= 5) & (YY <= 10) & (XX >= 1) & (XX <= 14)
    top[band, :3] = DRIFT[3]
    top[band & (YY == 5), :3] = DRIFT[5]
    top[band & (YY == 10), :3] = DRIFT[1]
    ch = band & (XX % 3 != 1) & (YY >= 6) & (YY <= 9)
    top[ch, :3] = iron[2]
    top[ch & (YY == 6), :3] = iron[3]
    top[ch & (YY == 9), :3] = iron[0]
    put(top, [(1, 7), (1, 8), (14, 7), (14, 8)], iron[1])
    out('bell_winch_top', top)


def mural_lines():
    """The carved motif: a world with an eye-ring sinking into the tide's waves."""
    art = ["....####....",
           "..##....##..",
           ".#........#.",
           "#....##....#",
           "#...#..#...#",
           "............",
           ".##..##..##.",
           "#..##..##..#",
           "............",
           "..##..##..##",
           ".#..##..##..",
           "............"]
    m = np.zeros((16, 16), bool)
    for j, r in enumerate(art):
        for i, ch in enumerate(r):
            if ch == '#':
                m[2 + j, 2 + i] = True
    return m


def mural_panels():
    a = F(f_bands, ch=0, nb=4, seed=1691, wob=0.08, grain=0.5)
    base = R(stone(a, 5, [0.08, 0.22, 0.34, 0.26, 0.10], emb=0.5), SHALE)
    box_frame(base, SHALE[4], SHALE[0])
    box_frame(base, SHALE[0], SHALE[4], inset=1)
    m = mural_lines()
    dark = base.copy()
    lip = np.roll(np.roll(m, 1, 0), 1, 1) & ~m & ~frame_mask(16, 2)
    dark[m, :3] = pix.hx('#141218')
    dark[lip, :3] = SHALE[4]
    out('mural_panel_dark', dark)
    lit = base.copy()
    lit[lip, :3] = SHALE[4]
    lit[m, :3] = pix.hx('#C98AC0')
    hi = m & (np.roll(~m, 1, 0) | np.roll(~m, 1, 1))
    lit[hi, :3] = pix.hx('#E8B4DE')
    out('mural_panel_lit', lit)
    emit('mural_panel_lit_emissive', lit, m)


def archive_tiles():
    pl = P('#7E938E', '#9FB3AE', '#B4C6C2', '#C9D6D2', '#DCE6E2', '#A9CEC6', '#8EBFB4')
    lay = brick_layout([8, 8], [[0, 8]])
    f = F(f_cluster, fx=4.0, seed=1701, vmix=0.4)
    idx = bricks(f, 5, lay, [0.2, 0.35, 0.3, 0.15], per_brick=0.5, seed=1701)
    for (x, y) in [(1, 1), (9, 1), (1, 9), (9, 9)]:
        idx[y, x] = 2
    for (x, y) in [(6, 6), (14, 6), (6, 14), (14, 14)]:
        idx[y, x] = 1
    s = F(f_noise, fx=3.0, seed=1702, det=2.0)
    s2 = F(f_noise, ss=2, fx=6.0, seed=1703, det=1.0)
    stain = (s > np.quantile(s, 0.86)) & ~lay[0]
    idx = np.where(stain, np.where(s2 > 0.5, 5, 6), idx)
    idx = pix.despeckle(idx, 1, keep=lay[0])
    out('archive_tiles', R(idx, pl))


BUILDERS = [wardens_lantern, drift_buoy, lantern_moth_jar, causeway_socket, causeway_plaque, drift_helm, ships_bell,
            courier_cache, tide_bell, roost_post, gravity_plate, anchor_frame_lamp, wyrm_roost, tidewrack,
            skystone_family, halo_glass, orrery_ring, orrery_lens, orrery_socket, starfall_shard, null_coral,
            null_coral_family, archive_shelf, bell_winch, mural_panels, archive_tiles]


def build():
    for b in BUILDERS:
        b()
