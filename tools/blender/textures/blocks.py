"""Block textures. Every surface starts as a Blender shader-node field baked with Cycles (tileable torus mapping),
then is quantised to the spec palette, lit from the top-left and cleaned of single-pixel static."""
import math
import numpy as np

from . import pix, bk
from .tex import (save, want, F, Fraw, stone, bricks, brick_layout, bevel_frame, frame_mask, disc, line_pts, grow,
                  edge_of, spikes, f_noise, f_cluster, f_bands, f_vein, f_cells)

P = pix.pal

# ------------------------------------------------------------------------------------------------- palettes
END = P('#9E9566', '#BDB37C', '#CEC58E', '#D9D29C', '#E6DFA8', '#F1ECBC')           # end stone family
MOSS = P('#75598A', '#86699C', '#9B7BB0', '#A887BA', '#B493C4', '#F1D6FF')          # lilac moss (+ fleck)
STILL = P('#0C0B1C', '#1B1530', '#2A2140', '#33284C', '#3A2D58')                    # stillstone body
STAR = P('#8FB0FF', '#DFE8FF')
SHALE = P('#1E1B23', '#28242D', '#2E2A33', '#3A3540', '#47414E')                    # undershale
SHALE_V = pix.hx('#3A2A4A')
SHALE_V2 = pix.hx('#4A3560')
DRIFT = P('#2B2340', '#382D52', '#43375E', '#4B3D66', '#5C4D79', '#6E5E8C')
_vp = P('#906590', '#9D6F9C', '#A472A3', '#AC7BAC', '#B286B2', '#BA95BA', '#C7A8C7')   # vanilla purpur hues (reference)
PURPUR = P('#7E5A86', '#8D649D', '#9A6EA8', '#A87BB6', '#B48BC0', '#C3A2CC')
WPURP = [pix.desat(c, 0.22) for c in PURPUR]                                        # weathered: ~20% desaturated
WSTREAK = pix.hx('#C4B4C8')
WSTREAK2 = pix.hx('#B3A2B8')
PURP_DARK = pix.hx('#2A1631')
NULL = P('#070205', '#10030A', '#1A0510', '#240814', '#2B0A18')
NULL_VEIN = P('#4A0A1E', '#7A1030')
ASH = P('#1E181E', '#2A222A', '#342A34', '#3A2F3A', '#4A3D4A', '#574857')
LUMEN = P('#0B4A46', '#0E5F59', '#16807A', '#22A79C', '#2FC9BB', '#6FE6DA', '#C8FFF8')
OBSID = P('#06030B', '#100C1C', '#1B1430', '#271E3D', '#3B2754')
PALE = P('#7F7B8C', '#9A96A6', '#B1AEBC', '#C6C3CF', '#D9D7E0', '#ECEAF2')            # still throne pale stone


def R(idx, palette, alpha=255):
    return pix.render_index(idx, palette, alpha)


def stars(seed, count, size=16, avoid=None, big=0.4):
    """Sparse 1-2 px silver flecks placed at Blender voronoi cell centres (min-distance spaced)."""
    f = F(f_cells, ss=2, size=size, ch=2, fx=3.0, seed=seed)
    order = np.argsort(f.ravel())
    pts = []
    rng = np.random.RandomState(seed)
    for o in order:
        y, x = divmod(o, size)
        if avoid is not None and avoid[y, x]:
            continue
        if all(min(abs(x - px), size - abs(x - px)) + min(abs(y - py), size - abs(y - py)) > 5 for px, py in pts):
            pts.append((x, y))
        if len(pts) >= count:
            break
    out = []
    for x, y in pts:
        out.append((x, y, 1))
        if rng.rand() < big:
            dx, dy = [(1, 0), (0, 1), (-1, 0), (0, -1)][rng.randint(4)]
            out.append(((x + dx) % size, (y + dy) % size, 0))
    return out


def apply_stars(img, pts, a=255):
    for x, y, b in pts:
        img[y, x, :3] = STAR[b]
        img[y, x, 3] = a
    return img


# =============================================================================================== chorus reach
def pale_loam():
    f = F(f_cluster, fx=5.0, seed=11, vmix=0.35)
    idx = stone(f, 5, [0.10, 0.22, 0.30, 0.26, 0.12], emb=1.0)
    fl = F(f_noise, ss=2, fx=9.0, seed=12, det=1.0)
    fleck = (fl > np.quantile(fl, 0.965)) & (idx >= 2)
    img = R(idx, MOSS[:5])
    img[fleck, :3] = MOSS[5]
    if want('block/pale_loam_top'):
        save('block/pale_loam_top', img)
    # side: end stone body + moss drape
    fe = F(f_cluster, fx=4.0, seed=21, vmix=0.5)
    eidx = stone(fe, 5, [0.06, 0.18, 0.30, 0.30, 0.16], emb=1.0) + 1
    side = R(eidx, END)
    dn = F(f_noise, ss=2, fx=4.0, seed=22, det=1.0)[0]  # one periodic row -> per-column drape depth
    depth = np.where(dn > np.median(dn), 3, 2)
    drip = F(f_noise, ss=2, fx=3.0, seed=23, det=1.0)[1]
    order = np.argsort(-drip)
    used = set()
    for x in order[:6]:  # a few longer drips, 1-2 px wide, never adjacent to each other
        if x in used or (x - 1) % 16 in used or (x + 1) % 16 in used:
            continue
        depth[x] += 2 if len(used) < 2 else 1
        used.add(x)
    depth[0], depth[15] = 2, 2  # same depth at the wrap so neighbouring blocks connect
    for x in range(16):
        d = depth[x]
        for y in range(d):
            side[y, x] = img[y, x]
            if y == d - 1:
                side[y, x, :3] = MOSS[0] if y > 1 else MOSS[1]
        if d >= 3:
            side[d, x, :3] = END[0]  # soft shadow only under the longer drips
    # break any 1px-wide drips: ensure drips are at least 1 neighbour-continuous (they are columns, fine)
    if want('block/pale_loam_side'):
        save('block/pale_loam_side', side)


