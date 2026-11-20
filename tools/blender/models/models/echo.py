"""Echo - thin, faint, semi-transparent Enderman shape with static shimmer at its edges, two white eye points (8.5.3)."""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat
from models._util import mirror_part

F = np.floor


def build():
    m = Model("echo", (64, 32))
    hd = m.part("head", None, (0, -18, 0))
    hd.cube((-3.5, -7, -3.5), (7, 7, 7), mat="shade")
    body = m.part("body", None, (0, -18, 0))
    body.cube((-3, 0, -1.5), (6, 12, 3), mat="shade")
    ra = m.part("right_arm", None, (-4, -16, 0), (0, 0, 3))
    ra.cube((-1, -2, -1), (2, 30, 2), mat="shade", share="limb")
    mirror_part(m, "right_arm", "left_arm")
    rl = m.part("right_leg", None, (-1.5, -6, 0))
    rl.cube((-1, 0, -1), (2, 30, 2), mat="shade", share="limb", bake=False)
    mirror_part(m, "right_leg", "left_leg")
    m.pack()
    return dict(model=m, mats=mats(), textures=[dict(path="echo/echo", paint=lambda sk: paint(sk, m), emissive=True)],
                preview=dict(az=-28, el=8, lens=85, margin=0.86), blend=True, emit_strength=6.0)


def mats():
    # faint grey-violet; the baked wave texture (strongly distorted) drives the static flecks
    return {"shade": Mat(["#15131c", "#211e2b", "#2e2a3b", "#3e3a4f"], base=1, kind="chitin", feature=3.0,
                         pattern_scale=1.3, lo=0.15, hi=0.15, alpha=130, rim=False, shade={"top": 1, "bottom": 0},
                         params={"band_dir": "Z", "distortion": 8.0, "line_frac": 0.2})}


def paint(sk, m):
    used = sk.used
    # silhouette edges of the figure = outer columns of the side/front/back faces (+ top edge of the head)
    side = sk.mask(face=["front", "back", "left", "right"])
    col_edge = side & ((sk.t_fx == 0) | (sk.t_fx == sk.t_fw - 1))
    row_edge = sk.mask(cube=m["head"].cubes[0], face=["front", "back", "left", "right"], cond=lambda t: t.fy == 0)
    edge = col_edge | row_edge
    g, r = sk.Gmin, sk.R
    # static: broken runs of bright and near-black texels along the edges (alpha 170-200)
    narrow = sk.t_fw <= 2
    thr_b = np.where(narrow, np.quantile(g[edge], 0.12), np.quantile(g[edge], 0.4))
    thr_d = np.where(narrow, np.quantile(r[edge], 0.12), np.quantile(r[edge], 0.3))
    bright = edge & (g < thr_b)
    dark = edge & ~bright & (r < thr_d)
    sk.set(bright, "#cfcbe2", alpha=195)
    sk.set(dark, "#08070b", alpha=175)
    # a few interference scanlines across the larger faces
    scan = sk.mask(face=["front", "back", "left", "right"], cond=lambda t: (t.fw >= 3) & (F(t.fy) % 7 == 3)) & ~edge
    sk.set(scan & (r > np.quantile(r[used], 0.4)), "#57526a", alpha=150)
    # inner flecks, sparse
    fleck = used & ~edge & (g < np.quantile(g[used], 0.06))
    sk.set(fleck, "#8a86a0", alpha=165)
    # no eyes visible except two white points (emissive)
    head = m["head"].cubes[0]
    pts = sk.mask(cube=head, face="front", cond=lambda t: (t.fy == 4) & ((t.fx == 1) | (t.fx == 5)))
    sk.set(pts, "#ffffff", alpha=255, emit="#ffffff")
