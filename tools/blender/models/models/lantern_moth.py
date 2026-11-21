"""Lantern Moth - a small moth drawn to Spire Lanterns: dusty purpur-grey wings with magenta eyespots (emissive),
a fuzzy pale body and feathery antennae.  Parts: body, left_wing, right_wing, antennae (child of body).  (spec E)"""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat
from models._util import mirror_part

F = np.floor
MAG, MAG_HI, MAG_DIM = "#ff4fd8", "#ffc2f1", "#b0307e"


def build():
    m = Model("lantern_moth", (32, 32))
    b = m.part("body", None, (0, 21, 0))
    b.cube((-1, -1, -2), (2, 2, 5), mat="fuzz", tag="abdomen")
    b.cube((-1.5, -1.5, -3), (3, 3, 2), mat="fuzz", tag="thorax", inflate=0.0)  # fluffy collar behind the head
    b.cube((-1, -1, -4), (2, 2, 1), mat="head", tag="head")
    a = m.part("antennae", "body", (0, -1, -4), (-20, 0, 0))
    a.cube((0, -3, 0), (3, 3, 0), mat="antenna", tag="ant_l", share="ant")
    a.cube((-3, -3, 0), (3, 3, 0), mirror=True, mat="antenna", tag="ant_r", share="ant", bake=False)
    lw = m.part("left_wing", "body", (1, -1, -1), (0, 0, -12))
    lw.cube((0, 0, -3), (7, 0, 9), mat="wing", tag="wing")  # fore + hind wing as one plate, outline cut below
    mirror_part(m, "left_wing", "right_wing")
    m.pack()
    return dict(model=m, mats=mats(), textures=[
        dict(path="lantern_moth/lantern_moth", paint=lambda sk: paint(sk, m), emissive=True)],
        preview=dict(az=-25, el=55, lens=85, margin=0.8),
        emit_strength=3.0)


def mats():
    return {
        "wing": Mat(["#33263a", "#4a3a52", "#62506a", "#7a6684", "#9686a0"], base=2, kind="lichen", feature=1.6,
                    pattern_scale=2.0, lo=0.18, hi=0.18, rim=False, shade={"top": 0, "bottom": -1},
                    extra={"lichen": ["#b8a9b8", "#c9bcc6"]}, params={"cover": 0.12, "b_scale": 1.0}),
        "fuzz": Mat(["#9c9090", "#bdb2ae", "#d8cec6", "#eee6dc"], base=2, feature=1.2, lo=0.25, hi=0.2),
        "head": Mat(["#6e5c72", "#8a7890", "#a898ac"], base=1, feature=1.5, lo=0.1, hi=0.1),
        "antenna": Mat(["#8a7890", "#bdb2ae", "#d8cec6"], base=1, feature=1.0, lo=0.0, hi=0.0, rim=False,
                       shade={"top": 0, "bottom": 0}),
    }


# hand-placed wing art, seen from above: columns = span from the root (x 0..6), rows = front -> back (z 0..8)
#   . cut   M baked dusty grey   D one step darker   L one step lighter   e pale fringe   Z dark moth line
#   E magenta eyespot ring (glows)   P pale-pink pupil (glows bright)   S small hindwing spot (glows)
WING_ART = [
    "DMLLe..",
    "DMMLLe.",
    "DMEEMLe",
    "DEPEMLe",
    "DMEEMMe",
    "DZZMZe.",
    "DMSML..",
    "DMMMe..",
    "DDe....",
]


def paint(sk, m):
    wing = m["left_wing"].cubes[0]
    W, D = wing.size[0], wing.size[2]
    art = np.array([list(r) for r in WING_ART])

    def at(x, z):
        xi = np.clip(F(x).astype(int), 0, W - 1)
        zi = np.clip(F(z).astype(int), 0, D - 1)
        return art[zi, xi]
    sk.cut_shape(wing, lambda x, y, z: at(x, z) != ".")
    for face, sh in (("top", 0), ("bottom", -1)):
        mk = sk.mask(cube=wing, face=face) & (sk.alpha > 0)
        code = np.full(mk.shape, ".", "<U1")
        code[mk] = at(sk.t_cx[mk], sk.t_cz[mk])
        sk.shift(mk & (code == "D"), -1)
        sk.shift(mk & (code == "L"), 1)
        sk.set(mk & (code == "e"), "#a898ac" if face == "top" else "#8a7890")
        sk.set(mk & (code == "Z"), "#3e3040")
        if face == "top":
            sk.set(mk & (code == "E"), MAG_DIM, emit=MAG, emit_alpha=210)
            sk.set(mk & (code == "P"), MAG_HI, emit=MAG_HI, emit_alpha=255)
            sk.set(mk & (code == "S"), MAG, emit=MAG, emit_alpha=170)
        else:  # a faint echo of the eyespot underneath so it still reads from below
            sk.set(mk & np.isin(code, ["E", "P"]), MAG_DIM, emit=MAG_DIM, emit_alpha=110)
            sk.set(mk & (code == "S"), "#56455a")
        sk.shift(mk & np.isin(code, ["M", "D", "L"]), sh)

    # body: fuzzy pale, darker rings on the abdomen, a dark head with two big dark eyes
    ab = [c for c in m["body"].cubes if c.tag == "abdomen"][0]
    sk.shift(sk.mask(cube=ab, face=["top", "left", "right", "bottom"], cond=lambda t: F(t.cz) % 2 == 1), -1)
    sk.set(sk.mask(cube=ab, face="back"), "#bdb2ae")
    hd = [c for c in m["body"].cubes if c.tag == "head"][0]
    sk.set(sk.mask(cube=hd, face=["left", "right"]), "#1c141e")
    sk.set(sk.mask(cube=hd, face="front", cond=lambda t: t.fy == 0), "#1c141e")
    # feathery antennae: comb pattern (alternate texels cut), a pale shaft along the diagonal
    ant = [c for c in m["antennae"].cubes if c.tag == "ant_l"][0]
    sk.cut_shape(ant, lambda x, y, z: (np.abs((3 - y) - x * 1.0) < 1.3) & ~((F(x) == 2) & (F(y) == 2)))
    sk.set(sk.mask(cube=ant, cond=lambda t: np.abs((3 - t.cy) - t.cx) < 0.6), "#eee6dc")