def lilac_tuft():
    """Cross plant: thin curly blades (1 px), two lilacs, pale curled tips. Blade paths are sampled from
    Blender-baked noise so every blade sways differently."""
    n = F(f_noise, ss=1, size=64, fx=3.0, seed=5, det=1.0)
    img = np.zeros((16, 16, 4), np.uint8)
    dark, mid, light, tipc = pix.hx('#7A5C8F'), pix.hx('#9B7BB0'), pix.hx('#B493C4'), pix.hx('#F1D6FF')
    blades = [(2, 9, -1), (4, 12, 1), (7, 14, -1), (9, 11, 1), (11, 13, 1), (13, 8, -1), (14, 5, 1),
              (5, 6, 1), (10, 5, -1)]
    for k, (x0, h, curl) in enumerate(blades):
        x = float(x0)
        pts = []
        for i in range(h):
            y = 15 - i
            x += (n[k * 6 % 64, i * 4 % 64] - 0.5) * 0.5 + curl * 0.10 * (i / h)
            pts.append((int(round(x)), y))
        # curl the top: hook sideways
        tx, ty = pts[-1]
        pts.append((tx + curl, ty))
        pts.append((tx + curl, ty + 1))
        col = mid if k % 2 else light
        if k >= 8:
            col = dark
        for j, (px, py) in enumerate(pts):
            if not (0 <= px < 16 and 0 <= py < 16):
                continue
            c = col
            if j >= len(pts) - 3:
                c = tipc if j >= len(pts) - 2 else light
            elif j < 3:
                c = dark
            if img[py, px, 3] and j < len(pts) - 3:
                continue
            img[py, px, :3] = c
            img[py, px, 3] = 255
    if want('block/lilac_tuft'):
        save('block/lilac_tuft', img)


def chorus_pod():
    """Segmented pod skin covering the whole 16x16 (it wraps a small box model): three rounded lobes,
    each lit from the left, seams between, darker caps at both ends; ripe stage gets the lavender glow line."""
    stages = [
        (P('#4F3D63', '#6A5480', '#86709E', '#A592BC', '#C2B4D4'), 'block/chorus_pod_stage0'),
        (P('#563F70', '#735890', '#9278B0', '#B39DD0', '#D2C3E6'), 'block/chorus_pod_stage1'),
        (P('#5E4486', '#7E5FAE', '#A383D6', '#D8B6FF', '#EEDDFF'), 'block/chorus_pod_stage2'),
    ]
    yy, xx = np.mgrid[0:16, 0:16]
    for k, (pl, name) in enumerate(stages):
        n = F(f_noise, fx=3.0, fy=6.0, seed=31 + k, det=2.0)
        seg = np.array([0] * 5 + [1] * 6 + [2] * 5)  # lobe widths 5/6/5 with seams at x=5 and x=11
        start = np.array([0, 5, 11])[seg]
        width = np.array([5, 6, 5])[seg]
        ph = (np.arange(16) - start + 0.5) / width  # 0..1 across lobe
        lobe = np.sin(ph * math.pi) ** 0.6 * (1.15 - 0.6 * ph)
        cap = np.sin((np.arange(16) + 0.5) / 16 * math.pi) ** 0.45
        f = lobe[None, :] * cap[:, None] + (n - 0.5) * 0.1
        idx = np.clip(np.floor(pix.norm(f) * 4.6), 0, 4).astype(int)
        seam = (xx == 5) | (xx == 11) | (xx == 0)
        idx = np.where(seam, np.minimum(idx, 1) - (yy % 15 == 0), idx)
        idx = np.where((yy == 0) | (yy == 15), np.minimum(idx, 1), idx)
        idx = np.clip(pix.despeckle(np.clip(idx, 0, 4), 1, wrap=False, keep=seam), 0, 4)
        img = R(idx, pl)
        if k == 2:
            cl = (xx == 8) & (yy >= 3) & (yy <= 12)
            img[cl, :3] = pix.hx('#F6ECFF')
            img[(xx == 8) & ((yy == 3) | (yy == 12)), :3] = pix.hx('#EBD8FF')
            img[(xx == 8) & (yy >= 6) & (yy <= 9), :3] = pix.hx('#FFFFFF')
            if want(name + '_emissive'):
                save(name + '_emissive', pix.emissive_from(img, cl))
        if want(name):
            save(name, img)


# ============================================================================================ stillwater basins
def stillstone_family():
    f = F(f_cluster, fx=4.0, seed=41, vmix=0.4)
    idx = stone(f, 5, [0.10, 0.22, 0.32, 0.24, 0.12], emb=1.0)
    img = R(idx, STILL)
    apply_stars(img, stars(41, 3))
    if want('block/stillstone'):
        save('block/stillstone', img)

    # polished: smooth with subtle concentric rings
    yy, xx = np.mgrid[0:16, 0:16]
    d = np.maximum(np.abs(xx - 7.5), np.abs(yy - 7.5)) * 0.35 + np.hypot(xx - 7.5, yy - 7.5) * 0.65
    ring = (np.floor(d / 2.2) % 2).astype(int)
    n = F(f_noise, fx=3.0, seed=42, det=1.0)
    idx = 2 + ring + (n > np.quantile(n, 0.85)).astype(int) * (1 - ring)
    idx = np.clip(idx, 1, 4)
    idx = bevel_frame(idx, 5, 1, light=1, dark=-2)
    idx[0, 0] = 4
    img = R(idx, STILL)
    apply_stars(img, [(4, 11, 1), (11, 4, 0)])
    if want('block/polished_stillstone'):
        save('block/polished_stillstone', img)

    # bricks (deepslate-brick rhythm: 4 px courses, 8 px bricks, offset)
    lay = brick_layout([4, 4, 4, 4], [[0, 8], [4, 12], [2, 10], [6, 14]])
    fb = F(f_cluster, fx=6.0, seed=43, vmix=0.3)
    idx = bricks(fb, 5, lay, [0.3, 0.4, 0.3], seed=43)
    img = R(idx, STILL)
    apply_stars(img, [(p[0], p[1], p[2]) for p in stars(43, 2, avoid=lay[0])])
    if want('block/stillstone_bricks'):
        save('block/stillstone_bricks', img)

    # chiseled: frame + 4-point star
    fc = F(f_cluster, fx=4.0, seed=44, vmix=0.3)
    idx = stone(fc, 4, [0.25, 0.45, 0.30], emb=0.6) + 1
    idx = bevel_frame(idx, 5, 1, light=1, dark=-1)
    inner = np.zeros((16, 16), bool)
    inner[2:14, 2] = inner[2:14, 13] = inner[2, 2:14] = inner[13, 2:14] = True
    idx = np.where(inner, 0, idx)
    img = R(idx, STILL)
    star = [
        "......#.......",
        "......#.......",
        ".....#o#......",
        ".....#o#......",
        "....#ooo#.....",
        "..##oo*oo##...",
        "##ooo***ooo##.",
        "..##oo*oo##...",
        "....#ooo#.....",
        ".....#o#......",
        ".....#o#......",
        "......#.......",
    ]
    for y, r in enumerate(star):
        for x, ch in enumerate(r):
            X, Y = x + 1, y + 2
            if ch == '#':
                img[Y, X, :3] = pix.hx('#3A4A7A')
            elif ch == 'o':
                img[Y, X, :3] = STAR[0]
            elif ch == '*':
                img[Y, X, :3] = STAR[1]
    if want('block/chiseled_stillstone'):
        save('block/chiseled_stillstone', img)


