"""Expansion (1.2 -> 2.0) item sprites, art/SPEC_EXPANSION.md section B.

Same recipe as items.py: round / solid things are small low-poly Blender models rendered on the orthographic toon
stage (flat N.L bands, top-left light), point-sampled to 16x16 and outlined with a darkened rim; flat things (charts,
feathers, scales, the tuning fork) are rasterised from analytic shapes in a rotated local frame with 4x supersampling,
then lit from the top-left and outlined the same way."""
import math
import numpy as np

from . import pix, bk
from .tex import save, want, F, f_noise, disc, edge_of, grow, line_pts
from .items import stage, finish, out, col_mask, helix, T, METAL

P = pix.pal
YY, XX = np.mgrid[0:16, 0:16]
TAU = math.pi * 2

# pact colours (art/SPEC_EXPANSION.md B): each pact is named by one hue, kept far apart so all eight read at 16x16
PACT = {'tide': '#3AE0D0', 'shards': '#FFB8E8', 'echoes': '#A87CFF', 'hoard': '#FF9A2E', 'memory': '#F2D04A',
        'stillness': '#D6DEEA', 'lone': '#FF7B7B', 'depths': '#3E62FF'}
SLATE = ['#16141A', '#24212A', '#3A3540', '#5A5266']
OBS = ['#0A0710', '#120C18', '#1E1828', '#2B2635', '#3D3749', '#524B60']
GOLD = ['#6E5420', '#9A7A30', '#C9A24A', '#E6C778', '#F6E3A8']
SILV = ['#5E6474', '#848B9C', '#AEB6C8', '#D6DCE8']
WARM_MAG = '#FF4FD8'


def hx(c):
    return pix.hx(c) if isinstance(c, str) else np.asarray(c)


def px(img, x, y, c, a=255):
    if 0 <= x < 16 and 0 <= y < 16:
        img[y, x, :3] = hx(c)
        img[y, x, 3] = a


def lighten(c, f):
    return pix.shade(hx(c), f)


def outlined(spr, darken=0.42):
    return pix.outline(spr, darken=darken)


# ============================================================================================ flat rasteriser
def raster(fn, ox, oy, ang, ss=4, cover=0.45):
    """Evaluate fn(u, v) -> int label (0 = empty) in a local frame whose +u axis leaves (ox, oy) at `ang` degrees
    (counter-clockwise from screen-right, screen y pointing down) and whose +v side faces the top-left light.
    Returns the majority label per pixel where coverage >= cover."""
    n = 16 * ss
    yy, xx = (np.mgrid[0:n, 0:n] + 0.5) / ss
    a = math.radians(ang)
    dx, dY = xx - ox, -(yy - oy)
    u = dx * math.cos(a) + dY * math.sin(a)
    v = -dx * math.sin(a) + dY * math.cos(a)
    lab = fn(u, v).astype(int)
    blocks = lab.reshape(16, ss, 16, ss).transpose(0, 2, 1, 3).reshape(16, 16, -1)
    out_ = np.zeros((16, 16), int)
    for y in range(16):
        for x in range(16):
            b = blocks[y, x]
            nz = b[b > 0]
            if len(nz) >= cover * len(b):
                vals, cnt = np.unique(nz, return_counts=True)
                out_[y, x] = vals[np.argmax(cnt)]
    return out_


def paint(labels, cols):
    img = np.zeros((16, 16, 4), np.uint8)
    for k, c in cols.items():
        m = labels == k
        img[m, :3] = hx(c)
        img[m, 3] = 255
    return img


# ================================================================================================ pact glyphs
GLYPHS = {
    'tide': [".##....", "#..#..#", "....##.", ".......", ".##....", "#..#..#", "....##."],
    'shards': ["...#...", "...#...", ".#.#...", ".#.##.#", "##.##.#", "##.####"],
    'echoes': ["#####", "#...#", "#...#", "#...#", "#####"],
    'hoard': ["...#...", "..#.#..", "..###..", ".##.##.", "#######"],
    'memory': ["..###..", ".#...#.", "#..#..#", ".#...#.", "..###.."],
    'stillness': [".###.", "#...#", "#...#", "#...#", ".###."],
    'lone': ["###....", "#.###..", "#.#.##.", "#..#.##", "#...#.#", ".#...##", "..###.."],
    'depths': ["######", ".....#", ".###.#", ".#.#.#", ".#...#", ".#####", "...#..", "...#.."],
}


def draw_glyph(spr, glyph, cx, cy, col, face):
    core = hx(col)
    hi = lighten(core, 1.55)
    mid = pix.shade(core, 0.68)
    h, w = len(glyph), len(glyph[0])
    x0, y0 = int(round(cx - w / 2)), int(round(cy - h / 2))
    g = {(x0 + i, y0 + j) for j, r in enumerate(glyph) for i, ch in enumerate(r) if ch == '#'}
    for (X, Y) in g:
        if (X + 1, Y + 1) not in g and 0 <= X + 1 < 16 and 0 <= Y + 1 < 16 and face[Y + 1, X + 1]:
            px(spr, X + 1, Y + 1, '#0E0C14')
    for (X, Y) in g:
        lit = (X - 1, Y) not in g or (X, Y - 1) not in g
        c = hi if lit else core
        if not lit and (X + 1, Y) not in g and (X, Y + 1) not in g:
            c = mid
        px(spr, X, Y, c)
    m = np.zeros((16, 16), bool)
    for X, Y in g:
        if 0 <= X < 16 and 0 <= Y < 16:
            m[Y, X] = True
    return m


