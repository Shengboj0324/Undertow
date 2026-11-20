"""Particle sprites (8x8). Shapes are drawn per age frame (frame 0 = newborn); soft/noisy ones (mist, ash, static,
wisp trail) come from Blender-baked 4D noise (W = frame) masked by a radial falloff."""
import math
import numpy as np

from . import pix, bk
from .tex import save, want, F, f_noise, disc, _off, TAU

S = 8
YY, XX = np.mgrid[0:S, 0:S]


def blank():
    return np.zeros((S, S, 4), np.uint8)


def dot(img, x, y, c, a=255):
    if 0 <= x < S and 0 <= y < S:
        img[y, x, :3] = pix.hx(c)
        img[y, x, 3] = a


def cross(img, cx, cy, arm, core, mid, a_mid=255):
    dot(img, cx, cy, core)
    for k in range(1, arm + 1):
        c = mid if k < arm or arm == 1 else mid
        aa = a_mid if k == arm else 255
        for dx, dy in ((k, 0), (-k, 0), (0, k), (0, -k)):
            dot(img, cx + dx, cy + dy, c, aa)


def blob(cx, cy, r):
    return (XX + 0.5 - cx) ** 2 + (YY + 0.5 - cy) ** 2 <= r * r


def series(name, frames):
    for i, f in enumerate(frames):
        if want('particle/%s_%d' % (name, i)):
            save('particle/%s_%d' % (name, i), f)


# ------------------------------------------------------------------------------------------- simple glow dots
def glow_dot_frames(core, mid, edge, sizes=(3, 2, 1, 0)):
    out = []
    for s in sizes:
        img = blank()
        if s >= 3:
            m = blob(4, 4, 1.8)
            img[m, :3] = pix.hx(edge)
            img[m, 3] = 170
            cross(img, 3, 3, 1, core, mid)
            dot(img, 4, 4, mid)
        elif s == 2:
            for x, y in [(3, 3), (4, 3), (3, 4), (4, 4)]:
                dot(img, x, y, mid)
            dot(img, 3, 3, core)
            for x, y in [(2, 3), (5, 4), (3, 2), (4, 5)]:
                dot(img, x, y, edge, 150)
        elif s == 1:
            dot(img, 3, 3, core)
            for x, y in [(2, 3), (4, 3), (3, 2), (3, 4)]:
                dot(img, x, y, edge, 140)
        else:
            dot(img, 3, 3, mid, 200)
        out.append(img)
    return out


def sparks(core, mid, dim):
    out = []
    # 0: 4-point star r2, 1: star r1 + diagonal glints, 2: small plus, 3: single dim pixel
    img = blank()
    cross(img, 3, 3, 2, core, mid, 190)
    for x, y in [(2, 2), (4, 4), (2, 4), (4, 2)]:
        dot(img, x, y, dim, 160)
    out.append(img)
    img = blank()
    cross(img, 3, 3, 2, core, mid, 120)
    out.append(img)
    img = blank()
    cross(img, 3, 3, 1, mid, dim, 200)
    out.append(img)
    img = blank()
    dot(img, 3, 3, dim)
    out.append(img)
    return out


def ring_frames():
    out = []
    for i, (r, a) in enumerate([(1.3, 230), (2.2, 200), (3.0, 150), (3.7, 90)]):
        img = blank()
        d = np.hypot(XX + 0.5 - 4, YY + 0.5 - 4)
        m = np.abs(d - r) < 0.55
        img[m, :3] = pix.hx('#DFE8FF')
        img[m, 3] = a
        hi = m & (XX + YY < 7)
        img[hi, :3] = pix.hx('#FFFFFF')
        out.append(img)
    return out


def f_puff(g, t=0.0, seed=0):
    co, w = g.tile(3.0, 3.0, _off(seed))
    n = g.noise(co, g.add(w, t * 1.7), 1.0, 3.0, 0.6, 2.0, 0.4)
    u, v = g.uv()
    du, dv = g.sub(u, 0.5), g.sub(v, 0.5)
    r = g.pw(g.add(g.mul(du, du), g.mul(dv, dv)), 0.5)
    fall = g.smooth(0.5, 0.15, r)
    g.out(g.mul(n, fall), n, fall)


def mist_frames(cols, seed, scale=(1.0, 0.9, 0.75, 0.6)):
    out = []
    for i, sc in enumerate(scale):
        a = bk.bake_down(f_puff, S, 4, t=i * 0.35, seed=seed)
        f = a[..., 0] * (1.15 if i == 0 else 1.0)
        m = blob(4, 4, 3.9 * sc)
        cuts = [0.18, 0.28, 0.38]
        idx = np.searchsorted(cuts, f)  # 0 = transparent
        idx = np.where(m, idx, 0)
        img = blank()
        for k, c in enumerate(cols):
            sel = idx == k + 1
            img[sel, :3] = pix.hx(c)
            img[sel, 3] = [140, 190, 230][k] - i * 22
        out.append(img)
    return out


