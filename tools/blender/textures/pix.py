"""Pixel-art post-processing helpers (pure numpy, usable inside Blender's Python and system python).

Conventions
-----------
* Images are numpy float/uint8 arrays shaped (H, W, 4) RGBA, row 0 = TOP of the texture (Minecraft orientation).
* "Index maps" are int arrays (H, W) holding palette indices, -1 = transparent.
* Palettes are lists of hex strings ordered DARK -> LIGHT.
"""
import struct
import zlib
import os
import numpy as np


# ----------------------------------------------------------------------------------------------------------- colour
def hx(h):
    h = h.lstrip('#')
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)], dtype=np.float64)


def pal(*hexes):
    return [hx(h) for h in hexes]


def lum(rgb):
    rgb = np.asarray(rgb, dtype=np.float64)
    return rgb[..., 0] * 0.299 + rgb[..., 1] * 0.587 + rgb[..., 2] * 0.114


def mix(a, b, t):
    return np.asarray(a, dtype=np.float64) * (1 - t) + np.asarray(b, dtype=np.float64) * t


def ramp(stops, n):
    """Build an n-colour palette by sampling a multi-stop gradient (stops = hex list, dark->light)."""
    cs = [hx(s) for s in stops]
    out = []
    for i in range(n):
        t = i / max(1, n - 1) * (len(cs) - 1)
        k = min(int(t), len(cs) - 2)
        out.append(np.round(mix(cs[k], cs[k + 1], t - k)))
    return out


def shade(c, f):
    """Darken (f<1) / lighten (f>1) keeping hue; f>1 mixes toward white."""
    c = np.asarray(c, dtype=np.float64)
    if f <= 1:
        return np.round(c * f)
    return np.round(c + (255 - c) * (f - 1))


def desat(c, amt):
    c = np.asarray(c, dtype=np.float64)
    g = lum(c)
    return np.round(c * (1 - amt) + g * amt)


# ------------------------------------------------------------------------------------------------------- fields
def norm(f, lo=None, hi=None):
    f = np.asarray(f, dtype=np.float64)
    lo = f.min() if lo is None else lo
    hi = f.max() if hi is None else hi
    return np.clip((f - lo) / max(1e-9, hi - lo), 0, 1)


