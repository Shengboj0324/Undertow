"""The Wyrm companion lineage (10.5): hatchling -> fledgling -> drake.

Shared design language so the three stages read as one gentle family growing up, and never as the Ender Dragon:
  * slate-blue scales with a pale teal belly (drake deepens to indigo towards the tail and along the spine);
  * a boxy head with a SHORT, rounded muzzle and big soft pink glowing eyes (no long crocodile snout, no teeth);
  * pale cream horns that grow from stubs -> curving back -> long swept-back;
  * membranous wings with pale radial veins, a leaf-shaped tail tip, and lavender spines that start to glow.
"""
import numpy as np

from mclib.geom import Model, part_world_matrices, xform
from mclib.mat import Mat
from models._util import mirror_part

F = np.floor

STAGES = {
    "hatchling": dict(
        tex=(64, 64), body_pivot=(0, 19, 1), body=((-3, -3, -3.5), (6, 5, 7)), belly=((-2.5, 2, -2.5), (5, 1, 5)),
        chest=None, leg_front=((-1, 0, -1), (2, 3, 2), (2, 2, -1.5)), leg_back=((-1, 0, -1), (2, 3, 2), (2, 2, 2.5)),
        neck=dict(pivot=(0, -2, -3), rot=(-25, 0, 0), cubes=[((-1.5, -1.5, -2.5), (3, 3, 3))]),
        head=dict(pivot=(0, -0.5, -1.5), rot=(20, 0, 0), box=((-3, -5, -4.5), (6, 5, 6)), snout=((-2, -2.5, -6.5), (4, 2, 2)),
                  jaw=dict(pivot=(0, -0.5, -4), box=((-1.5, 0, -2.5), (3, 1, 2)))),
        horn=dict(pivot=(1.8, -5, -0.5), rot=(-15, 0, 12), cubes=[((-0.5, -2, -0.5), (1, 2, 1))]),
        eye=dict(w=2, h=2, z0=1, y0=1),
        wing=dict(pivot=(3, -2.5, -1), rot=(0, -20, -48), arm=None, mem=((0, 0, -1), (4, 0, 4)),
                  tip=dict(rot=(0, -5, 10), arm=None, mem=((0, 0, -0.5), (3, 0, 3)))),
        tail=[((-1.5, -1.5, 0), (3, 3, 4), (-1, 3.5), (-14, 0, 0)), ((-1, -1, 0), (2, 2, 4), (0, 4), (-8, 18, 0)),
              ((-0.5, -0.5, 0), (1, 1, 4), (0, 4), (14, 20, 0))],
        leaf=((-1, 0, 2), (2, 0, 3)),
        spines=[((-0.5, -1, z), (1, 1, 1)) for z in (-2, 0, 2)], spine_glow=0,
        preview=dict(az=-38, el=28, lens=85, margin=0.8),
    ),
    "fledgling": dict(
        tex=(128, 64), body_pivot=(0, 14, 2), body=((-3, -3, -6), (6, 6, 12)), belly=((-2.5, 3, -5), (5, 1, 10)),
        chest=None, leg_front=((-1, 0, -1), (2, 8, 2), (2.5, 2, -4)), leg_back=((-1, -1, -1.5), (2, 9, 3), (2.5, 2, 4)),
        neck=dict(pivot=(0, -1.5, -5.5), rot=(-38, 0, 0), cubes=[((-1.5, -1.5, -6.5), (3, 3, 7))]),
        head=dict(pivot=(0, 0, -6), rot=(32, 0, 0), box=((-2.5, -4, -4), (5, 4, 5)), snout=((-1.5, -2, -6.5), (3, 2, 3)),
                  jaw=dict(pivot=(0, 0, -3), box=((-1.5, 0, -3.5), (3, 1, 4)))),
        horn=dict(pivot=(1.5, -4, 0), rot=(32, 0, 8), cubes=[((-0.5, -0.5, -0.5), (1, 1, 3)), ((-0.5, 0.2, 2.2), (1, 1, 2))]),
        eye=dict(w=2, h=1, z0=1, y0=1),
        wing=dict(pivot=(3, -2.5, -3), rot=(0, -15, -28), arm=((0, -0.5, -1), (10, 1, 1)), mem=((0, 0, 0), (10, 0, 9)),
                  tip=dict(rot=(0, -10, 22), arm=((0, -0.5, -1), (9, 1, 1)), mem=((0, 0, 0), (9, 0, 7)))),
        tail=[((-1.5, -1.5, 0), (3, 3, 7), (-1, 6), (-12, 0, 0)), ((-1, -1, 0), (2, 2, 7), (0, 7), (-4, 14, 0)),
              ((-0.5, -0.5, 0), (1, 1, 7), (0, 7), (6, 18, 0))],
        leaf=((-1.5, 0, 4), (3, 0, 4)),
        spines=[((-0.5, -2, z), (1, 2, 1)) for z in (-4, -1, 2, 5)], spine_glow=90,
        preview=dict(az=-36, el=34, lens=85, margin=0.86),
    ),
    "drake": dict(
        tex=(128, 128), body_pivot=(0, 9, 2), body=((-5, -4, -10), (10, 9, 20)), belly=((-4, 5, -8), (8, 1, 16)),
        chest=((-4.5, -3, -11.5), (9, 7, 2)),
        leg_front=((-1.5, -2, -1.5), (3, 12, 3), (3.5, 5, -7)), leg_back=((-2, -3, -2), (4, 13, 4), (3.5, 5, 6)),
        neck=dict(pivot=(0, -2, -10.5), rot=(-42, 0, 0), cubes=[((-2.5, -2.5, -6), (5, 5, 7)), ((-2, -2, -13), (4, 4, 8))]),
        head=dict(pivot=(0, 0, -12.5), rot=(38, 0, 0), box=((-3, -5, -5), (6, 5, 7)), snout=((-2, -3, -9), (4, 3, 4)),
                  jaw=dict(pivot=(0, 0, -4), box=((-2, 0, -4.5), (4, 1, 5)))),
        horn=dict(pivot=(2, -4.5, 0.5), rot=(24, 0, 6), cubes=[((-1, -1, -1), (2, 2, 5)), ((-0.5, -0.3, 3.5), (1, 1, 5))]),
        eye=dict(w=2, h=1, z0=1, y0=2),
        wing=dict(pivot=(5, -3, -6), rot=(0, -12, -24), arm=((0, -1, -1), (16, 2, 2)), mem=((0, 0, 0), (16, 0, 14)),
                  tip=dict(rot=(0, -8, 16), arm=((0, -0.5, -0.5), (16, 1, 1)), mem=((0, 0, 0), (16, 0, 12)))),
        tail=[((-2, -2, 0), (4, 4, 10), (-1, 9.5), (-12, 0, 0)), ((-1.5, -1.5, 0), (3, 3, 10), (0, 10), (-2, 14, 0)),
              ((-1, -1, 0), (2, 2, 10), (0, 10), (4, 20, 0))],
        leaf=((-2.5, 0, 7), (5, 0, 6)),
        spines=[((-0.5, -2, z), (1, 2, 2)) for z in (-8, -5, -2, 1, 4, 7)], spine_glow=140,
        neck_spines=[((-0.5, -4, z), (1, 2, 1)) for z in (-11, -8, -5, -2)],
        preview=dict(az=-34, el=34, lens=85, margin=0.88),
    ),
}


