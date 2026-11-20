"""Functional / structure blocks, the Fracture rift animation and the Stillwater fluid."""
import math
import numpy as np

from . import pix, bk
from .tex import (save, want, F, Fraw, stone, bricks, brick_layout, bevel_frame, frame_mask, disc, line_pts, grow,
                  edge_of, f_noise, f_cluster, f_bands, f_cells, TAU, _off)
from .blocks import (P, R, END, PURPUR, WPURP, PURP_DARK, STILL, STAR, SHALE, SHALE_V, SHALE_V2, DRIFT, NULL,
                     NULL_VEIN, LUMEN, OBSID, PALE, crack_paths, apply_stars, stars)

YY, XX = np.mgrid[0:16, 0:16]
TEAL = P('#0E4F4A', '#1E8E86', '#3AE0D0', '#8AF0E6', '#B8FFF7')
MAG = P('#A3196E', '#FF3FB4', '#FFB6F0', '#FFFFFF')
OVER = {'grass': pix.hx('#6FBF4A'), 'grass2': pix.hx('#4E9A33'), 'dirt': pix.hx('#8A5F3C'), 'dirt2': pix.hx('#6B4428'),
        'stone': pix.hx('#8D8D8D'), 'stone2': pix.hx('#6E6E6E'), 'sand': pix.hx('#E3D59A'), 'red': pix.hx('#FF5A7A'),
        'yel': pix.hx('#FFD84A'), 'bark': pix.hx('#5E4126'), 'bark2': pix.hx('#7A5634'), 'ring': pix.hx('#B08A55')}


def endstone_base(seed, n=5, emb=1.0):
    f = F(f_cluster, fx=4.0, seed=seed, vmix=0.5)
    return R(stone(f, 5, [0.06, 0.18, 0.30, 0.30, 0.16], emb=emb) + 1, END)


def put(img, pts, c, a=255):
    for x, y in pts:
        if 0 <= x < 16 and 0 <= y < 16:
            img[y, x, :3] = c
            img[y, x, 3] = a


def rect(img, x0, y0, x1, y1, c):
    img[y0:y1 + 1, x0:x1 + 1, :3] = c
    img[y0:y1 + 1, x0:x1 + 1, 3] = 255


def box_frame(img, c_light, c_dark, inset=0):
    a, b = inset, 15 - inset
    for i in range(a, b + 1):
        put(img, [(i, a), (a, i)], c_light)
        put(img, [(i, b), (b, i)], c_dark)


# ================================================================================================= the hoard
def overworld_patch(img, x, y, kind):
    o = OVER
    if kind == 'grass':
        put(img, [(x, y), (x + 1, y), (x + 2, y)], o['grass'])
        put(img, [(x, y + 1), (x + 2, y + 1)], o['grass2'])
        put(img, [(x + 1, y + 1), (x, y + 2), (x + 1, y + 2), (x + 2, y + 2)], o['dirt'])
        put(img, [(x + 2, y + 2)], o['dirt2'])
    elif kind == 'log':
        put(img, [(x, y), (x, y + 1), (x, y + 2)], o['bark'])
        put(img, [(x + 1, y), (x + 1, y + 1), (x + 1, y + 2)], o['bark2'])
        put(img, [(x + 2, y), (x + 2, y + 2)], o['bark'])
        put(img, [(x + 2, y + 1)], o['ring'])
    elif kind == 'ore':
        put(img, [(x, y), (x + 1, y), (x + 2, y), (x, y + 1), (x + 2, y + 1), (x + 1, y + 2), (x + 2, y + 2)],
            o['stone'])
        put(img, [(x + 1, y + 1)], o['yel'])
        put(img, [(x, y + 2)], o['stone2'])
    elif kind == 'flower':
        put(img, [(x + 1, y)], o['red'])
        put(img, [(x, y + 1), (x + 2, y + 1)], o['red'])
        put(img, [(x + 1, y + 1)], o['yel'])
        put(img, [(x + 1, y + 2)], o['grass2'])


