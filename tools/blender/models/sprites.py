"""Billboard sprites (no geometry): tidewisp 32x32, the Unmade core 64x64 and tendril 16x64.
Run with Blender's python (numpy) or any python with numpy:  python3 sprites.py"""
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from mclib.png import write_png  # noqa: E402

REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
TEX = os.path.join(REPO, "src", "main", "resources", "assets", "worldsfall", "textures", "entity")


def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.float64)


def ramp(t, stops):
    t = np.clip(t, 0, 1)
    out = np.zeros(t.shape + (3,))
    for (a, ca), (b, cb) in zip(stops[:-1], stops[1:]):
        m = (t >= a) & (t <= b)
        f = ((t - a) / max(b - a, 1e-9))[m][:, None]
        out[m] = hexc(ca) * (1 - f) + hexc(cb) * f
    return out


def quant(x, levels):
    """Quantise alpha to a few steps so sprites stay pixel-crisp (no smooth airbrush)."""
    return np.round(np.clip(x, 0, 1) * (levels - 1)) / (levels - 1)


def tidewisp():
    n = 32
    yy, xx = np.mgrid[0:n, 0:n] + 0.5
    cx, cy = 16, 11
    r = np.hypot(xx - cx, yy - cy)
    core = np.clip(1 - r / 6.5, 0, 1)
    a = core ** 1.2
    # three faint trailing streamers drifting down, slightly curved
    for k, (x0, bend) in enumerate(((12.5, -1.5), (16.0, 0.6), (19.5, 1.8))):
        y = yy
        xc = x0 + bend * ((y - 14) / 16) ** 2 * 3 + 0.8 * np.sin(y / 3.0 + k)
        on = (y > 13) & (y < 30 - k * 2)
        w = np.clip(1 - np.abs(xx - xc) / 1.1, 0, 1)
        fade = np.clip(1 - (y - 13) / (17 - k * 2), 0, 1)
        a = np.maximum(a, on * w * fade * 0.55)
    a = quant(a, 6)
    col = ramp(1 - np.clip(r / 8, 0, 1), [(0, "#2fc9bb"), (0.5, "#8ff5ea"), (0.85, "#e6fffb"), (1, "#ffffff")])
    stream = (yy > 13) & (r > 4)
    col[stream] = ramp(np.clip(1 - (yy[stream] - 13) / 16, 0, 1), [(0, "#2fc9bb"), (1, "#b8fff7")])
    img = np.dstack([col, a * 255]).round().astype(np.uint8)
    img[a <= 0] = 0
    return img


def core():
    n = 64
    yy, xx = np.mgrid[0:n, 0:n] + 0.5
    r = np.hypot(xx - 32, yy - 32) / 32
    col = ramp(r, [(0, "#ffffff"), (0.28, "#ffffff"), (0.45, "#ffb3ec"), (0.72, "#ff3fb4"), (1.0, "#b0106e")])
    a = np.clip(1.0 - (np.clip(r - 0.42, 0, 1) / 0.58) ** 1.4, 0, 1)
    a = quant(a, 8)
    img = np.dstack([col, a * 255]).round().astype(np.uint8)
    img[a <= 0] = 0
    return img


def tendril():
    w, h = 16, 64
    yy, xx = np.mgrid[0:h, 0:w] + 0.5
    t = yy / h  # 0 at the root (top) -> 1 at the tapering tip
    half = 5.5 * (1 - t) ** 0.8 + 0.6
    cxl = 8 + 1.5 * np.sin(t * 6.0)
    d = np.abs(xx - cxl)
    body = d <= half - 1.0
    glow = (d > half - 1.0) & (d <= half + 1.2)
    col = np.zeros((h, w, 3))
    col[:] = hexc("#1a0510")
    col[body] = ramp(1 - d[body] / np.maximum(half[body], 1), [(0, "#3a0a24"), (0.6, "#1a0510"), (1, "#100308")])
    col[glow] = hexc("#ff3fb4")
    edge_hi = glow & (d <= half)
    col[edge_hi] = hexc("#ff8fd6")
    a = np.zeros((h, w))
    a[body] = 1.0
    a[glow] = np.where(d[glow] <= half[glow], 0.85, 0.4)
    a *= np.clip((1 - t) * 6, 0, 1) ** 0.3  # soften the very tip
    a = quant(a, 6)
    img = np.dstack([col, a * 255]).round().astype(np.uint8)
    img[a <= 0] = 0
    return img


def main():
    for rel, fn in (("tidewisp/tidewisp.png", tidewisp), ("the_unmade/core.png", core), ("the_unmade/tendril.png", tendril)):
        p = os.path.join(TEX, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        write_png(p, fn())
        print("[sprites] wrote", p)


if __name__ == "__main__":
    main()
