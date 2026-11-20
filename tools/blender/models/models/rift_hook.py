"""Rift Hook - small sea-green pearl held in a pale metal claw with three curved prongs."""
import math

import numpy as np

from mclib.geom import Model
from mclib.mat import Mat


def build():
    m = Model("rift_hook", (32, 32))
    cl = m.part("claw", None, (0, 20, 0))
    cl.cube((-2, 0, -2), (4, 2, 4), mat="metal", tag="cup")
    cl.cube((-1, 2, -1), (2, 2, 2), mat="metal", tag="stem")
    cl.cube((-1.5, 4, -1.5), (3, 1, 3), mat="metal", tag="eye")
    pe = m.part("pearl", "claw", (0, 0.5, 0))
    pe.cube((-2, -4, -2), (4, 4, 4), mat="pearl", tag="pearl")
    for i in range(3):
        phi = i * 120.0
        d = (-math.sin(math.radians(phi)), -math.cos(math.radians(phi)))
        pr = m.part("prong_%d" % i, "claw", (round(2.4 * d[0], 3), 0.5, round(2.4 * d[1], 3)), (-10, phi, 0))
        pr.cube((-0.5, -4, -0.5), (1, 4, 1), mat="metal", share="prong", bake=(i == 0), tag="bar")
        pr.cube((-0.5, -5, -0.5), (1, 1, 2), mat="metal", share="prongtip", bake=(i == 0), tag="tip")
    m.pack()
    return dict(model=m, mats={
        "metal": Mat(["#6c707c", "#9a9eaa", "#c4c8d2", "#e6e8ee"], base=2, feature=2.0, lo=0.15, hi=0.15),
        "pearl": Mat(["#1d6e52", "#2a8f6c", "#3fbf8c", "#7fe8b8", "#c8ffe6"], base=2, feature=2.0, lo=0.15, hi=0.2,
                     rim=False, shade={"top": 1, "bottom": -1}),
    }, textures=[dict(path="rift_hook/rift_hook", paint=lambda sk: paint(sk, m), emissive=True)],
        preview=dict(az=-30, el=22, lens=85, margin=0.55), emit_strength=3.0)


def paint(sk, m):
    pearl = m["pearl"].cubes[0]
    sk.glow(sk.mask(cube=pearl), "#3fbf8c", 170)
    hi = sk.mask(cube=pearl, face=["front", "left", "top"], cond=lambda t: (t.fx == 1) & (t.fy == 1))
    sk.set(hi, "#e8fff4", emit="#c8ffe6")
    cup = m["claw"].cubes[0]
    sk.set(sk.mask(cube=cup, face=["front", "back", "left", "right"], cond=lambda t: t.fy == 1), "#6c707c")
    tip = [c for c in m["prong_0"].cubes if c.tag == "tip"][0]
    sk.shift(sk.mask(cube=tip), 1)
