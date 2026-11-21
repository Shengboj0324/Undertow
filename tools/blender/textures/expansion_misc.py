"""Expansion (1.2 -> 2.0) particles, GUI and environment textures, art/SPEC_EXPANSION.md sections C and D.

Particles follow particles.py (frame 0 = newborn; soft puffs from Blender-baked 4D noise under a radial falloff).
GUI panels take their stone and parchment grain from baked tileable fields; the overmoon is a baked noise disc with
limb darkening and an alpha halo."""
import math
import numpy as np

from . import pix, bk
from .tex import save, want, f_noise, f_bands, _off, TAU
from .particles import blank, dot, cross, blob, mist_frames, series

S = 8
YY, XX = np.mgrid[0:S, 0:S]


# ================================================================================================== particles
def glyph_frames():
    """Four pale runes of the Wardensong; each a different stroke figure, the last already breaking up."""
    runes = [["..#..", ".#.#.", "#...#", ".#.#.", "..#.."],
             ["#.#.#", ".###.", "..#..", "..#..", ".#.#."],
             ["###..", "#....", "####.", "...#.", "..##."],
             ["#...#", ".#.#.", "..#..", "...#.", "#...."]]
    out = []
    for k, r in enumerate(runes):
        img = blank()
        for j, row in enumerate(r):
            for i, ch in enumerate(row):
                if ch == '#':
                    x, y = 1 + i, 1 + j
                    lit = j == 0 or r[j - 1][i] != '#'
                    dot(img, x, y, '#F4E9FF' if lit else '#E2CCFF', 255 if k < 3 else 200)
                    if x + 1 < S and y + 1 < S and (j + 1 >= 5 or i + 1 >= 5 or r[j + 1][i + 1] != '#'):
                        if img[y + 1, x + 1, 3] == 0:
                            dot(img, x + 1, y + 1, '#B06CD8', 110)
        out.append(img)
    return out


def current_frames():
    """16x8 soft elongated white-gold streaks: long and bright when born, shorter and fainter after."""
    out = []
    for k, (L, a0) in enumerate([(13, 255), (10, 210), (6, 160)]):
        img = np.zeros((8, 16, 4), np.uint8)
        x0 = 8 - L // 2
        for i in range(L):
            t = i / max(1, L - 1)          # 0 tail .. 1 head (head on the right)
            x = x0 + i
            core = '#FFFFFF' if t > 0.8 else '#FFF6D8' if t > 0.4 else '#E8D38A'
            a = int(a0 * (0.35 + 0.65 * t))
            img[4, x, :3] = pix.hx(core)
            img[4, x, 3] = a
            if 0.25 < t < 0.95:
                for y in (3, 5):
                    img[y, x, :3] = pix.hx('#E8D38A')
                    img[y, x, 3] = int(a * 0.45)
        out.append(img)
    return out


def spark_frames():
    a = blank()
    cross(a, 3, 3, 2, '#FFFFFF', '#F6E3A8', 200)
    for x, y in [(2, 2), (4, 4), (2, 4), (4, 2)]:
        dot(a, x, y, '#C9A24A', 150)
    b = blank()
    cross(b, 3, 3, 1, '#FFF6D8', '#C9A24A', 200)
    return [a, b]


def swirl_frames():
    out = []
    for k in range(4):
        img = blank()
        rot = k * TAU / 8
        for i in range(36):
            t = i / 35
            a = rot + t * TAU * 1.1
            r = 0.6 + 3.0 * t
            x, y = int(math.floor(4 + math.cos(a) * r)), int(math.floor(4 + math.sin(a) * r))
            c = '#C8FFF8' if t < 0.25 else '#7FF2E2' if t < 0.6 else '#2FC9BB'
            alpha = int((255 - 40 * k) * (1.0 - 0.55 * t))
            if 0 <= x < S and 0 <= y < S and img[y, x, 3] < alpha:
                dot(img, x, y, c, alpha)
        out.append(img)
    return out


def mote():
    img = blank()
    dot(img, 3, 3, '#FFFFFF')
    for x, y in [(4, 3), (3, 4)]:
        dot(img, x, y, '#DDE6F2', 200)
    dot(img, 4, 4, '#8F9AA6', 150)
    return img


