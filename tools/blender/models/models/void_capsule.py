"""Void Capsule - glass-and-metal capsule (~0.8 x 1.2 blocks): grey metal caps, teal glass, coiling teal strand inside."""
import math

import numpy as np

from mclib.geom import Model
from mclib.mat import Mat


def build():
    m = Model("void_capsule", (64, 32))
    top = m.part("cap_top", None, (0, 7, 0))
    top.cube((-5, -1.5, -5), (10, 3, 10), inflate=0.5, mat="metal", tag="cap", share="cap")
    gl = m.part("glass", None, (0, 15, 0))
    gl.cube((-4, -5.5, -4), (8, 11, 8), inflate=1.0, mat="glass", tag="glass")
    bot = m.part("cap_bottom", None, (0, 22, 0))
    bot.cube((-5, -1.5, -5), (10, 3, 10), inflate=0.5, mat="metal", tag="cap", share="cap", mirror=False, bake=False)
    st = m.part("strand", None, (0, 15, 0))
    for i in range(11):
        a = math.radians(i * 52)
        x, z = 2.2 * math.sin(a), 2.2 * math.cos(a)
        st.cube((round(x - 0.5, 2), round(-5.5 + i, 2), round(z - 0.5, 2)), (1, 1, 1), mat="strand", share="bead",
                bake=(i == 0), tag="bead")
    m.pack()
    return dict(model=m, mats={
        "metal": Mat(["#2c2f37", "#464a56", "#636876", "#8a8f9e", "#b4b8c6"], base=2, feature=2.5, lo=0.15, hi=0.15),
        "glass": Mat(["#17484e", "#1f5f66", "#2a7f86", "#3aa6a8", "#6fd6cf"], base=2, feature=4.0, lo=0.1, hi=0.1,
                     alpha=120, rim=False, shade={"top": 0, "bottom": 0}),
        "strand": Mat(["#2fc9bb", "#3ae0d0", "#b8fff7"], base=1, feature=1.0, lo=0.0, hi=0.0, rim=False),
    }, textures=[dict(path="void_capsule/void_capsule", paint=lambda sk: paint(sk, m), emissive=True)],
        preview=dict(az=-30, el=16, lens=85, margin=0.66), blend=True, emit_strength=5.0)


def paint(sk, m):
    cap = m["cap_top"].cubes[0]
    # metal caps: a rivet band and a darker seam towards the glass, bright bevel on top
    sk.set(sk.mask(cube=cap, face=["front", "back", "left", "right"], cond=lambda t: (t.fy == 1) & (t.fx % 3 == 1)), "#b4b8c6")
    sk.set(sk.mask(cube=cap, face=["front", "back", "left", "right"], cond=lambda t: t.fy == 2), "#2c2f37")
    sk.set(sk.mask(cube=cap, face="top", cond=lambda t: (t.fx >= 3) & (t.fx <= 6) & (t.fy >= 3) & (t.fy <= 6)), "#464a56")
    sk.set(sk.mask(cube=cap, face="bottom", cond=lambda t: (t.fx >= 3) & (t.fx <= 6) & (t.fy >= 3) & (t.fy <= 6)), "#464a56")
    gl = m["glass"].cubes[0]
    # glass: clearer middle, denser rim, a vertical highlight streak on two faces
    sk.alpha[sk.mask(cube=gl, cond=lambda t: (t.fx == 0) | (t.fx == t.fw - 1))] = 175
    sk.set(sk.mask(cube=gl, face=["front", "left"], cond=lambda t: (t.fx == 1) & (t.fy >= 1) & (t.fy <= 8)), "#c8fff8", alpha=190)
    sk.set(sk.mask(cube=gl, face=["front", "left"], cond=lambda t: (t.fx == 2) & (t.fy >= 2) & (t.fy <= 4)), "#9ff0e8", alpha=170)
    # strand: glowing teal beads
    bead = m["strand"].cubes[0]
    sk.set(sk.mask(cube=bead), "#3ae0d0", emit="#3ae0d0")
    sk.set(sk.mask(cube=bead, face="top"), "#b8fff7", emit="#b8fff7")
