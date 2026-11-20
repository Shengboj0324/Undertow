"""Chorus Grazer - cow-sized, six-legged soft browser with a long curved neck and a glowing crystal frill (8.3)."""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat
from models._util import mirror_part

F = np.floor


def build():
    m = Model("chorus_grazer", (128, 64))
    body = m.part("body", None, (0, 11, 1))
    body.cube((-5, -4, -9), (10, 8, 18), mat={"*": "hide", "bottom": "belly"})
    body.cube((-4, -5, -7), (8, 1, 14), mat="hide")  # rounded back
    body.cube((-4, 4, -7), (8, 1, 14), mat="belly")  # rounded belly
    body.cube((-4, -3, -10), (8, 6, 1), mat="hide")  # chest
    body.cube((-4, -3, 9), (8, 6, 1), mat="hide")  # rump

    n1 = m.part("neck_1", "body", (0, -2, -9), (-48, 0, 0))
    n1.cube((-2, -2, -5), (4, 4, 6), mat={"*": "hide", "bottom": "belly"})
    n2 = m.part("neck_2", "neck_1", (0, 0, -5), (-22, 0, 0))
    n2.cube((-1.5, -1.5, -5), (3, 3, 6), mat={"*": "hide", "bottom": "belly"})
    n3 = m.part("neck_3", "neck_2", (0, 0, -5), (38, 0, 0))
    n3.cube((-1.5, -1.5, -4), (3, 3, 5), mat={"*": "hide", "bottom": "belly"})
    hd = m.part("head", "neck_3", (0, 0, -3.5), (40, 0, 0))
    hd.cube((-2.5, -2.5, -5), (5, 4, 5), mat={"*": "hide", "bottom": "belly"})
    hd.cube((-1.5, -0.5, -7), (3, 2, 2), mat="snout")  # short soft snout
    hd.cube((-2.5, -3.5, -1), (1, 1, 1), mat="hide", tag="ear")  # little ear nubs
    hd.cube((1.5, -3.5, -1), (1, 1, 1), mat="hide", share="ear", mirror=True, bake=False)
    hd.cubes[2].share = "ear"

    fr = m.part("frill", "body", (0, -5, 0))
    for i, (z, h) in enumerate(((-8, 3), (-5, 5), (-2, 6), (1, 6), (4, 5), (7, 3))):
        fr.cube((-1, -h + 1, z - 1), (2, h - 1, 2), mat="crystal", tag="plate")
        fr.cube((-0.5, -h - 1, z - 0.5), (1, 2, 1), mat="crystal", tag="tip", share="frill_tip", bake=(i == 0))

    for name, z in (("front", -6), ("mid", 0), ("back", 6)):
        lg = m.part("leg_%s_left" % name, "body", (3.5, 3, z))
        lg.cube((-1, 0, -1), (2, 10, 2), mat="leg")
        mirror_part(m, "leg_%s_left" % name, "leg_%s_right" % name)
    tl = m.part("tail", "body", (0, -2.5, 9.5), (-52, 0, 0))
    tl.cube((-1, -1, 0), (2, 2, 5), mat={"*": "hide", "back": "belly"})
    m.pack()
    return dict(model=m, mats=mats(), textures=[
        dict(path="chorus_grazer/chorus_grazer", paint=lambda sk: paint(sk, m, False), emissive=True),
        dict(path="chorus_grazer/chorus_grazer_sheared", paint=lambda sk: paint(sk, m, True), emissive=True),
    ], preview=dict(az=-42, el=16, lens=85, margin=0.84, extra=[dict(suffix="_sheared", texture=1)]),
        emit_strength=1.6)


def mats():
    return {
        "hide": Mat(["#7d5f93", "#a385b8", "#b493c4", "#c6a6d5", "#d9c6e6"], base=2, feature=4.0, lo=0.1, hi=0.18),
        "belly": Mat(["#c8a9d7", "#d9c6e6", "#e9dcf2"], base=1, feature=4.0, lo=0.15, hi=0.2,
                     shade={"bottom": 0, "top": 0}),
        "snout": Mat(["#a47fae", "#c79bc4", "#dcbad8"], base=1, feature=2.0, lo=0.1, hi=0.15),
        "leg": Mat(["#21142e", "#2f1d42", "#3f2858", "#533670"], base=1, feature=3.0, lo=0.15, hi=0.15),
        "crystal": Mat(["#7a5fa6", "#9479c6", "#b096e0", "#cdb6f6"], base=1, kind="crystal", feature=2.0,
                       pattern_scale=2.5, lo=0.0, hi=0.0, params={"line_frac": 0.12}),
    }


