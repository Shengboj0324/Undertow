"""Gale Harrier - the Zenith's hawk-like predator, wingspan ~2 blocks.  Slate-blue (#4A5B7A) with silver-gold barred
flight feathers, a pale-gold hooked beak and amber eyes (emissive).
Parts: body, head -> beak, left_wing -> left_wing_tip, right_wing -> right_wing_tip, tail, left_leg, right_leg.  (spec E)"""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat
from models._util import mirror_part

F = np.floor
AMBER, AMBER_HI = "#ffb030", "#ffe08a"
SILVER, GOLD, BAR = "#c9d0dc", "#d9b860", "#2e3a52"


def build():
    m = Model("gale_harrier", (64, 64))
    b = m.part("body", None, (0, 15, 0), (8, 0, 0))  # gliding pose: chest a little lower than the tail
    b.cube((-2.5, -2, -4), (5, 4, 9), mat={"*": "slate", "bottom": "breast"}, tag="body")
    b.cube((-2, -1.5, -5.5), (4, 4, 2), mat={"*": "slate", "bottom": "breast", "front": "breast"}, tag="chest")
    h = m.part("head", "body", (0, -1, -5), (-8, 0, 0))
    h.cube((-2, -3, -3.5), (4, 4, 4), mat={"*": "slate", "bottom": "breast"}, tag="head")
    h.cube((-2.5, -3.5, -2.5), (5, 1, 2), mat="slate", tag="brow")  # heavy brow ridge = raptor glare
    k = m.part("beak", "head", (0, -1.5, -3.5))
    k.cube((-1, -1, -2), (2, 2, 2), mat="beak", tag="beak")
    k.cube((-0.5, 1, -2), (1, 1, 1), mat="beak", tag="hook")  # the hooked tip
    # wings: inner arm (coverts + secondaries) and outer hand (barred primaries), 1 px thick plates
    lw = m.part("left_wing", "body", (2.5, -1.5, -2), (0, 0, -4))
    lw.cube((0, 0, -1.5), (7, 1, 7), mat={"*": "slate", "bottom": "under", "top": "wing"}, tag="arm")
    lt = m.part("left_wing_tip", "left_wing", (7, 0, 0), (0, -6, -3))
    lt.cube((0, 0, -1), (8, 1, 7), mat={"*": "slate", "bottom": "under", "top": "wing"}, tag="hand")
    mirror_part(m, "left_wing", "right_wing", "body")
    mirror_part(m, "left_wing_tip", "right_wing_tip", "right_wing")
    t = m.part("tail", "body", (0, -1, 5), (-6, 0, 0))
    t.cube((-2.5, 0, 0), (5, 1, 7), mat={"*": "slate", "top": "wing", "bottom": "under"}, tag="tail")
    lg = m.part("left_leg", "body", (1.3, 2, 1.5), (40, 0, 0))  # legs tucked back in flight
    lg.cube((-0.5, 0, -0.5), (1, 3, 1), mat="leg", tag="shin", share="shin")
    lg.cube((-1, 3, -1.5), (2, 1, 2), mat="talon", tag="foot", share="foot")
    mirror_part(m, "left_leg", "right_leg")
    m.pack()
    return dict(model=m, mats=mats(), textures=[
        dict(path="gale_harrier/gale_harrier", paint=lambda sk: paint(sk, m), emissive=True)],
        preview=dict(az=-40, el=30, lens=85, margin=0.86), emit_strength=3.0)


def mats():
    return {
        "slate": Mat(["#202a40", "#2e3a52", "#3c4a66", "#4a5b7a", "#5f7395", "#7a8fb0"], base=3, kind="scales",
                     feature=2.0, pattern_scale=1.6, lo=0.15, hi=0.15, params={"line_frac": 0.1}),
        "wing": Mat(["#2e3a52", "#3c4a66", "#4a5b7a", "#5f7395", "#7a8fb0"], base=2, feature=2.5, lo=0.15, hi=0.15,
                    shade={"top": 0}, rim=False),
        "under": Mat(["#8d98b0", "#aab4c8", "#c8cfdc", "#e2e6ee"], base=1, feature=2.5, lo=0.15, hi=0.15,
                     shade={"bottom": 0}, rim=False),
        "breast": Mat(["#8d98b0", "#aab4c8", "#c8cfdc", "#e2e6ee"], base=2, feature=2.0, lo=0.15, hi=0.1,
                      shade={"bottom": 0}),
        "beak": Mat(["#a8883e", "#d1b25e", "#e8d38a", "#f6e9b8"], base=2, feature=1.5, lo=0.1, hi=0.1),
        "leg": Mat(["#a8883e", "#d1b25e", "#e8d38a"], base=1, feature=1.5, lo=0.0, hi=0.0),
        "talon": Mat(["#141822", "#252b38", "#3a4150"], base=1, feature=1.5, lo=0.0, hi=0.0),
    }


def _feather_outline(sk, cube, X0, total, depth):
    """Hawk wing planform: straight leading edge, trailing edge cut into finger-like primaries towards the tip."""
    oz = cube.origin[2] + 1.5

    def keep(x, y, z):
        X = X0 + x
        t = np.clip(X / total, 0, 1)
        Z = z + oz  # 0 = leading edge row of the arm
        chord = depth - 1.2 * t ** 2  # slight taper
        fingers = (X > total * 0.62) & ((F(X) % 2) == 1) & (Z > chord - 2.0)  # slotted primaries
        tip = (X > total - 1.0) & (Z < 1.0)  # rounded tip
        return (Z <= chord) & ~fingers & ~tip
    sk.cut_shape(cube, keep)


