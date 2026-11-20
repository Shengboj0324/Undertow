"""Elytra upgrade skins (9.7): vanilla elytra.png as the base (64x32 layout), with the upgrade painted on.
Gale = teal glowing veins, Still = dark mirror-like wing-tip edge, Hover = two teal nodes near the shoulders.
The model below is the vanilla ElytraModel geometry, rebuilt only for the preview (no JSON is exported)."""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat
from mclib.png import read_png
from mclib.paths import vanilla

TEAL, TEAL_HI, TEAL_DIM = "#3ae0d0", "#b8fff7", "#2a9e98"


def build():
    m = Model("elytra_upgrades", (64, 32))
    lw = m.part("left_wing", None, (5, 4, 2), (15, 0, -15))
    lw.cube((-10, 0, 0), (10, 20, 2), uv=(22, 0), inflate=1.0, mat="base")
    rw = m.part("right_wing", None, (-5, 4, 2), (15, 0, 15))
    rw.cube((0, 0, 0), (10, 20, 2), uv=(22, 0), inflate=1.0, mirror=True, mat="base", share="w", bake=False)
    lw.cubes[0].share = "w"
    m.pack()
    van = read_png(vanilla("textures/entity/equipment/wings/elytra.png"))
    T = []
    for kind in ("gale", "still", "hover"):
        T.append(dict(path="equipment/wings/elytra_" + kind, paint=(lambda k: lambda sk: paint(sk, van, k))(kind)))
    return dict(model=m, json=False, mats={"base": Mat(["#000000"], base=0, lo=0, hi=0, rim=False)}, textures=T,
                preview=dict(az=160, el=12, lens=85, margin=0.8, res=1024,
                             extra=[dict(suffix="_still", texture=1), dict(suffix="_hover", texture=2)]))


def line(p0, p1):
    n = int(max(abs(p1[0] - p0[0]), abs(p1[1] - p0[1]))) + 1
    return [(int(round(p0[0] + (p1[0] - p0[0]) * i / max(n - 1, 1))), int(round(p0[1] + (p1[1] - p0[1]) * i / max(n - 1, 1))))
            for i in range(n)]


def paint(sk, van, kind):
    img = van.astype(np.int32)
    sk.override[:] = img
    sk.has_override[:] = True
    sk.alpha[:] = img[:, :, 3]
    op = img[:, :, 3] > 0

    def put(pts, col, only=True):
        mk = np.zeros(op.shape, bool)
        for x, y in pts:
            if 0 <= y < op.shape[0] and 0 <= x < op.shape[1]:
                mk[y, x] = True
        if only:
            mk &= op
        sk.set(mk, col)

    if kind == "gale":
        # veins fan out from the shoulder (top, near the body) down the membrane; brighter near the root
        sh = (37, 3)
        for end in ((39, 21), (42, 20), (44, 16), (45, 11)):
            pts = line(sh, end)
            put(pts[len(pts) // 3:], TEAL_DIM)
            put(pts[:len(pts) // 3], TEAL)
        put([(37, 3), (38, 3)], TEAL_HI)
        put(line((40, 9), (43, 7)), TEAL_DIM)  # small branch
    elif kind == "still":
        # dark mirror edge along the wing tips: the lowest 2 opaque texels of every column + the outer column below row 12
        for x in range(30, 48):
            ys = np.nonzero(op[:, x])[0]
            ys = ys[ys >= 2]
            if len(ys) == 0:
                continue
            y1 = ys.max()
            put([(x, y1), (x, y1 - 1)], "#0c0b1c")
            put([(x, y1 - 2)], "#dfe8ff" if x % 3 else "#8fb0ff")
        for y in range(12, 22):
            xs = np.nonzero(op[y, 34:48])[0]
            if len(xs):
                put([(34 + xs.max(), y)], "#0c0b1c")
                put([(34 + xs.max() - 1, y)], "#1a1830")
        put([(41, 17), (44, 15)], "#dfe8ff")
    elif kind == "hover":
        # one node per wing near the shoulder (two in total on the worn elytra), with a dark setting ring
        cx, cy = 38, 4
        put([(cx - 1, cy - 1), (cx + 2, cy - 1), (cx - 1, cy + 2), (cx + 2, cy + 2)], "#2a3340")
        put([(cx, cy - 1), (cx + 1, cy - 1), (cx - 1, cy), (cx - 1, cy + 1), (cx + 2, cy), (cx + 2, cy + 1),
             (cx, cy + 2), (cx + 1, cy + 2)], "#3a4456")
        put([(cx, cy), (cx + 1, cy), (cx, cy + 1), (cx + 1, cy + 1)], TEAL)
        put([(cx, cy)], TEAL_HI)