def paint(sk, m, sheared):
    v = sk.v
    # eyes: dark, round, gentle (2x2 with a single pale catch-light) on both head flanks
    head = m["head"].cubes[0]
    for face in ("right", "left"):
        eye = sk.mask(cube=head, face=face, cond=lambda t: (F(t.cz) == 1) & (t.cy >= 1) & (t.cy < 3))
        sk.set(eye, "#1a1024")
        # soft blush below the eye
        sk.set(sk.mask(cube=head, face=face, cond=lambda t: (F(t.cz) == 1) & (F(t.cy) == 3)), "#c79bc4")
    snout = m["head"].cubes[1]
    sk.set(sk.mask(cube=snout, face="front", cond=lambda t: (t.fy == 0) & ((t.fx == 0) | (t.fx == 2))), "#7d5f93")
    sk.set(sk.mask(cube=snout, face="front", cond=lambda t: (t.fy == 1) & (t.fx == 1)), "#a47fae")
    # hooves: darkest last texel row of every leg, a lighter "knee" band
    legs = [m["leg_%s_left" % n].cubes[0] for n in ("front", "mid", "back")]
    sk.set(sk.mask(cube=legs, face=["right", "left", "front", "back"], cond=lambda t: t.fy >= t.fh - 1), "#150c1e")
    sk.set(sk.mask(cube=legs, face="bottom"), "#150c1e")
    sk.shift(sk.mask(cube=legs, face=["right", "left", "front", "back"], cond=lambda t: (t.fy == 4)), 1)
    # back: a soft darker saddle line where the frill grows from
    ridge = m["body"].cubes[1]
    sk.shift(sk.mask(cube=ridge, face="top", cond=lambda t: (t.fx >= 3) & (t.fx <= 4)), -1)
    # belly/flank transition: lower two rows of the body sides take the belly colour
    main = m["body"].cubes[0]
    sk.set(sk.mask(cube=main, face=["right", "left", "front", "back"], cond=lambda t: t.fy >= t.fh - 2), "#d9c6e6")
    sk.set(sk.mask(cube=main, face=["right", "left", "front", "back"], cond=lambda t: t.fy == t.fh - 3), "#c8a9d7")
    # tail tuft
    tail = m["tail"].cubes[0]
    sk.set(sk.mask(cube=tail, face=["back"]), "#e9dcf2")
    sk.set(sk.mask(cube=tail, cond=lambda t: (t.cz >= 4) & (t.fy >= 0)), "#d9c6e6")
    # crystal frill: glowing lavender tips (top 2 px + top face), facet lines lighter
    plates = m["frill"].cubes
    tips = [c for c in plates if c.tag == "tip" and c.owner is None]
    plates = [c for c in plates if c.tag == "plate"]
    if not sheared:
        for c in tips:
            sk.set(sk.mask(cube=c), "#d8b6ff", emit="#d8b6ff", emit_alpha=200)
            sk.set(sk.mask(cube=c, face="top"), "#e4c8ff", emit="#d8b6ff", emit_alpha=200)
        for c in plates:
            h = c.size[1]
            sk.set(sk.mask(cube=c, face=["front", "back", "right", "left"], cond=lambda t: t.cy < 1), "#cdb6f6",
                   emit="#b090e0", emit_alpha=110)
            sk.set(sk.mask(cube=c, face="top"), "#cdb6f6", emit="#b090e0", emit_alpha=110)
            sk.shift(sk.mask(cube=c, cond=lambda t, h=h: t.cy >= h - 1), -1)
    else:
        for c in tips:
            sk.cut(sk.mask(cube=c))
    for c in plates:
        h = c.size[1]
        if not sheared:
            continue
        else:
            # sheared: plates cut down to dull 1-2 px stubs
            stub = 1 if h <= 3 else 2
            sk.cut(sk.mask(cube=c, cond=lambda t, h=h, stub=stub: t.cy < h - stub))
            sk.set(sk.mask(cube=c, cond=lambda t, h=h, stub=stub: (t.cy >= h - stub) & (t.cy < h - stub + 1)), "#a98ad6")
            sk.set(sk.mask(cube=c, cond=lambda t, h=h, stub=stub: t.cy >= h - stub + 1), "#8a6fb8")
            sk.set(sk.mask(cube=c, face="top"), "#a98ad6")