def particles():
    series('wardensong_glyph', glyph_frames())
    series('sky_current', current_frames())
    series('downdraft_dust', mist_frames(['#5E5A68', '#7E7A88', '#A6A2AE'], 1811, scale=(1.0, 0.85, 0.65)))
    series('starfall_spark', spark_frames())
    series('maw_swirl', swirl_frames())
    if want('particle/eclipse_mote'):
        save('particle/eclipse_mote', mote())


# ======================================================================================================== GUI
def _grain(seed, fx=24.0):
    def g(gr):
        co, w = gr.tile(fx, fx, _off(seed))
        gr.out(gr.noise(co, w, 1.0, 2.0, 0.55))
    return g


def lattice():
    """256x256 sheet, 176x166 container panel at (0,0): dark Undershale frame (banded), thin teal line around the
    work area, magenta corner gems; vanilla container conventions (1px dark outline, cut corners)."""
    W, H = 176, 166
    img = np.zeros((256, 256, 4), np.uint8)
    a = bk.bake(lambda g: f_bands(g, nb=22, seed=1901, wob=0.05, grain=0.5), 256)[..., 0]
    shale = pix.pal('#1E1B23', '#28242D', '#2E2A33', '#3A3540', '#47414E')  # undershale (blocks.SHALE)
    idx = np.clip(np.searchsorted(np.quantile(a, [0.12, 0.35, 0.65, 0.9]), a), 0, 4)
    frame = pix.render_index(idx, shale)
    n = bk.bake(_grain(1902), 256)[..., 0]
    inner_pal = pix.pal('#0E0D12', '#121016', '#16141B')
    inner = pix.render_index(np.clip(np.searchsorted(np.quantile(n, [0.3, 0.75]), n), 0, 2), inner_pal)
    yy, xx = np.mgrid[0:256, 0:256]
    panel = (xx < W) & (yy < H)
    for (cx, cy) in [(0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)]:  # cut corners
        panel &= ~((np.abs(xx - cx) + np.abs(yy - cy)) < 2)
    img[panel] = frame[panel]
    B = 6
    work = (xx >= B) & (xx < W - B) & (yy >= B) & (yy < H - B)
    img[work] = inner[work]
    Pm = np.pad(panel, 1)
    edge = panel & ~(Pm[:-2, 1:-1] & Pm[2:, 1:-1] & Pm[1:-1, :-2] & Pm[1:-1, 2:])
    img[edge, :3] = pix.hx('#050407')
    hl = panel & ~edge & ((xx == 1) | (yy == 1)) & (xx < W - 2) & (yy < H - 2)
    img[hl, :3] = pix.hx('#47414E')
    sh = panel & ~edge & ((xx == W - 2) | (yy == H - 2)) & (xx > 1) & (yy > 1)
    img[sh, :3] = pix.hx('#0B0A0E')
    # bevel of the work area: dark above/left, lit below/right (recessed)
    img[(yy == B - 1) & (xx >= B - 1) & (xx <= W - B), :3] = pix.hx('#0B0A0E')
    img[(xx == B - 1) & (yy >= B - 1) & (yy <= H - B), :3] = pix.hx('#0B0A0E')
    img[(yy == H - B) & (xx >= B) & (xx <= W - B), :3] = pix.hx('#3A3540')
    img[(xx == W - B) & (yy >= B) & (yy <= H - B), :3] = pix.hx('#3A3540')
    # thin teal line inside the work area
    T = B + 2
    line = (((xx == T) | (xx == W - 1 - T)) & (yy >= T) & (yy <= H - 1 - T)) | \
           (((yy == T) | (yy == H - 1 - T)) & (xx >= T) & (xx <= W - 1 - T))
    img[line, :3] = pix.hx('#1E8E86')
    img[line & ((xx + yy) % 6 == 0), :3] = pix.hx('#2FC9BB')
    # magenta corner gems (5x5 diamonds sitting on the teal line's corners)
    for (cx, cy) in [(T, T), (W - 1 - T, T), (T, H - 1 - T), (W - 1 - T, H - 1 - T)]:
        d = np.abs(xx - cx) + np.abs(yy - cy)
        img[d <= 3, :3] = pix.hx('#2A0A1E')
        img[d <= 2, :3] = pix.hx('#C0206A')
        img[(d <= 2) & (xx + yy < cx + cy), :3] = pix.hx('#FF3FB4')
        img[(xx == cx - 1) & (yy == cy - 1), :3] = pix.hx('#FFB6F0')
        img[d == 0, :3] = pix.hx('#FF7AD9')
    if want('gui/lattice'):
        save('gui/lattice', img)


