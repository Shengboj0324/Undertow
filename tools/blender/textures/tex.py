"""Shared recipe helpers: Blender field graphs (tileable) + numpy pixel-art layout tools, and the output registry."""
import os
import math
import numpy as np

from . import pix
from . import bk

ROOT = os.environ.get('WF_ROOT', '/Users/jiangshengbo/Undertow')
OUT = os.path.join(ROOT, 'src/main/resources/assets/worldsfall/textures')

WRITTEN = []
ONLY = None  # set of rel names (without .png) or prefixes to restrict a run


def want(rel):
    if not ONLY:
        return True
    return any(rel == o or rel.startswith(o) for o in ONLY)


BLOCK_MAX = 8      # spec: 4-8 colours per block texture
ITEM_MAX = 10      # spec asks 3-6 for items; vanilla items use 8-17 - we cap at 10 and report


def color_limit(rel, rgba):
    if rel.endswith('_emissive'):
        return None
    if rel.startswith('block/') and rgba.shape[0] == rgba.shape[1]:
        return BLOCK_MAX
    if rel.startswith('item/'):
        return ITEM_MAX
    return None


def save(rel, rgba, frametime=None, interpolate=True):
    """rel like 'block/stillstone' (no extension)."""
    p = os.path.join(OUT, rel + '.png')
    lim = color_limit(rel, rgba)
    if lim:
        rgba = pix.limit_colors(rgba, lim)
    pix.save_png(p, rgba)
    if frametime is not None:
        pix.write_mcmeta(p + '.mcmeta', frametime, interpolate)
    WRITTEN.append(rel)


# =============================================================================================== field graphs
# Every field below is built from Blender shader nodes and evaluated by a Cycles emission bake on a Clifford-torus
# mapping, so all of them tile seamlessly. `seed` offsets the 4D sample point.

def _off(seed):
    r = np.random.RandomState(seed)
    return tuple(float(x) for x in r.uniform(-50, 50, 4))


def f_noise(g, fx=4, fy=None, seed=0, det=2.0, rough=0.5, dist=0.0, ntype='FBM', shear=0, lac=2.0):
    co, w = g.tile(fx, fy, _off(seed), shear)
    g.out(g.noise(co, w, 1.0, det, rough, lac, dist, ntype))


def f_cluster(g, fx=4.0, seed=0, vmix=0.45, det=2.0, dist=0.0, vfx=None):
    """End-stone style chunky blobs: fBm noise blended with voronoi F1 cells."""
    co, w = g.tile(fx, None, _off(seed))
    n = g.noise(co, w, 1.0, det, 0.55, 2.0, dist)
    co2, w2 = g.tile(vfx or fx * 1.3, None, _off(seed + 7))
    v = g.voronoi(co2, w2, 1.0, 'F1')
    g.out(g.lerp(n, g.sub(1.0, v), vmix))


def f_bands(g, nb=5, seed=0, wob=0.06, wfx=3.0, det=2.0, grain=0.25):
    """Horizontal strata: sin(2pi*nb*(v + wobble)) + grain noise. Periodic because nb is an integer."""
    u, v = g.uv()
    co, w = g.tile(wfx, 2.0, _off(seed))
    wn = g.sub(g.noise(co, w, 1.0, det, 0.5), 0.5)
    ph = g.mul(g.add(v, g.mul(wn, wob)), TAU * nb)
    s = g.mul(g.add(g.sin(ph), 1.0), 0.5)
    co2, w2 = g.tile(6.0, 6.0, _off(seed + 3))
    gr = g.noise(co2, w2, 1.0, 2.0, 0.6)
    g.out(g.lerp(s, gr, grain), s, gr)


def f_vein(g, fx=3.0, seed=0, det=3.0, width=0.06, dist=0.3):
    """Ridged veins: 1 near the zero-crossings of an fBm field (thin connected lines)."""
    co, w = g.tile(fx, None, _off(seed))
    n = g.noise(co, w, 1.0, det, 0.55, 2.0, dist)
    r = g.absv(g.sub(n, 0.5))
    g.out(g.sub(1.0, g.smooth(0.0, width, r)), n, n)


