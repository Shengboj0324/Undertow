"""Tether pylon crystal - magenta crystal cluster (~1.4 wide x 2 tall) crowning a Null Obsidian pillar (13.3)."""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat


def build():
    m = Model("tether_pylon", (64, 64))
    c = m.part("crystal", None, (0, 24, 0))
    c.cube((-3, -18, -3), (6, 18, 6), mat="crystal", tag="main")
    c.cube((-2, -24, -2), (4, 6, 4), mat="crystal", tag="upper")
    c.cube((-1, -28, -1), (2, 4, 2), mat="crystal", tag="tip")
    for name, piv, rot, size, tip in (("crystal_a", (-2.5, 0, 1), (-6, 25, -30), (4, 13, 4), (2, 4, 2)),
                                      ("crystal_b", (2.5, 0, -1), (10, -30, 26), (4, 10, 4), (2, 3, 2)),
                                      ("crystal_c", (0.5, 0, 2.5), (-34, 40, 0), (3, 8, 3), (1, 3, 1))):
        p = m.part(name, "crystal", piv, rot)
        w, h, d = size
        p.cube((-w / 2, -h, -d / 2), size, mat="crystal", tag="side")
        p.cube((-tip[0] / 2, -h - tip[1], -tip[2] / 2), tip, mat="crystal", tag="tip")
    m.pack()
    return dict(model=m, mats={
        "crystal": Mat(["#5a0f3c", "#8a1660", "#c02890", "#ff3fb4", "#ff8fd6", "#ffd0f0"], base=3, kind="crystal",
                       feature=3.0, pattern_scale=2.5, lo=0.1, hi=0.1, params={"line_frac": 0.12}),
    }, textures=[dict(path="tether_pylon/tether_pylon", paint=lambda sk: paint(sk, m), emissive=True)],
        preview=dict(az=-30, el=14, lens=85, margin=0.8), emit_strength=2.2)


def paint(sk, m):
    allc = [c for c in m.cubes() if c.owner is None]
    # glow from within: the crystal bodies emit softly, facet highlights and tips burn brighter
    for c in allc:
        mk = sk.mask(cube=c)
        sk.glow(mk, "#ff3fb4", 70)
        h = c.size[1]
        sk.shift(sk.mask(cube=c, face=["front", "back", "left", "right"], cond=lambda t, h=h: t.fy >= t.fh - 2), -1)
        if c.tag == "tip":
            sk.set(mk, "#ff8fd6", emit="#ffb3ec", emit_alpha=230)
            sk.set(sk.mask(cube=c, face="top"), "#ffd0f0", emit="#ffffff", emit_alpha=255)
        # vertical facet lines: darker crease down the middle of wide faces, bright edge on the lit corner
        sk.set(sk.mask(cube=c, face=["front", "left"], cond=lambda t: (t.fx == 0) & (t.fw >= 3) & (t.fy < t.fh - 2)), "#ff8fd6",
               emit="#ff8fd6", emit_alpha=200)
        sk.shift(sk.mask(cube=c, face=["front", "back", "left", "right"], cond=lambda t: (t.fw >= 6) & (t.fx == t.fw // 2)), -1)
