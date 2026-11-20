"""Unmade shard - black diamond crystal (~1.2 blocks) with thin magenta edges; three rings of them orbit the Core (13.10)."""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat


def build():
    m = Model("unmade_shard", (32, 32))
    s = m.part("shard", None, (0, 0, 0))
    s.cube((-1, -9, -1), (2, 4, 2), mat="crystal", tag="cap", share="cap")
    s.cube((-2, -5, -2), (4, 3, 4), mat="crystal", tag="shoulder", share="shoulder")
    s.cube((-3, -2, -3), (6, 4, 6), mat="crystal", tag="mid")
    s.cube((-2, 2, -2), (4, 3, 4), mat="crystal", tag="shoulder", share="shoulder", bake=False)
    s.cube((-1, 5, -1), (2, 4, 2), mat="crystal", tag="cap", share="cap", bake=False)
    m.pack()
    return dict(model=m, mats={
        "crystal": Mat(["#030205", "#09060d", "#130c1b", "#20132b", "#33203f"], base=1, kind="crystal", feature=2.0,
                       pattern_scale=1.8, lo=0.0, hi=0.0, params={"line_frac": 0.1}),
    }, textures=[dict(path="the_unmade/shard", paint=lambda sk: paint(sk, m), emissive=True)],
        preview=dict(az=-30, el=16, lens=85, margin=0.6), emit_strength=4.0)


def paint(sk, m):
    for c in m["shard"].cubes:
        if c.owner is not None:
            continue
        # thin magenta edges: one glowing corner column per side face (traces the diamond's vertical edges)
        side = sk.mask(cube=c, face=["front", "back", "left", "right"], cond=lambda t: t.fx == 0)
        sk.set(side, "#a0206e", emit="#ff3fb4", emit_alpha=200)
    # the step ledges of the widest tier trace the diamond's girdle
    mid = [c for c in m["shard"].cubes if c.tag == "mid"][0]
    ledge = sk.mask(cube=mid, face=["top", "bottom"], cond=lambda t: (t.fx == 0) | (t.fx == t.fw - 1) | (t.fy == 0) | (t.fy == t.fh - 1))
    sk.set(ledge, "#ff3fb4", emit="#ff3fb4", emit_alpha=230)
    sk.set(sk.mask(cube=mid, face="front", cond=lambda t: (t.fx == 2) & (t.fy == 1)), "#5a3a78")