def hoard_cache():
    dk, lt = END[0], END[5]
    side = endstone_base(141)
    box_frame(side, END[4], dk)
    side[5, 1:15, :3] = dk           # lid seam
    side[6, 1:15, :3] = END[2]
    overworld_patch(side, 2, 9, 'grass')
    overworld_patch(side, 10, 10, 'log')
    overworld_patch(side, 9, 1, 'ore')
    if want('block/hoard_cache_side'):
        save('block/hoard_cache_side', side)
    front = endstone_base(142)
    box_frame(front, END[4], dk)
    front[5, 1:15, :3] = dk
    front[6, 1:15, :3] = END[2]
    overworld_patch(front, 2, 10, 'ore')
    overworld_patch(front, 11, 9, 'grass')
    overworld_patch(front, 2, 1, 'log')
    # latch: crude iron hasp straddling the lid seam
    rect(front, 7, 3, 8, 7, OVER['stone'])
    put(front, [(7, 3), (7, 4)], pix.hx('#C6C6C6'))
    put(front, [(8, 7), (7, 7)], OVER['stone2'])
    put(front, [(6, 5), (9, 5)], pix.hx('#4A4A4A'))
    if want('block/hoard_cache_front'):
        save('block/hoard_cache_front', front)
    top = endstone_base(143)
    box_frame(top, END[4], dk)
    overworld_patch(top, 3, 3, 'grass')
    overworld_patch(top, 10, 4, 'flower')
    overworld_patch(top, 6, 10, 'log')
    put(top, [(12, 11), (13, 11), (12, 12)], OVER['sand'])
    if want('block/hoard_cache_top'):
        save('block/hoard_cache_top', top)


# ============================================================================================ spires furniture
def purpur_base(seed, pl=PURPUR, fx=4.0):
    f = F(f_cluster, fx=fx, seed=seed, vmix=0.3)
    return R(stone(f, 4, [0.2, 0.35, 0.3, 0.15], emb=0.5, desp=2) + 1, pl)


def keystone_socket():
    top = purpur_base(151)
    box_frame(top, PURPUR[5], PURPUR[0])
    box_frame(top, PURPUR[1], PURPUR[4], inset=1)
    # key-shaped hollow: ring bow at top, shaft, two teeth
    hollow = np.zeros((16, 16), bool)
    hollow |= disc(16, 8, 5, 2.9) & ~disc(16, 8, 5, 1.2)
    hollow[7:13, 7:9] = True
    hollow[10, 9:11] = True
    hollow[12, 9:11] = True
    rim = grow(hollow, 1) & ~hollow
    top[rim, :3] = PURP_DARK
    glow = hollow.copy()
    top[hollow, :3] = MAG[1]
    core = hollow & ((XX == 7) | (YY == 3))
    top[core, :3] = MAG[2]
    if want('block/keystone_socket_top'):
        save('block/keystone_socket_top', top)
    if want('block/keystone_socket_top_emissive'):
        save('block/keystone_socket_top_emissive', pix.emissive_from(top, glow))
    side = purpur_base(152)
    # pedestal: wide cap (rows 0-3) and foot (12-15), recessed shaft between
    shaft = (YY >= 4) & (YY <= 11) & ((XX < 3) | (XX > 12))
    side[shaft, :3] = np.round(side[shaft, :3] * 0.62)
    side[3, :, :3] = PURPUR[0]
    side[12, :, :3] = PURPUR[5]
    side[0, :, :3] = PURPUR[5]
    side[15, :, :3] = PURPUR[0]
    side[4:12, 3, :3] = PURPUR[5]
    side[4:12, 12, :3] = PURPUR[0]
    put(side, [(7, 1), (8, 1)], MAG[0])
    if want('block/keystone_socket_side'):
        save('block/keystone_socket_side', side)


def ornate(img, pl):
    box_frame(img, pl[5], pl[0])
    box_frame(img, pl[0], pl[4], inset=2)
    for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        put(img, [(x, y)], pix.hx('#E3CBE8'))
    return img


