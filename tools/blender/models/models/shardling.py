"""Shardling - floating jagged black crystal (~1 block) with a magenta glowing core and two orbiting splinters (8.8)."""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat

F = np.floor


def build():
    m = Model("shardling", (32, 32))
    core = m.part("core", None, (0, 9, 0))
    core.cube((-2.5, -1.5, -2.5), (5, 3, 5), mat="core", tag="core")
    sh = m.part("shard", "core", (0, 0, 0), (0, 45, 7))
    sh.cube((-2, -7, -2), (4, 6, 4), mat="crystal", tag="upper")
    sh.cube((-1, -10, -1), (2, 3, 2), mat="crystal", tag="tip")
    sh.cube((-1.5, 1, -1.5), (3, 4, 3), mat="crystal", tag="lower")
    sh.cube((-0.5, 5, -0.5), (1, 2, 1), mat="crystal", tag="ltip")
    sh.cube((1.5, -6, -1), (2, 3, 2), mat="crystal", tag="jag")  # jagged side spur
    sh.cube((-3, -4, 0), (1, 2, 1), mat="crystal", tag="jag2")
    for name, ry in (("splinter_a", 20), ("splinter_b", 200)):
        sp = m.part(name, "core", (0, 0, 0), (0, ry, 14 if name == "splinter_a" else -10))
        sp.cube((7, -2, -1), (2, 3, 2), mat="crystal", tag="splinter", share="spl", bake=(name == "splinter_a"))
        sp.cube((7.5, -4, -0.5), (1, 2, 1), mat="crystal", tag="spltip", share="spltip", bake=(name == "splinter_a"))
    m.pack()
    return dict(model=m, mats={
        "crystal": Mat(["#040306", "#0b0710", "#150d1e", "#24152f", "#3a2448"], base=1, kind="crystal", feature=2.0,
                       pattern_scale=1.6, lo=0.0, hi=0.0, params={"line_frac": 0.1}),
        "core": Mat(["#a0105e", "#e0208c", "#ff3fb4", "#ff8fd6"], base=2, feature=2.0, lo=0.1, hi=0.2, rim=False),
    }, textures=[dict(path="shardling/shardling", paint=lambda sk: paint(sk, m), emissive=True)],
        preview=dict(az=-30, el=14, lens=85, margin=0.7), emit_strength=6.0)


def paint(sk, m):
    core = m["core"].cubes[0]
    sk.glow(sk.mask(cube=core), "#ff3fb4")
    sk.set(sk.mask(cube=core, face=["front", "back", "left", "right"], cond=lambda t: t.fy == 1), "#ffb3ec", emit="#ffb3ec")
    # crystal: thin magenta glints on facet edges nearest the core, black glassy bodies
    for c in [c for p in ("shard", "splinter_a") for c in m[p].cubes if c.owner is None]:
        h = c.size[1]
        if c.tag in ("upper", "splinter", "jag"):
            near = sk.mask(cube=c, face=["front", "back", "left", "right"], cond=lambda t, h=h: t.fy == t.fh - 1)
        elif c.tag == "lower":
            near = sk.mask(cube=c, face=["front", "back", "left", "right"], cond=lambda t: t.fy == 0)
        else:
            near = None
        if near is not None:
            sk.set(near, "#8a1660", emit="#ff3fb4", emit_alpha=140)
        sk.set(sk.mask(cube=c, face=["front", "left"], cond=lambda t: (t.fx == 0) & (t.fh >= 3) & (t.fy == 1)), "#5a3a78")