def static_frames():
    out = []
    for i in range(4):
        a = bk.bake_down(lambda g, i=i: _static(g, i), S, 1)
        f = a[..., 0]
        img = blank()
        on = f > np.quantile(f, 0.62)
        img[on, :3] = pix.hx('#3AE0D0')
        img[on, 3] = 210
        hot = f > np.quantile(f, 0.9)
        img[hot, :3] = pix.hx('#C8FFF8')
        img[hot, 3] = 255
        # keep the static inside a soft 7px disc
        m = blob(4, 4, 3.8)
        img[~m] = 0
        out.append(img)
    return out


def _static(g, i):
    co, w = g.tile(5.0, 5.0, _off(700 + i))
    g.out(g.noise(co, g.add(w, i * 3.3), 1.0, 0.0, 0.5))


def ash_frames():
    out = []
    shapes = [[(2, 3), (3, 3), (4, 3), (3, 4), (4, 4), (5, 4), (4, 2)],
              [(3, 3), (4, 3), (4, 4), (5, 4), (2, 4)],
              [(3, 3), (4, 3), (3, 4)],
              [(3, 3), (4, 4)]]
    for k, sh in enumerate(shapes):
        img = blank()
        for j, (x, y) in enumerate(sh):
            dot(img, x, y, ['#4A3D4A', '#6E626E', '#8E828E'][(x + y + k) % 3], 230 - k * 30)
        out.append(img)
    return out


def drip_frames():
    out = []
    for k in range(2):
        img = blank()
        # droplet falling upward: round head on top, tail trailing below
        for x, y in [(3, 2), (4, 2), (3, 3), (4, 3)]:
            dot(img, x, y, '#8AF0E6')
        dot(img, 3, 2, '#E6FFFB')
        tail = [(3, 4), (4, 4), (3, 5)] if k == 0 else [(3, 4), (3, 5), (3, 6)]
        for j, (x, y) in enumerate(tail):
            dot(img, x, y, '#2FC9BB', 220 - j * 50)
        out.append(img)
    return out


def trail_frames():
    out = []
    for k in range(4):
        img = blank()
        L = 6 - k
        for i in range(L):
            x = 1 + i
            y = 4 + int(round(math.sin(i * 0.9 + k) * 0.9))
            c = '#E6FFFB' if i == L - 1 else '#8AF0E6' if i > L - 3 else '#2FC9BB'
            dot(img, x, y, c, 255 - (L - 1 - i) * 30)
            if i == L - 1:
                for dx, dy in ((0, -1), (0, 1), (1, 0)):
                    dot(img, x + dx, y + dy, '#8AF0E6', 180)
        out.append(img)
    return out


def shell_frames():
    out = []
    shapes = [[(2, 2), (3, 2), (4, 2), (2, 3), (3, 3), (4, 3), (5, 3), (3, 4), (4, 4)],
              [(3, 2), (4, 2), (2, 3), (3, 3), (4, 3), (3, 4)],
              [(3, 3), (4, 3), (3, 4), (4, 4)],
              [(3, 3), (4, 4)]]
    for k, sh in enumerate(shapes):
        img = blank()
        for x, y in sh:
            dot(img, x, y, '#1F1730' if (x + y) % 2 else '#120D1C')
        # pink broken edge on the upper-left of each fragment
        ys = min(y for _, y in sh)
        for x, y in sh:
            if y == ys or (x, y) == min(sh):
                dot(img, x, y, '#FF7AD9')
        out.append(img)
    return out


def wake_frames():
    out = []
    for k, (r, a) in enumerate([(1.6, 255), (1.2, 220), (0.8, 170), (0.5, 120)]):
        img = blank()
        m = blob(4, 4, r + 0.3)
        img[m, :3] = pix.hx('#2FC9BB')
        img[m, 3] = a
        c = blob(3.6, 3.6, max(0.5, r - 0.6))
        img[c, :3] = pix.hx('#B8FFF7')
        out.append(img)
    return out


def build():
    series('pollen_mote', glow_dot_frames('#FFFFFF', '#F1D6FF', '#B493C4'))
    series('pod_pop', sparks('#FFFFFF', '#D8B6FF', '#9B7BB0'))
    series('still_ring', ring_frames())
    series('stillfall_mist', mist_frames(['#2A2A36', '#3A3A48', '#4E4E5E'], 811))
    dust = []
    for k in range(2):
        img = blank()
        for x, y in ([(3, 3), (4, 3), (3, 4)] if k == 0 else [(3, 3), (4, 4)]):
            dot(img, x, y, '#6E5E8C' if (x + y) % 2 else '#8E7EA8')
        dust.append(img)
    series('dust_fall', dust)
    series('socket_spark', sparks('#FFFFFF', '#C77DFF', '#7A3AB0'))
    series('up_mote', glow_dot_frames('#FFFFFF', '#C8FFF8', '#2FC9BB'))
    series('up_drip', drip_frames())
    series('skate_wake', wake_frames())
    series('tide_crest', sparks('#FFFFFF', '#B8FFF7', '#3AE0D0'))
    series('erosion_static', static_frames())
    series('ash_inflow', ash_frames())
    series('shard_spark', sparks('#FFFFFF', '#FF3FB4', '#A3196E'))
    series('tidewisp_trail', trail_frames())
    series('shell_fragment', shell_frames())