def token(name, rim_pal, face_pal, r=6.4, view=(-22, 0)):
    st = stage(view=view)
    st.mat('rim', T(rim_pal, cuts=[-1, -0.35, 0.1, 0.6]))
    st.mat('face', T(face_pal, cuts=[-1, 0.0, 0.5]))
    st.cyl('rim', loc=(0, 0, 0), r=r, h=2.0, rot=(90, 0, 0), verts=20)
    st.cyl('face', loc=(0, -0.2, 0), r=r - 1.15, h=2.0, rot=(90, 0, 0), verts=20)
    spr, em = finish(st, name, cover=0.42, outline=False)
    face = col_mask(spr, face_pal, 2)
    ys, xs = np.nonzero(face)
    return spr, face, xs.mean() + 0.5, ys.mean() + 0.5


def pact_glyphs():
    for k, col in PACT.items():
        spr, face, cx, cy = token('pact_glyph_' + k, SLATE, ['#2B2635', '#353042', '#3F394D'])
        draw_glyph(spr, GLYPHS[k], cx, cy, col, face)
        out('pact_glyph_' + k, outlined(spr))


# =============================================================================================== pact crystals
def rot_pts(pts, rx, rz):
    a, b = math.radians(rz), math.radians(rx)
    Rz = np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])
    Rx = np.array([[1, 0, 0], [0, math.cos(b), -math.sin(b)], [0, math.sin(b), math.cos(b)]])
    return [tuple(Rx @ Rz @ np.array(p, float)) for p in pts]


