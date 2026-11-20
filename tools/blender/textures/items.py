"""Item sprites. 3D items are modelled as small low-poly objects in Blender and rendered with an orthographic toon
stage (flat N.L colour bands, light from the top-left), then point-sampled down to 16x16 with mode filtering,
outlined with a darkened rim (vanilla style) and hand-fixed in numpy where readability needs it.
Stage units: ortho=16 so 1 Blender unit == 1 texture pixel; X = screen right, Z = screen up, Y = depth."""
import math
import numpy as np

from . import pix, bk
from .tex import save, want, F, f_noise, f_cells, disc, edge_of, grow, line_pts, spikes

P = pix.pal
T = bk.Toon
YY, XX = np.mgrid[0:16, 0:16]

# shared palettes (dark -> light)
METAL = ['#3E3E48', '#6A6A78', '#9C9CAA', '#D2D2DC']
TEALG = ['#0E5F59', '#1E9E93', '#3AE0D0', '#9CF5EA']
TEAL_EM = ['#B8FFF7']
PURP = ['#5E3F6E', '#8D649D', '#A87BB6', '#C9A6D4']
SHALEP = ['#1E1B23', '#2E2A33', '#3A3540', '#524A5C']
NULLP = ['#0A0207', '#1A0510', '#2B0A18', '#4A1428']
PALEP = ['#8E8A9C', '#B1AEBC', '#D3D1DC', '#F0EEF6']
LAV = ['#6C4E93', '#8C68B6', '#AE8ADB', '#D8B6FF']


def col_mask(img, cols, tol=4):
    m = np.zeros(img.shape[:2], bool)
    for c in cols:
        c = pix.hx(c) if isinstance(c, str) else np.asarray(c)
        m |= (np.abs(img[..., :3].astype(int) - c.astype(int)).sum(-1) <= tol) & (img[..., 3] > 0)
    return m


def finish(st, name, res=64, cover=0.42, outline=True, darken=0.42, fix=None):
    img = st.render(res, name.replace('/', '_'))
    spr = bk.downsample_sprite(img, 16, cover)
    if fix:
        spr = fix(spr)
    em_cols = []
    for k, s in st.specs.items():
        if s.emissive:
            em_cols += [c for c in s.palette]
    em = col_mask(spr, em_cols, 2) if em_cols else np.zeros((16, 16), bool)
    if outline:
        spr = pix.outline(spr, darken=darken)
    return spr, em


def out(name, spr, em=None, emissive=False):
    if want('item/' + name):
        save('item/' + name, spr)
    if emissive and want('item/' + name + '_emissive'):
        save('item/' + name + '_emissive', pix.emissive_from(spr, em))


def stage(view=(0, 0), light=(-0.55, 0.62, 0.56)):
    return bk.Stage(view=view, ortho=16.0, light_cam=light)


def helix(r, h, turns, n=60, z0=0.0, phase=0.0):
    return [(r * math.cos(phase + TAU * turns * i / n), r * math.sin(phase + TAU * turns * i / n),
             z0 + h * i / n) for i in range(n + 1)]


TAU = math.pi * 2


# ============================================================================================== void anchor
def void_anchor(cracked=False):
    st = stage(view=(-18, 0))
    glass = ['#0B3C39', '#145E58', '#1F7F77', '#2EA79B'] if cracked else TEALG
    st.mat('metal', T(METAL, cuts=[-1, -0.35, 0.2, 0.62]))
    st.mat('glass', T(glass, cuts=[-1, -0.3, 0.2, 0.6]))
    st.mat('strand', T(['#7FD9CF' if cracked else '#E6FFFB'], emissive=True))
    st.cyl('metal', loc=(0, 0, 4.6), r=4.3, h=2.0, verts=16)
    st.cyl('metal', loc=(0, 0, -4.6), r=4.3, h=2.0, verts=16)
    st.cyl('glass', loc=(0, 0, 0), r=3.6, h=7.4, verts=16)
    st.tube('strand', helix(1.6, 6.0, 1.6, z0=-3.0, phase=0.6), r=0.55, loc=(0, -2.2, 0))
    st.torus('metal', loc=(0, 0, 6.6), R=1.5, r=0.55, rot=(90, 0, 0))
    spr, em = finish(st, 'void_anchor%s' % ('_c' if cracked else ''), cover=0.4)
    if cracked:
        op = col_mask(spr, glass, 2)
        for x, y in [(6, 6), (7, 7), (7, 8), (8, 9), (9, 9), (6, 9), (5, 10)]:
            if op[y, x]:
                spr[y, x, :3] = pix.hx('#C9F2EC') if (x + y) % 3 else pix.hx('#06201E')
        out('cracked_void_anchor', spr)
    else:
        em |= col_mask(spr, [TEALG[3]], 2)
        out('void_anchor', spr, em, emissive=True)


# ============================================================================================== rift tether
def rift_tether(cast=False):
    st = stage(view=(-55, 0))
    st.mat('cord', T(['#2B1F36', '#3A2A4A', '#55406A', '#7A5E96'], cuts=[-1, -0.3, 0.2, 0.6]))
    st.mat('claw', T(['#7E7A70', '#B6B2A6', '#D8D4C8', '#F2EFE6'], cuts=[-1, -0.3, 0.2, 0.6]))
    st.mat('pearl', T(['#1C5E4E', '#2E8A72', '#3FB89A', '#9CEBD5'], cuts=[-1, -0.25, 0.25, 0.65]))
    # coil (bottom-left), loops stacked
    coil = []
    for i in range(121):
        a = TAU * 3.0 * i / 120
        coil.append((-2.4 + 4.0 * math.cos(a), -1.0 + 4.0 * math.sin(a), -3.2 + 2.4 * i / 120))
    st.tube('cord', coil, r=0.75)
    end = coil[-1]
    if not cast:
        lead = [end, (end[0] + 1.5, -1.0, end[2] + 1.6), (2.4, -1.0, 2.0), (3.2, -1.0, 3.0)]
        st.tube('cord', lead, r=0.6)
        # claw: collar + three curved prongs around the pearl
        st.cyl('claw', loc=(3.6, -1.0, 3.4), r=1.1, h=1.2, rot=(0, 40, 0), verts=8)
        st.sphere('pearl', loc=(5.0, -1.4, 5.0), r=2.2)
        for k in range(3):
            a = TAU * k / 3 + 0.4
            pts = []
            for j in range(9):
                t = j / 8
                rr = 1.2 + 1.5 * math.sin(t * math.pi * 0.9)
                pts.append((4.0 + 1.3 * t + rr * math.cos(a) * 0.75, -1.4 + rr * math.sin(a) * 0.75,
                            3.8 + 2.8 * t))
            st.tube('claw', pts, r=0.42)
    else:
        lead = [end, (end[0] + 1.8, -1.0, end[2] + 2.2), (3.0, -1.5, 3.2), (4.4, -1.5, 4.0), (5.2, -1.0, 5.8)]
        st.tube('cord', lead, r=0.55)
    spr, em = finish(st, 'rift_tether%s' % ('_cast' if cast else ''), cover=0.38)
    out('rift_tether_cast' if cast else 'rift_tether', spr)