def spire_vault():
    side = ornate(purpur_base(161), PURPUR)
    dia = (np.abs(XX - 7.5) + np.abs(YY - 7.5)) <= 3.6
    dedge = edge_of(dia)
    side[dia, :3] = PURPUR[4]
    side[dedge & (XX + YY <= 15), :3] = PURPUR[5]
    side[dedge & (XX + YY > 15), :3] = PURPUR[1]
    put(side, [(7, 7), (8, 8)], PURPUR[2])
    if want('block/spire_vault_side'):
        save('block/spire_vault_side', side)
    top = ornate(purpur_base(162), PURPUR)
    for i in range(3, 13):
        put(top, [(i, 7), (7, i)], PURPUR[1])
        put(top, [(i, 8), (8, i)], PURPUR[4])
    put(top, [(7, 7), (8, 7), (7, 8), (8, 8)], pix.hx('#E3CBE8'))
    if want('block/spire_vault_top'):
        save('block/spire_vault_top', top)
    front = ornate(purpur_base(163), PURPUR)
    lock = (np.abs(XX - 7.5) * 1.25 + np.abs(YY - 7.5)) <= 3.6
    setting = grow(lock, 1) & ~lock
    front[setting, :3] = PURP_DARK
    front[lock, :3] = MAG[1]
    front[lock & (XX + YY < 15), :3] = MAG[2]
    front[lock & (XX + YY > 16), :3] = MAG[0]
    put(front, [(6, 6), (7, 5)], MAG[3])
    if want('block/spire_vault_front'):
        save('block/spire_vault_front', front)
    if want('block/spire_vault_front_emissive'):
        save('block/spire_vault_front_emissive', pix.emissive_from(front, lock))


def drift_base(seed):
    f = F(f_noise, fx=9.0, fy=2.5, seed=seed, det=3.0, rough=0.6, shear=1)
    return R(stone(f, 5, [0.1, 0.25, 0.3, 0.25, 0.1], emb=0.6) + 1, DRIFT)


def teal_glass(img, m):
    f = F(f_noise, fx=3.0, seed=171, det=1.0)
    idx = np.clip(pix.quantile_index(f, [0.35, 0.45, 0.2]) + 1, 1, 3)
    idx = np.where(edge_of(m) & ((XX + YY) % 1 == 0) & (YY == YY), np.where(XX + YY < 16, 3, 0), idx)
    g = R(idx, TEAL)
    img[m] = g[m]
    # diagonal glints
    for x, y in zip(XX[m], YY[m]):
        if (x - y) % 7 == 0 and img[y, x, :3].sum() > 0:
            img[y, x, :3] = TEAL[4]


def drift_cache():
    side = drift_base(172)
    box_frame(side, DRIFT[5], DRIFT[0])
    win = (YY >= 6) & (YY <= 9) & (XX >= 3) & (XX <= 12)
    teal_glass(side, win)
    side[5, 3:13, :3] = DRIFT[0]
    side[10, 3:13, :3] = DRIFT[5]
    if want('block/drift_cache_side'):
        save('block/drift_cache_side', side)
    top = drift_base(173)
    box_frame(top, DRIFT[5], DRIFT[0])
    cross = (((XX >= 7) & (XX <= 8)) | ((YY >= 7) & (YY <= 8))) & (XX > 2) & (XX < 13) & (YY > 2) & (YY < 13)
    teal_glass(top, cross)
    if want('block/drift_cache_top'):
        save('block/drift_cache_top', top)
    front = drift_base(174)
    box_frame(front, DRIFT[5], DRIFT[0])
    win = (YY >= 3) & (YY <= 12) & (XX >= 3) & (XX <= 12)
    teal_glass(front, win)
    box_frame(front, DRIFT[0], DRIFT[5], inset=2)
    front[7:9, 7:9, :3] = DRIFT[1]  # latch boss over the glass
    put(front, [(7, 7)], DRIFT[5])
    if want('block/drift_cache_front'):
        save('block/drift_cache_front', front)
    if want('block/drift_cache_front_emissive'):
        save('block/drift_cache_front_emissive', pix.emissive_from(front, win & ~((XX >= 7) & (XX <= 8) & (YY >= 7) & (YY <= 8))))


# ================================================================================================ ritual blocks
def stillstone_base(seed):
    f = F(f_cluster, fx=4.0, seed=seed, vmix=0.35)
    return R(stone(f, 4, [0.25, 0.4, 0.35], emb=0.6) + 1, STILL)