def crystal_item(name, crystal_pal, cage_pal, stud):
    """End-crystal idiom: a faceted octahedral crystal hanging in a dark cubic cage; the cage's corner glyph studs
    carry the pact colour, the crystal is tinted toward it."""
    st = stage(view=(-6, 0))
    st.mat('cr', T(crystal_pal, cuts=[-1, -0.4, 0.1, 0.55]))
    st.mat('cage', T(cage_pal, cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('stud', T([stud], emissive=True))
    s = 3.7
    corners = [(x, y, z) for x in (-s, s) for y in (-s, s) for z in (-s, s)]
    rc = rot_pts(corners, 35.26, 45)
    for i in range(8):
        for j in range(i + 1, 8):
            if sum(1 for a, b in zip(corners[i], corners[j]) if a != b) == 1:
                st.tube('cage', [rc[i], rc[j]], r=0.5)
    for p in rc:
        if p[1] < 0.5:  # studs on the corners facing the camera
            st.sphere('stud', loc=p, r=0.95, seg=8)
    ov = [(3.3, 0, 0), (-3.3, 0, 0), (0, 3.3, 0), (0, -3.3, 0), (0, 0, 4.3), (0, 0, -4.3)]
    of = [(0, 2, 4), (2, 1, 4), (1, 3, 4), (3, 0, 4), (2, 0, 5), (1, 2, 5), (3, 1, 5), (0, 3, 5)]
    st.mesh('cr', [(x, y - 1.6, z) for x, y, z in rot_pts(ov, 10, 40)], of)
    spr, em = finish(st, name, cover=0.4)
    out(name, spr)


def pact_crystals():
    for k, col in PACT.items():
        c = hx(col)
        pal = [pix.shade(c, 0.38), pix.shade(c, 0.66), c, pix.shade(c, 1.6)]
        crystal_item('pact_crystal_' + k, pal, ['#0C0A12', '#1A1622', '#2E2838', '#4A4058'], col)
    crystal_item('stillness_crystal', ['#9AA6BA', '#C8D2E0', '#E8EEF6', '#FFFFFF'], SILV, '#F4F8FF')


# =========================================================================================== warden trophies
def dragon_scale():
    prof = {2: 2.5, 3: 4.0, 4: 5.0, 5: 5.5, 6: 5.6, 7: 5.5, 8: 5.2, 9: 4.7, 10: 4.0, 11: 3.2, 12: 2.3, 13: 1.4,
            14: 0.6}
    cx = 7.5
    m = np.zeros((16, 16), bool)
    for y, w in prof.items():
        m[y] = np.abs(XX[0] + 0.5 - cx - 0.5) <= w
    lit = -((XX + 0.5 - 6.0) / 6.0 + (YY + 0.5 - 5.0) / 8.0)
    idx = np.clip(np.round(2.5 + lit * 2.2), 1, 5).astype(int)
    spr = np.zeros((16, 16, 4), np.uint8)
    for y, x in zip(*np.nonzero(m)):
        px(spr, x, y, OBS[idx[y, x]])
    e = edge_of(m)
    for y, x in zip(*np.nonzero(e)):
        px(spr, x, y, GOLD[3] if lit[y, x] > 0.0 else GOLD[1] if lit[y, x] < -0.6 else GOLD[2])
    for x, y in line_pts(5, 5, 9, 11):  # faint magenta shimmer along the scale
        if m[y, x] and not e[y, x]:
            px(spr, x, y, '#B03A9A')
    for x, y in [(5, 5), (6, 6)]:
        px(spr, x, y, WARM_MAG)
    for x in range(5, 11):  # the overlapping lip of the scale above
        if m[3, x] and not e[3, x]:
            px(spr, x, 4, OBS[0])
    out('dragon_scale', outlined(spr))


def wyrm_barding():
    """Folded scale-plate armour: a draped blanket of slate scales (lit lip, gold rim under each scale), gold trim
    along the top edge, a fold shadow and a gold buckle."""
    spr = np.zeros((16, 16, 4), np.uint8)
    m = np.zeros((16, 16), bool)
    for y in range(3, 14):
        spread = (y - 3) * 0.25
        x0, x1 = 3 - spread, 12 + spread
        m[y] = (XX[0] >= x0) & (XX[0] <= x1)
    for x in range(16):  # scalloped hem
        if x % 3 == 1:
            m[13, x] = False
    m[13, :] &= m[12, :]
    for y, x in zip(*np.nonzero(m)):
        ly = (y - 4) % 2
        lx = (x + ((y - 4) // 2 % 2) * 1) % 3
        if ly == 0:
            c = OBS[5] if lx == 0 else OBS[4] if lx == 1 else OBS[3]
        else:
            c = OBS[2] if lx != 1 else GOLD[1]
        px(spr, x, y, c)
    for x in range(16):
        if m[3, x]:
            px(spr, x, 3, GOLD[3] if x < 9 else GOLD[2])
    for y in range(4, 14):  # fold: shadow line down the middle-right
        x = 9 + (y - 4) // 4
        if m[y, x]:
            px(spr, x, y, OBS[1])
    for (x, y, c) in [(6, 2, GOLD[2]), (7, 2, GOLD[3]), (6, 1, GOLD[1]), (7, 1, GOLD[2])]:
        px(spr, x, y, c)
    out('wyrm_barding', outlined(spr))


def tuning_fork():
    spr = np.zeros((16, 16, 4), np.uint8)
    for x in range(8, 14):          # prong A (lit, top-left) on x+y=15
        px(spr, x, 15 - x, SILV[3] if x < 12 else SILV[2])
    for x in range(10, 16):         # prong B on x+y=19
        px(spr, x, 19 - x, SILV[2] if x < 14 else SILV[1])
    for (x, y, c) in [(9, 8, SILV[2]), (8, 8, SILV[2]), (9, 9, SILV[1]), (10, 10, SILV[1])]:
        px(spr, x, y, c)
    for x in range(3, 9):           # handle: two diagonals, lit and shaded
        px(spr, x, 17 - x, SILV[3])
        px(spr, x + 1, 17 - x, SILV[1])
    for (x, y, c) in [(2, 14, SILV[2]), (2, 15, SILV[1]), (3, 15, SILV[0])]:
        px(spr, x, y, c)
    for (x, y, c) in [(8, 9, WARM_MAG), (7, 10, '#C2309E'), (8, 10, WARM_MAG), (7, 9, '#FFC8F2')]:
        px(spr, x, y, c)
    out('tuning_fork', outlined(spr))


def warden_banner_pattern():
    """Banner-pattern item idiom: a short paper scroll (rolled top and bottom) printed with the Warden motif,
    a dragon wing folded over a pillar."""
    spr = np.zeros((16, 16, 4), np.uint8)
    paper, paper_l, paper_d = '#E6DCC6', '#F4EEDF', '#C9BCA0'
    roll, roll_d, roll_l = '#B8A888', '#8E7E62', '#D6CAB0'
    for y in range(3, 14):
        for x in range(3, 13):
            px(spr, x, y, paper_l if x == 3 else paper_d if x == 12 else paper)
    for y0 in (1, 13):
        for x in range(2, 14):
            px(spr, x, y0, roll_l if x < 12 else roll)
            px(spr, x, y0 + 1, roll if x < 12 else roll_d)
        px(spr, 2, y0, roll)
        px(spr, 13, y0 + 1, roll_d)
    motif = ["####....",
             "#..##...",
             "#.#..##.",
             "#..#####",
             ".#...##.",
             "..#..##.",
             ".....##.",
             ".....##.",
             "....####"]
    for j, r in enumerate(motif):
        for i, ch in enumerate(r):
            if ch == '#':
                px(spr, 4 + i, 4 + j, '#3A3446')
    out('warden_banner_pattern', outlined(spr, 0.5))


def wardenborn_seal():
    spr = np.zeros((16, 16, 4), np.uint8)
    d = np.hypot(XX + 0.5 - 8, YY + 0.5 - 7.5)
    ang = np.arctan2(YY + 0.5 - 7.5, XX + 0.5 - 8)
    wob = 6.0 + 0.55 * np.sin(ang * 7 + 0.4)
    m = d <= wob
    lit = -((XX + 0.5 - 8) + (YY + 0.5 - 7.5)) / 9.0
    idx = np.clip(np.round(3 + lit * 1.6 - (d > 5.0) * 0.8), 1, 5).astype(int)
    for y, x in zip(*np.nonzero(m)):
        px(spr, x, y, OBS[idx[y, x]])
    ring = (np.abs(d - 4.3) < 0.55)
    for y, x in zip(*np.nonzero(ring)):
        px(spr, x, y, GOLD[3] if lit[y, x] > 0 else GOLD[1])
    wing = ["#....",
            "##...",
            "#.##.",
            "#..##",
            "##.#."]
    for j, r in enumerate(wing):
        for i, ch in enumerate(r):
            if ch == '#':
                px(spr, 6 + i, 5 + j, GOLD[2] if j < 3 else GOLD[1])
    for x, y, c in [(5, 14, OBS[3]), (6, 14, OBS[2]), (10, 14, OBS[2]), (11, 14, OBS[1]), (5, 15, GOLD[2]),
                    (11, 15, GOLD[2])]:
        px(spr, x, y, c)
    out('wardenborn_seal', outlined(spr))


# ===================================================================================================== discs
def disc_item(name, disc_pal, label_pal, groove, decorate=None):
    st = stage(view=(-62, 0))
    st.mat('d', T(disc_pal, cuts=[-1, -0.2, 0.4, 0.8]))
    st.mat('label', T(label_pal, cuts=[-1, 0.0, 0.6]))
    st.mat('hole', T(['#050407'], emissive=True))
    st.cyl('d', r=7.6, h=0.8, verts=28)
    st.cyl('label', loc=(0, 0, 0.3), r=3.0, h=0.6, verts=20)
    st.cyl('hole', loc=(0, 0, 0.5), r=0.8, h=0.6, verts=8)
    spr, em = finish(st, name, cover=0.42, outline=False)
    d = col_mask(spr, disc_pal[2:], 2)
    for y, x in zip(*np.nonzero(d)):
        if (x - y) % 5 == 0:
            spr[y, x, :3] = hx(groove)
    lab = col_mask(spr, label_pal, 2)
    if decorate:
        decorate(spr, lab, col_mask(spr, disc_pal, 2))
    out(name, outlined(spr))


def discs():
    disc_item('music_disc_warden', OBS[1:5], GOLD[1:4], OBS[5])

    def ladder(spr, lab, body):
        ys, xs = np.nonzero(lab)
        x0, x1 = xs.min(), xs.max()
        for y in range(ys.min(), ys.max() + 1):
            for x in (x0 + 1, x1 - 1):
                if lab[y, x]:
                    px(spr, x, y, '#56678A')
            if (y - ys.min()) % 2 == 1:
                for x in range(x0 + 1, x1):
                    if lab[y, x]:
                        px(spr, x, y, '#56678A')
    disc_item('music_disc_ladders', ['#6E7FA0', '#93A6C4', '#B9C7DD', '#D6DCE8'], ['#C9B46A', '#E8D38A', '#F6E9B8'],
              '#A8B8D2', ladder)

    def spiral(spr, lab, body):
        ys, xs = np.nonzero(body)
        cx, cy = (xs.min() + xs.max()) / 2 + 0.5, (ys.min() + ys.max()) / 2 + 0.5
        ry = (ys.max() - ys.min() + 1) / (xs.max() - xs.min() + 1)
        for i in range(160):
            t = i / 159
            a = t * TAU * 2.2
            r = 1.8 + 5.6 * t
            x, y = int(math.floor(cx + math.cos(a) * r)), int(math.floor(cy + math.sin(a) * r * ry))
            if 0 <= x < 16 and 0 <= y < 16 and body[y, x] and not lab[y, x]:
                px(spr, x, y, '#0B1416')
    disc_item('music_disc_undertow', ['#5E9C95', '#8FCFC6', '#CFF3EC', '#EAFBF8'], ['#1E8E86', '#3AE0D0', '#7FF2E2'],
              '#B4E6DE', spiral)


# ============================================================================================= the causeway
def causeway_keystone():
    st = stage(view=(-16, 28))
    st.mat('p', T(['#5E3F6E', '#8D649D', '#A87BB6', '#C9A6D4'], cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('d', T(['#43375E', '#5C4D79', '#7A6A98', '#9A8CB8'], cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('chip', T(['#6FE6DA'], emissive=True))

    def wedge(zb, zt, wb, wt, dep, mat, grow_=0.0):
        hb, ht, hd = wb / 2 + grow_, wt / 2 + grow_, dep / 2 + grow_
        v = [(-hb, -hd, zb), (hb, -hd, zb), (ht, -hd, zt), (-ht, -hd, zt),
             (-hb, hd, zb), (hb, hd, zb), (ht, hd, zt), (-ht, hd, zt)]
        f = [(0, 1, 2, 3), (5, 4, 7, 6), (4, 0, 3, 7), (1, 5, 6, 2), (3, 2, 6, 7), (4, 5, 1, 0)]
        st.mesh(mat, v, f)
    wedge(-6.0, 6.0, 6.4, 11.6, 5.0, 'p')
    wedge(-1.8, 1.4, 8.4, 9.9, 5.0, 'd', grow_=0.55)
    st.ico('chip', loc=(0.0, -3.1, 3.8), r=1.2, sub=1)
    spr, em = finish(st, 'keystone', cover=0.42)
    out('causeway_keystone', spr)


def city_chart():
    """A rolled chart: cream paper between two scroll rolls, with a purple End City tower silhouette."""
    spr = np.zeros((16, 16, 4), np.uint8)
    for y in range(3, 14):
        for x in range(3, 13):
            px(spr, x, y, '#E3D6B4' if (x + y) % 7 else '#D6C8A2')
    for x0 in (1, 13):
        for y in range(2, 15):
            px(spr, x0, y, '#D6C8A2')
            px(spr, x0 + 1, y, '#B4A47E')
        px(spr, x0, 1, '#8E7E62')
        px(spr, x0 + 1, 15, '#8E7E62')
    tower = ["...#..",
             "..###.",
             ".#####",
             "..###.",
             "..###.",
             ".#####",
             "..###.",
             "..###.",
             "..###."]
    for j, r in enumerate(tower):
        for i, ch in enumerate(r):
            if ch == '#':
                px(spr, 4 + i, 4 + j, '#8D649D' if i < 3 else '#5E3F6E')
    px(spr, 7, 4, '#C9A6D4')
    for x, y in [(10, 6), (11, 8), (10, 11)]:
        px(spr, x, y, '#B4A47E')
    out('city_chart', outlined(spr, 0.5))


def chart_base():
    """Dark parchment square with curled corners (vanilla map idiom)."""
    m = np.zeros((16, 16), bool)
    m[1:15, 1:15] = True
    for x, y in [(1, 1), (14, 14), (14, 1), (1, 14)]:
        m[y, x] = False
    spr = np.zeros((16, 16, 4), np.uint8)
    n = F(f_noise, ss=2, size=16, fx=4.0, seed=2101, det=2.0)
    idx = np.clip(pix.quantile_index(n, [0.3, 0.5, 0.2]), 0, 2)
    idx = pix.despeckle(idx, 2)
    pal = ['#241E32', '#2A2438', '#31293F']
    for y, x in zip(*np.nonzero(m)):
        px(spr, x, y, pal[idx[y, x]])
    e = edge_of(m)
    for y, x in zip(*np.nonzero(e)):
        px(spr, x, y, '#3D3550' if (x + y) < 15 else '#17131F')
    inner = m & ~e
    return spr, inner


def chart_grid(spr, inner):
    for y, x in zip(*np.nonzero(inner)):
        if (x - 1) % 4 == 0 or (y - 1) % 4 == 0:
            px(spr, x, y, '#3A3150')


def chart_contours(spr, inner):
    f = F(f_noise, ss=4, size=16, fx=2.2, seed=2102, det=2.0)
    land = (f > np.quantile(f, 0.62)) & inner
    land = pix.despeckle(land.astype(int), 2, wrap=False).astype(bool) & inner
    tide = (f < np.quantile(f, 0.3)) & inner
    for y, x in zip(*np.nonzero(tide)):
        px(spr, x, y, '#1E5A58' if (x + 2 * y) % 3 else '#2E7F78')
    for y, x in zip(*np.nonzero(land)):
        px(spr, x, y, '#5E5672')
    for y, x in zip(*np.nonzero(edge_of(land))):
        px(spr, x, y, '#C9C3D8')
    return land


def charts():
    spr, inner = chart_base()
    chart_grid(spr, inner)
    out('void_chart', outlined(spr, 0.5))
    spr, inner = chart_base()
    chart_grid(spr, inner)
    chart_contours(spr, inner)
    out('void_chart_filled', outlined(spr, 0.5))
    spr, inner = chart_base()
    chart_grid(spr, inner)
    chart_contours(spr, inner)
    border = edge_of(np.pad(np.ones((14, 14), bool), 1)) & ~np.isin(XX + YY * 16, [17, 30, 225, 238])
    border &= spr[..., 3] > 0
    for y, x in zip(*np.nonzero(border)):
        px(spr, x, y, '#E8D38A' if (x + y) < 15 else '#B89A4A')
    for (x, y) in [(4, 4), (10, 6), (6, 10), (11, 11)]:
        px(spr, x, y, '#FFF6D8')
    for (x, y) in [(3, 4), (5, 4), (4, 3), (4, 5)]:
        px(spr, x, y, '#E8D38A')
    out('star_chart', outlined(spr, 0.5))


def courier_token():
    spr, face, cx, cy = token('courier_token', ['#6E3E0E', '#A8641A', '#E08A26', '#FFC266'],
                              ['#C9782A', '#E8962E', '#FFB238'], r=5.2, view=(-20, 0))
    arrow = ["..#..", "...#.", "#####", "...#.", "..#.."]
    h, w = len(arrow), len(arrow[0])
    x0, y0 = int(round(cx - w / 2)), int(round(cy - h / 2))
    for j, r in enumerate(arrow):
        for i, ch in enumerate(r):
            if ch == '#':
                if face[min(y0 + j + 1, 15), min(x0 + i + 1, 15)]:
                    px(spr, x0 + i + 1, y0 + j + 1, '#FFD890')
    for j, r in enumerate(arrow):
        for i, ch in enumerate(r):
            if ch == '#':
                px(spr, x0 + i, y0 + j, '#5A3008')
    out('courier_token', outlined(spr))


# ========================================================================================== zenith & undertow
def fish(name, pal, speck):
    """Flat mirror fish laid diagonally (vanilla fish idiom): head bottom-left, forked tail top-right."""
    def fn(u, v):
        lab = np.zeros(u.shape, int)
        t = np.clip(u / 10.0, 0, 1)
        hw = 2.6 * np.sin(np.pi * np.clip((u + 0.6) / 10.6, 0, 1)) ** 0.7
        body = (u >= -0.6) & (u <= 10.0) & (np.abs(v) <= hw)
        tail = (u > 9.2) & (u <= 13.2) & (np.abs(v) <= (u - 9.2) * 0.75 + 0.3) & ~((np.abs(v) < (u - 11.6) * 0.9))
        fin = (u > 3.5) & (u < 6.5) & (v < -hw + 0.1) & (v > -hw - 1.2 + (u - 3.5) * 0.3)
        lab[body] = 2
        lab[body & (v > hw * 0.35)] = 3
        lab[body & (np.abs(v - hw * 0.05) < 0.45) & (u > 1.5) & (u < 8.5)] = 4
        lab[body & (v < -hw * 0.45)] = 1
        lab[tail | fin] = 1
        lab[tail & (v > 0.4)] = 2
        return lab
    lab = raster(fn, 2.4, 13.0, 45)
    spr = paint(lab, {1: pal[0], 2: pal[1], 3: pal[2], 4: pal[3]})
    ys, xs = np.nonzero(lab)
    eye = (3, 10)
    px(spr, eye[0], eye[1], '#0B0B12')
    for x, y in [(6, 9), (8, 6), (9, 8), (5, 11)]:
        if lab[y, x] in (2, 3):
            px(spr, x, y, speck)
    out(name, outlined(spr))


def pile(name, pal, glints, seed, flecks=0.86, glint_cols=('#FFFFFF',)):
    st = stage(view=(-25, 0))
    st.mat('d', T(pal, cuts=[-1, -0.3, 0.2, 0.62]))
    st.lathe('d', [(0, 2.6), (2.5, 1.9), (5.0, -0.6), (6.6, -3.2), (0, -3.2)], seg=12, scale=(1, 0.8, 1), smooth=False)
    st.lathe('d', [(0, 0.2), (1.6, -0.6), (3.0, -2.4), (0, -2.4)], loc=(4.8, -2.5, -1.0), seg=8, smooth=False)
    spr, em = finish(st, name, cover=0.42, outline=False)
    op = spr[..., 3] > 0
    n = F(f_noise, ss=1, size=16, fx=8.0, seed=seed, det=1.0)
    gl = op & (n > np.quantile(n[op], flecks))
    for k, (y, x) in enumerate(zip(*np.nonzero(gl))):
        px(spr, x, y, glints[k % len(glints)])
    for k, (x, y) in enumerate([(3, 5), (12, 6)]):
        px(spr, x, y, glint_cols[k % len(glint_cols)])
    out(name, outlined(spr))


def harrier_plume():
    def fn(u, v):
        lab = np.zeros(u.shape, int)
        hw = 2.3 * np.clip(np.sin(np.pi * np.clip(u / 12.5, 0, 1)), 0, 1) ** 0.55
        vane = (u >= 0.3) & (u <= 12.5) & (np.abs(v + 0.25 * np.sin(u * 0.3)) <= hw)
        quill = (u >= -2.2) & (u <= 12.0) & (np.abs(v) <= 0.45)
        lab[vane] = 2
        lab[vane & (v > 0.5)] = 3
        lab[vane & (v < -0.9)] = 1
        bar = vane & (np.floor(u / 1.6) % 2 == 0) & (u > 2.0) & (u < 11.0)
        lab[bar & (v > 0)] = 5
        lab[bar & (v <= 0)] = 4
        lab[quill] = 6
        return lab
    lab = raster(fn, 2.6, 13.4, 45, cover=0.4)
    spr = paint(lab, {1: '#33405A', 2: '#4A5B7A', 3: '#6A7CA0', 4: '#AEB6C8', 5: '#D9C88A', 6: '#E6EAF2'})
    out('harrier_plume', outlined(spr))


def current_vane():
    st = stage(view=(0, 0))
    st.mat('rim', T(SILV, cuts=[-1, -0.2, 0.3, 0.7]))
    st.mat('dial', T(['#141826', '#1E2230', '#262B3C'], cuts=[-1, 0.2, 0.75]))
    st.cyl('dial', loc=(0, 0.6, 0), r=6.4, h=1.0, rot=(90, 0, 0), verts=24)
    st.torus('rim', loc=(0, 0, 0), R=6.4, r=1.05, rot=(90, 0, 0), maj=28, minr=8)
    spr, em = finish(st, 'current_vane', cover=0.4, outline=False)
    for x, y in [(8, 3), (8, 12), (3, 8), (12, 8)]:
        px(spr, x, y, '#5E6474')
    for j, (x, y) in enumerate(line_pts(5, 11, 11, 5)):
        px(spr, x, y, '#F6E3A8' if j >= 4 else '#C9A24A')
    for x, y in [(10, 5), (11, 6), (11, 5)]:
        px(spr, x, y, '#FFF6D8')
    for x, y in [(4, 11), (5, 12), (4, 10), (6, 12)]:
        px(spr, x, y, '#9A7A30')
    px(spr, 8, 8, '#E6C778')
    out('current_vane', outlined(spr))


def leviathan_barnacle():
    st = stage(view=(-38, 15))
    st.mat('sh', T(['#7A7282', '#A8A0AE', '#CEC7D2', '#EEE9F0'], cuts=[-1, -0.3, 0.2, 0.6]))
    st.mat('in', T(['#E8C46A'], emissive=True))
    st.mat('dk', T(['#5A4A3A'], emissive=True))
    for (x, y, r, h) in [(-2.6, 0.5, 3.0, 4.6), (2.8, 1.2, 2.6, 4.0), (0.4, -2.6, 2.2, 3.4)]:
        st.cone('sh', loc=(x, y, -1.5 + h / 2 - 2.0), r1=r, r2=r * 0.55, h=h, verts=10)
        st.cyl('dk', loc=(x, y, -1.5 + h - 1.95), r=r * 0.55, h=0.2, verts=10)
        st.cyl('in', loc=(x, y, -1.5 + h - 1.85), r=r * 0.36, h=0.25, verts=10)
    for (x, y, z) in [(-4.6, -1.0, -3.0), (4.8, -0.8, -3.2), (-0.8, 3.0, -2.6), (1.6, -4.0, -3.4)]:
        st.sphere('sh', loc=(x, y, z), r=1.2, seg=8)
    spr, em = finish(st, 'barnacle', cover=0.42)
    out('leviathan_barnacle', spr)


def eclipse_dust():
    """Dye idiom: a small lumpy clump of dark powder on a smear of dust; each lump carries a thin silver
    crescent on its lit rim (the eclipse), a few silver glints."""
    st = stage(view=(-24, 0))
    st.mat('d', T(['#0C0B12', '#16141E', '#22202C', '#34303F'], cuts=[-1, -0.3, 0.2, 0.62]))
    for (x, y, z, r) in [(-1.6, 0, 0.4, 3.6), (2.4, 0.6, -0.6, 3.0), (0.2, -1.4, -2.4, 2.6)]:
        st.sphere('d', loc=(x, y, z), r=r, scale=(1.0, 1.0, 0.85), seg=14)
    st.lathe('d', [(0, -3.2), (5.6, -3.8), (6.8, -5.0), (0, -5.0)], seg=14, smooth=False)
    spr, em = finish(st, 'eclipse', cover=0.42, outline=False)
    op = spr[..., 3] > 0
    light = col_mask(spr, ['#34303F'], 2)
    Pm = np.pad(light, 1)
    cres = light & ~Pm[:-2, 1:-1]
    spr[cres, :3] = pix.hx('#C9D2DC')
    for x, y in [(9, 12), (4, 12), (12, 11), (7, 10)]:
        if op[y, x]:
            px(spr, x, y, '#8F9AA6')
    out('eclipse_dust', outlined(spr))


def archive_codex():
    st = stage(view=(-18, 30))
    st.mat('cov', T(['#1A1626', '#2A2140', '#3A2D58', '#4A3A6E'], cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('pg', T(['#8FA6A0', '#C9D6D2', '#E6EEEA'], cuts=[-1, 0.0, 0.5]))
    st.cube('cov', size=(9.6, 4.2, 12.4))
    st.cube('pg', loc=(0.7, 0, 0), size=(9.0, 3.4, 11.6))
    st.cube('cov', loc=(0, -2.3, 0), size=(9.6, 0.6, 12.4))
    spr, em = finish(st, 'codex', cover=0.42, outline=False)
    face = col_mask(spr, ['#2A2140', '#3A2D58', '#4A3A6E'], 2)
    ys, xs = np.nonzero(face)
    cx, cy = int(round(np.median(xs))), int(round(np.median(ys)))
    glyph = [".#.", "###", ".#.", "#.#"]
    g = np.zeros((16, 16), bool)
    for j, r in enumerate(glyph):
        for i, ch in enumerate(r):
            if ch == '#':
                x, y = cx - 1 + i, cy - 2 + j
                if face[y, x]:
                    px(spr, x, y, '#7FF2E2' if j < 2 else '#3AE0D0')
                    g[y, x] = True
    for x in range(xs.min(), xs.max() + 1):  # cover band near the spine top
        if face[ys.min() + 1, x]:
            px(spr, x, ys.min() + 1, '#1A1626')
    spr = outlined(spr)
    out('archive_codex', spr, g, emissive=True)


def abyssal_anchor():
    st = stage(view=(-18, 0))
    st.mat('cap', T(['#1E2230', '#2E3444', '#4A5266', '#6E788E'], cuts=[-1, -0.35, 0.2, 0.62]))
    st.mat('glass', T(['#04100F', '#0A2224', '#123638', '#1E5250'], cuts=[-1, -0.3, 0.2, 0.6]))
    st.mat('chain', T(SILV, cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('core', T(['#FFFFFF'], emissive=True))
    st.cyl('cap', loc=(0, 0, 3.9), r=4.3, h=1.8, verts=16)
    st.cyl('cap', loc=(0, 0, -5.0), r=4.3, h=1.8, verts=16)
    st.cyl('glass', loc=(0, 0, -0.55), r=3.6, h=7.2, verts=16)
    st.sphere('core', loc=(0, -2.6, -0.6), r=1.6, seg=10)
    st.tube('chain', helix(1.7, 5.4, 1.4, z0=-3.2, phase=0.6), r=0.42, loc=(0, -2.3, 0))
    st.torus('chain', loc=(0, 0, 5.7), R=1.3, r=0.5, rot=(90, 0, 0))
    st.torus('chain', loc=(0, 0, 7.3), R=1.2, r=0.5, rot=(0, 0, 0))
    spr, em = finish(st, 'abyssal_anchor', cover=0.4)
    out('abyssal_anchor', spr)


def undertow_pearl():
    st = stage(view=(-10, 0))
    st.mat('p', T(['#5E9C95', '#8FCFC6', '#CFF3EC', '#F2FFFC'], cuts=[-1, -0.35, 0.2, 0.62]))
    st.sphere('p', r=5.4, seg=20)
    spr, em = finish(st, 'pearl', cover=0.42, outline=False)
    op = spr[..., 3] > 0
    ys, xs = np.nonzero(op)
    cx, cy = xs.mean() + 0.5, ys.mean() + 0.5
    for i in range(50):
        t = i / 49
        a = t * TAU * 1.25 + 2.0
        r = 0.3 + 2.4 * t
        x, y = int(math.floor(cx + math.cos(a) * r)), int(math.floor(cy + math.sin(a) * r))
        px(spr, x, y, '#0B1416' if t < 0.35 else '#2E6A66' if t < 0.75 else '#5E9C95')
    px(spr, int(cx) - 3, int(cy) - 3, '#FFFFFF')
    out('undertow_pearl', outlined(spr))


def void_bell():
    st = stage(view=(-14, 20))
    st.mat('f', T(['#1A1622', '#2E2838', '#4A4058', '#6A5E7E'], cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('gl', T(['#12303A', '#1E4A58', '#3E6A78'], cuts=[-1, 0.0, 0.5]))
    st.mat('core', T(['#7FF2E2'], emissive=True))
    st.mat('lamp', T(['#FFD27A'], emissive=True))
    st.lathe('f', [(0.0, 4.6), (2.6, 4.2), (4.6, 2.2), (5.6, -1.4), (6.4, -4.4), (5.6, -4.8), (0.0, -4.8)], seg=18)
    st.cyl('gl', loc=(0, -5.2, -0.6), r=2.3, h=1.2, rot=(90, 0, 0), verts=14)
    st.cyl('core', loc=(0, 0, 5.4), r=1.5, h=1.6, verts=10)
    st.sphere('lamp', loc=(0, 0, -5.6), r=1.1, seg=8)
    spr, em = finish(st, 'void_bell', cover=0.42)
    g = col_mask(spr, ['#3E6A78'], 2)
    ys, xs = np.nonzero(g)
    if len(ys):
        px(spr, xs.min(), ys.min(), '#BFEFFF')
    out('void_bell', spr)


BUILDERS = [pact_glyphs, pact_crystals, dragon_scale, wyrm_barding, tuning_fork, warden_banner_pattern,
            wardenborn_seal, discs, causeway_keystone, city_chart, charts, courier_token,
            lambda: fish('mirrorfish', ['#5E6474', '#9AA4B6', '#C8D2E0', '#F2F6FC'], '#2A3040'),
            lambda: fish('cooked_mirrorfish', ['#6A5448', '#A88C74', '#CDB496', '#EEDDC2'], '#3A2A22'),
            lambda: pile('driftsilt', ['#3E5A5C', '#5E7E7E', '#7E9E9C', '#A8C4C0'], ['#E6FFFB', '#7FF2E2'], 2201),
            harrier_plume, current_vane, leviathan_barnacle, eclipse_dust,
            lambda: pile('null_pigment', ['#07080A', '#14171C', '#232830', '#3A424C'], ['#8F9AA6', '#C9D2DC'], 2202,
                         flecks=0.8, glint_cols=('#8F9AA6',)),
            archive_codex, abyssal_anchor, undertow_pearl, void_bell]


def build():
    for b in BUILDERS:
        b()