# =========================================================================================== gateway compass
def gateway_compass():
    for i in range(16):
        st = stage(view=(0, 0))
        st.mat('rim', T(['#3B2458', '#5E3A8A', '#8A5CC0', '#B48BE6'], cuts=[-1, -0.2, 0.3, 0.7]))
        st.mat('dial', T(['#0A0812', '#120E20', '#1B1530'], cuts=[-1, 0.2, 0.75]))
        st.mat('gate', T(['#06040C'], emissive=True))
        st.cyl('dial', loc=(0, 0.6, 0), r=6.4, h=1.0, rot=(90, 0, 0), verts=24)
        st.torus('rim', loc=(0, 0, 0), R=6.4, r=1.05, rot=(90, 0, 0), maj=28, minr=8)
        # needle: 4-point star with the north point elongated, rotated 22.5 deg per frame (clockwise).
        # Dial, rim and gateway diamond are rendered in Blender; the needle's 1-px arms do not survive the
        # coverage downsample at diagonal angles, so it is rasterised as pixel lines from the rotated star.
        ang = math.radians(22.5 * i)
        spr, em = finish(st, 'compass_%02d' % i, cover=0.4, outline=False)
        cx, cy = 8, 8
        arms = [(0.0, 5.3), (90.0, 2.4), (180.0, 2.4), (270.0, 2.4)]
        for k, (off, L) in enumerate(arms):
            a = ang + math.radians(off)
            ex, ey = cx + math.sin(a) * L, cy - math.cos(a) * L
            pts = line_pts(cx, cy, int(round(ex)), int(round(ey)))
            for j, (x, y) in enumerate(pts):
                c = '#FF7AD9'
                if k == 0:
                    c = '#FFFFFF' if j == len(pts) - 1 else '#FFB3EC' if j >= len(pts) - 3 else '#FF7AD9'
                spr[y, x, :3] = pix.hx(c)
                spr[y, x, 3] = 255
        spr[cy, cx, :3] = pix.hx('#FFB3EC')
        spr = pix.outline(spr, darken=0.42)
        out('gateway_compass_%02d' % i, spr)