def sigil_pedestal():
    top = stillstone_base(181)
    box_frame(top, STILL[4], STILL[0])
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        put(top, [(x, y)], STAR[0])
    recess = disc(16, 8, 8, 5.0)
    ring = recess & ~disc(16, 8, 8, 4.0)
    top[recess, :3] = pix.hx('#08071A')
    rim = grow(recess, 1) & ~recess
    top[rim & (XX + YY < 16), :3] = STILL[0]
    top[rim & (XX + YY >= 16), :3] = STILL[4]
    top[ring, :3] = pix.hx('#B9A3FF')
    top[ring & (XX + YY < 15), :3] = pix.hx('#EDE6FF')
    put(top, [(8, 8), (7, 7)], pix.hx('#2A2140'))
    if want('block/sigil_pedestal_top'):
        save('block/sigil_pedestal_top', top)
    if want('block/sigil_pedestal_top_emissive'):
        save('block/sigil_pedestal_top_emissive', pix.emissive_from(top, ring))
    side = np.zeros((16, 16, 4), np.uint8)
    a = F(f_bands, ch=0, nb=4, seed=182, wob=0.08, grain=0.5)
    sh = R(stone(a, 5, [0.08, 0.22, 0.34, 0.26, 0.10], emb=0.5), SHALE)
    st = stillstone_base(183)
    side[:] = sh
    side[0:5] = st[0:5]
    side[5, :, :3] = STAR[0]
    side[6, :, :3] = SHALE[0]
    side[4, :, :3] = STILL[0]
    for x in (2, 7, 12):
        put(side, [(x, 2)], STAR[1] if x == 7 else STAR[0])
    side[15, :, :3] = SHALE[0]
    if want('block/sigil_pedestal_side'):
        save('block/sigil_pedestal_side', side)


def obsidian_base(seed):
    f = F(f_cluster, fx=4.0, seed=seed, vmix=0.45)
    return R(stone(f, 5, [0.2, 0.3, 0.25, 0.17, 0.08], emb=0.8), OBSID)


def endbrick_base(seed):
    lay = brick_layout([4, 4, 4, 4], [[0, 8], [4, 12], [0, 8], [4, 12]])
    f = F(f_cluster, fx=5.0, seed=seed, vmix=0.4)
    pl = [pix.hx('#9E9566')] + END[1:]
    return R(bricks(f, 6, lay, [0.15, 0.3, 0.3, 0.15, 0.1], seed=seed), pl)


def egg_cradle():
    top = obsidian_base(191)
    eb = endbrick_base(192)
    ring = (np.maximum(np.abs(XX - 7.5), np.abs(YY - 7.5)) >= 4.5)
    top[ring] = eb[ring]
    inner = (np.maximum(np.abs(XX - 7.5), np.abs(YY - 7.5)) == 3.5)
    top[inner & (XX + YY < 15), :3] = OBSID[0]
    top[inner & (XX + YY >= 15), :3] = OBSID[4]
    if want('block/egg_cradle_top'):
        save('block/egg_cradle_top', top)
    side = obsidian_base(193)
    side[0:9] = eb[0:9]
    side[9, :, :3] = OBSID[0]
    if want('block/egg_cradle_side'):
        save('block/egg_cradle_side', side)
    for lit in (False, True):
        post = obsidian_base(194)
        cr = (XX >= 4) & (XX <= 11) & (YY >= 1) & (YY <= 10)
        m, f, s, _ = spikes_vertical()
        pal = LUMEN if lit else [pix.desat(pix.shade(c, 0.62), 0.45) for c in LUMEN]
        idx = np.where(f == 0, 4, np.where(f == 1, 3, 2))
        idx = np.where(m & (YY <= 3), idx + (2 if lit else 1), idx)
        idx = np.clip(idx, 0, 6)
        crys = R(np.where(m, idx, -1), pal)
        post[m] = crys[m]
        post[11, 3:13, :3] = OBSID[4]
        post[12:16, 3:13, :3] = np.round(post[12:16, 3:13, :3] * 1.0)
        name = 'block/egg_cradle_post_lit' if lit else 'block/egg_cradle_post'
        if want(name):
            save(name, post)
        if lit and want('block/egg_cradle_post_lit_emissive'):
            save('block/egg_cradle_post_lit_emissive', pix.emissive_from(post, m))


def spikes_vertical():
    from .tex import spikes
    return spikes([(8, 11, 8, 0.2, 3.2, 3.0, 0.4)])