def box_down(f, k):
    """Box-average downsample by integer factor k (works on (H,W) or (H,W,C))."""
    h, w = f.shape[:2]
    s = f.reshape(h // k, k, w // k, k, *f.shape[2:])
    return s.mean(axis=(1, 3))


def quantile_index(f, weights, mask=None):
    """Map a scalar field to palette indices so that palette entry i covers ~weights[i] of the pixels.
    Gives vanilla-like controlled colour distribution independent of the field's range."""
    w = np.asarray(weights, dtype=np.float64)
    w = w / w.sum()
    vals = f[mask] if mask is not None else f.ravel()
    cuts = np.quantile(vals, np.cumsum(w)[:-1])
    idx = np.searchsorted(cuts, f, side='right')
    if mask is not None:
        idx = np.where(mask, idx, -1)
    return idx.astype(np.int64)


def threshold_index(f, cuts):
    return np.searchsorted(np.asarray(cuts), f, side='right').astype(np.int64)


def emboss(idx, height, n, strength=1.0, wrap=True, mask=None):
    """Top-left lighting: shift palette index up where the height field rises toward the top-left
    (the lit side of a bump) and down on the bottom-right slopes."""
    h = np.asarray(height, dtype=np.float64)
    if wrap:
        tl = np.roll(np.roll(h, 1, 0), 1, 1)
        br = np.roll(np.roll(h, -1, 0), -1, 1)
    else:
        p = np.pad(h, 1, mode='edge')
        tl = p[:-2, :-2]
        br = p[2:, 2:]
    d = (h - tl) * 0 + (br - tl)  # negative when top-left is higher -> we are on the lit far side
    d = -d
    s = np.std(d) + 1e-9
    step = np.clip(np.round(d / s * 0.5 * strength), -1, 1).astype(np.int64)
    out = np.clip(idx + step, 0, n - 1)
    if mask is not None:
        out = np.where(mask, out, idx)
    out = np.where(idx < 0, idx, out)
    return out


def despeckle(idx, passes=2, wrap=True, keep=None, min_same=1):
    """Remove single-pixel static: a pixel with fewer than `min_same` 4-neighbours of the same index
    takes the most common index among its 8 neighbours. `keep` = boolean mask of pixels never touched
    (deliberate flecks)."""
    idx = idx.copy()
    H, W = idx.shape
    for _ in range(passes):
        if wrap:
            P = np.pad(idx, 1, mode='wrap')
        else:
            P = np.pad(idx, 1, mode='edge')
        nb4 = [P[0:H, 1:W + 1], P[2:H + 2, 1:W + 1], P[1:H + 1, 0:W], P[1:H + 1, 2:W + 2]]
        nb8 = nb4 + [P[0:H, 0:W], P[0:H, 2:W + 2], P[2:H + 2, 0:W], P[2:H + 2, 2:W + 2]]
        same = sum((n == idx).astype(np.int64) for n in nb4)
        bad = (same < min_same) & (idx >= 0)
        if keep is not None:
            bad &= ~keep
        if not bad.any():
            break
        stack = np.stack(nb8, -1)
        new = idx.copy()
        for y, x in zip(*np.nonzero(bad)):
            v = stack[y, x]
            v = v[v >= 0]
            if len(v) == 0:
                continue
            vals, cnt = np.unique(v, return_counts=True)
            best = vals[cnt == cnt.max()]
            # tie-break toward the closest index to the current one (keeps clusters' shading coherent)
            new[y, x] = best[np.argmin(np.abs(best - idx[y, x]))]
        idx = new
    return idx


def render_index(idx, palette, alpha=255):
    """Index map -> RGBA uint8. alpha may be scalar or (H,W) array or per-palette list."""
    H, W = idx.shape
    out = np.zeros((H, W, 4), dtype=np.float64)
    P = np.array([np.asarray(c, dtype=np.float64) for c in palette])
    m = idx >= 0
    out[m, :3] = P[np.clip(idx[m], 0, len(P) - 1)]
    if isinstance(alpha, (list, tuple)):
        a = np.array(alpha, dtype=np.float64)
        out[m, 3] = a[np.clip(idx[m], 0, len(a) - 1)]
    else:
        out[..., 3] = np.where(m, alpha, 0)
    return np.clip(np.round(out), 0, 255).astype(np.uint8)


def quantize_rgba(img, palette, alpha_cut=128):
    """Nearest-colour (perceptual-ish weighted RGB) mapping of an RGBA float/uint8 image to a palette.
    Returns index map."""
    img = np.asarray(img, dtype=np.float64)
    P = np.array([np.asarray(c, dtype=np.float64) for c in palette])
    wts = np.array([0.30, 0.59, 0.11]) ** 0.5 * 1.7
    d = (((img[..., None, :3] - P[None, None]) * wts) ** 2).sum(-1)
    idx = np.argmin(d, -1)
    return np.where(img[..., 3] >= alpha_cut, idx, -1)


def count_colors(rgba):
    a = np.asarray(rgba)
    m = a[..., 3] > 0
    if not m.any():
        return 0
    return len(np.unique(a[m].reshape(-1, 4), axis=0))


# ------------------------------------------------------------------------------------------------------ sprites
def outline(rgba, color=None, darken=0.45, diagonal=False):
    """Add a 1px outline around opaque pixels. If color is None the outline uses a darkened version
    of the adjacent body pixel (vanilla item style)."""
    a = rgba.copy().astype(np.float64)
    H, W = a.shape[:2]
    op = a[..., 3] > 0
    P = np.pad(op, 1)
    Pc = np.pad(a, ((1, 1), (1, 1), (0, 0)))
    offs = [(-1, 0), (1, 0), (0, -1), (0, 1)]
    if diagonal:
        offs += [(-1, -1), (-1, 1), (1, -1), (1, 1)]
    out = a.copy()
    for y in range(H):
        for x in range(W):
            if op[y, x]:
                continue
            cs = []
            for dy, dx in offs:
                if P[y + 1 + dy, x + 1 + dx]:
                    cs.append(Pc[y + 1 + dy, x + 1 + dx, :3])
            if cs:
                if color is not None:
                    out[y, x, :3] = hx(color) if isinstance(color, str) else color
                else:
                    c = min(cs, key=lambda c: lum(c))
                    out[y, x, :3] = np.round(c * darken)
                out[y, x, 3] = 255
    return np.clip(out, 0, 255).astype(np.uint8)


def inner_edge_darken(rgba, f=0.6, sides='br'):
    """Darken opaque pixels whose bottom/right neighbour is transparent (shadow side)."""
    a = rgba.copy().astype(np.float64)
    op = a[..., 3] > 0
    H, W = op.shape
    P = np.pad(op, 1)
    m = np.zeros_like(op)
    if 'b' in sides:
        m |= op & ~P[2:, 1:-1]
    if 'r' in sides:
        m |= op & ~P[1:-1, 2:]
    if 't' in sides:
        m |= op & ~P[:-2, 1:-1]
    if 'l' in sides:
        m |= op & ~P[1:-1, :-2]
    a[m, :3] = np.round(a[m, :3] * f)
    return a.astype(np.uint8)


def compose(dst, src):
    """Alpha-over src onto dst (both uint8 RGBA)."""
    d = dst.astype(np.float64) / 255
    s = src.astype(np.float64) / 255
    sa = s[..., 3:4]
    da = d[..., 3:4]
    oa = sa + da * (1 - sa)
    oc = np.where(oa > 0, (s[..., :3] * sa + d[..., :3] * da * (1 - sa)) / np.maximum(oa, 1e-9), 0)
    return np.clip(np.round(np.concatenate([oc, oa], -1) * 255), 0, 255).astype(np.uint8)


def emissive_from(rgba, mask, boost=1.0):
    """Emissive layer: copy of rgba where mask, transparent elsewhere."""
    out = np.zeros_like(rgba)
    out[mask] = rgba[mask]
    if boost != 1.0:
        out[mask, :3] = np.clip(np.round(out[mask, :3].astype(np.float64) * boost), 0, 255)
    out[mask, 3] = 255
    out[~mask] = 0
    return out


def put(img, pts, color, a=255):
    c = hx(color) if isinstance(color, str) else np.asarray(color)
    for x, y in pts:
        if 0 <= y < img.shape[0] and 0 <= x < img.shape[1]:
            img[y, x, :3] = c
            img[y, x, 3] = a


def from_ascii(rows, legend):
    """Draw a sprite from ASCII art. legend: char -> hex (or None for transparent)."""
    H = len(rows)
    W = max(len(r) for r in rows)
    out = np.zeros((H, W, 4), dtype=np.uint8)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            c = legend.get(ch)
            if c is None:
                continue
            if isinstance(c, tuple):
                out[y, x, :3] = hx(c[0])
                out[y, x, 3] = c[1]
            else:
                out[y, x, :3] = hx(c)
                out[y, x, 3] = 255
    return out


# ------------------------------------------------------------------------------------------------------- png io
def save_png(path, rgba):
    rgba = np.ascontiguousarray(np.asarray(rgba, dtype=np.uint8))
    H, W = rgba.shape[:2]
    if rgba.shape[2] == 3:
        rgba = np.concatenate([rgba, np.full((H, W, 1), 255, np.uint8)], -1)
    raw = b''.join(b'\x00' + rgba[y].tobytes() for y in range(H))

    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)

    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', W, H, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(data)