# =========================================================================================== shulker satchel
def shulker_satchel():
    st = stage(view=(-15, 25))
    st.mat('bag', T(['#3E2A4E', '#5A3F6E', '#76578C', '#9275A8'], cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('shell', T(['#5E3F6E', '#8D649D', '#A87BB6', '#C9A6D4'], cuts=[-1, -0.3, 0.2, 0.6]))
    st.mat('brass', T(['#7A5A1E', '#C9A03A', '#F0D27A'], cuts=[-1, 0.0, 0.55]))
    st.mat('amber', T(['#FFB238'], emissive=True))
    st.mat('strap', T(['#1E1426', '#2E2236', '#43334E'], cuts=[-1, 0.0, 0.5]))
    st.sphere('bag', loc=(0, 0, -1.5), r=5.6, scale=(1.05, 0.7, 0.95), seg=16)
    st.cube('bag', loc=(0, 0, -3.5), size=(10.5, 7.0, 4.0))
    for k, (z, s) in enumerate([(2.8, 10.0), (1.4, 9.2), (0.0, 8.0)]):
        st.cube('shell', loc=(0, -3.6 - 0.5 * k, z), size=(s, 1.2, 2.6), rot=(-18, 0, 0))
    st.cube('brass', loc=(0, -5.3, -1.6), size=(2.2, 0.8, 2.2))
    st.sphere('amber', loc=(0, -5.9, -1.6), r=0.7, seg=8)
    st.torus('strap', loc=(0, 0, 4.0), R=3.6, r=0.6, rot=(90, 0, 0), maj=20, scale=(1, 1, 0.9))
    spr, em = finish(st, 'satchel', cover=0.42)
    out('shulker_satchel', spr)


# ================================================================================================= void lens
def void_lens():
    st = stage(view=(-20, -35))
    st.mat('b1', T(SHALEP, cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('band', T(['#2A1E3A', '#3A2A4A', '#4A3560', '#6A4C86'], cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('lens', T(['#2FC9BB', '#6FE6DA'], cuts=[-1, 0.3]))
    st.mat('glint', T(['#C8FFF8'], emissive=True))
    rot = (0, 45, 0)
    d = (math.sin(math.radians(45)), 0, math.cos(math.radians(45)))

    def along(t):
        return (d[0] * t, d[1] * t, d[2] * t)
    st.cyl('b1', loc=along(-4.0), r=1.7, h=6.0, rot=rot, verts=10)
    st.cyl('band', loc=along(-0.4), r=2.1, h=1.2, rot=rot, verts=10)
    st.cyl('b1', loc=along(2.4), r=2.3, h=4.6, rot=rot, verts=10)
    st.cyl('band', loc=along(5.0), r=2.8, h=1.2, rot=rot, verts=10)
    st.cyl('lens', loc=along(5.75), r=2.5, h=0.5, rot=rot, verts=10)
    st.sphere('glint', loc=tuple(np.add(along(5.9), (-0.6, -1.0, 0.6))), r=0.8, seg=8)
    spr, em = finish(st, 'void_lens', cover=0.4)
    out('void_lens', spr)


# ============================================================================================== skate harness
def skate_harness():
    st = stage(view=(-48, 32))
    st.mat('leather', T(['#4A2E18', '#7A4E2C', '#9A6A3E', '#B88552'], cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('edge', T(['#9E96B4', '#CFC8E0', '#ECE6F6'], cuts=[-1, 0.0, 0.5]))
    st.mat('brass', T(['#7A5A1E', '#C9A03A', '#F0D27A'], cuts=[-1, 0.0, 0.55]))
    st.mat('strap', T(['#3A2414', '#5A3820', '#74492A'], cuts=[-1, 0.0, 0.5]))
    st.sphere('leather', loc=(0, 0, 1.6), r=1.0, scale=(5.2, 3.6, 1.3), seg=16)
    st.sphere('edge', loc=(0, 0, 1.1), r=1.0, scale=(5.9, 4.2, 0.6), seg=16)
    for sx in (-1, 1):
        pts = [(sx * 4.6, 1.0, 1.4), (sx * 5.6, -1.5, -0.5), (sx * 5.4, -4.0, -2.0)]
        st.tube('strap', pts, r=0.75)
        st.torus('brass', loc=(sx * 5.2, -5.6, -2.6), R=1.4, r=0.5, rot=(60, 0, 0), maj=14)
    spr, em = finish(st, 'harness', cover=0.4)
    # stitches along the pale edge
    m = col_mask(spr, ['#CFC8E0', '#ECE6F6'], 2)
    for y, x in zip(*np.nonzero(m)):
        if (x + y) % 2 == 0:
            spr[y, x, :3] = pix.hx('#9E96B4')
    out('skate_harness', spr)


# ======================================================================================================== keys
def key_item(name, shaft_pal, gem_pal, gem_em, teeth='tower', extra=None):
    st = stage(view=(-8, 0))
    st.mat('k', T(shaft_pal, cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('gem', T(gem_pal, cuts=[-1, -0.1, 0.5]))
    st.mat('gemc', T([gem_em], emissive=True))
    a = math.radians(-45)
    ux, uz = math.cos(a), math.sin(a)   # shaft direction: from bow (top-left) toward bottom-right

    def P2(t, n=0.0):
        return (-4.2 + ux * t - uz * n, 0, 4.2 + uz * t + ux * n)
    st.torus('k', loc=P2(0), R=2.9, r=0.95, rot=(90, 0, 0), maj=16, minr=6)
    st.ico('gem', loc=(P2(0)[0], -0.6, P2(0)[2]), r=1.8, sub=1)
    st.sphere('gemc', loc=(P2(0)[0] - 0.3, -2.0, P2(0)[2] + 0.3), r=0.75, seg=8)
    mid = P2(7.6)
    st.cyl('k', loc=mid, r=0.95, h=10.0, rot=(0, 45 + 90, 0), verts=8)
    if teeth == 'tower':
        for t, h in [(8.4, 2.6), (10.6, 3.4)]:
            c = P2(t, -1.4 - h / 2)
            st.cube('k', loc=c, size=(1.4, 1.4, h), rot=(0, -45, 0))
            st.cone('k', loc=P2(t, -1.4 - h - 0.6), r1=1.0, h=1.4, verts=4, rot=(0, 180 - 45, 0))
    else:
        for t, h in [(8.0, 2.0), (10.2, 2.8), (11.8, 1.6)]:
            st.cube('k', loc=P2(t, -1.2 - h / 2), size=(1.3, 1.3, h), rot=(0, -45, 0))
    if extra:
        extra(st, P2)
    spr, em = finish(st, name, cover=0.4)
    return spr, em


def keys():
    spr, em = key_item('spire_key', PURP, ['#A3196E', '#FF3FB4', '#FFB6F0'], '#FFFFFF')
    em |= col_mask(spr, ['#FF3FB4', '#FFB6F0'], 2)
    out('spire_key', spr, em, emissive=True)

    def gems(st, P2):
        st.mat('g1', T(['#C9A6D4'], emissive=True))
        st.mat('g2', T(['#3AE0D0'], emissive=True))
        st.mat('g3', T(['#8FB0FF'], emissive=True))
    spr, em = key_item('rift_key', NULLP, ['#7A1030', '#C0206A', '#FF3FB4'], '#FFB3EC', teeth='plain')
    # three sigil gems set around the bow (Spire / Tide / Under)
    for (x, y), c in zip([(2, 2), (6, 2), (2, 6)], ['#C9A6D4', '#3AE0D0', '#8A7FA0']):
        if spr[y, x, 3]:
            spr[y, x, :3] = pix.hx(c)
    out('rift_key', spr)


# ============================================================================================== discs / sigils
def disc_item(name, face_pal, rim_pal, glyph, glyph_cols, emissive=False, translucent=False):
    st = stage(view=(-24, 0))
    st.mat('rim', T(rim_pal, cuts=[-1, -0.35, 0.1, 0.6]))
    st.mat('face', T(face_pal, cuts=[-1, 0.0, 0.5]))
    st.cyl('rim', loc=(0, 0, 0), r=6.6, h=2.0, rot=(90, 0, 0), verts=20)
    st.cyl('face', loc=(0, -0.2, 0), r=5.4, h=2.0, rot=(90, 0, 0), verts=20)
    spr, em = finish(st, name, cover=0.42, outline=False)
    face = col_mask(spr, face_pal, 2)
    ys, xs = np.nonzero(face)
    cx, cy = int(round(xs.mean() - 0.5)), int(round(ys.mean() - 0.5))
    g = np.zeros((16, 16), bool)
    for y, r in enumerate(glyph):
        for x, ch in enumerate(r):
            X, Y = cx - len(r) // 2 + x + 1, cy - len(glyph) // 2 + y
            if ch != '.' and 0 <= X < 16 and 0 <= Y < 16:
                spr[Y, X, :3] = pix.hx(glyph_cols[ch])
                g[Y, X] = True
    if translucent:
        spr[face & ~g, 3] = 220
    spr = pix.outline(spr, darken=0.42)
    out(name, spr, g, emissive=emissive)


def sigils():
    disc_item('spire_sigil', ['#8D649D', '#A87BB6', '#BA95C4'], ['#4A2D55', '#6E4A7E', '#9A72A8', '#C9A6D4'],
              ["..a..", ".aba.", ".aba.", "aabaa", "abbba"], {'a': '#2A1631', 'b': '#E3CBE8'})
    disc_item('tide_sigil', ['#1E9E93', '#2FC9BB', '#6FE6DA'], ['#0E5F59', '#167F78', '#22A79C', '#8AF0E6'],
              [".bb..bb", "b..bb..", "......."], {'b': '#E6FFFB'}, emissive=True, translucent=True)
    disc_item('under_sigil', ['#2E2A33', '#3A3540', '#47414E'], ['#16141A', '#24212A', '#3A3540', '#5A5266'],
              ["aaaaa", "..a..", ".aaa.", "a.a.a", "..a.."], {'a': '#9A8CB8'})


# ================================================================================================ orbs / eggs
def heart_of_stillness():
    st = stage(view=(-12, 0))
    st.mat('s', T(PALEP, cuts=[-1, -0.3, 0.25, 0.68]))
    st.mat('ring', T(['#6E86B8', '#B9D3FF'], cuts=[-1, 0.2]))
    st.mat('core', T(['#FFFFFF'], emissive=True))
    st.sphere('s', r=5.6, seg=24)
    st.sphere('core', loc=(-0.6, -5.0, 0.6), r=1.9, seg=12)
    spr, em = finish(st, 'heart', cover=0.42)
    halo = col_mask(spr, [PALEP[3]], 2) & disc(16, 7.5, 7.5, 3.3)
    for x, y in [(1, 4), (14, 11), (2, 12)]:  # faint 'ringing' glints around the orb
        spr[y, x, :3] = pix.hx('#B9D3FF')
        spr[y, x, 3] = 255
    spr[halo, :3] = pix.hx('#E8F0FF')
    em |= halo
    out('heart_of_stillness', spr, em, emissive=True)


def stillness_fragment():
    st = stage(view=(-15, 20))
    st.mat('s', T(PALEP, cuts=[-1, -0.3, 0.2, 0.6]))
    st.ico('s', r=5.2, scale=(1.0, 0.6, 0.75), rot=(10, 30, 25), sub=1)
    spr, em = finish(st, 'stillfrag', cover=0.42)
    for x, y in [(7, 6), (8, 7)]:
        if spr[y, x, 3]:
            spr[y, x, :3] = pix.hx('#FFFFFF')
    out('stillness_fragment', spr)


def dragon_egg_fragment():
    st = stage(view=(-20, 10))
    st.mat('shell', T(['#07050C', '#120D1C', '#1F1730', '#30244A'], cuts=[-1, -0.3, 0.2, 0.62],
                      pattern=lambda g: g.voronoi(g.objco(), 0.0, 0.35, 'F1', dims='3D'), pattern_cut=0.22,
                      palette2=['#07050C', '#1A0F2A', '#3B1F5C', '#5A2E86']))
    st.mat('in', T(['#2A1E3A', '#3D2C52'], cuts=[-1, 0.2]))
    # a curved piece of shell: a spherical cap, solidified
    verts, faces = [], []
    R_, n = 7.5, 7
    for i in range(n + 1):
        for j in range(n + 1):
            a = -0.7 + 1.4 * i / n
            b = -0.6 + 1.2 * j / n
            jag = 0.15 * math.sin(i * 2.1 + j * 1.3)
            verts.append((R_ * math.sin(a) * math.cos(b) * (1 + jag * 0.1), -R_ * math.cos(a) * math.cos(b) + 5.0,
                          R_ * math.sin(b)))
    for i in range(n):
        for j in range(n):
            k = i * (n + 1) + j
            faces.append((k, k + 1, k + n + 2, k + n + 1))
    st.mesh('shell', verts, faces, rot=(0, 25, 0), solidify=1.4)
    spr, em = finish(st, 'eggfrag', cover=0.42, outline=False)
    op = spr[..., 3] > 0
    # jagged break edge: nibble the silhouette, then a pink crack across
    for x, y in [(3, 5), (12, 11), (4, 12)]:
        spr[y, x, 3] = 0
    for x, y in line_pts(5, 4, 10, 11):
        if spr[y, x, 3]:
            spr[y, x, :3] = pix.hx('#FF7AD9')
    for x, y in [(6, 5), (9, 10)]:
        if spr[y, x, 3]:
            spr[y, x, :3] = pix.hx('#FFB3EC')
    spr = pix.outline(spr, darken=0.5)
    out('dragon_egg_fragment', spr)


def egg_profile(n=16, h=12.6, r=5.0):
    prof = []
    for i in range(n + 1):
        t = i / n
        z = -h / 2 + h * t
        rr = r * math.sin(math.pi * t) ** 0.55 * (1.0 - 0.16 * t)
        prof.append((max(rr, 0.0), z))
    return prof


def slumbering_egg():
    st = stage(view=(-10, 0))
    st.mat('e', T(['#2A3550', '#3A4A66', '#4B5D7A', '#6B80A0'], cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('ridge', T(['#4B5D7A', '#7F94B6', '#A9BCD8'], cuts=[-1, 0.0, 0.5]))
    st.mat('glow', T(['#FF9AD5'], emissive=True))
    st.lathe('e', egg_profile(16, 13.0, 5.4), seg=20)
    # the curled wyrm silhouette inside: a spiral ridge on the shell
    sp = []
    for i in range(50):
        t = i / 49
        a = 0.6 + TAU * 1.1 * t
        rr = 1.0 + 3.0 * t
        sp.append((rr * math.cos(a) * 0.9, -4.2 - 0.5 * t, rr * math.sin(a) * 1.0 - 0.5))
    st.tube('ridge', sp, r=0.55)
    st.sphere('glow', loc=(-0.5, -4.9, -0.6), r=1.2, seg=8)
    spr, em = finish(st, 'slumber', cover=0.42)
    rid = col_mask(spr, ['#A9BCD8'], 2)
    spr[rid, :3] = pix.hx('#FFC4E8')
    em |= rid
    out('slumbering_egg', spr, em, emissive=True)


# ================================================================================================== bottles
def bottle(name, contents, cap='#B07A4A', emissive=False):
    """Glass bottle: Blender lathe silhouette -> glass outline pixels + contents fill (vanilla potion idiom)."""
    st = stage(view=(0, 0))
    st.mat('g', T(['#FFFFFF'], emissive=True))
    prof = [(0.0, -6.6), (3.4, -6.6), (4.9, -5.0), (5.2, -2.6), (4.4, -0.4), (1.9, 1.2), (1.6, 3.6), (2.1, 4.4),
            (1.9, 5.6), (0.0, 5.6)]
    st.lathe('g', prof, seg=16)
    img = st.render(64, name)
    sil = bk.downsample_sprite(img, 16, 0.4)[..., 3] > 0
    spr = np.zeros((16, 16, 4), np.uint8)
    e = edge_of(sil)
    glass_l, glass_d = pix.hx('#BFD8E8'), pix.hx('#7E9CB8')
    for y, x in zip(*np.nonzero(e)):
        spr[y, x, :3] = glass_l if x < 8 else glass_d
        spr[y, x, 3] = 255
    neck_top = np.nonzero(sil.any(1))[0][0]
    for y in range(neck_top, neck_top + 2):
        xs = np.nonzero(sil[y])[0]
        spr[y, xs, :3] = pix.hx(cap)
        spr[y, xs[-1], :3] = pix.shade(pix.hx(cap), 0.7)
        spr[y, xs, 3] = 255
    inner = sil & ~e
    inner[:neck_top + 2] = False
    glow = contents(spr, inner)
    out(name, spr, glow, emissive=emissive)


def bottles():
    def ink(spr, inner):
        ys = np.nonzero(inner.any(1))[0]
        fill = inner & (YY >= ys[0] + 3)
        spr[fill, :3] = pix.hx('#0C0B1C')
        spr[fill, 3] = 255
        spr[fill & (XX + YY < 17), :3] = pix.hx('#15122C')
        top = fill & ~np.roll(fill, 1, 0)
        spr[top, :3] = pix.hx('#3A4A7A')
        for x, y in [(6, 11), (9, 10), (8, 13)]:
            if fill[y, x]:
                spr[y, x, :3] = pix.hx('#DFE8FF') if x == 9 else pix.hx('#8FB0FF')
        return np.zeros((16, 16), bool)
    bottle('bottle_of_stillwater', ink)

    def tea(spr, inner):
        ys = np.nonzero(inner.any(1))[0]
        fill = inner & (YY >= ys[0] + 3)
        spr[fill, :3] = pix.hx('#4A3866')
        spr[fill, 3] = 255
        spr[fill & (XX + YY < 18), :3] = pix.hx('#5E4A80')
        top = fill & ~np.roll(fill, 1, 0)
        spr[top, :3] = pix.hx('#B493C4')
        for x, y in [(7, 12), (9, 11)]:
            if fill[y, x]:
                spr[y, x, :3] = pix.hx('#9B7BB0')
        return np.zeros((16, 16), bool)
    bottle('stillwater_tea', tea)

    def wisp(spr, inner):
        spr[inner, :3] = pix.hx('#123C3A')
        spr[inner, 3] = 120
        ys, xs = np.nonzero(inner)
        cy, cx = ys.mean() + 0.5, xs.mean()
        w = inner & disc(16, cx + 0.5, cy + 0.5, 2.4)
        spr[w, :3] = pix.hx('#3AE0D0')
        spr[w, 3] = 255
        c = inner & disc(16, cx, cy, 1.3)
        spr[c, :3] = pix.hx('#B8FFF7')
        spr[int(cy) - 1, int(cx) - 1, :3] = pix.hx('#FFFFFF')
        tails = [(int(cx) + 2, int(cy) + 2), (int(cx) - 2, int(cy) - 2)]
        for x, y in tails:
            if inner[y, x]:
                spr[y, x, :3] = pix.hx('#1E9E93')
                spr[y, x, 3] = 255
        return w | c
    bottle('bottled_tidewisp', wisp, cap='#8D649D', emissive=True)


def stillwater_bucket():
    st = stage(view=(-28, 0))
    st.mat('iron', T(['#4E4E58', '#7E7E8A', '#A8A8B4', '#D6D6E0'], cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('ink', T(['#0C0B1C'], emissive=True))
    st.lathe('iron', [(4.0, -5.6), (5.8, 4.4), (5.0, 4.4), (3.4, -4.8)], seg=18)
    st.cyl('iron', loc=(0, 0, -5.4), r=4.0, h=0.6, verts=18)
    st.cyl('ink', loc=(0, 0, 3.6), r=5.2, h=0.4, verts=18)
    st.torus('iron', loc=(0, 0, 4.4), R=5.8, r=0.5, maj=24)
    spr, em = finish(st, 'bucket', cover=0.42)
    ink = col_mask(spr, ['#0C0B1C'], 2)
    for x, y in [(6, 3), (9, 4)]:
        if ink[y, x]:
            spr[y, x, :3] = pix.hx('#8FB0FF') if x == 6 else pix.hx('#DFE8FF')
    out('stillwater_bucket', spr)


# ===================================================================================================== food
def chorus_pod_item(roasted=False):
    st = stage(view=(-12, 20))
    pal = ['#4A2E40', '#7A4A5E', '#9A6A72', '#C0947E'] if roasted else LAV
    pal2 = ['#3A2232', '#5E3A4C', '#7A5060', '#9A6E6A'] if roasted else ['#5E4486', '#6C4E93', '#8C68B6', '#B593E6']
    seg = lambda g: g.sin(g.mul(g.m('ARCTAN2', g.separate(g.objco())[1], g.separate(g.objco())[0]), 5.0))
    st.mat('pod', T(pal, cuts=[-1, -0.3, 0.2, 0.62], pattern=seg, pattern_cut=0.75, palette2=pal2))
    st.mat('stem', T(['#3E2A4E', '#5E3F6E', '#8A5C8E'], cuts=[-1, 0.0, 0.5]))
    st.sphere('pod', loc=(0, 0, -0.8), r=5.4, scale=(1.0, 1.0, 1.15), rot=(0, -20, 0))
    st.cyl('stem', loc=(1.6, 0, 5.6), r=0.8, h=2.6, rot=(0, -20, 0), verts=6)
    spr, em = finish(st, 'pod%d' % roasted, cover=0.42)
    if not roasted:
        for x, y in [(5, 6), (6, 5)]:
            if spr[y, x, 3]:
                spr[y, x, :3] = pix.hx('#F1D6FF')
    else:
        # split skin showing the pale toasted inside
        for x, y in [(7, 8), (8, 8), (8, 9), (9, 9)]:
            if spr[y, x, 3]:
                spr[y, x, :3] = pix.hx('#E8CFA8') if (x + y) % 2 else pix.hx('#C9A27A')
    out('roasted_chorus_pod' if roasted else 'chorus_pod', spr)


def flank(cooked=False):
    st = stage(view=(-30, 0))
    meat = ['#5A2A2E', '#7A3A44', '#9E5068', '#C2789A'] if not cooked else ['#3A1E1A', '#5A3020', '#7A4628', '#9A6234']
    fat = ['#B7A0C2', '#D9C6E6', '#F1E6F6'] if not cooked else ['#8A6A4A', '#B08A5E', '#D0AC78']
    st.mat('m', T(meat, cuts=[-1, -0.3, 0.2, 0.62],
                  pattern=lambda g: g.add(g.separate(g.objco())[0], g.mul(g.noise(g.objco(), 0, 0.5, 1, dims='3D'), 2)),
                  pattern_cut=3.4, palette2=fat + [fat[-1]]))
    st.sphere('m', r=5.6, scale=(1.25, 0.9, 0.62), rot=(0, 35, 0), seg=14)
    spr, em = finish(st, 'flank%d' % cooked, cover=0.42)
    if cooked:
        for x, y in line_pts(4, 7, 8, 11) + line_pts(7, 4, 11, 8):
            if spr[y, x, 3]:
                spr[y, x, :3] = pix.hx('#2A1410')
    out('cooked_grazer_flank' if cooked else 'grazer_flank', spr)


def glowbulb_item():
    st = stage(view=(-10, 0))
    st.mat('bulb', T(['#9C2C80', '#D2489F', '#FF7AD9', '#FFB3EC'], cuts=[-1, -0.45, 0.15, 0.62]))
    st.mat('stem', T(['#1E1426', '#33233F', '#4A3758'], cuts=[-1, -0.2, 0.4]))
    st.mat('core', T(['#FFE0F6'], emissive=True))
    st.sphere('bulb', loc=(0, 0, -1.2), r=5.4, scale=(1, 1, 0.94))
    st.sphere('core', loc=(-0.8, -5.0, -0.4), r=1.4, seg=8)
    st.cyl('stem', loc=(0.6, 0, 5.0), r=0.9, h=3.6, verts=6, rot=(0, 15, 0))
    st.cone('stem', loc=(0, 0, 3.8), r1=2.2, r2=0.8, h=1.4, verts=6)
    spr, em = finish(st, 'gbulb', cover=0.42)
    out('glowbulb', spr)


def drift_stew():
    st = stage(view=(-32, 0))
    st.mat('bowl', T(['#3E2A1A', '#5E4026', '#7E5A36', '#9E7848'], cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('stew', T(['#4A2E44', '#6A4258', '#80546A'], cuts=[-1, 0.1, 0.6]))
    st.lathe('bowl', [(2.6, -3.4), (5.0, -2.6), (6.6, -0.2), (7.2, 1.6), (6.4, 1.6), (5.6, -0.4), (2.6, -2.4)], seg=20)
    st.cyl('stew', loc=(0, 0, 1.0), r=6.4, h=0.4, verts=20)
    spr, em = finish(st, 'stew', cover=0.42)
    s = col_mask(spr, ['#4A2E44', '#6A4258', '#80546A'], 2)
    ys, xs = np.nonzero(s)
    for (x, y), c in zip([(xs.min() + 2, ys.min() + 1), (xs.max() - 3, ys.min() + 2), (xs.min() + 5, ys.max()),
                          (xs.max() - 1, ys.max() - 1)], ['#FF7AD9', '#D8B6FF', '#9A6234', '#B8FFF7']):
        if s[y, x]:
            spr[y, x, :3] = pix.hx(c)
    out('drift_stew', spr)


# ================================================================================================ materials
def skate_membrane():
    st = stage(view=(-30, 0))
    st.mat('m', T(['#1A1E3E', '#21446B', '#2E5C88', '#4A7AA8'], cuts=[-1, -0.3, 0.2, 0.62]))
    verts, faces = [], []
    n = 8
    for i in range(n + 1):
        for j in range(n + 1):
            x = -6.5 + 13 * i / n
            y = -5 + 10 * j / n
            z = 1.4 * math.sin(i * 0.9) + 0.8 * math.cos(j * 1.3) - 0.04 * (x * x)
            verts.append((x, y, z + 0.4 * x * 0.2))
    for i in range(n):
        for j in range(n):
            k = i * (n + 1) + j
            faces.append((k, k + 1, k + n + 2, k + n + 1))
    st.mesh('m', verts, faces, rot=(0, -20, 15), solidify=0.6)
    spr, em = finish(st, 'membrane', cover=0.42)
    op = spr[..., 3] > 0
    e = edge_of(op)
    for y, x in zip(*np.nonzero(e)):
        if (x * 3 + y) % 4 == 0:
            spr[y, x, :3] = pix.hx('#3AE0D0')
    out('skate_membrane', spr)


def glow_gland():
    st = stage(view=(-12, 0))
    st.mat('sac', T(['#0E4F4A', '#167F78', '#22A79C', '#4FD3C6'], cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('core', T(['#C8FFF8'], emissive=True))
    st.mat('vein', T(['#7FF0E2'], emissive=True))
    st.sphere('sac', r=5.4, scale=(1.0, 0.9, 1.15), rot=(0, 0, 0))
    st.sphere('core', loc=(-0.6, -4.0, 0.4), r=2.2, seg=10)
    st.cone('sac', loc=(0.5, 0, 6.2), r1=1.6, r2=0.6, h=2.0, verts=8)
    spr, em = finish(st, 'gland', cover=0.42)
    for x, y in [(4, 7), (11, 9), (10, 5), (5, 11)]:
        if spr[y, x, 3]:
            spr[y, x, :3] = pix.hx('#7FF0E2')
            em[y, x] = True
    em |= col_mask(spr, ['#4FD3C6'], 2) & disc(16, 7.5, 8, 3.5)
    out('glow_gland', spr, em, emissive=True)


def grazer_frill():
    st = stage(view=(-10, 0))
    st.mat('f', T(['#6C4E93', '#9C7CC8', '#C2A2EA', '#E2CCFF'], cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('tip', T(['#F1D6FF'], emissive=True))
    # fan-shaped crystal plate: thin wedge with three ridged spines
    verts = [(0, 0, -6.4)]
    faces = []
    n = 7
    for i in range(n + 1):
        a = math.radians(-58 + 116 * i / n)
        rr = 12.6 if i % 2 == 0 else 10.8
        verts.append((rr * math.sin(a) * 0.62, 0, -6.4 + rr * math.cos(a)))
    for i in range(n):
        faces.append((0, i + 1, i + 2))
    st.mesh('f', verts, faces, rot=(12, 0, 0), solidify=1.0)
    for i in range(0, n + 1, 2):
        a = math.radians(-58 + 116 * i / n)
        st.sphere('tip', loc=(11.9 * math.sin(a) * 0.62, -0.8, -6.4 + 11.9 * math.cos(a)), r=0.8, seg=6)
    spr, em = finish(st, 'frill', cover=0.42, outline=False)
    # crystal ridges fanning from the root
    for i in range(0, 8, 2):
        a = math.radians(-58 + 116 * i / 7)
        for x, y in line_pts(7.5, 14.5, 7.5 + 11.0 * math.sin(a) * 0.62, 14.5 - 11.0 * math.cos(a))[1:-1]:
            if spr[y, x, 3]:
                spr[y, x, :3] = pix.hx('#E2CCFF') if y < 8 else pix.hx('#9C7CC8')
    spr = pix.outline(spr, darken=0.42)
    out('grazer_frill', spr)


def voidroot_fiber():
    st = stage(view=(-62, 0))
    st.mat('f', T(['#241A30', '#3A2A4A', '#55406A', '#6E5688'], cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('amber', T(['#FFD27A'], emissive=True))
    pts = []
    for i in range(80):
        t = i / 79
        a = TAU * 2.2 * t
        pts.append((-1.5 + 4.6 * math.cos(a) * (1 - 0.25 * t) + 3 * t, -1 + 4.6 * math.sin(a) * (1 - 0.25 * t), -2 + 4 * t))
    st.tube('f', pts, r=0.42)
    st.tube('f', [(-5, 0, -5.5), (-3, 0, -4), pts[0]], r=0.55)
    st.sphere('amber', loc=(-5.3, -0.6, -5.8), r=0.8, seg=6)
    spr, em = finish(st, 'fiber', cover=0.38)
    out('voidroot_fiber', spr)


def shard(name, pal, hi, emissive, axis=(3.2, 13.2, 12.6, 2.6), w=2.6, edge_col=None):
    """Diagonal faceted shard (amethyst/echo-shard idiom) rasterised from the facet model + outline."""
    m, f, s, _ = spikes([(axis[0], axis[1], axis[2], axis[3], w, w * 0.9, 0.3)], cover=0.42)
    idx = np.where(f == 0, 3, np.where(f == 1, 2, 1))
    idx = np.where(m & (s < 0.12), 1, idx)
    Pm = np.pad(m, 1)
    rim = m & ~Pm[1:-1, :-2] | m & ~Pm[:-2, 1:-1]
    idx = np.where(rim & (f == 0), 4, idx)
    idx = np.where(m & (s > 0.82) & (f != 2), 4, idx)
    img = pix.render_index(np.where(m, idx, -1), P(*pal, hi))
    if edge_col:
        img[rim & m, :3] = pix.hx(edge_col)
    em = m & (idx == 4)
    img = pix.outline(img, darken=0.42)
    out(name, img, em, emissive=emissive)


def shards():
    shard('lumen_crystal_shard', ['#0B4A46', '#0E5F59', '#22A79C', '#3FDCCB'], '#C8FFF8', True)
    shard('mirror_shard_fragment', ['#05040B', '#0C0B1C', '#1A1C30', '#2C3452'], '#DFE8FF', False,
          axis=(4.0, 13.0, 11.8, 2.4), w=2.0)


def echo_dust():
    st = stage(view=(-25, 0))
    st.mat('d', T(['#4E4A66', '#6E6A88', '#8E8AA6', '#B9B5CF'], cuts=[-1, -0.3, 0.2, 0.62]))
    st.lathe('d', [(0, 2.8), (2.5, 2.0), (5.0, -0.6), (6.8, -3.2), (0, -3.2)], seg=12, scale=(1, 0.8, 1), smooth=False)
    st.lathe('d', [(0, 0.2), (1.6, -0.6), (3.0, -2.4), (0, -2.4)], loc=(5.0, -2.5, -1.0), seg=8, smooth=False)
    spr, em = finish(st, 'echodust', cover=0.42)
    op = spr[..., 3] > 0
    n = F(f_noise, ss=1, size=16, fx=8.0, seed=301, det=1.0)
    stat = op & (n > np.quantile(n[op], 0.85))
    spr[stat, :3] = pix.hx('#E6E2F6')
    for x, y in [(3, 4), (12, 5), (6, 2)]:
        spr[y, x, :3] = pix.hx('#B9B5CF')
        spr[y, x, 3] = 255
    out('echo_dust', spr)


def lurker_lens():
    st = stage(view=(-8, 0))
    st.mat('l', T(['#0A0810', '#141020', '#221A32', '#3A2E52'], cuts=[-1, -0.3, 0.25, 0.65]))
    st.mat('rim', T(['#1A1622', '#2E2838', '#4A4058'], cuts=[-1, 0.0, 0.5]))
    st.sphere('l', r=5.6, scale=(1, 0.55, 1))
    st.torus('rim', R=5.8, r=0.8, rot=(90, 0, 0), maj=24)
    spr, em = finish(st, 'lens', cover=0.42)
    for x, y, c in [(8, 6, '#FF3FB4'), (8, 7, '#FF3FB4'), (8, 8, '#FF7AD9'), (8, 9, '#FF3FB4'), (7, 7, '#A3196E'),
                    (9, 8, '#A3196E'), (6, 5, '#FFFFFF')]:
        spr[y, x, :3] = pix.hx(c)
    out('lurker_lens', spr)


def echo_tablet():
    st = stage(view=(-20, 18))
    st.mat('s', T(['#7F7B8C', '#A6A2B2', '#C6C3CF', '#E2E0EA'], cuts=[-1, -0.3, 0.2, 0.62]))
    st.cube('s', size=(10.5, 2.0, 13.0), rot=(0, 8, 0), bevel=0.6)
    spr, em = finish(st, 'tablet', cover=0.42, outline=False)
    face = col_mask(spr, ['#C6C3CF', '#E2E0EA'], 2)
    ys, xs = np.nonzero(face)
    gl = np.zeros((16, 16), bool)
    rows = [ys.min() + 2, ys.min() + 5, ys.min() + 8]
    for r_, y in enumerate(rows):
        for x in range(xs.min() + 2, xs.max() - 1):
            if face[y, x] and ((x * 7 + r_ * 3) % 5) not in (0,):
                gl[y, x] = True
    gl &= face
    spr[gl, :3] = pix.hx('#9FE8FF')
    spr[gl & (XX % 3 == 0), :3] = pix.hx('#DDFAFF')
    spr = pix.outline(spr, darken=0.42)
    out('echo_tablet', spr, gl, emissive=True)


def music_disc():
    st = stage(view=(-62, 0))
    st.mat('d', T(['#08070C', '#141220', '#22202E', '#3A3648'], cuts=[-1, -0.2, 0.4, 0.8]))
    st.mat('label', T(['#A3196E', '#FF3FB4', '#FF8AD0'], cuts=[-1, 0.0, 0.6]))
    st.mat('hole', T(['#050407'], emissive=True))
    st.cyl('d', r=7.6, h=0.8, verts=28)
    st.cyl('label', loc=(0, 0, 0.3), r=3.0, h=0.6, verts=20)
    st.cyl('hole', loc=(0, 0, 0.5), r=0.8, h=0.6, verts=8)
    spr, em = finish(st, 'disc', cover=0.42)
    d = col_mask(spr, ['#22202E', '#3A3648'], 2)
    for y, x in zip(*np.nonzero(d)):
        if (x - y) % 5 == 0:
            spr[y, x, :3] = pix.hx('#4A4660')
    out('music_disc_unmade', spr)


# ========================================================================================== templates (2D)
def tablet_shape():
    m = np.zeros((16, 16), bool)
    m[1:15, 3:13] = True
    for (x, y) in [(3, 1), (12, 1), (3, 14), (12, 14)]:
        m[y, x] = False
    m[2:14, 2] = True
    m[2:14, 13] = True
    m[1, 4:12] = True
    return m


def template(name, pal, motif, motif_cols, seed):
    m = tablet_shape()
    n = F(f_noise, ss=2, size=16, fx=5.0, seed=seed, det=2.0)
    idx = np.clip(pix.quantile_index(n, [0.3, 0.45, 0.25]) + 1, 1, 3)
    idx = pix.despeckle(idx, 2)
    Pm = np.pad(m, 1)
    top = m & ~Pm[:-2, 1:-1] | m & ~Pm[1:-1, :-2]
    bot = m & ~Pm[2:, 1:-1] | m & ~Pm[1:-1, 2:]
    idx = np.where(top, 4, np.where(bot, 0, idx))
    img = pix.render_index(np.where(m, idx, -1), P(*pal))
    for y, r in enumerate(motif):
        for x, ch in enumerate(r):
            if ch != '.':
                img[2 + y, 3 + x, :3] = pix.hx(motif_cols[ch])
    img = pix.outline(img, darken=0.45)
    out(name, img)


def templates():
    up = ['#1A1428', '#2A2140', '#3A2D58', '#4A3A6E', '#6A5890']  # stillstone slab for upgrade templates
    template('gale_upgrade_template', up, [
        "..........",
        "....a.....",
        "...ab.a...",
        "..a.b.b...",
        "..b..bb.a.",
        ".b...b.ab.",
        ".b..bb.b..",
        "..bb..bb..",
        "...b.b....",
        "....bb....",
        "....b.....",
        "...b......",
    ], {'a': '#B8FFF7', 'b': '#2FC9BB'}, 401)
    template('still_upgrade_template', up, [
        "..........",
        ".cccccccc.",
        ".c......d.",
        ".c.dddd.d.",
        ".c.d..c.d.",
        ".c.d..c.d.",
        ".c.d..c.d.",
        ".c.dccc.d.",
        ".c......d.",
        ".cddddddd.",
        "..........",
        "..........",
    ], {'c': '#DFE8FF', 'd': '#05040B'}, 402)
    template('hover_upgrade_template', up, [
        "..........",
        "..........",
        "..aa..aa..",
        ".abba.abba",
        ".abba.abba",
        "..aa..aa..",
        "..........",
        "...c..c...",
        "..........",
        "....cc....",
        "..........",
        "..........",
    ], {'a': '#2FC9BB', 'b': '#C8FFF8', 'c': '#1E9E93'}, 403)
    trim = ['#4A2D55', '#6E4A7E', '#8D649D', '#A87BB6', '#C9A6D4']
    template('spire_armor_trim_smithing_template', trim, [
        "....a.....",
        "....a.....",
        "...aba....",
        "...aba....",
        "..a.b.a...",
        "..a.b.a...",
        ".ab.b.ba..",
        ".ab.b.ba..",
        ".ab.b.ba..",
        ".ab.b.ba..",
        "aaaaaaaaa.",
        "..........",
    ], {'a': '#2A1631', 'b': '#F0DDF4'}, 404)
    drift = ['#0E3A3A', '#16524F', '#1E6E68', '#2A8A82', '#4FB8AE']
    template('drift_armor_trim_smithing_template', drift, [
        "..........",
        ".aa...aa..",
        "a..a.a..a.",
        "....a.....",
        "..........",
        ".bb...bb..",
        "b..b.b..b.",
        "....b.....",
        "..........",
        ".aa...aa..",
        "a..a.a..a.",
        "....a.....",
    ], {'a': '#B8FFF7', 'b': '#062624'}, 405)
    under = ['#16141A', '#24212A', '#2E2A33', '#3A3540', '#524A5C']
    template('undersong_armor_trim_smithing_template', under, [
        "aaaaaaaaa.",
        "....a.....",
        "...a.a....",
        "..a...a...",
        "..a..ba...",
        ".a...b.a..",
        ".b..a..b..",
        "....a.....",
        "...b.b....",
        "....b.....",
        "..........",
        "..........",
    ], {'a': '#2FC9BB', 'b': '#C8FFF8'}, 406)


# ================================================================================================ spawn eggs
EGGS = {
    'void_skate': (['#141A36', '#21446B', '#2A2560', '#3A5A88'], ['#1E9E93', '#3AE0D0', '#3AE0D0', '#B8FFF7'], 0.30),
    'chorus_grazer': (['#8C6EA0', '#B493C4', '#C9AED6', '#D9C6E6'], ['#4E3460', '#6E4A82', '#8A5C9E', '#A87BC0'], 0.28),
    'underside_lurker': (['#08070C', '#14111C', '#1E1A26', '#2E2838'], ['#A3196E', '#FF3FB4', '#FF3FB4', '#FF7AD9'], 0.18),
    'hoarder': (['#060608', '#101014', '#1A1A20', '#2A2A32'], ['#3E7A2A', '#6FBF4A', '#8A5F3C', '#A07A4A'], 0.26),
    'spire_warden': (['#060608', '#101014', '#1C1A22', '#2A2632'], ['#6E4A7E', '#8D649D', '#A87BB6', '#C9A6D4'], 0.32),
    'echo': (['#3A3648', '#504C62', '#6E6A80', '#8E8AA2'], ['#8E8AA2', '#B9B5CF', '#DCD8EE', '#FFFFFF'], 0.18),
    'wild_shulker': (['#5E3F6E', '#7A5A86', '#8D649D', '#A47CB0'], ['#9E9566', '#BDB37C', '#D9D29C', '#E6DFA8'], 0.3),
    'tidewisp': (['#0E5F59', '#1E9E93', '#2FC9BB', '#3AE0D0'], ['#8AF0E6', '#B8FFF7', '#E6FFFB', '#FFFFFF'], 0.3),
    'shardling': (['#050307', '#0D0812', '#1A1022', '#2A1A36'], ['#A3196E', '#FF3FB4', '#FF7AD9', '#FFB3EC'], 0.22),
    'wyrm': (['#2A3550', '#3A4A66', '#4B5D7A', '#6B80A0'], ['#5FB0A8', '#8FD8CF', '#AFEAE2', '#D0F6F0'], 0.3),
}


def spawn_eggs():
    for k, (base, spots, cut) in EGGS.items():
        st = stage(view=(-8, 0))
        seed = sum(map(ord, k)) % 97
        pat = lambda g, seed=seed: g.voronoi(g.add(g.objco(), (seed, seed * 0.37, 0)) if False else g.objco(),
                                             float(seed), 0.42, 'F1', dims='4D')
        st.mat('egg', T(base, cuts=[-1, -0.35, 0.15, 0.6], pattern=lambda g, s=seed: g.sub(1.0, g.voronoi(
            g.objco(), float(s), 0.24, 'F1', dims='4D')), pattern_cut=1.0 - (cut + 0.22), palette2=spots))
        st.lathe('egg', egg_profile(18, 13.4, 5.4), seg=20)
        spr, em = finish(st, 'egg_' + k, cover=0.42, outline=False)
        # creature motifs (hand-placed, where the creature has a signature feature)
        def px(pts, c):
            for x, y in pts:
                if spr[y, x, 3]:
                    spr[y, x, :3] = pix.hx(c)
        if k == 'underside_lurker':
            px([(5, 8), (7, 9), (9, 9), (11, 8)], '#FF3FB4')
        elif k == 'hoarder':
            px([(6, 6), (9, 6)], '#FFB238')
        elif k == 'spire_warden':
            px([(6, 6), (9, 6)], '#FF3FB4')
        elif k == 'echo':
            px([(6, 6), (9, 6)], '#FFFFFF')
        elif k == 'shardling':
            px([(7, 8), (8, 8), (7, 9), (8, 9)], '#FF3FB4')
            px([(7, 8)], '#FFB3EC')
        elif k == 'wyrm':
            px([(6, 6), (9, 6)], '#FF9AD5')
        elif k == 'void_skate':
            px([(6, 5), (9, 5)], '#DFE8FF')
        spr = pix.outline(spr, darken=0.42)
        out(k + '_spawn_egg', spr)


# =========================================================================================== spire lantern item
def spire_lantern_item():
    st = stage(view=(-12, 30))
    st.mat('p', T(PURP, cuts=[-1, -0.3, 0.2, 0.62]))
    st.mat('glass', T(['#B497C0', '#CDB3D6', '#E8D6EE'], cuts=[-1, 0.0, 0.5]))
    st.mat('core', T(['#FFE4F9'], emissive=True))
    st.mat('chain', T(['#4A2D55', '#6E4A7E'], cuts=[-1, 0.2]))
    st.cube('p', loc=(0, 0, 3.6), size=(7, 7, 1.6))
    st.cube('p', loc=(0, 0, -5.4), size=(7, 7, 1.6))
    st.cube('glass', loc=(0, 0, -0.9), size=(6.0, 6.0, 7.4))
    st.cube('core', loc=(0, -2.9, -0.9), size=(1.8, 0.4, 5.4))
    st.cube('p', loc=(0, 0, 5.0), size=(4, 4, 1.4))
    st.torus('chain', loc=(0, 0, 6.6), R=1.4, r=0.5, rot=(90, 0, 30), maj=12)
    spr, em = finish(st, 'slantern', cover=0.42)
    core = col_mask(spr, ['#FFE4F9'], 2)
    ys, xs = np.nonzero(core)
    if len(ys):
        spr[ys.min(), xs, :3] = pix.hx('#FFB6F0')
        spr[ys.max(), xs, :3] = pix.hx('#FFB6F0')
    out('spire_lantern', spr)


def build():
    for f in (lambda: void_anchor(False), lambda: void_anchor(True), lambda: rift_tether(False),
              lambda: rift_tether(True), gateway_compass, shulker_satchel, void_lens, skate_harness, keys, sigils,
              heart_of_stillness, stillness_fragment, dragon_egg_fragment, slumbering_egg, bottles,
              stillwater_bucket, lambda: chorus_pod_item(False), lambda: chorus_pod_item(True), lambda: flank(False),
              lambda: flank(True), glowbulb_item, drift_stew, skate_membrane, glow_gland, grazer_frill,
              voidroot_fiber, shards, echo_dust, lurker_lens, echo_tablet, music_disc, templates, spawn_eggs,
              spire_lantern_item):
        f()
