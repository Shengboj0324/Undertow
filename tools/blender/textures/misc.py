"""GUI sprites and environment maps."""
import math
import numpy as np

from . import pix, bk
from .tex import save, want, F, f_noise, f_bands, f_cluster, stone, _off, TAU


# ============================================================================================================ GUI
def adv_background():
    a = F(f_bands, ch=None, nb=4, seed=901, wob=0.09, grain=0.55)
    pl = pix.pal('#141218', '#1B1820', '#221F28', '#2A2631', '#332E3B')
    idx = stone(a[..., 0], 5, [0.1, 0.25, 0.32, 0.23, 0.10], emb=0.6)
    img = pix.render_index(idx, pl)
    for (x, y), c in zip([(3, 4), (11, 10), (12, 2), (6, 13)], ['#2FC9BB', '#6FE6DA', '#1E8E86', '#2FC9BB']):
        img[y, x, :3] = pix.hx(c)
    img[4, 4, :3] = pix.hx('#0E5F59')
    img[11, 11, :3] = pix.hx('#0E5F59')
    if want('gui/advancements/backgrounds/worldsfall'):
        save('gui/advancements/backgrounds/worldsfall', img)


def stamina_arc():
    """9x38 slim vertical arc bulging right (sits right of the crosshair)."""
    W, H = 9, 38
    ss = 8
    yy, xx = (np.mgrid[0:H * ss, 0:W * ss] + 0.5) / ss
    cx, cy, R = -23.0, 19.0, 29.0
    d = np.hypot(xx - cx, yy - cy)
    band = (np.abs(d - R) <= 1.45) & (yy > 1.0) & (yy < H - 1.0)
    cov = band.reshape(H, ss, W, ss).mean((1, 3))
    m = cov > 0.45
    # round the arc ends
    bg = np.zeros((H, W, 4), np.uint8)
    edge = m & ~(np.pad(m, 1)[:-2, 1:-1] & np.pad(m, 1)[2:, 1:-1] & np.pad(m, 1)[1:-1, :-2] & np.pad(m, 1)[1:-1, 2:])
    bg[m, :3] = pix.hx('#0B1F22')
    bg[m, 3] = 150
    bg[edge, :3] = pix.hx('#050C0E')
    bg[edge, 3] = 200
    pr = np.zeros((H, W, 4), np.uint8)
    inner = m & ~edge
    ys = np.arange(H)[:, None].repeat(W, 1)
    t = ys / (H - 1)
    pal = pix.pal('#1E9E93', '#2FC9BB', '#6FE6DA', '#B8FFF7')
    k = np.clip((1 - t) * 3.99, 0, 3).astype(int)
    for i in range(4):
        sel = inner & (k == i)
        pr[sel, :3] = pal[i]
        pr[sel, 3] = 255
    pr[edge, :3] = pix.hx('#0E5F59')
    pr[edge, 3] = 230
    if want('gui/sprites/hud/skate_stamina_background'):
        save('gui/sprites/hud/skate_stamina_background', bg)
    if want('gui/sprites/hud/skate_stamina_progress'):
        save('gui/sprites/hud/skate_stamina_progress', pr)


def tide_indicator():
    S = 18
    yy, xx = np.mgrid[0:S, 0:S]
    d = np.hypot(xx + 0.5 - 9, yy + 0.5 - 9)
    disc_m = d <= 8.3
    ring = disc_m & (d > 7.2)
    bg = np.zeros((S, S, 4), np.uint8)
    bg[disc_m, :3] = pix.hx('#0A0C16')
    bg[disc_m, 3] = 170
    bg[ring, :3] = pix.hx('#2A2140')
    bg[ring, 3] = 255
    bg[ring & (xx + yy < 17), :3] = pix.hx('#4B3D66')
    # empty wave outline in the middle
    wave = np.zeros((S, S), bool)
    for x in range(3, 15):
        y = int(round(9 + 1.5 * math.sin((x - 3) / 12 * TAU * 1.5)))
        wave[y, x] = True
    bg[wave & disc_m & ~ring, :3] = pix.hx('#3A4A6A')
    bg[wave & disc_m & ~ring, 3] = 255
    fill = np.zeros((S, S, 4), np.uint8)
    inner = disc_m & ~ring
    crest = np.zeros((S, S), bool)
    for x in range(S):
        y0 = int(round(7 + 1.5 * math.sin((x - 3) / 12 * TAU * 1.5)))
        crest[y0, x] = True
        below = inner & (yy > y0) & (xx == x)
        fill[below, :3] = pix.hx('#2FC9BB')
        fill[below & (yy > y0 + 4), :3] = pix.hx('#1E9E93')
        fill[below, 3] = 255
    crest &= inner
    fill[crest, :3] = pix.hx('#B8FFF7')
    fill[crest, 3] = 255
    for x, y in [(6, 12), (11, 13), (9, 10)]:
        if fill[y, x, 3]:
            fill[y, x, :3] = pix.hx('#8AF0E6')
    if want('gui/sprites/hud/tide_indicator_background'):
        save('gui/sprites/hud/tide_indicator_background', bg)
    if want('gui/sprites/hud/tide_indicator_fill'):
        save('gui/sprites/hud/tide_indicator_fill', fill)


