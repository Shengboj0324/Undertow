"""Spire Warden - 3.5-block armoured Enderman: cracked purpur plates bound by magenta light, purpur crest (8.5.2).
Three skins: intact, cracked, shattered (plates visibly break away as it loses health)."""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat
from models._util import mirror_part

F = np.floor
MAG = "#ff3fb4"
MAG_HI = "#ffb6f0"


def build():
    m = Model("spire_warden", (128, 128))
    hd = m.part("head", None, (0, -18, -0.5), (4, 0, 0))
    hd.cube((-4, -8, -4), (8, 8, 8), mat="skin", tag="head")
    cr = m.part("crest", "head", (0, -8, 0))
    cr.cube((-5, -6, 3.5), (10, 13, 2), mat="purpur", tag="back")  # rises behind the head like a pointed hood
    cr.cube((-4.5, -1.5, -3), (9, 2, 7), mat="purpur", tag="cap")  # cap plate enclosing the skull
    cr.cube((-5, 1, -2.5), (1, 6, 6), mat="purpur", tag="cheek")  # cheek guards frame the face
    cr.cube((4, 1, -2.5), (1, 6, 6), mat="purpur", share="cheek", mirror=True, bake=False)
    cr.cubes[2].share = "cheek"
    body = m.part("body", None, (0, -2, 0))
    body.cube((-5, -16, -2.5), (10, 16, 5), mat="skin", tag="torso")
    body.cube((-6, -16.5, -3), (12, 6, 6), mat="skin", tag="chest")
    pc = m.part("plate_chest", "body", (0, -16, -3))
    pc.cube((-5.5, 0.5, -1.5), (11, 9, 2), mat="purpur", tag="chest_plate")
    pc.cube((-3.5, 9.5, -1), (7, 3, 1), mat="purpur", tag="belly_plate")
    ra = m.part("right_arm", None, (-7.5, -16.5, 0), (-4, 0, 6))
    ra.cube((-1.5, -1.5, -1.5), (3, 17, 3), mat="skin")
    rf = m.part("right_forearm", "right_arm", (0, 15, 0), (-14, 0, -4))
    rf.cube((-1.5, 0, -1.5), (3, 17, 3), mat="skin")
    ps = m.part("plate_shoulder_right", "right_arm", (0, -1, 0), (0, 0, -14))
    ps.cube((-4.5, -3, -4), (7, 5, 8), mat="purpur", tag="pauldron")
    ps.cube((-5, 2, -3.5), (5, 2, 7), mat="purpur", tag="pauldron_lip")
    pf = m.part("plate_forearm_right", "right_forearm", (0, 3, 0))
    pf.cube((-2.5, 0, -2.5), (5, 9, 5), mat="purpur", tag="bracer")
    mirror_part(m, "right_arm", "left_arm")
    mirror_part(m, "right_forearm", "left_forearm", "left_arm")
    mirror_part(m, "plate_shoulder_right", "plate_shoulder_left", "left_arm")
    mirror_part(m, "plate_forearm_right", "plate_forearm_left", "left_forearm")
    rl = m.part("right_leg", None, (-2.5, -2, 0))
    rl.cube((-1.5, 0, -1.5), (3, 26, 3), mat="skin")
    mirror_part(m, "right_leg", "left_leg")
    m.pack()
    T = []
    for state in ("", "_cracked", "_shattered"):
        T.append(dict(path="spire_warden/spire_warden" + state, paint=(lambda st: lambda sk: paint(sk, m, st))(state),
                      emissive=True, mats=mats(state)))
    return dict(model=m, mats=mats(""), textures=T,
                preview=dict(az=-30, el=8, lens=85, margin=0.88,
                             extra=[dict(suffix="_cracked", texture=1, res=640), dict(suffix="_shattered", texture=2, res=640)]),
                emit_strength=5.0)


def mats(state):
    cover = {"": 0.25, "_cracked": 0.6, "_shattered": 0.8}[state]
    return {
        "skin": Mat(["#060508", "#0d0b10", "#16131b", "#221c2a"], base=1, feature=3.0, lo=0.12, hi=0.15),
        # weathered purpur: vanilla purpur desaturated ~20%, with hairline cracks
        "purpur": Mat(["#5a3f66", "#73567f", "#8d6a98", "#a383ad", "#bba0c3"], base=2, kind="cracks", feature=3.0,
                      pattern_scale=4.0, lo=0.18, hi=0.18,
                      extra={"crack": "#2a1631"}, params={"crack_cover": cover, "line_frac": 0.16}),
    }