def glass_frame(body_pal, frame_c, hi_c, f, weights, body_a, frame_a=255, sheen=True):
    idx = pix.quantile_index(f, weights)
    idx = pix.despeckle(idx, 2)
    img = R(idx, body_pal, body_a)
    fm = frame_mask()
    img[fm, :3] = frame_c
    img[fm, 3] = frame_a
    if sheen:  # vanilla-glass style diagonal glints top-left and bottom-right
        for (x, y) in [(3, 4), (4, 3), (5, 2), (11, 13), (12, 12)]:
            img[y, x, :3] = hi_c
            img[y, x, 3] = max(body_a, 200)
    return img


def basin_glass():
    f = F(f_noise, fx=2.0, seed=51, det=1.0, shear=1)
    img = glass_frame(P('#090914', '#0E0F1C', '#131729', '#1A2238'), pix.hx('#1E2A44'), pix.hx('#4A6496'),
                      f, [0.25, 0.35, 0.28, 0.12], 180, 215)
    # long faint blue sheen streak (diagonal)
    for i in range(6):
        x, y = 9 + i, 1 + i
        if 0 < x < 15 and 0 < y < 15:
            img[y, x, :3] = pix.hx('#2B3A5E')
    if want('block/rimglass'):
        save('block/rimglass', img)
    f2 = F(f_noise, fx=3.0, seed=52, det=1.0)
    img = glass_frame(P('#07060F', '#0C0B1C', '#141128'), pix.hx('#05040B'), pix.hx('#3A4A7A'), f2,
                      [0.3, 0.45, 0.25], 150, 255, sheen=False)
    for x, y, b in [(4, 5, 1), (10, 3, 0), (12, 10, 1), (6, 12, 0), (9, 8, 0)]:
        img[y, x, :3] = STAR[b]
        img[y, x, 3] = 200
    if want('block/stillglass'):
        save('block/stillglass', img)
    pane = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        pane[y, 7, :3] = pix.hx('#05040B')
        pane[y, 8, :3] = pix.hx('#141128') if y % 5 else pix.hx('#3A4A7A')
        pane[y, 7:9, 3] = 255
    if want('block/stillglass_pane_top'):
        save('block/stillglass_pane_top', pane)
    mirror_shard_sprite()


# ============================================================================================== fallen spires
def purpur_tiles(pl, field, weights, streak=None, chips=()):
    """Vanilla purpur rhythm: 2x2 bevelled 8px tiles."""
    lay = brick_layout([8, 8], [[0, 8]])
    idx = bricks(field, len(pl), lay, weights, per_brick=0.35, seed=61)
    img = R(idx, pl)
    if streak is not None:
        img[streak & ~lay[0], :3] = WSTREAK
    for (x, y, c) in chips:
        img[y, x, :3] = c
    return img, lay