def f_cells(g, fx=4.0, seed=0, out='Color', feature='F1', rnd=1.0):
    co, w = g.tile(fx, None, _off(seed))
    if out == 'Color':
        c = g.voronoi(co, w, 1.0, feature, rnd=rnd, out='Color')
        g.out(g.bw(c), g.voronoi(co, w, 1.0, 'DISTANCE_TO_EDGE', rnd=rnd), g.voronoi(co, w, 1.0, 'F1', rnd=rnd))
    else:
        g.out(g.voronoi(co, w, 1.0, feature, rnd=rnd, out=out))


TAU = math.pi * 2


def F(fn, ss=4, size=16, ch=0, **kw):
    """Bake a field at size*ss, box-average to size; return channel ch (or all if ch is None)."""
    a = bk.bake_down(fn, size, ss, key=fn.__name__, **kw)
    return a if ch is None else a[..., ch]


def Fraw(fn, size=64, ch=0, **kw):
    a = bk.bake(fn, size, key=fn.__name__ + '_raw', **kw)
    return a if ch is None else a[..., ch]


# ============================================================================================ index-map tools
def stone(field, n, weights, emb=1.0, desp=2, wrap=True, mask=None):
    idx = pix.quantile_index(field, weights, mask)
    if emb:
        idx = pix.emboss(idx, field, n, emb, wrap=wrap, mask=mask)
    if desp:
        idx = pix.despeckle(idx, desp, wrap=wrap)
    return idx


def brick_layout(courses, breaks, size=16):
    """courses: list of course heights (sum=size). breaks: per course list of x positions where a vertical mortar
    column sits. Returns (mortar bool, brick_id int, edge_light bool, edge_dark bool)."""
    mortar = np.zeros((size, size), bool)
    bid = np.zeros((size, size), int)
    y0 = 0
    k = 0
    for ci, h in enumerate(courses):
        mortar[y0 + h - 1, :] = True
        bx = sorted(breaks[ci % len(breaks)])
        for x in bx:
            mortar[y0:y0 + h, x % size] = True
        for x in range(size):
            # brick id: count breaks at or before x
            j = sum(1 for b in bx if b <= x)
            if j == len(bx):
                j = 0  # wraps around to the first brick
            bid[y0:y0 + h, x] = k + j
        k += len(bx) + 1
        y0 += h
    # light top/left edges of each brick, dark bottom/right edges (pixel adjacent to mortar)
    up = np.roll(mortar, 1, 0)
    lf = np.roll(mortar, 1, 1)
    dn = np.roll(mortar, -1, 0)
    rt = np.roll(mortar, -1, 1)
    light = (~mortar) & (up | lf)
    dark = (~mortar) & (dn | rt) & ~light
    return mortar, bid, light, dark


def bricks(field, n, layout, weights=None, mortar_idx=0, per_brick=0.6, seed=0):
    """Quantise a field inside a brick layout with per-brick tone variation + bevel lighting."""
    mortar, bid, light, dark = layout
    rng = np.random.RandomState(seed)
    tone = rng.uniform(-1, 1, bid.max() + 1)
    f = (field - field.mean()) / (field.std() + 1e-9) + per_brick * tone[bid]
    weights = weights or [1] * (n - 1)
    inner = ~mortar
    idx = pix.quantile_index(f, weights, inner) + 1  # 1..n-1 for bricks
    idx = np.where(mortar, mortar_idx, idx)
    idx = np.where(light, np.minimum(idx + 1, n - 1), idx)
    idx = np.where(dark, np.maximum(idx - 1, 1), idx)
    idx = pix.despeckle(idx, 1, keep=mortar | light | dark)
    return idx


def frame_mask(size=16, w=1):
    m = np.zeros((size, size), bool)
    m[:w, :] = m[-w:, :] = m[:, :w] = m[:, -w:] = True
    return m


