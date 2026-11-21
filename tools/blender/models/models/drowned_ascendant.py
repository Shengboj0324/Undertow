"""Drowned Ascendant - a silent, translucent echo of the old civilization: a tall thin robed figure (~2.4 blocks) with a
hood, pale teal-white robe, glowing eyes under the hood, holding a small tablet.  Never hostile.
Parts: body, robe (child of body), head -> hood, left_arm, right_arm -> tablet.  (spec E)"""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat
from models._util import mirror_part

F = np.floor
GLOW, GLOW_HI, GLYPH = "#7ff2e2", "#e6fffb", "#3ae0d0"
A_ROBE, A_SKIN = 190, 175  # skin alpha: translucent echo (renderer is translucency-capable)


def build():
    m = Model("drowned_ascendant", (64, 64))
    b = m.part("body", None, (0, -4, 0))
    # non-overlapping shells (the figure is drawn translucent): torso, then a robe that flares in half-texel steps
    b.cube((-3.5, 2, -2), (7, 8, 4), mat="robe", tag="torso")
    b.cube((-4, 0, -2.5), (8, 2, 5), mat="trim", tag="mantle")  # shoulder mantle
    r = m.part("robe", "body", (0, 10, 0))
    r.cube((-4, 0, -2.5), (8, 10, 5), mat="robe", tag="skirt")
    r.cube((-4.5, 10, -3), (9, 8, 6), mat="robe", tag="hem")  # flared hem trailing to the ground (y 24)
    h = m.part("head", None, (0, -4, 0))
    h.cube((-3, -7, -3), (6, 7, 6), mat="face", tag="head")
    hd = m.part("hood", "head", (0, 0, 0))
    hd.cube((-3.5, -7.5, -3.5), (7, 8, 7), mat="robe", tag="hood")
    hd.cube((-2.5, -8.5, -2), (5, 1, 5), mat="robe", tag="crown")  # rounded crown of the hood
    hd.cube((-1, -9.5, 0), (2, 1, 3), mat="robe", tag="peak")  # small peak leaning back
    hd.cube((-2.5, -5, 3.5), (5, 6, 1), mat="robe", tag="drape")  # hood drapes down the back of the neck
    ra = m.part("right_arm", None, (-4.5, -3, 0), (-55, -14, 0))  # raised, holding the tablet up to read
    ra.cube((-1.5, -1, -1.5), (3, 11, 3), mat="robe", tag="sleeve", share="sleeve")
    ra.cube((-2, 7, -2), (4, 5, 4), mat="robe", tag="cuff", share="cuff")  # wide hanging sleeve end
    ra.cube((-1, 11, -1), (2, 2, 2), mat="hand", tag="hand", share="hand")
    la = m.part("left_arm", None, (4.5, -3, 0), (-4, 0, -4))
    la.cube((-1.5, -1, -1.5), (3, 11, 3), mirror=True, mat="robe", tag="sleeve", share="sleeve", bake=False)
    la.cube((-2, 7, -2), (4, 5, 4), mirror=True, mat="robe", tag="cuff", share="cuff", bake=False)
    la.cube((-1, 11, -1), (2, 2, 2), mirror=True, mat="hand", tag="hand", share="hand", bake=False)
    t = m.part("tablet", "right_arm", (1, 12, -1), (70, 0, 10))
    t.cube((-3, -1, -4), (6, 1, 8), mat="stone", tag="tablet")
    m.pack()
    return dict(model=m, mats=mats(), textures=[
        dict(path="drowned_ascendant/drowned_ascendant", paint=lambda sk: paint(sk, m), emissive=True)],
        preview=dict(az=-32, el=10, lens=85, margin=0.86), blend=True, emit_strength=4.0)


def mats():
    return {
        "robe": Mat(["#6f9a98", "#93bdb8", "#b5d8d2", "#d4eee8", "#eaf8f4"], base=2, kind="chitin", feature=3.0,
                    pattern_scale=2.2, lo=0.12, hi=0.18, alpha=A_ROBE,
                    params={"band_dir": "X", "distortion": 1.5, "line_frac": 0.12}),
        "trim": Mat(["#5b8481", "#7fa9a4", "#a5cbc5"], base=1, feature=2.0, lo=0.1, hi=0.1, alpha=A_ROBE),
        "face": Mat(["#0b1416", "#142226", "#1f3236"], base=1, feature=2.0, lo=0.1, hi=0.1, alpha=230),
        "hand": Mat(["#93bdb8", "#b5d8d2", "#d4eee8"], base=1, feature=2.0, lo=0.1, hi=0.1, alpha=A_SKIN),
        "stone": Mat(["#56605e", "#7d8885", "#a3aeaa", "#c7d0cc"], base=2, feature=2.0, lo=0.15, hi=0.15, alpha=230),
    }