# ============================================================================================ gravity anchor
def gravity_anchor():
    img = purpur_base(201)
    for y in (3, 7, 11, 15):
        img[y, :, :3] = PURPUR[0]
        img[(y + 1) % 16, :, :3] = PURPUR[5]
    for y in (1, 5, 9, 13):
        put(img, [(1, y), (14, y)], pix.hx('#E3CBE8'))
        put(img, [(2, y), (15, y)], PURPUR[1])
    img[:, 0, :3] = np.round(img[:, 0, :3] * 0.8)
    if want('block/gravity_anchor_frame'):
        save('block/gravity_anchor_frame', img)
    f = F(f_cluster, fx=3.0, seed=202, vmix=0.4)
    idx = stone(f, 5, [0.25, 0.3, 0.25, 0.15, 0.05], emb=0.6)
    sheen = disc(16, 5.0, 5.0, 3.6)
    idx = np.where(sheen, np.minimum(idx + 1, 4), idx)
    idx = np.where(disc(16, 4.5, 4.5, 1.6), 5, idx)
    img = R(idx, OBSID + [pix.hx('#5E3F8A')])
    if want('block/gravity_anchor_core'):
        save('block/gravity_anchor_core', img)

    # anchor frame: thin purpur lattice, faint teal inner edges, transparent between bars
    img = np.zeros((16, 16, 4), np.uint8)
    bar = frame_mask(16, 2)
    diag = (np.abs(XX - YY) <= 0) | (np.abs(XX + YY - 15) <= 0)
    bar |= diag
    base = purpur_base(203)
    img[bar] = base[bar]
    img[bar & ((XX == 0) | (YY == 0)), :3] = PURPUR[5]
    img[bar & ((XX == 15) | (YY == 15)), :3] = PURPUR[0]
    teal = (grow(~bar, 1) & bar) & ~((XX == 0) | (YY == 0) | (XX == 15) | (YY == 15))
    teal &= ~diag | ((XX + YY) % 3 == 0)
    img[teal, :3] = pix.hx('#2A9C93')
    img[teal & ((XX + YY) % 4 == 0), :3] = pix.hx('#5FD9CC')
    if want('block/anchor_frame'):
        save('block/anchor_frame', img)
    if want('block/anchor_frame_emissive'):
        save('block/anchor_frame_emissive', pix.emissive_from(img, teal))


# =============================================================================================== fracture rift
def null_base(seed):
    f = F(f_cluster, fx=4.0, seed=seed, vmix=0.45)
    return R(stone(f, 5, [0.12, 0.24, 0.32, 0.22, 0.10], emb=0.8), NULL)


def rift_frame():
    side = null_base(211)
    cr = np.zeros((16, 16), bool)
    for path in crack_paths(212, 2, length=(9, 13)):
        for x, y in path:
            if 0 <= x < 16 and 0 <= y < 16:
                cr[y, x] = True
    side[cr, :3] = MAG[0]
    side[cr & (XX % 3 == 0), :3] = MAG[1]
    box_frame(side, NULL[4], NULL[0])
    if want('block/fracture_rift_frame_side'):
        save('block/fracture_rift_frame_side', side)
    top = null_base(213)
    cr = np.zeros((16, 16), bool)
    for path in crack_paths(214, 3, length=(7, 11)):
        for x, y in path:
            if 0 <= x < 16 and 0 <= y < 16:
                cr[y, x] = True
    # sigil: small ring with three ticks (Spire / Tide / Under)
    sig = disc(16, 8, 8, 3.2) & ~disc(16, 8, 8, 2.1)
    for (x, y) in [(7, 3), (8, 3), (7, 2), (8, 2), (3, 11), (4, 11), (12, 11), (11, 11), (7, 7), (8, 8)]:
        sig[y, x] = True
    glow = cr | sig
    top[cr, :3] = MAG[1]
    top[sig, :3] = MAG[2]
    box_frame(top, NULL[4], NULL[0])
    glow &= ~frame_mask()
    if want('block/fracture_rift_frame_top'):
        save('block/fracture_rift_frame_top', top)
    if want('block/fracture_rift_frame_top_emissive'):
        save('block/fracture_rift_frame_top_emissive', pix.emissive_from(top, glow))