def plates(m):
    out = []
    for p in ("plate_chest", "plate_shoulder_right", "plate_forearm_right", "crest"):
        out += [c for c in m[p].cubes if c.owner is None]
    return out


def paint(sk, m, state):
    head = [c for c in m["head"].cubes if c.tag == "head"][0]
    # burning magenta eyes (Enderman band, brighter core)
    sk.set(sk.mask(cube=head, face="front", cond=lambda t: (t.fy == 4) & ((t.fx <= 2) | (t.fx >= 5)) & (t.fx >= 1) & (t.fx <= 6)),
           "#d0208c", emit=MAG)
    sk.set(sk.mask(cube=head, face="front", cond=lambda t: (t.fy == 4) & ((t.fx == 2) | (t.fx == 5))), MAG_HI, emit=MAG_HI)
    pl = plates(m)
    # purpur block pattern: carved border on each plate face (vanilla purpur reads as tiles)
    for c in pl:
        sk.shift(sk.mask(cube=c, face=["front", "back", "left", "right", "top"],
                         cond=lambda t: (t.fx == 0) | (t.fy == 0)), 1)
        sk.shift(sk.mask(cube=c, face=["front", "back", "left", "right", "top"],
                         cond=lambda t: (t.fx == t.fw - 1) | (t.fy == t.fh - 1)), -1)
    crack_col = {"": "#2a1631", "_cracked": "#4a1840", "_shattered": "#6a1650"}[state]
    crack_emit = {"": None, "_cracked": ("#b02488", 90), "_shattered": ("#d0309c", 140)}[state]
    crack = sk.has_override & sk.mask(cube=pl)
    if crack_emit:
        sk.set(crack, crack_col, emit=crack_emit[0], emit_alpha=crack_emit[1])
    # binding light: thin magenta seams where the plates meet the body (plate undersides + inner edges)
    seam_alpha = {"": 90, "_cracked": 140, "_shattered": 200}[state]
    for p in ("plate_chest", "plate_shoulder_right", "plate_forearm_right"):
        for c in m[p].cubes:
            if c.owner is not None:
                continue
            sk.set(sk.mask(cube=c, face="bottom", cond=lambda t: (t.fx + t.fy) % 3 != 0), "#6a1a58", emit=MAG,
                   emit_alpha=seam_alpha)
            sk.set(sk.mask(cube=c, face=["front", "back", "left", "right"],
                           cond=lambda t: (t.fy == t.fh - 1) & ((t.fx * 7 + t.fy) % 3 == 0)),
                   "#6a1a58", emit=MAG, emit_alpha=seam_alpha)
    # body: faint magenta light leaking through the torso where plates are bound (chest band, wrists)
    torso = [c for c in m["body"].cubes if c.tag == "torso"][0]
    sk.set(sk.mask(cube=torso, face=["left", "right"], cond=lambda t: (t.fy == 2) | (t.fy == 9)), "#3a1030", emit=MAG,
           emit_alpha=seam_alpha // 2)
    fa = m["right_forearm"].cubes[0]
    sk.set(sk.mask(cube=fa, face=["front", "back", "left", "right"], cond=lambda t: (t.fy == 2) | (t.fy == 12)), "#3a1030",
           emit=MAG, emit_alpha=seam_alpha // 2)
    # crest silhouette: the back plate rises to a point
    back = [c for c in m["crest"].cubes if c.tag == "back"][0]
    sk.cut_shape(back, lambda x, y, z: y >= np.abs(x - 5) * 1.1 - 0.5)
    # damage states: chips and missing chunks
    if state in ("_cracked", "_shattered"):
        rng_ = np.random.default_rng(11 if state == "_cracked" else 12)
        for c in pl:
            corners = sk.mask(cube=c, face=["front", "back", "left", "right", "top"],
                              cond=lambda t: ((t.fx == 0) | (t.fx == t.fw - 1)) & ((t.fy == 0) | (t.fy == t.fh - 1)))
            pick = corners & (rng_.random(corners.shape) < (0.5 if state == "_cracked" else 0.9))
            sk.cut(pick)
    if state == "_shattered":
        # big chunks of the plates have fallen away: cut where the baked macro noise is high, glow at the break
        for c in pl:
            mk = sk.mask(cube=c)
            hole = mk & (sk.B > np.quantile(sk.B[mk], 0.55))
            sk.cut(hole)
        for c in pl:
            edge = sk.outline_edge(c, faces=("front", "back", "left", "right", "top", "bottom"))
            sk.set(edge & (sk.alpha > 0) & (sk.R > np.quantile(sk.R, 0.4)), "#6a1a58", emit=MAG, emit_alpha=120)