def build(stage):
    S = STAGES[stage]
    m = Model("wyrm_" + stage, S["tex"])
    body = m.part("body", None, S["body_pivot"])
    body.cube(*S["body"], mat={"*": "scales", "bottom": "belly"}, tag="body")
    body.cube(*S["belly"], mat="belly", tag="belly")
    if S.get("chest"):
        body.cube(*S["chest"], mat={"*": "scales", "bottom": "belly", "front": "belly"}, tag="chest")
    nk = S["neck"]
    neck = m.part("neck", "body", nk["pivot"], nk["rot"])
    for o, s in nk["cubes"]:
        neck.cube(o, s, mat={"*": "scales", "bottom": "belly"}, tag="neck")
    for o, s in S.get("neck_spines", []):
        neck.cube(o, s, mat="spine", tag="nspine", share="nspine", bake=False)
    if S.get("neck_spines"):
        [c for c in neck.cubes if c.tag == "nspine"][0].bake = True
    hd = S["head"]
    head = m.part("head", "neck", hd["pivot"], hd["rot"])
    head.cube(*hd["box"], mat={"*": "scales", "bottom": "belly"}, tag="head")
    head.cube(*hd["snout"], mat={"*": "scales", "bottom": "belly"}, tag="snout")
    jw = m.part("jaw", "head", hd["jaw"]["pivot"])
    jw.cube(*hd["jaw"]["box"], mat={"*": "scales", "bottom": "belly"}, tag="jaw")
    hn = S["horn"]
    hl = m.part("horn_left", "head", hn["pivot"], hn["rot"])
    for o, s in hn["cubes"]:
        hl.cube(o, s, mat="horn", tag="horn")
    mirror_part(m, "horn_left", "horn_right")
    # wings: optional leading-edge arm bone + membrane plane; tip segment the same
    wg = S["wing"]
    lw = m.part("left_wing", "body", wg["pivot"], wg["rot"])
    if wg["arm"]:
        lw.cube(*wg["arm"], mat="horn" if stage == "drake" else "scales", tag="arm")
    lw.cube(*wg["mem"], mat="membrane", tag="mem")
    arm_len = wg["mem"][1][0]
    lt = m.part("left_wing_tip", "left_wing", (arm_len, 0, 0), wg["tip"]["rot"])
    if wg["tip"]["arm"]:
        lt.cube(*wg["tip"]["arm"], mat="horn" if stage == "drake" else "scales", tag="arm")
    lt.cube(*wg["tip"]["mem"], mat="membrane", tag="mem")
    mirror_part(m, "left_wing", "right_wing", "body")
    mirror_part(m, "left_wing_tip", "right_wing_tip", "right_wing")
    prev = "body"
    bz = S["body"][0][2] + S["body"][1][2]
    for i, (o, s, (py, pz), rot) in enumerate(S["tail"]):
        piv = (0, py, bz - 0.5) if i == 0 else (0, 0, pz)
        t = m.part("tail_%d" % (i + 1), prev, piv, rot)
        t.cube(o, s, mat={"*": "scales_deep", "bottom": "belly"}, tag="tail")
        if i == 2:
            t.cube(*S["leaf"], mat="leaf", tag="leaf")
        prev = "tail_%d" % (i + 1)
    for name, key in (("front", "leg_front"), ("back", "leg_back")):
        o, s, piv = S[key]
        lg = m.part("leg_%s_left" % name, "body", piv)
        lg.cube(o, s, mat={"*": "scales", "bottom": "claw"}, tag="leg")
        mirror_part(m, "leg_%s_left" % name, "leg_%s_right" % name)
    cr = m.part("crest", "body", (0, S["body"][0][1], 0))
    for i, (o, s) in enumerate(S["spines"]):
        cr.cube(o, s, mat="spine", tag="spine", share="spine", bake=(i == 0))
    m.pack()
    wm = part_world_matrices(m)
    origin = xform(wm["left_wing"], (0, 0, 0))
    mats = make_mats(stage, origin)
    return dict(model=m, mats=mats, textures=[
        dict(path="wyrm/wyrm_" + stage, paint=lambda sk: paint(sk, m, stage), emissive=True)],
        preview=S["preview"], emit_strength=2.4)