def weathered_purpur():
    f = F(f_cluster, fx=5.0, seed=61, vmix=0.3)
    s = F(f_noise, ss=2, fx=10.0, fy=1.4, seed=62, det=2.0)
    s2 = F(f_noise, ss=2, fx=3.0, fy=3.0, seed=63, det=1.0)
    streak = (s > np.quantile(s, 0.88)) & (s2 > 0.45)
    streak = pix.despeckle(streak.astype(int), 1).astype(bool)
    chip = [(1, 1, WPURP[0]), (2, 1, WPURP[1]), (1, 2, WPURP[1]), (14, 9, WPURP[0]), (14, 10, WPURP[1]),
            (9, 14, WPURP[1]), (6, 6, WPURP[0])]
    img, lay = purpur_tiles(WPURP, f, [0.2, 0.35, 0.3, 0.15], streak, chip)
    if want('block/weathered_purpur'):
        save('block/weathered_purpur', img)

    def pillar_side(pl, fseed):
        fp = F(f_noise, fx=4.0, fy=8.0, seed=fseed, det=2.0)
        yy, xx = np.mgrid[0:16, 0:16]
        idx = stone(fp, 4, [0.2, 0.35, 0.3, 0.15], emb=0.0, desp=1) + 1
        groove = (xx % 4 == 3) & (yy >= 2) & (yy <= 13)
        idx = np.where(groove, 1, idx)
        idx = np.where((xx % 4 == 0) & (yy >= 2) & (yy <= 13), np.minimum(idx + 1, len(pl) - 1), idx)
        band = (yy == 1) | (yy == 14)
        idx = np.where(band, 0, idx)
        idx = np.where((yy == 0) | (yy == 15), np.where(yy == 0, len(pl) - 1, 1), idx)
        return idx

    def pillar_top(pl, fseed):
        fp = F(f_cluster, fx=4.0, seed=fseed, vmix=0.3)
        idx = stone(fp, 4, [0.2, 0.35, 0.3, 0.15], emb=0.5, desp=1) + 1
        yy, xx = np.mgrid[0:16, 0:16]
        c = np.maximum(np.abs(xx - 7.5), np.abs(yy - 7.5))
        idx = np.where(np.abs(c - 6.5) < 0.6, 0, idx)
        idx = np.where(np.abs(c - 2.5) < 0.6, 1, idx)
        idx = np.where(np.abs(c - 5.5) < 0.6, np.minimum(idx + 1, len(pl) - 1), idx)
        idx = bevel_frame(idx, len(pl), 1)
        return idx

    side = R(pillar_side(WPURP, 64), WPURP)
    st = F(f_noise, ss=2, fx=8.0, fy=1.2, seed=65, det=2.0)
    m = (st > np.quantile(st, 0.90))
    m[0:2] = m[14:] = False
    side[m, :3] = WSTREAK2
    side[13, 3, :3] = side[14, 3, :3] = WPURP[0]
    if want('block/weathered_purpur_pillar'):
        save('block/weathered_purpur_pillar', side)
    top = R(pillar_top(WPURP, 66), WPURP)
    top[2, 13, :3] = top[13, 2, :3] = WSTREAK
    if want('block/weathered_purpur_pillar_top'):
        save('block/weathered_purpur_pillar_top', top)

    # cracked pillar: diagonal crack + missing chips
    side = R(pillar_side(PURPUR, 67), PURPUR)
    crack = line_pts(12, 0, 3, 15)
    for i, (x, y) in enumerate(crack):
        side[y, x, :3] = PURP_DARK
        if 0 <= x + 1 < 16 and i % 3 != 1:
            side[y, x + 1, :3] = PURPUR[0]
        if x - 1 >= 0 and i % 4 == 0:
            side[y, x - 1, :3] = PURPUR[5]
    for (x, y) in [(0, 5), (0, 6), (1, 6), (15, 10), (15, 11), (14, 11), (6, 0), (7, 0)]:
        side[y, x, :3] = PURP_DARK if (x in (0, 15) or y == 0) else PURPUR[0]
    if want('block/cracked_purpur_pillar'):
        save('block/cracked_purpur_pillar', side)
    top = R(pillar_top(PURPUR, 68), PURPUR)
    for i, (x, y) in enumerate(line_pts(1, 2, 14, 13)):
        top[y, x, :3] = PURP_DARK
        if i % 3 == 0 and y + 1 < 16:
            top[y + 1, x, :3] = PURPUR[0]
    for (x, y) in [(15, 0), (14, 0), (15, 1), (0, 15), (1, 15)]:
        top[y, x, :3] = PURP_DARK
    if want('block/cracked_purpur_pillar_top'):
        save('block/cracked_purpur_pillar_top', top)


def purpur_rubble():
    a = F(f_cells, ss=4, ch=None, fx=4.2, seed=71, rnd=0.9)
    cellv, edge, f1 = a[..., 0], a[..., 1], a[..., 2]
    tone = np.floor(pix.norm(cellv) * 3.999).astype(int)  # 0..3 per chunk
    idx = tone + 1
    # bevel each chunk: lit top-left, shaded bottom-right using the F1 distance gradient
    g = np.roll(np.roll(f1, 1, 0), 1, 1) - np.roll(np.roll(f1, -1, 0), -1, 1)
    idx = np.where(g > 0.04, idx - 1, idx)
    idx = np.where(g < -0.04, idx + 1, idx)
    idx = np.clip(idx, 1, 5)
    gap = edge < np.quantile(edge, 0.13)
    idx = np.where(gap, 0, idx)
    idx = pix.despeckle(idx, 2)
    pl = [pix.hx('#4A2D55')] + PURPUR[1:]
    img = R(idx, pl)
    if want('block/purpur_rubble'):
        save('block/purpur_rubble', img)


def spire_lantern():
    """Sheet in vanilla lantern.png layout: body side (0,2)-(6,9), body top/bottom (0,9)-(6,15),
    cap side (1,0)-(5,2), cap top (1,10)-(5,14), hook (11,1)-(14,3) and (11,10)-(14,12)."""
    img = np.zeros((16, 16, 4), np.uint8)
    PL = PURPUR

    def px(x, y, c):
        img[y, x, :3] = c
        img[y, x, 3] = 255
    # cap side (top knob)
    for x in range(1, 5):
        px(x, 0, PL[4] if x < 3 else PL[3])
        px(x, 1, PL[2] if x < 4 else PL[1])
    # body side 6x7: purpur cap row, glass, core, purpur base
    glass = [pix.hx('#E8D6EE'), pix.hx('#CDB3D6'), pix.hx('#B497C0')]
    for x in range(0, 6):
        px(x, 2, PL[4] if x < 3 else PL[3])
        px(x, 8, PL[2] if x < 3 else PL[1])
    for y in range(3, 8):
        px(0, y, PL[3])
        px(5, y, PL[1])
        for x in range(1, 5):
            px(x, y, glass[0] if x == 1 else glass[1] if x == 4 else glass[1])
    core = [(2, 3), (3, 3), (2, 4), (3, 4), (2, 5), (3, 5), (2, 6), (3, 6), (2, 7), (3, 7)]
    ccol = {3: '#FFB6F0', 4: '#FFD3F6', 5: '#FFFFFF', 6: '#FFE4F9', 7: '#FFB6F0'}
    for x, y in core:
        px(x, y, pix.hx(ccol[y]) if x == 2 else pix.hx('#FFB6F0' if y in (3, 7) else '#FFD3F6'))
    # body top/bottom 6x6 at (0,9)
    for y in range(9, 15):
        for x in range(0, 6):
            e = x in (0, 5) or y in (9, 14)
            px(x, y, PL[1] if e else PL[3])
    for y in range(10, 14):
        for x in range(1, 5):
            px(x, y, PL[4] if (x + y) % 3 == 0 else PL[3])  # cap top region (1,10)-(5,14)
    for x, y in [(2, 11), (3, 11), (2, 12), (3, 12)]:
        px(x, y, PL[2])
    # hooks (chain) in darker purpur
    for x in range(11, 14):
        for y in (1, 2):
            px(x, y, PL[0] if (x + y) % 2 else PL[2])
        for y in (10, 11):
            px(x, y, PL[0] if (x + y) % 2 else PL[2])
    if want('block/spire_lantern'):
        save('block/spire_lantern', img)
    em = np.zeros_like(img)
    for x, y in core:
        em[y, x] = img[y, x]
    if want('block/spire_lantern_emissive'):
        save('block/spire_lantern_emissive', em)