def f_rift(g, t=0.0):
    """Swirl: spiral arms in polar coords around the tile centre (phase advances with t, loops at t=1), blended
    into tileable 4D noise whose sample point travels around a circle in time (also loops)."""
    u, v = g.uv()
    du, dv = g.sub(u, 0.5), g.sub(v, 0.5)
    r = g.pw(g.add(g.mul(du, du), g.mul(dv, dv)), 0.5)
    th = g.m('ARCTAN2', dv, du)
    a, b = math.cos(TAU * t) * 0.6, math.sin(TAU * t) * 0.6
    co, w = g.tile(4.0, 4.0, (a + 3.1, b - 7.2, 1.3, 2.2))
    n = g.noise(co, w, 1.0, 3.0, 0.55, 2.0, 0.6)
    arms = g.sin(g.add(g.sub(g.mul(th, 3.0), g.mul(g.m('LOGARITHM', g.add(r, 0.02), 2.718), 4.2)),
                       g.add(g.mul(n, 3.0), -TAU * t)))
    arms01 = g.mul(g.add(arms, 1.0), 0.5)
    fade = g.smooth(0.46, 0.18, r)            # 1 near centre, 0 at the tile border -> seamless edges
    centre = g.smooth(0.0, 0.17, r)           # dark core
    sw = g.lerp(n, g.lerp(n, arms01, 0.85), fade)
    g.out(g.mul(sw, g.lerp(0.25, 1.0, centre)), n, fade)


def fracture_rift():
    pl = P('#050104', '#12030B', '#1A0510', '#3A0A1E', '#7A1030', '#C0206A', '#FF3FB4')
    frames = []
    for i in range(16):
        a = bk.bake_down(f_rift, 16, 4, t=i / 16.0)
        f = a[..., 0]
        idx = threshold_rift(f)
        idx = pix.despeckle(idx, 1)
        frames.append(R(idx, pl))
    if want('block/fracture_rift'):
        save('block/fracture_rift', np.concatenate(frames, 0), frametime=2)


def threshold_rift(f):
    return np.searchsorted([0.22, 0.36, 0.50, 0.62, 0.72, 0.81], f).astype(int)


# ================================================================================================ still throne
def pale_base(seed):
    f = F(f_cluster, fx=4.0, seed=seed, vmix=0.4)
    return R(stone(f, 5, [0.08, 0.2, 0.32, 0.26, 0.14], emb=0.8) + 1, PALE)


def still_throne():
    side = pale_base(221)
    box_frame(side, PALE[5], PALE[0])
    for x in (4, 7, 10):  # carved fluting
        side[3:13, x, :3] = PALE[1]
        side[3:13, x + 1, :3] = PALE[4]
    side[2, 2:14, :3] = PALE[1]
    side[13, 2:14, :3] = PALE[5]
    if want('block/still_throne_side'):
        save('block/still_throne_side', side)
    top = pale_base(222)
    box_frame(top, PALE[5], PALE[0])
    sock = disc(16, 8, 8, 4.2)
    rim = grow(sock, 1) & ~sock
    top[rim & (XX + YY < 16), :3] = PALE[0]
    top[rim & (XX + YY >= 16), :3] = PALE[5]
    hol = sock.copy()
    top[hol, :3] = pix.hx('#4A4858')
    top[hol & (XX + YY > 17), :3] = pix.hx('#5E5C6E')
    if want('block/still_throne_top'):
        save('block/still_throne_top', top)
    filled = top.copy()
    sph = disc(16, 8, 8, 3.6)
    d = np.hypot(XX + 0.5 - 7.0, YY + 0.5 - 7.0)
    sp = P('#8E9CC0', '#B9D3FF', '#DCE8FF', '#FFFFFF')
    idx = np.where(d < 1.3, 3, np.where(d < 2.6, 2, np.where(XX + YY > 17, 0, 1)))
    s = R(np.where(sph, idx, -1), sp)
    filled[sph] = s[sph]
    if want('block/still_throne_top_filled'):
        save('block/still_throne_top_filled', filled)
    if want('block/still_throne_top_filled_emissive'):
        save('block/still_throne_top_filled_emissive', pix.emissive_from(filled, sph & (idx >= 1)))