def paint(sk, m):
    arm = m["left_wing"].cubes[0]
    hand = m["left_wing_tip"].cubes[0]
    total = arm.size[0] + hand.size[0]
    _feather_outline(sk, arm, 0.0, total, 7.0)
    _feather_outline(sk, hand, float(arm.size[0]), total, 7.0)
    # flight feathers: rear half of the wing barred silver / gold / dark, coverts plain slate in front
    for cube, X0, oz in ((arm, 0.0, arm.origin[2] + 1.5), (hand, float(arm.size[0]), hand.origin[2] + 1.5)):
        for face in ("top", "bottom"):
            fl = sk.mask(cube=cube, face=face, cond=lambda t, oz=oz: (t.cz + oz) >= 3.0) & (sk.alpha > 0)
            X = F(sk.t_cx + X0)
            Z = F(sk.t_cz + oz)
            # feathers run front-to-back: alternate silver / pale-gold feather columns, crossed by dark bars
            sk.set(fl & (X % 2 == 0), SILVER if face == "top" else "#e2e6ee")
            sk.set(fl & (X % 2 == 1), GOLD if face == "top" else "#e8d38a")
            sk.set(fl & ((Z == 4) | (Z == 6)), BAR if face == "top" else "#8d98b0")
            # coverts row: a lighter scalloped edge where coverts overlap the flight feathers
            cov = sk.mask(cube=cube, face=face, cond=lambda t, oz=oz: F(t.cz + oz) == 2.0) & (sk.alpha > 0)
            sk.set(cov & (X % 2 == 0), "#7a8fb0" if face == "top" else "#e2e6ee")
        # leading edge highlight
        sk.set(sk.mask(cube=cube, face="top", cond=lambda t, oz=oz: F(t.cz + oz) == 0) & (sk.alpha > 0), "#7a8fb0")
    # tail: fan with three dark bars across silver-gold, pale tip
    tail = m["tail"].cubes[0]
    sk.cut_shape(tail, lambda x, y, z: np.abs(x - 2.5) <= 1.6 + z * 0.16)
    for face in ("top", "bottom"):
        tl = sk.mask(cube=tail, face=face) & (sk.alpha > 0)
        zc = F(sk.t_cz)
        xc = F(sk.t_cx)
        sk.set(tl & (zc >= 2) & (xc % 2 == 0), SILVER if face == "top" else "#e2e6ee")
        sk.set(tl & (zc >= 2) & (xc % 2 == 1), GOLD if face == "top" else "#e8d38a")
        sk.set(tl & ((zc == 3) | (zc == 5)), BAR if face == "top" else "#8d98b0")
        sk.set(tl & (zc == 6), "#7a8fb0" if face == "top" else SILVER)
    # breast: fine dark chevron barring on the pale underside (raptor breast)
    body = [c for c in m["body"].cubes if c.tag == "body"][0]
    sk.set(sk.mask(cube=body, face="bottom", cond=lambda t: (F(t.cz) % 2 == 0) & ((t.fx + F(t.cz) // 2) % 2 == 0)),
           "#5f7395")
    sk.set(sk.mask(cube=body, face=["left", "right"], cond=lambda t: t.fy == t.fh - 1), "#aab4c8")
    # head: amber glowing eyes under the brow, dark eye-stripe behind them (hawk "mask"), pale cheek
    head = [c for c in m["head"].cubes if c.tag == "head"][0]
    for face in ("left", "right"):
        sk.set(sk.mask(cube=head, face=face, cond=lambda t: (F(t.cz) == 0) & (t.fy == 1)), AMBER, emit=AMBER,
               emit_alpha=255)
        sk.set(sk.mask(cube=head, face=face, cond=lambda t: (F(t.cz) == 1) & (t.fy == 1)), "#1a1e2a")
        sk.set(sk.mask(cube=head, face=face, cond=lambda t: (F(t.cz) >= 2) & (t.fy == 1)), "#202a40")
        sk.set(sk.mask(cube=head, face=face, cond=lambda t: (t.fy >= 2)), "#c8cfdc")
    sk.set(sk.mask(cube=head, face="front", cond=lambda t: (t.fy == 1) & ((t.fx == 0) | (t.fx == 3))), AMBER_HI,
           emit=AMBER, emit_alpha=230)
    sk.set(sk.mask(cube=head, face="front", cond=lambda t: t.fy >= 2), "#c8cfdc")
    brow = [c for c in m["head"].cubes if c.tag == "brow"][0]
    sk.shift(sk.mask(cube=brow, face=["front", "left", "right"]), -1)
    # beak: darker hooked tip, nostril (cere) line
    hook = [c for c in m["beak"].cubes if c.tag == "hook"][0]
    sk.set(sk.mask(cube=hook), "#3a3326")
    beak = [c for c in m["beak"].cubes if c.tag == "beak"][0]
    sk.set(sk.mask(cube=beak, face="top", cond=lambda t: t.fy == t.fh - 1), "#f6e9b8")
    sk.set(sk.mask(cube=beak, face="front", cond=lambda t: t.fy == 1), "#a8883e")