def paint(sk, m):
    # hood: open face (cut the front panel where the face sits), darker rim inside the opening
    hood = [c for c in m["hood"].cubes if c.tag == "hood"][0]
    op = sk.mask(cube=hood, face="front", cond=lambda t: (t.fx >= 1) & (t.fx <= 5) & (t.fy >= 2) & (t.fy <= 7))
    sk.cut(op)
    sk.set(sk.mask(cube=hood, face="front", cond=lambda t: (t.fx >= 1) & (t.fx <= 5) & (t.fy == 1)), "#5b8481",
           alpha=A_ROBE)
    sk.set(sk.mask(cube=hood, face="front", cond=lambda t: ((t.fx == 0) | (t.fx == 6)) & (t.fy >= 2)), "#7fa9a4",
           alpha=A_ROBE)
    # the face inside: near-black void, two glowing teal-white eyes
    head = [c for c in m["head"].cubes if c.tag == "head"][0]
    eyes = sk.mask(cube=head, face="front", cond=lambda t: (t.fy == 3) & ((t.fx == 1) | (t.fx == 4)))
    sk.set(eyes, GLOW_HI, alpha=255, emit=GLOW_HI, emit_alpha=255)
    sk.set(sk.mask(cube=head, face="front", cond=lambda t: (t.fy == 3) & ((t.fx == 2) | (t.fx == 3))), "#142226")
    sk.set(sk.mask(cube=head, face="front", cond=lambda t: (t.fy == 4) & ((t.fx == 1) | (t.fx == 4))), "#1f6f6c",
           alpha=240, emit=GLYPH, emit_alpha=90)
    # robe: long vertical folds, a pale embroidered band down the front with faint teal glyphs (the old script)
    torso = [c for c in m["body"].cubes if c.tag == "torso"][0]
    skirt = [c for c in m["robe"].cubes if c.tag == "skirt"][0]
    hem = [c for c in m["robe"].cubes if c.tag == "hem"][0]
    for c in (torso, skirt, hem):
        fold = sk.mask(cube=c, face=["front", "back", "left", "right"], cond=lambda t: (t.fx % 3 == 2) & (t.fw >= 5))
        sk.shift(fold, -1)
        band = sk.mask(cube=c, face="front", cond=lambda t: np.abs(t.fx - (t.fw - 1) / 2.0) < 1.1)
        sk.set(band, "#d4eee8", alpha=A_ROBE + 20)
        g = sk.mask(cube=c, face="front", cond=lambda t: (np.abs(t.fx - (t.fw - 1) / 2.0) < 0.6) & (F(t.fy) % 3 == 1))
        sk.set(g, "#9ff0e4", alpha=230, emit=GLYPH, emit_alpha=150)
    # hem: ragged, fading edge (the echo dissolves into the water near the floor)
    sk.cut(sk.mask(cube=hem, face=["front", "back", "left", "right"],
                   cond=lambda t: (t.fy == t.fh - 1) & ((t.fx * 7 + 3) % 5 < 2)))
    sk.set(sk.mask(cube=hem, face=["front", "back", "left", "right"], cond=lambda t: (t.fy >= t.fh - 2)), "#b5d8d2",
           alpha=130)
    # the hem's upper ledge reads as a deep fold, not a shelf
    sk.shift(sk.mask(cube=hem, face="top"), -2)
    # belt sash
    sk.set(sk.mask(cube=torso, face=["front", "back", "left", "right"], cond=lambda t: (t.fy == 6) | (t.fy == 7)),
           "#5b8481", alpha=A_ROBE + 20)
    # sleeves: darker inner opening at the cuff
    cuff = [c for c in m["right_arm"].cubes if c.tag == "cuff"][0]
    sk.set(sk.mask(cube=cuff, face="bottom"), "#142226", alpha=230)
    sk.shift(sk.mask(cube=cuff, face=["front", "back", "left", "right"], cond=lambda t: t.fy == t.fh - 1), -1)
    # tablet: pale stone slab, dark border, glowing teal glyph rows on the face turned to the reader
    tab = [c for c in m["tablet"].cubes if c.tag == "tablet"][0]
    sk.set(sk.mask(cube=tab, face=["top", "bottom"], cond=lambda t: (t.fx == 0) | (t.fx == t.fw - 1) | (t.fy == 0) |
                   (t.fy == t.fh - 1)), "#56605e", alpha=240)
    rows = sk.mask(cube=tab, face=["top", "bottom"], cond=lambda t: (t.fx >= 1) & (t.fx <= t.fw - 2) & (t.fy % 2 == 1) &
                   (t.fy >= 1) & (t.fy <= t.fh - 2) & (((t.fx * 3 + t.fy) % 4) != 0))
    sk.set(rows, "#9ff0e4", alpha=255, emit=GLYPH, emit_alpha=210)