def bevel_frame(idx, n, w=1, light=1, dark=-1, size=16):
    """Polished-block style border: top/left lighter, bottom/right darker."""
    idx = idx.copy()
    t = np.zeros((size, size), bool)
    t[:w, :] = True
    t[:, :w] = True
    b = np.zeros((size, size), bool)
    b[-w:, :] = True
    b[:, -w:] = True
    b &= ~(t & b) | (np.add.outer(np.arange(size), np.arange(size)) >= size)
    idx = np.where(t & ~b, np.clip(idx + light, 0, n - 1), idx)
    idx = np.where(b, np.clip(idx + dark, 0, n - 1), idx)
    return idx


def disc(size, cx, cy, r):
    yy, xx = np.mgrid[0:size, 0:size]
    return (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2 <= r * r


def line_pts(x0, y0, x1, y1):
    pts = []
    n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
    for i in range(n):
        t = i / max(1, n - 1)
        pts.append((int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t))))
    return pts


def grow(mask, k=1, wrap=False):
    m = mask.copy()
    for _ in range(k):
        P = np.pad(m, 1, mode='wrap' if wrap else 'constant')
        m = m | P[:-2, 1:-1] | P[2:, 1:-1] | P[1:-1, :-2] | P[1:-1, 2:]
    return m


def edge_of(mask):
    """Pixels of mask that touch a non-mask 4-neighbour (outline inside the shape)."""
    P = np.pad(mask, 1)
    nb = P[:-2, 1:-1] & P[2:, 1:-1] & P[1:-1, :-2] & P[1:-1, 2:]
    return mask & ~nb


def rgba_from(idx, palette, alpha=255):
    return pix.render_index(idx, palette, alpha)


# ============================================================================================ faceted crystals
def spike_layer(x0, y0, x1, y1, w0, w1=None, tip=0.35, size=16, ss=4):
    """Rasterise one faceted crystal (base (x0,y0) -> tip (x1,y1), half-width w0 at base, w1 before the tip
    taper, last `tip` fraction tapers to a point). Supersampled; returns (coverage (H,W), facet (H,W) in
    {0 lit-left, 1 front, 2 shaded-right}, s along axis 0..1)."""
    w1 = w0 if w1 is None else w1
    n = size * ss
    yy, xx = (np.mgrid[0:n, 0:n] + 0.5) / ss
    ax = np.array([x1 - x0, y1 - y0], float)
    L = np.hypot(*ax)
    ax /= L
    nx = np.array([-ax[1], ax[0]])
    if nx[0] < 0:
        nx = -nx  # +across = screen-right
    dx, dy = xx - x0, yy - y0
    s = (dx * ax[0] + dy * ax[1]) / L
    a = dx * nx[0] + dy * nx[1]
    hw = np.where(s < 1 - tip, w0 + (w1 - w0) * s / max(1e-6, 1 - tip), w1 * np.clip((1 - s) / tip, 0, 1))
    inside = (s >= 0) & (s <= 1) & (np.abs(a) <= hw + 1e-6)
    r = np.where(hw > 0, a / np.maximum(hw, 1e-6), 0)
    facet = np.where(r < -0.30, 0, np.where(r > 0.36, 2, 1))
    cov = inside.reshape(size, ss, size, ss).mean((1, 3))
    # facet per pixel: majority among inside samples
    fs = np.where(inside, facet, -1).reshape(size, ss, size, ss).transpose(0, 2, 1, 3).reshape(size, size, -1)
    fac = np.argmax(np.stack([(fs == j).sum(-1) for j in (0, 1, 2)], -1), -1)
    sm = np.where(inside, s, np.nan).reshape(size, ss, size, ss)
    with np.errstate(all='ignore'):
        sv = np.nanmean(sm, axis=(1, 3))
    return cov, fac, np.nan_to_num(sv)


def spikes(defs, size=16, cover=0.4):
    """Layer several spikes (later = in front). Returns (mask, facet, s, id) maps."""
    mask = np.zeros((size, size), bool)
    facet = np.zeros((size, size), int)
    sval = np.zeros((size, size))
    sid = -np.ones((size, size), int)
    for i, d in enumerate(defs):
        cov, fac, s = spike_layer(*d, size=size)
        m = cov >= cover
        mask |= m
        facet = np.where(m, fac, facet)
        sval = np.where(m, s, sval)
        sid = np.where(m, i, sid)
    return mask, facet, sval, sid
