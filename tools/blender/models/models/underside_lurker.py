"""Underside Lurker - armored near-black teardrop on a thread, crown on top, ring of 4 magenta eyes, 7 jointed legs (8.4)."""
import math

import numpy as np

from mclib.geom import Model
from mclib.mat import Mat

F = np.floor
LEG_UP = 12
LEG_LO = 17


def build():
    m = Model("underside_lurker", (64, 64))
    body = m.part("body", None, (0, -1, 0))
    body.cube((-5, -2, -5), (10, 6, 10), mat="chitin", tag="bulb")
    body.cube((-4, -5, -4), (8, 3, 8), mat="chitin")
    body.cube((-3, -7, -3), (6, 2, 6), mat="chitin")
    body.cube((-4, 4, -4), (8, 2, 8), mat="chitin")
    body.cube((-2, 6, -2), (4, 1, 4), mat="chitin")
    cr = m.part("crown", "body", (0, -7, 0))
    cr.cube((-2, -3, -2), (4, 3, 4), mat="crown")
    cr.cube((-1, -6, -1), (2, 3, 2), mat="crown")
    cr.cube((-0.5, -8, -0.5), (1, 2, 1), mat="crown", tag="tip")
    for i, (x, z) in enumerate(((-2, -2), (1, -2), (-2, 1), (1, 1))):
        cr.cube((x, -5, z), (1, 2, 1), mat="crown", share="prong", bake=(i == 0), tag="prong")
    for i in range(7):
        phi = i * 360.0 / 7.0 + 180.0 / 7.0
        d = (-math.sin(math.radians(phi)), -math.cos(math.radians(phi)))
        lg = m.part("leg_%d" % i, "body", (round(4.0 * d[0], 3), 3.5, round(4.0 * d[1], 3)), (30, phi, 0))
        lg.cube((-1, -1, -LEG_UP), (2, 2, LEG_UP), mat="leg", share="leg_up", bake=(i == 0))
        lo = m.part("leg_%d_lower" % i, "leg_%d" % i, (0, 0, -LEG_UP + 0.5), (46, 0, 0))
        lo.cube((-0.5, -0.5, -LEG_LO), (1, 1, LEG_LO), mat="leg", share="leg_lo", bake=(i == 0))
        lo.cube((-1, -1, -1.5), (2, 2, 2), mat="leg", share="knee", bake=(i == 0), tag="knee")
    m.pack()
    return dict(model=m, mats=mats(), textures=[
        dict(path="underside_lurker/underside_lurker", paint=lambda sk: paint(sk, m), emissive=True),
    ], preview=dict(az=30, el=12, lens=85, margin=0.86), emit_strength=6.0)


def mats():
    chit = ["#07060a", "#0f0c15", "#1a1524", "#2a2238", "#3d3152"]
    return {
        "chitin": Mat(chit, base=2, kind="chitin", feature=3.0, pattern_scale=2.2, lo=0.15, hi=0.15,
                      params={"band_dir": "Z", "distortion": 1.5, "line_frac": 0.16}),
        "crown": Mat(chit, base=2, kind="crystal", feature=2.0, pattern_scale=2.0, lo=0.0, hi=0.0),
        "leg": Mat(["#060509", "#0e0b13", "#191422", "#2a2238"], base=1, feature=3.0, lo=0.1, hi=0.15),
    }


def paint(sk, m):
    bulb = m["body"].cubes[0]
    # ring of four small magenta eyes around the middle (one per side), glowing
    for face in ("front", "back", "left", "right"):
        mk = sk.mask(cube=bulb, face=face, cond=lambda t: (t.fy == 2) & ((t.fx == 4) | (t.fx == 5)))
        sk.set(mk, "#ff3fb4", emit="#ff3fb4")
        core = sk.mask(cube=bulb, face=face, cond=lambda t: (t.fy == 2) & (t.fx == 4))
        sk.set(core, "#ff7ad9", emit="#ff7ad9")
        lid = sk.mask(cube=bulb, face=face, cond=lambda t: ((t.fy == 1) | (t.fy == 3)) & ((t.fx == 4) | (t.fx == 5)))
        sk.set(lid, "#3d3152")
    # armour-plate seams: darker line at the top of the bulb sides, sheen highlight row
    sk.set(sk.mask(cube=bulb, face=["front", "back", "left", "right"], cond=lambda t: t.fy == 0), "#2a2238")
    sk.set(sk.mask(cube=bulb, face=["front", "back", "left", "right"], cond=lambda t: t.fy == 5), "#07060a")
    # crown: faint violet sheen on the tip
    tip = [c for c in m["crown"].cubes if c.tag == "tip"][0]
    sk.set(sk.mask(cube=tip, cond=lambda t: t.cy < 1), "#5a4a78")
    # legs: banded joints and pale-ish claw tips
    up = m["leg_0"].cubes[0]
    sk.shift(sk.mask(cube=up, cond=lambda t: (F(t.cz) % 4 == 0)), 1)
    lo = m["leg_0_lower"].cubes[0]
    sk.set(sk.mask(cube=lo, cond=lambda t: t.cz < 2), "#4a3e60")
    sk.set(sk.mask(cube=lo, cond=lambda t: t.cz < 1), "#6b5a88")
    sk.shift(sk.mask(cube=lo, cond=lambda t: (F(t.cz) % 5 == 2)), 1)