# ================================================================================================= underside
def undershale_family():
    a = F(f_bands, ch=None, nb=4, seed=81, wob=0.09, grain=0.55)
    f, s = a[..., 0], a[..., 1]
    idx = stone(f, 5, [0.08, 0.22, 0.34, 0.26, 0.10], emb=0.6, desp=2)
    img = R(idx, SHALE)
    # thin violet lines on one phase of the strata
    brk = F(f_noise, fx=5.0, seed=85, det=1.0) > 0.4
    vio = (np.abs(s - 0.03) < 0.03) & brk
    vio2 = (np.abs(s - 0.985) < 0.015) & brk
    img[vio, :3] = SHALE_V
    img[vio2 & ~vio, :3] = SHALE_V2
    base = img
    if want('block/undershale'):
        save('block/undershale', img)

    # polished: smooth broad bands inside a bevelled frame
    yy = np.arange(16)[:, None].repeat(16, 1)
    idx = np.where((yy // 3) % 2 == 0, 2, 3)
    n = F(f_noise, fx=3.0, seed=82, det=1.0)
    idx = np.where((n > np.quantile(n, 0.9)) & (idx == 2), 3, idx)
    idx = bevel_frame(idx, 5, 1, light=1, dark=-2)
    img = R(idx, SHALE)
    img[(yy == 7) & (np.arange(16)[None, :] > 1) & (np.arange(16)[None, :] < 14), :3] = SHALE_V
    if want('block/polished_undershale'):
        save('block/polished_undershale', img)

    lay = brick_layout([4, 4, 4, 4], [[0, 10], [5], [2, 12], [8]])
    fb = F(f_bands, nb=8, seed=83, wob=0.04, grain=0.5)
    idx = bricks(fb, 5, lay, [0.3, 0.4, 0.3], seed=83)
    img = R(idx, SHALE)
    for y in (1, 9):
        img[y, [3, 4, 5, 13, 14] if y == 1 else [10, 11, 12], :3] = SHALE_V
    if want('block/undershale_bricks'):
        save('block/undershale_bricks', img)

    lay = brick_layout([8, 8], [[0, 8]])
    ft = F(f_bands, nb=4, seed=84, wob=0.05, grain=0.5)
    idx = bricks(ft, 5, lay, [0.3, 0.4, 0.3], per_brick=0.8, seed=84)
    img = R(idx, SHALE)
    for (x, y) in [(2, 3), (3, 3), (4, 3), (10, 12), (11, 12), (12, 12)]:
        img[y, x, :3] = SHALE_V
    if want('block/undershale_tiles'):
        save('block/undershale_tiles', img)
    return base


def ceiling_moss():
    pl = P('#06211F', '#0B3532', '#114A46', '#1B635D', '#2A7F77')
    n = F(f_noise, ss=2, fx=6.0, seed=91, det=2.0)
    col = n[3]  # per-column variation
    img = np.zeros((16, 16, 4), np.uint8)
    rng = np.random.RandomState(91)
    for x in range(16):
        centre = 1 - abs(x - 7.5) / 8.0
        L = int(np.clip(round(3 + centre * 9 + (col[x] - 0.5) * 8), 2, 15))
        if x in (0, 15):
            L = min(L, 4)
        sx = x
        for y in range(L):
            # strands wobble one pixel sideways occasionally
            if y > 2 and rng.rand() < 0.12:
                sx = int(np.clip(sx + rng.choice([-1, 1]), 0, 15))
            t = y / max(1, L - 1)
            ci = 4 - int(round(t * 3)) if y < L - 1 else 0
            ci = int(np.clip(ci + (1 if (y + x) % 5 == 0 else 0), 0, 4))
            img[y, sx, :3] = pl[ci]
            img[y, sx, 3] = 255
    # top rows denser (the mat the strands hang from)
    for x in range(16):
        img[0, x, :3] = pl[3] if x % 3 else pl[4]
        img[0, x, 3] = 255
        if n[1, x] > 0.45:
            img[1, x, :3] = pl[2]
            img[1, x, 3] = 255
    if want('block/ceiling_moss'):
        save('block/ceiling_moss', img)


def lumen_crystals():
    noise = F(f_noise, fx=4.0, fy=4.0, seed=93, det=2.0)
    nz = np.where(noise > np.quantile(noise, 0.82), 1, np.where(noise < np.quantile(noise, 0.12), -1, 0))
    pal = LUMEN  # 0 darkest .. 6 brightest
    # base: wide at the ceiling (top) narrowing toward the middle piece
    m, f, s, _ = spikes([(8, 0, 8, 16.01, 6.2, 3.6, 1e-6)])
    base = crystal_index(m, f, s, 7, base=(3, 2, 1), grad=1.4, noise=nz * (f == 1))
    yy = np.arange(16)[:, None].repeat(16, 1)
    base = np.where(m & (yy <= 1), np.minimum(base, 1), base)
    # growth step (a ring where the base narrows)
    base = np.where(m & (yy == 7), np.maximum(base - 1, 0), base)
    m2, f2, s2, _ = spikes([(8, 0, 8, 16.01, 3.6, 3.6, 1e-6)])
    mid = crystal_index(m2, f2, s2, 7, base=(4, 3, 1), grad=0.0, noise=nz * (f2 == 1))
    m3, f3, s3, _ = spikes([(8, 0, 8, 15.0, 3.6, 3.0, 0.62)])
    tip = crystal_index(m3, f3, s3, 7, base=(4, 3, 2), grad=1.6, noise=nz * (f3 == 1))
    em = m3 & (yy >= 10)
    tip = np.where(em, np.maximum(tip, 5), tip)
    tip = np.where(m3 & (yy >= 12), 6, tip)
    out = {'base': R(base, pal), 'middle': R(mid, pal), 'tip': R(tip, pal)}
    for k, img in out.items():
        if want('block/lumen_crystal_' + k):
            save('block/lumen_crystal_' + k, img)
    if want('block/lumen_crystal_tip_emissive'):
        save('block/lumen_crystal_tip_emissive', pix.emissive_from(out['tip'], m3 & (yy >= 11)))

    # cluster (amethyst-cluster-like fan)
    defs = [(5.0, 15.8, 0.8, 9.5, 1.25, 1.25, 0.34), (11.0, 15.8, 15.2, 10.0, 1.25, 1.25, 0.34),
            (6.5, 15.8, 4.2, 4.0, 1.4, 1.4, 0.32), (9.5, 15.8, 12.0, 5.0, 1.4, 1.4, 0.32),
            (8.0, 15.9, 8.0, 0.5, 1.9, 1.8, 0.3)]
    m, f, s, sid = spikes(defs, cover=0.42)
    idx = crystal_index(m, f, s, 7, base=(4, 3, 2), grad=-1.6)
    # back crystals one step darker so the fan reads in depth
    idx = np.where((sid >= 0) & (sid < 2), np.maximum(idx - 1, 0), idx)
    # bright edges: the lit-left rim and the tip third of each crystal
    Pm = np.pad(m, 1)
    rim = m & ~Pm[1:-1, :-2]
    bright = (rim | (m & (s > 0.72) & (f != 2))) & m
    idx = np.where(bright, np.maximum(idx, 5), idx)
    idx = np.where(m & (s > 0.86), 6, idx)
    img = R(idx, pal)
    if want('block/lumen_cluster'):
        save('block/lumen_cluster', img)
    if want('block/lumen_cluster_emissive'):
        save('block/lumen_cluster_emissive', pix.emissive_from(img, bright | (m & (s > 0.86))))

    # crystal block: voronoi facets, each facet a flat tone, thin dark seams, lit top-left of facets
    a = F(f_cells, ch=None, fx=3.4, seed=95, rnd=1.0)
    cellv, edge, f1 = a[..., 0], a[..., 1], a[..., 2]
    tone = np.floor(pix.norm(cellv) * 2.999).astype(int) + 2  # 2..4
    g = np.roll(np.roll(f1, 1, 0), 1, 1) - np.roll(np.roll(f1, -1, 0), -1, 1)
    idx = np.where(g > 0.06, tone + 1, np.where(g < -0.06, tone - 1, tone))
    seam = edge < np.quantile(edge, 0.10)
    idx = np.where(seam, 1, np.clip(idx, 1, 5))
    idx = pix.despeckle(idx, 2)
    gl = (g > np.quantile(g, 0.97)) & ~seam
    idx = np.where(gl, 6, idx)
    img = R(idx, LUMEN)
    if want('block/lumen_crystal_block'):
        save('block/lumen_crystal_block', img)
    return img


def voidroot():
    pl = P('#160F1E', '#241A30', '#3A2A4A', '#55406A')
    amber = P('#C98A2E', '#FFD27A', '#FFF0C2')

    def strands(img, H, period, seed, tip=False):
        rng = np.random.RandomState(seed)
        roots = [(3, 2.2, 0.0), (6, 1.6, 1.3), (10, 2.0, 2.6), (12, 1.3, 4.0), (8, 1.0, 5.1)]
        ends = {}
        for k, (x0, amp, ph) in enumerate(roots):
            L = H if not tip else int(16 - [2, 4, 1, 6, 8][k])
            prevx = None
            for y in range(L):
                x = int(round(x0 + amp * math.sin(2 * math.pi * y / period + ph)))
                xs = [x] if prevx is None or abs(prevx - x) <= 1 else list(range(min(prevx, x), max(prevx, x) + 1))
                for xx in xs:
                    xx %= 16
                    lit = math.cos(2 * math.pi * y / period + ph) > 0.3
                    c = pl[3] if lit and k % 2 == 0 else pl[2] if lit or k % 2 == 0 else pl[1]
                    img[y, xx, :3] = c
                    img[y, xx, 3] = 255
                    # shadow pixel on the right of each root
                    if xx + 1 < 16 and img[y, xx + 1, 3] == 0:
                        img[y, xx + 1, :3] = pl[0]
                        img[y, xx + 1, 3] = 255
                prevx = x
            ends[k] = (prevx % 16, L - 1)
        return ends

    mid = np.zeros((16, 16, 4), np.uint8)
    strands(mid, 16, 16, 1)
    if want('block/voidroot_middle'):
        save('block/voidroot_middle', mid)
    tip = np.zeros((16, 16, 4), np.uint8)
    ends = strands(tip, 16, 16, 1, tip=True)
    em = np.zeros((16, 16), bool)
    for k, (x, y) in ends.items():
        for j, c in enumerate([amber[0], amber[1], amber[2]]):
            yy = y - 1 + j
            if 0 <= yy < 16:
                tip[yy, x, :3] = c
                tip[yy, x, 3] = 255
                em[yy, x] = j >= 1
        if y + 2 < 16:
            pass
    if want('block/voidroot_tip'):
        save('block/voidroot_tip', tip)
    if want('block/voidroot_tip_emissive'):
        save('block/voidroot_tip_emissive', pix.emissive_from(tip, em))


def glowbulb():
    st = bk.Stage(view=(-10, 0, 0), ortho=1.0, light_cam=(-0.6, 0.6, 0.5))
    st.mat('bulb', bk.Toon(P('#9C2C80', '#D2489F', '#FF7AD9', '#FFB3EC'), cuts=[-1, -0.45, 0.15, 0.62]))
    st.mat('stem', bk.Toon(P('#1E1426', '#33233F', '#4A3758'), cuts=[-1, -0.2, 0.4]))
    st.sphere('bulb', loc=(0, 0, -0.1), r=0.31, scale=(1, 1, 0.94))
    st.cyl('stem', loc=(0, 0, 0.32), r=0.05, h=0.36, verts=6)
    st.cone('stem', loc=(0, 0, 0.2), r1=0.13, r2=0.04, h=0.08, verts=6)
    img = bk.downsample_sprite(st.render(64, 'bulb'), 16, cover=0.45)
    op = img[..., 3] > 0
    body = op & (np.abs(img[..., :3].astype(int) - pix.hx('#33233F')).sum(-1) > 6) \
        & (np.abs(img[..., :3].astype(int) - pix.hx('#1E1426')).sum(-1) > 6) \
        & (np.abs(img[..., :3].astype(int) - pix.hx('#4A3758')).sum(-1) > 6)
    rim = edge_of(body)
    img[rim & body, :3] = pix.hx('#7A1F63')
    inner = body & ~rim
    img[inner, 3] = 215  # translucent bulb flesh
    img[rim, 3] = 255
    yy, xx = np.mgrid[0:16, 0:16]
    cy = np.mean(np.nonzero(body)[0])
    cx = np.mean(np.nonzero(body)[1])
    core = body & ((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2 < 2.6 ** 2)
    img[core, :3] = pix.hx('#FFC6F0')
    img[core, 3] = 255
    cc = body & ((xx + 0.5 - cx + 0.5) ** 2 + (yy + 0.5 - cy + 0.5) ** 2 < 1.1 ** 2)
    img[cc, :3] = pix.hx('#FFFFFF')
    if want('block/glowbulb'):
        save('block/glowbulb', img)
    if want('block/glowbulb_emissive'):
        save('block/glowbulb_emissive', pix.emissive_from(img, core))
    # picked: stem with a small shrivelled dull bulb
    st = bk.Stage(view=(-10, 0, 0), ortho=1.0, light_cam=(-0.6, 0.6, 0.5))
    st.mat('bulb', bk.Toon(P('#4A2443', '#6E3A62', '#8E5582'), cuts=[-1, -0.3, 0.4]))
    st.mat('stem', bk.Toon(P('#1E1426', '#33233F', '#4A3758'), cuts=[-1, -0.2, 0.4]))
    st.ico('bulb', loc=(0.02, 0, 0.0), r=0.14, scale=(1, 1, 1.25), rot=(10, 20, 15), sub=1)
    st.cyl('stem', loc=(0, 0, 0.32), r=0.05, h=0.36, verts=6)
    st.cone('stem', loc=(0, 0, 0.2), r1=0.11, r2=0.04, h=0.08, verts=6)
    img = bk.downsample_sprite(st.render(64, 'bulbp'), 16, cover=0.45)
    if want('block/glowbulb_picked'):
        save('block/glowbulb_picked', img)


def lumen_lamp(shale, crystal):
    for on in (False, True):
        img = shale.copy()
        fm = np.zeros((16, 16), bool)
        fm[3:13, 3:13] = True
        cr = crystal.copy()
        if not on:
            # dull, unlit teal: drop each colour two steps darker and desaturate
            idx = pix.quantize_rgba(cr, LUMEN)
            idx = np.clip(idx - 2, 0, 6)
            cr = R(idx, [pix.desat(c, 0.35) for c in LUMEN])
        img[fm] = cr[fm]
        # undershale frame lip: dark inner border, lit corner studs
        for i in range(2, 14):
            for (x, y) in [(i, 2), (2, i)]:
                img[y, x, :3] = SHALE[0]
            for (x, y) in [(i, 13), (13, i)]:
                img[y, x, :3] = SHALE[4]
        for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14)]:
            img[y, x, :3] = SHALE_V2
        name = 'block/lumen_lamp_on' if on else 'block/lumen_lamp'
        if on:
            img[4, 4:7, :3] = LUMEN[6]
            img[5, 4, :3] = LUMEN[6]
        if want(name):
            save(name, img)
        if on and want('block/lumen_lamp_on_emissive'):
            save('block/lumen_lamp_on_emissive', pix.emissive_from(img, fm))


# ===================================================================================================== drift
def driftstone_family():
    f = F(f_noise, fx=11.0, fy=2.5, seed=101, det=3.0, rough=0.6, shear=1)
    c = F(f_cluster, fx=4.0, seed=102, vmix=0.4)
    g = 0.72 * pix.norm(f) + 0.28 * pix.norm(c)
    idx = stone(g, 6, [0.06, 0.16, 0.28, 0.26, 0.16, 0.08], emb=0.8, desp=2)
    img = R(idx, DRIFT)
    if want('block/driftstone'):
        save('block/driftstone', img)
    f2 = F(f_noise, fx=6.0, fy=2.0, seed=103, det=1.0, shear=1)
    idx = np.clip(pix.quantile_index(f2, [0.3, 0.5, 0.2]) + 2, 0, 5)
    idx = pix.despeckle(idx, 2)
    idx = bevel_frame(idx, 6, 1, light=1, dark=-2)
    yy, xx = np.mgrid[0:16, 0:16]
    inner = (np.maximum(np.abs(xx - 7.5), np.abs(yy - 7.5)) == 5.5)
    idx = np.where(inner & ((xx + yy) % 2 == 0), 2, idx)
    img = R(idx, DRIFT)
    if want('block/polished_driftstone'):
        save('block/polished_driftstone', img)
    lay = brick_layout([5, 5, 6], [[0, 8], [4, 12], [0, 8]])
    idx = bricks(g, 6, lay, [0.2, 0.35, 0.3, 0.15], seed=104)
    img = R(idx, DRIFT)
    if want('block/driftstone_bricks'):
        save('block/driftstone_bricks', img)
    return R(stone(g, 6, [0.06, 0.16, 0.28, 0.26, 0.16, 0.08], emb=0.8, desp=2), DRIFT)


# ==================================================================================================== fracture
def null_obsidian():
    f = F(f_cluster, fx=4.0, seed=111, vmix=0.45)
    idx = stone(f, 5, [0.12, 0.24, 0.32, 0.22, 0.10], emb=0.8)
    img = R(idx, NULL)
    v = F(f_vein, ss=4, fx=2.2, seed=112, width=0.04)
    vm = v > 0.36
    vm = pix.despeckle(vm.astype(int), 1).astype(bool)
    img[vm, :3] = NULL_VEIN[0]
    hot = vm & (F(f_noise, fx=3.0, seed=113) > 0.55)
    img[hot, :3] = NULL_VEIN[1]
    if want('block/null_obsidian'):
        save('block/null_obsidian', img)
    if want('block/null_obsidian_emissive'):
        save('block/null_obsidian_emissive', pix.emissive_from(img, hot))
    return img, vm, hot


def fracture_glass():
    f = F(f_noise, fx=2.5, seed=121, det=1.0)
    img = glass_frame(P('#2A0612', '#3A0A1A', '#4E1024'), pix.hx('#1A0510'), pix.hx('#7A1030'), f,
                      [0.3, 0.45, 0.25], 170, 255, sheen=False)
    cr = np.zeros((16, 16), bool)
    hot = np.zeros((16, 16), bool)
    for k, path in enumerate(crack_paths(122, 3)):
        for i, (x, y) in enumerate(path):
            if 1 <= x < 15 and 1 <= y < 15:
                cr[y, x] = True
                if 2 < i < len(path) - 2 and k == 0:
                    hot[y, x] = True
    img[cr, :3] = pix.hx('#FF3FB4')
    img[cr, 3] = 255
    img[hot, :3] = pix.hx('#FFB3EC')
    if want('block/fracture_glass'):
        save('block/fracture_glass', img)
    if want('block/fracture_glass_emissive'):
        save('block/fracture_glass_emissive', pix.emissive_from(img, cr))


def unmade_shard():
    defs = [(4.5, 15.8, 2.0, 7.5, 1.6, 1.4, 0.4), (12.0, 15.8, 14.5, 9.0, 1.5, 1.3, 0.4),
            (8.2, 15.8, 7.0, 0.6, 2.8, 2.0, 0.45)]
    m, f, s, sid = spikes(defs, cover=0.42)
    pl = P('#050307', '#0D0812', '#1A1022', '#2A1A36', '#FF3FB4', '#FFB3EC', '#A3196E')
    idx = crystal_index(m, f, s, 4, base=(3, 2, 1), grad=0.0)
    Pm = np.pad(m, 1)
    left = m & ~Pm[1:-1, :-2]
    right = m & ~Pm[1:-1, 2:]
    # jagged: notch the main spike's right edge
    for y in (4, 9):
        xs = np.nonzero(m[y])[0]
        if len(xs) > 2:
            m[y, xs[-1]] = False
            idx[y, xs[-1]] = -1
    glow = (left | (m & (s > 0.8))) & m
    idx = np.where(glow, np.where(s > 0.45, 4, 6), idx)
    idx = np.where(m & (s > 0.9), 5, idx)
    img = R(idx, pl)
    if want('block/unmade_shard'):
        save('block/unmade_shard', img)
    if want('block/unmade_shard_emissive'):
        save('block/unmade_shard_emissive', pix.emissive_from(img, glow))


def ash_stone():
    f = F(f_cluster, fx=4.5, seed=131, vmix=0.45)
    idx = stone(f, 6, [0.06, 0.18, 0.30, 0.26, 0.14, 0.06], emb=1.0)
    img = R(idx, ASH)
    if want('block/ash_stone'):
        save('block/ash_stone', img)


def crystal_index(mask, facet, s, n, base=(4, 3, 1), grad=0.0, noise=None, edges=True):
    """Facet shading for spikes: lit-left / front / shaded-right tones, gradient along the axis,
    a light rim on the left silhouette and a dark rim on the right."""
    b = np.array(base)
    idx = b[facet] + np.round(grad * (s - 0.5)).astype(int)
    if noise is not None:
        idx = idx + noise
    if edges:
        Pm = np.pad(mask, 1)
        left_open = mask & ~Pm[1:-1, :-2]
        right_open = mask & ~Pm[1:-1, 2:]
        idx = np.where(right_open, idx - 1, idx)
        idx = np.where(left_open & ~right_open, idx + 1, idx)
    idx = np.clip(idx, 0, n - 1)
    return np.where(mask, idx, -1)



def mirror_shard_sprite():
    m, f, s, sid = spikes([(9.0, 15.8, 10.2, 1.0, 1.9, 1.5, 0.35), (5.5, 15.8, 3.8, 8.0, 1.2, 1.1, 0.4)])
    pl = P('#05040B', '#0C0B1C', '#1A1C30', '#2C3452', '#8FB0FF', '#DFE8FF')
    idx = crystal_index(m, f, s, 4, base=(2, 1, 0), grad=0.0)
    Pm = np.pad(m, 1)
    left = m & ~Pm[1:-1, :-2]
    idx = np.where(left & (sid == 0), np.where(s > 0.45, 5, 4), idx)
    idx = np.where(left & (sid == 1), 3, idx)
    img = R(idx, pl)
    if want('block/mirror_shard'):
        save('block/mirror_shard', img)



def crack_paths(seed, n_cracks, size=16, start_side=None, length=(8, 14)):
    """Jagged crack polylines: random walks steered by a Blender-baked fBm direction field."""
    fld = F(f_noise, ss=1, size=64, fx=3.0, seed=seed, det=2.0)
    rng = np.random.RandomState(seed)
    paths = []
    for c in range(n_cracks):
        x, y = rng.uniform(3, 13), rng.uniform(3, 13)
        ang = rng.uniform(0, 2 * math.pi)
        pts = [(int(x), int(y))]
        L = rng.randint(*length)
        for i in range(L):
            ang += (fld[int(y * 4) % 64, int(x * 4) % 64] - 0.5) * 1.1
            x += math.cos(ang) * 1.0
            y += math.sin(ang) * 1.0
            p = (int(round(x)), int(round(y)))
            if p != pts[-1]:
                pts.append(p)
            if rng.rand() < 0.12 and i > 2:  # branch
                bx, by, ba = x, y, ang + rng.choice([-1, 1]) * 1.1
                for j in range(rng.randint(2, 5)):
                    bx += math.cos(ba)
                    by += math.sin(ba)
                    pts.append((int(round(bx)), int(round(by))))
                pts.append(p)
        paths.append(pts)
    return paths



BUILDERS = [pale_loam, lilac_tuft, chorus_pod, stillstone_family, basin_glass, weathered_purpur, purpur_rubble,
            spire_lantern, ceiling_moss, voidroot, glowbulb, driftstone_family, null_obsidian,
            fracture_glass, unmade_shard, ash_stone]


def build():
    for b in BUILDERS:
        b()
    shale = undershale_family()
    crystal = lumen_crystals()
    lumen_lamp(shale, crystal)
    try:
        from . import blocks2
    except ImportError:
        return
    blocks2.build()