def load_png(path):
    """Minimal PNG reader (8-bit RGB/RGBA/palette/grey, non-interlaced) -> RGBA uint8."""
    with open(path, 'rb') as f:
        d = f.read()
    pos = 8
    idat = b''
    plte = None
    trns = None
    while pos < len(d):
        ln = struct.unpack('>I', d[pos:pos + 4])[0]
        t = d[pos + 4:pos + 8]
        body = d[pos + 8:pos + 8 + ln]
        if t == b'IHDR':
            W, H, bd, ct, _, _, il = struct.unpack('>IIBBBBB', body)
        elif t == b'PLTE':
            plte = np.frombuffer(body, np.uint8).reshape(-1, 3)
        elif t == b'tRNS':
            trns = np.frombuffer(body, np.uint8)
        elif t == b'IDAT':
            idat += body
        pos += 12 + ln
    assert bd == 8 and il == 0, 'unsupported png'
    ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    raw = zlib.decompress(idat)
    stride = W * ch
    out = np.zeros((H, stride), np.uint8)
    prev = np.zeros(stride, np.int64)
    p = 0
    for y in range(H):
        ft = raw[p]
        line = np.frombuffer(raw[p + 1:p + 1 + stride], np.uint8).astype(np.int64)
        p += 1 + stride
        cur = np.zeros(stride, np.int64)
        for i in range(stride):
            a = cur[i - ch] if i >= ch else 0
            b = prev[i]
            c = prev[i - ch] if i >= ch else 0
            x = line[i]
            if ft == 0:
                v = x
            elif ft == 1:
                v = x + a
            elif ft == 2:
                v = x + b
            elif ft == 3:
                v = x + (a + b) // 2
            else:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                v = x + (a if pa <= pb and pa <= pc else (b if pb <= pc else c))
            cur[i] = v & 255
        out[y] = cur
        prev = cur
    img = out.reshape(H, W, ch)
    if ct == 6:
        return img
    if ct == 2:
        return np.concatenate([img, np.full((H, W, 1), 255, np.uint8)], -1)
    if ct == 0:
        return np.concatenate([img, img, img, np.full((H, W, 1), 255, np.uint8)], -1)
    if ct == 4:
        return np.concatenate([img[..., :1]] * 3 + [img[..., 1:2]], -1)
    rgb = plte[img[..., 0]]
    a = np.full((H, W, 1), 255, np.uint8)
    if trns is not None:
        tt = np.full(256, 255, np.uint8)
        tt[:len(trns)] = trns
        a = tt[img[..., 0]][..., None]
    return np.concatenate([rgb, a], -1)


def write_mcmeta(path, frametime, interpolate=True):
    with open(path, 'w') as f:
        f.write('{\n  "animation": {\n    "frametime": %d,\n    "interpolate": %s\n  }\n}\n'
                % (frametime, 'true' if interpolate else 'false'))