def make_mats(stage, wing_origin):
    scales = ["#26304a", "#3a4a66", "#4b5d7a", "#5d7290", "#7189a6"]
    deep = {"hatchling": scales, "fledgling": ["#2a2e58", "#36406c", "#45537c", "#566890", "#6a7ea4"],
            "drake": ["#1c1b40", "#28285c", "#343674", "#434a86", "#55619a"]}[stage]
    mem = {"hatchling": ["#3d4a78", "#4d5b90", "#5f6ea8", "#7484bc"],
           "fledgling": ["#29245e", "#352f78", "#433b92", "#5249a8"],
           "drake": ["#4b2a7a", "#5e3a92", "#7350ac", "#8a66c2"]}[stage]
    vein = {"hatchling": "#9fb0d8", "fledgling": "#b9b3e6", "drake": "#d9ccf5"}[stage]
    return {
        "scales": Mat(scales, base=2, kind="scales", feature=3.0, pattern_scale=2.4, lo=0.12, hi=0.14,
                      params={"line_frac": 0.14}),
        "scales_deep": Mat(deep, base=2, kind="scales", feature=3.0, pattern_scale=2.4, lo=0.12, hi=0.14,
                           params={"line_frac": 0.14}),
        "belly": Mat(["#6aa6a2", "#8ccac2", "#a9e0d6", "#c6efe6"], base=2, feature=3.0, lo=0.12, hi=0.1,
                     shade={"bottom": 0, "top": 0}),
        "horn": Mat(["#a89c88", "#cbc1ad", "#e5ddcb", "#f5f0e3"], base=2, feature=2.0, lo=0.15, hi=0.15),
        "claw": Mat(["#a89c88", "#cbc1ad", "#e5ddcb"], base=1, feature=2.0, lo=0.1, hi=0.1),
        "membrane": Mat(mem, base=1, kind="veins", feature=5.0, lo=0.12, hi=0.15, shade={"top": 0, "bottom": 0},
                        rim=False, extra={"vein": vein},
                        params={"origin": wing_origin, "count": 3.2 if stage != "hatchling" else 2.0, "warp": 0.35,
                                "line_frac": 0.12}),
        "spine": Mat(["#8a6fb8", "#a98ad6", "#c7a6f0", "#e2cdfd"], base=2, feature=2.0, lo=0.1, hi=0.1),
        "leaf": Mat(["#5f9a9a", "#7fbcb6", "#a9e0d6", "#d0f4ec"], base=1, feature=3.0, lo=0.1, hi=0.1,
                    shade={"top": 0, "bottom": 0}, rim=False),
    }