def void_chart_frame():
    """256x256 chart frame: dark parchment border 8px wide, inner 240x240 transparent."""
    img = np.zeros((256, 256, 4), np.uint8)
    n = bk.bake(_grain(1911, 32.0), 256)[..., 0]
    pal = pix.pal('#1E1929', '#241E32', '#2A2438', '#31293F')
    idx = np.clip(np.searchsorted(np.quantile(n, [0.15, 0.5, 0.85]), n), 0, 3)
    parch = pix.render_index(idx, pal)
    yy, xx = np.mgrid[0:256, 0:256]
    band = ~((xx >= 8) & (xx < 248) & (yy >= 8) & (yy < 248))
    img[band] = parch[band]
    outer = (xx == 0) | (yy == 0) | (xx == 255) | (yy == 255)
    img[outer, :3] = pix.hx('#120E18')
    img[((xx == 1) | (yy == 1)) & ~outer, :3] = pix.hx('#3D3550')
    inner_e = band & ((xx == 7) | (yy == 7) | (xx == 248) | (yy == 248)) & (xx >= 7) & (xx <= 248) & (yy >= 7) \
        & (yy <= 248)
    img[inner_e, :3] = pix.hx('#17131F')
    img[inner_e & ((xx == 248) | (yy == 248)), :3] = pix.hx('#3D3550')
    # pale tick marks along the border every 16 px (chart graticule) and corner rosettes
    tick = band & ~outer & (((xx % 16 == 0) & ((yy == 3) | (yy == 4) | (yy == 251) | (yy == 252))) |
                            ((yy % 16 == 0) & ((xx == 3) | (xx == 4) | (xx == 251) | (xx == 252))))
    img[tick, :3] = pix.hx('#5E5672')
    for cx, cy in [(4, 4), (251, 4), (4, 251), (251, 251)]:
        d = np.abs(xx - cx) + np.abs(yy - cy)
        img[d <= 2, :3] = pix.hx('#7A6E92')
        img[d == 0, :3] = pix.hx('#C9C3D8')
    if want('gui/void_chart'):
        save('gui/void_chart', img)


# ================================================================================================ environment
def overmoon():
    """64x64: pale silver-white disc with soft limb darkening and faint maria, a faint gold halo fading out."""
    N = 64
    n = bk.bake(_grain(1921, 5.0), N)[..., 0]
    n = pix.norm(n)
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.hypot(xx + 0.5 - 32, yy + 0.5 - 32)
    R = 21.0
    mu = np.sqrt(np.clip(1 - (d / R) ** 2, 0, 1))
    limb = 0.62 + 0.38 * mu ** 0.6
    maria = np.clip((n - 0.5) / 0.35, 0, 1) ** 1.5
    base = pix.hx('#F4F6FA') * (1 - 0.13 * maria)[..., None]
    base = base * limb[..., None] + pix.hx('#9AA4B8') * (1 - limb[..., None]) * 0.35
    img = np.zeros((N, N, 4), np.float64)
    inside = d <= R
    img[inside, :3] = base[inside]
    img[inside, 3] = 255
    edge = (d > R - 1) & inside
    img[edge, 3] = 255 * np.clip(R - d[edge] + 0.5, 0, 1) + 0
    halo = (d > R - 0.5) & (d < 31.5)
    t = np.clip((d - R) / (31.5 - R), 0, 1)
    ha = 150 * (1 - t) ** 2.2
    gold = pix.hx('#E8D38A')
    hm = halo & ~inside
    img[hm, :3] = gold
    img[hm, 3] = ha[hm]
    img = np.clip(np.round(img), 0, 255).astype(np.uint8)
    if want('environment/overmoon'):
        save('environment/overmoon', img)


def build(group):
    if group == 'xparticles':
        particles()
    elif group == 'xgui':
        lattice()
        void_chart_frame()
    else:
        overmoon()