def boss_bar():
    W, H = 182, 5
    n = bk.bake(lambda g: _bar_noise(g), 64)[..., 0]
    nn = np.array([n[2, int(x / W * 64)] for x in range(W)])
    notch = [int(W * 0.34), int(W * 0.67)]

    def bar(rows, frame, noise_amp, notch_c):
        img = np.zeros((H, W, 4), np.uint8)
        img[0, 1:W - 1, :3] = pix.hx(frame)
        img[H - 1, 1:W - 1, :3] = pix.hx(frame)
        img[1:H - 1, 0, :3] = pix.hx(frame)
        img[1:H - 1, W - 1, :3] = pix.hx(frame)
        img[0, 1:W - 1, 3] = img[H - 1, 1:W - 1, 3] = 255
        img[1:H - 1, 0, 3] = img[1:H - 1, W - 1, 3] = 255
        for r, c in enumerate(rows):
            base = pix.hx(c).astype(float)
            for x in range(1, W - 1):
                f = 1.0 + (nn[x] - 0.5) * noise_amp
                img[1 + r, x, :3] = np.clip(np.round(base * f), 0, 255)
                img[1 + r, x, 3] = 255
        for x in notch:
            img[1:H - 1, x, :3] = pix.hx(notch_c)
        return img
    bg = bar(['#4A0A2E', '#38061F', '#4A0A2E'], '#1A0510', 0.25, '#12030B')
    pr = bar(['#FFB3EC', '#FF3FB4', '#C0206A'], '#4A0A2E', 0.35, '#7A1030')
    if want('gui/sprites/boss_bar/unmade_background'):
        save('gui/sprites/boss_bar/unmade_background', bg)
    if want('gui/sprites/boss_bar/unmade_progress'):
        save('gui/sprites/boss_bar/unmade_progress', pr)


def _bar_noise(g):
    co, w = g.tile(24.0, 2.0, _off(911))
    g.out(g.noise(co, w, 1.0, 2.0, 0.5))


# ===================================================================================================== environment
def f_tide(g):
    """Flowing teal sheet detail: domain-warped ridged noise stretched along X (flow direction); tileable."""
    co, w = g.tile(2.0, 6.0, _off(921))
    warp = g.noise(co, w, 1.0, 3.0, 0.55, 2.0, 0.0)
    co2, w2 = g.tile(4.0, 14.0, _off(922))
    off = g.combine(0.0, 0.0, g.mul(warp, 1.6))
    vm = g.node('ShaderNodeVectorMath', operation='ADD')
    g.link(co2, vm.inputs[0])
    g.link(off, vm.inputs[1])
    rid = g.noise(vm.outputs[0], w2, 1.0, 3.0, 0.45, 2.0, 0.0, 'RIDGED_MULTIFRACTAL', offset=1.0, gain=2.0)
    co3, w3 = g.tile(6.0, 6.0, _off(923))
    big = g.noise(co3, w3, 1.0, 2.0, 0.5)
    g.out(g.lerp(g.mul(rid, 0.6), big, 0.45))


def f_nebula(g):
    co, w = g.tile(5.0, 5.0, _off(931))
    g.out(g.noise(co, w, 1.0, 7.0, 0.58, 2.0, 0.35))


def environment():
    a = bk.bake(f_tide, 256)[..., 0]
    a = pix.norm(a, np.quantile(a, 0.01), np.quantile(a, 0.995))
    g = np.round(a * 255).astype(np.uint8)
    img = np.stack([g, g, g, np.full_like(g, 255)], -1)
    if want('environment/tide_plane'):
        save('environment/tide_plane', img)
    b = bk.bake(f_nebula, 256)[..., 0]
    b = pix.norm(b, np.quantile(b, 0.005), np.quantile(b, 0.995))
    g = np.round(b * 255).astype(np.uint8)
    img = np.stack([g, g, g, np.full_like(g, 255)], -1)
    if want('environment/nebula_noise'):
        save('environment/nebula_noise', img)


def build(group):
    if group == 'gui':
        adv_background()
        stamina_arc()
        tide_indicator()
        boss_bar()
    else:
        environment()