def membrane_shape(stage, X0, length_total, depth, tip):
    """Membrane outline in (x along span from the wing root, z back from the leading edge) - scalloped trailing edge
    between the finger points so the wing reads as a soft membrane (bat-like, not the Ender Dragon's ragged sail)."""
    n = {"hatchling": 1, "fledgling": 2, "drake": 3}[stage]

    def keep(x, z):
        X = X0 + x
        t = np.clip(X / length_total, 0, 1)
        # chord shrinks towards the tip; rounded scallops between fingers
        chord = depth * (1.0 - 0.55 * t ** 1.2)
        seg = length_total / (n + 1)
        sc = np.sin(np.pi * X / seg) ** 2  # smooth, rounded scallops (soft membrane, no ragged teeth)
        te = chord - (0.12 + 0.08 * t) * depth * sc * (n > 1)
        le = 0.0 + 0.6 * t * (stage == "hatchling")
        return (z >= le - 0.01) & (z <= te + 0.01)
    return keep


def paint(sk, m, stage):
    S = STAGES[stage]
    head = [c for c in m["head"].cubes if c.tag == "head"][0]
    snout = [c for c in m["head"].cubes if c.tag == "snout"][0]
    e = S["eye"]
    eye_glow = "#ff8fd0"
    eye_core = "#ffd6ef"
    for face in ("left", "right"):
        ey = sk.mask(cube=head, face=face, cond=lambda t: (t.cz >= e["z0"]) & (t.cz < e["z0"] + e["w"]) &
                     (t.cy >= e["y0"]) & (t.cy < e["y0"] + e["h"]))
        sk.set(ey, "#e0559e", emit=eye_glow, emit_alpha=170)
        # soft catch-light in the front-top corner of the eye, a calm lid line above
        core = sk.mask(cube=head, face=face, cond=lambda t: (F(t.cz) == e["z0"]) & (F(t.cy) == e["y0"]))
        sk.set(core, eye_core, emit=eye_core, emit_alpha=200)
        lid = sk.mask(cube=head, face=face, cond=lambda t: (t.cz >= e["z0"]) & (t.cz < e["z0"] + e["w"]) &
                      (F(t.cy) == e["y0"] - 1))
        sk.set(lid, "#7189a6")
    # muzzle: two tiny nostrils on top-front, gentle mouth line, lighter chin
    sk.set(sk.mask(cube=snout, face="top", cond=lambda t: (F(t.cz) == 0) & ((F(t.cx) == 0) | (F(t.cx) == t.fw - 1))
                   & (t.fw >= 3)), "#26304a")
    sk.shift(sk.mask(cube=snout, face=["front", "left", "right"], cond=lambda t: t.fy == t.fh - 1), 1)
    # pale teal belly runs up the lowest row of the body and neck sides (countershading)
    bod = [c for c in m["body"].cubes if c.tag == "body"][0]
    sk.set(sk.mask(cube=bod, face=["left", "right", "front", "back"], cond=lambda t: t.fy == t.fh - 1), "#8ccac2")
    sk.set(sk.mask(cube=[c for c in m["neck"].cubes if c.tag == "neck"], face=["front"]), "#8ccac2")
    if stage == "drake":
        # slate-blue -> deep indigo: a darker band down the spine
        sk.shift(sk.mask(cube=bod, face="top", cond=lambda t: (t.fx >= 3) & (t.fx <= t.fw - 4)), -1)
        sk.shift(sk.mask(cube=bod, face="top", cond=lambda t: (t.fx >= 4) & (t.fx <= t.fw - 5)), -1)
    # horns: lighter tips
    for c in m["horn_left"].cubes:
        sk.shift(sk.mask(cube=c, cond=lambda t, c=c: t.cz >= c.size[2] - 1.5), 1)
    # claws: pale toe tips at the front of every foot
    for p in ("leg_front_left", "leg_back_left"):
        c = m[p].cubes[0]
        sk.set(sk.mask(cube=c, face="front", cond=lambda t: (t.fy == t.fh - 1) & (t.fx % 2 == 0)), "#e5ddcb")
    # wings: membrane outline, pale veins, a lighter trailing rim
    wg = S["wing"]
    L1 = wg["mem"][1][0]
    L = L1 + wg["tip"]["mem"][1][0]
    for p, X0 in (("left_wing", 0.0), ("left_wing_tip", L1)):
        mem = [c for c in m[p].cubes if c.tag == "mem"][0]
        depth = wg["mem"][1][2] if p == "left_wing" else wg["tip"]["mem"][1][2]
        d0 = wg["mem"][1][2]
        keep = membrane_shape(stage, X0, L, d0, p.endswith("tip"))
        oz = mem.origin[2] - (wg["mem"][0][2])
        sk.cut_shape(mem, lambda x, y, z, keep=keep, oz=oz: keep(x, z + oz))
        edge = sk.outline_edge(mem)
        sk.shift(edge, 1)
    # leaf tail tip: pale teal leaf with a lavender midrib (lineage marker), glowing faintly on the drake
    leaf = [c for c in m["tail_3"].cubes if c.tag == "leaf"][0]
    lw_, ld = leaf.size[0], leaf.size[2]
    sk.cut_shape(leaf, lambda x, y, z: np.abs(x - lw_ / 2) <= (lw_ / 2) * np.sin(np.pi * np.clip(z / ld, 0, 1)) ** 0.7 + 0.3)
    mid = sk.mask(cube=leaf, cond=lambda t: np.abs(t.cx - lw_ / 2) < 0.6)
    sk.set(mid, "#d8b6ff", emit="#d8b6ff" if stage != "hatchling" else None, emit_alpha=150 if stage == "drake" else 90)
    # crest spines: lavender, glowing from the fledgling on
    g = S["spine_glow"]
    sp = [c for c in m["crest"].cubes if c.owner is None]
    if S.get("neck_spines"):
        sp += [c for c in m["neck"].cubes if c.tag == "nspine" and c.owner is None]
    for c in sp:
        if g:
            sk.set(sk.mask(cube=c, cond=lambda t: t.cy < 1), "#d2b4f8", emit="#c09cf4", emit_alpha=g)
            sk.set(sk.mask(cube=c, cond=lambda t: t.cy >= 1), "#b496e0", emit="#a080d0", emit_alpha=g // 3)
        else:
            sk.set(sk.mask(cube=c), "#a98ad6")