# ======================================================================================================= tidewisp
def tidewisp_jar():
    """Front view of a small glass jar (bottle model sheet): grey lid, translucent glass, teal-white wisp."""
    img = np.zeros((16, 16, 4), np.uint8)
    glass_e = pix.hx('#9FC7CC')
    body = (XX >= 4) & (XX <= 11) & (YY >= 5) & (YY <= 14)
    body &= ~(((XX == 4) | (XX == 11)) & ((YY == 5) | (YY == 14)))
    img[body, :3] = pix.hx('#2E4A52')
    img[body, 3] = 90
    e = edge_of(body)
    img[e, :3] = glass_e
    img[e, 3] = 200
    img[e & (XX > 9), :3] = pix.hx('#6E9AA2')
    # neck + lid
    rect(img, 5, 3, 10, 4, pix.hx('#7A7A88'))
    rect(img, 5, 2, 10, 2, pix.hx('#B0B0BE'))
    put(img, [(10, 3), (10, 4)], pix.hx('#55555F'))
    put(img, [(5, 2)], pix.hx('#D6D6E0'))
    # highlight streak
    put(img, [(5, 7), (5, 8), (5, 9)], pix.hx('#DDF4F6'))
    # wisp
    w = disc(16, 8.2, 9.6, 2.4)
    img[w, :3] = TEAL[2]
    img[w, 3] = 255
    core = disc(16, 7.8, 9.2, 1.3)
    img[core, :3] = TEAL[4]
    put(img, [(7, 9)], pix.hx('#FFFFFF'))
    tail = [(9, 12), (10, 12), (6, 7), (9, 6)]
    put(img, tail, TEAL[1])
    glow = w | core
    for x, y in tail:
        glow[y, x] = True
    if want('block/tidewisp_jar'):
        save('block/tidewisp_jar', img)
    if want('block/tidewisp_jar_emissive'):
        save('block/tidewisp_jar_emissive', pix.emissive_from(img, glow))


# ===================================================================================================== tide gauge
def tide_gauge():
    side = purpur_base(231)
    win = (XX >= 3) & (XX <= 12) & (YY >= 3) & (YY <= 12)
    side[win, :3] = pix.hx('#CDB8D8')
    side[win, 3] = 70
    for (x, y) in [(4, 5), (5, 4), (4, 6), (10, 11), (11, 10)]:
        side[y, x, :3] = pix.hx('#F3EAF7')
        side[y, x, 3] = 170
    box_frame(side, PURPUR[5], PURPUR[0])
    rim = grow(win, 1) & ~win
    side[rim & ((XX < 3) | (YY < 3)), :3] = PURPUR[0]
    side[rim & ((XX > 12) | (YY > 12)), :3] = PURPUR[5]
    for y in (1, 14):  # level ticks
        pass
    for y in range(4, 12, 2):
        put(side, [(1, y)], pix.hx('#3AE0D0') if y == 4 else PURPUR[1])
    if want('block/tide_gauge_side'):
        save('block/tide_gauge_side', side)
    top = purpur_base(232)
    box_frame(top, PURPUR[5], PURPUR[0])
    box_frame(top, PURPUR[1], PURPUR[4], inset=3)
    put(top, [(7, 7), (8, 8)], PURPUR[5])
    put(top, [(8, 7), (7, 8)], PURPUR[1])
    if want('block/tide_gauge_top'):
        save('block/tide_gauge_top', top)
    f = F(f_noise, fx=6.0, fy=2.0, seed=233, det=2.0)
    idx = np.clip(pix.quantile_index(f, [0.25, 0.5, 0.25]), 0, 2)
    pl = P('#1E8E86', '#2BB8AC', '#3AE0D0', '#8AF0E6', '#B8FFF7')
    idx = pix.despeckle(idx, 2)
    idx[0, :] = 4
    idx[1, :] = np.where(XX[1] % 5 == 1, 4, 3)
    sp = [(3, 6), (11, 9), (6, 12)]
    for x, y in sp:
        idx[y, x] = 3
    fl = R(idx, pl)
    if want('block/tide_gauge_fluid'):
        save('block/tide_gauge_fluid', fl)
    em = (YY == 0) | ((YY == 1) & (idx == 4))
    for x, y in sp:
        em[y, x] = True
    if want('block/tide_gauge_fluid_emissive'):
        save('block/tide_gauge_fluid_emissive', pix.emissive_from(fl, em))


# ===================================================================================================== stillwater
def f_still(g, t=0.0):
    """Very slow ink: tileable 4D fBm whose sample point travels around a small circle in time -> loops."""
    a, b = math.cos(TAU * t) * 0.35, math.sin(TAU * t) * 0.35
    co, w = g.tile(3.0, 3.0, (a + 11.0, b - 4.0, 2.0, -6.0))
    g.out(g.noise(co, w, 1.0, 2.0, 0.5, 2.0, 0.3))


def stillwater():
    pl = P('#08070F', '#0C0B1C', '#100E24', '#15122C')
    N = 32
    # stars: each lives inside a window of the loop, fades in, drifts 2-3 px, fades out (loops perfectly)
    rng = np.random.RandomState(241)
    starsd = []
    for k in range(7):
        starsd.append(dict(x=rng.uniform(0, 16), y=rng.uniform(0, 16), vx=rng.choice([-1, 1]) * rng.uniform(0.05, 0.1),
                           vy=rng.uniform(-0.05, 0.05), t0=k * N / 7 + rng.uniform(0, 2), life=rng.randint(12, 20)))
    frames = []
    for i in range(N):
        f = bk.bake_down(f_still, 16, 4, t=i / N)[..., 0]
        idx = np.clip(np.searchsorted([0.38, 0.5, 0.62], f), 0, 3)
        idx = pix.despeckle(idx, 2)
        img = R(idx, pl, 235)
        for s in starsd:
            age = (i - s['t0']) % N
            if age >= s['life']:
                continue
            ph = age / s['life']
            x = int(s['x'] + s['vx'] * age) % 16
            y = int(s['y'] + s['vy'] * age) % 16
            br = math.sin(ph * math.pi)
            if br > 0.75:
                img[y, x, :3] = STAR[1]
                img[y, x, 3] = 255
            elif br > 0.3:
                img[y, x, :3] = STAR[0]
                img[y, x, 3] = 245
            else:
                img[y, x, :3] = pix.hx('#3A4A7A')
        frames.append(img)
    if want('block/stillwater_still'):
        save('block/stillwater_still', np.concatenate(frames, 0), frametime=2)
    # flow: a tileable 32x32 curtain with faint vertical streaks, scrolled down 1 px per frame (32 frames = loop)
    a = bk.bake_down(lambda g: _flow(g), 32, 4)
    f = a[..., 0]
    idx = np.clip(np.searchsorted(np.quantile(f, [0.35, 0.7, 0.93]), f), 0, 3)
    idx = pix.despeckle(idx, 2)
    plf = P('#07060E', '#0C0B1C', '#14122A', '#232A48')
    base = R(idx, plf, 220)
    frames = [np.roll(base, i, 0) for i in range(32)]
    if want('block/stillwater_flow'):
        save('block/stillwater_flow', np.concatenate(frames, 0), frametime=2)
    ov = frames[0][:16, :16].copy() if False else R(pix.despeckle(np.clip(np.searchsorted(
        [0.38, 0.5, 0.62], bk.bake_down(f_still, 16, 4, t=0.0)[..., 0]), 0, 3), 2), pl, 200)
    ov[0, :, :3] = pix.hx('#3A4A7A')
    ov[0, :, 3] = 220
    put(ov, [(4, 0), (11, 0)], STAR[0])
    if want('block/stillwater_overlay'):
        save('block/stillwater_overlay', ov)


def _flow(g):
    co, w = g.tile(9.0, 1.5, _off(251))
    n = g.noise(co, w, 1.0, 2.0, 0.5)
    co2, w2 = g.tile(3.0, 3.0, _off(252))
    m = g.noise(co2, w2, 1.0, 1.0, 0.5)
    g.out(g.lerp(n, m, 0.35))


def build():
    for b in (hoard_cache, keystone_socket, spire_vault, drift_cache, sigil_pedestal, egg_cradle, gravity_anchor,
              rift_frame, fracture_rift, still_throne, tidewisp_jar, tide_gauge, stillwater):
        b()
