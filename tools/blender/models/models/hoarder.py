"""Hoarder - stooped Enderman caste in a ragged patchwork cloak of Overworld materials, side pouch, amber eyes (8.5.1)."""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat
from models._util import mirror_part

F = np.floor
STOOP = 14.0

# patch colours: grass, bark, dirt, wool (white, red, yellow, light blue, lime), with a darker partner for shading
PATCHES = [("#6fbf4a", "#4f8f34"), ("#8a5f3c", "#6b4a2b"), ("#e9e4da", "#c9c2b4"), ("#b0302e", "#86221f"),
           ("#e8b830", "#c08f1c"), ("#6aa7d8", "#4a82b4"), ("#5d7f2e", "#45611f"), ("#a8784a", "#86603a")]


def build():
    m = Model("hoarder", (64, 64))
    hd = m.part("head", None, (0, -13.5, -3.5), (8, 0, 0))
    hd.cube((-4, -8, -4), (8, 8, 8), mat="skin")
    body = m.part("body", None, (0, -2, 0), (STOOP, 0, 0))
    body.cube((-4, -12, -2), (8, 12, 4), mat="skin")
    ck = m.part("cloak", "body", (0, -12, 0))
    ck.cube((-6.5, -2, -3.5), (13, 5, 7), inflate=0.25, mat="cloak", tag="mantle")  # draped over both shoulders
    ck.cube((-6, 3, 2.6), (12, 16, 1), mat="cloak", tag="drape")  # ragged back drape to the knees
    ck.cube((-6.3, 3, -3.6), (3, 9, 1), mat="cloak", tag="lapel")  # front panels hanging down the chest
    ck.cube((3.3, 3, -3.6), (3, 9, 1), mat="cloak", tag="lapel_l")
    ck.cube((-3.5, -3, 0), (7, 2, 4), mat="cloak", tag="collar")  # bunched collar behind the neck
    pc = m.part("pouch", "body", (4.5, -4, 1.5))
    pc.cube((-1, 0, -1.5), (3, 4, 3), mat="leather", tag="bag")
    pc.cube((-1.2, -0.4, -1.7), (3, 1, 3), inflate=0.15, mat="leather", tag="flap")
    ra = m.part("right_arm", None, (-5, -12.5, -2.5))
    ra.cube((-1, -2, -1), (2, 28, 2), mat="skin")
    mirror_part(m, "right_arm", "left_arm")
    rl = m.part("right_leg", None, (-2, -2, 0))
    rl.cube((-1, 0, -1), (2, 26, 2), mat="skin")
    mirror_part(m, "right_leg", "left_leg")
    m.pack()
    return dict(model=m, mats=mats(), textures=[
        dict(path="hoarder/hoarder", paint=lambda sk: paint(sk, m), emissive=True),
    ], preview=dict(az=-38, el=10, lens=85, margin=0.86), emit_strength=5.0)


def mats():
    return {
        "skin": Mat(["#060508", "#0d0b10", "#16131b", "#211c28"], base=1, feature=3.0, lo=0.12, hi=0.15),
        "cloak": Mat(["#3a2a1c", "#5a4128", "#7a5a38"], base=1, kind="plain", feature=2.0, lo=0.2, hi=0.2,
                     shade={"top": 0}, rim=False, cleanup=False),
        "leather": Mat(["#3e2616", "#5c3a20", "#7c522e", "#9a6c3e"], base=2, feature=2.0, lo=0.15, hi=0.15),
    }


def patchwork(sk, cubes):
    """Sew rectangular patches (3-5 px) over the cloak faces; the patch colour comes from the baked cell noise."""
    m = sk.mask(cube=cubes)
    fx, fy = sk.t_fx[m], sk.t_fy[m]
    cid, fid = sk.t_cube[m], sk.t_face[m]
    # patch grid with per-row offsets so seams do not line up (like real quilting)
    row = (fy // 5).astype(int)
    col = ((fx + (row % 2) * 3) // 6).astype(int)
    key = cid * 131 + fid * 31 + row * 7 + col * 13
    rng_ = np.random.default_rng(7)
    table = rng_.integers(0, len(PATCHES), 4096)
    pidx = table[key % 4096]
    tone = sk.R[m]
    ys, xs = np.nonzero(m)
    for i, (lightc, darkc) in enumerate(PATCHES):
        sel = pidx == i
        dark = sel & (tone < np.quantile(tone, 0.3))
        sk.set(_mk(sk, ys[sel], xs[sel]), lightc)
        sk.set(_mk(sk, ys[dark], xs[dark]), darkc)
    # seams: dashed dark stitches on patch borders
    fy_in = fy % 5 == 0
    fx_in = (fx + (row % 2) * 3) % 6 == 0
    seam = (fy_in | fx_in) & ((fx + fy) % 2 == 0)
    sk.set(_mk(sk, ys[seam], xs[seam]), "#2c2015")


def _mk(sk, ys, xs):
    mk = np.zeros((sk.H, sk.W), bool)
    mk[ys, xs] = True
    return mk


def paint(sk, m):
    head = m["head"].cubes[0]
    # amber eyes: a narrow band like the Enderman's, bright amber core
    eyes = sk.mask(cube=head, face="front", cond=lambda t: (t.fy == 4) & ((t.fx == 1) | (t.fx == 2) | (t.fx == 5) | (t.fx == 6)))
    sk.set(eyes, "#e08a1c", emit="#ffb02e")
    sk.set(sk.mask(cube=head, face="front", cond=lambda t: (t.fy == 4) & ((t.fx == 2) | (t.fx == 5))), "#ffe08a", emit="#ffe08a")
    # cloak patchwork + ragged bottom edge of the back drape
    ck = m["cloak"].cubes
    patchwork(sk, ck)
    drape = [c for c in ck if c.tag == "drape"][0]
    rng_ = np.random.default_rng(3)
    jag = rng_.integers(0, 4, 32)
    sk.cut_shape(drape, lambda x, y, z: y < 16 - jag[np.floor(x).astype(int) % 32] * (np.floor(x).astype(int) % 3 != 1))
    for tag in ("lapel", "lapel_l"):
        lap = [c for c in ck if c.tag == tag][0]
        sk.cut_shape(lap, lambda x, y, z: y < 9 - jag[(np.floor(x).astype(int) + 5) % 32] % 3)
    # shoulders of the mantle a bit lighter (top-left light), underside darker
    mantle = [c for c in ck if c.tag == "mantle"][0]
    sk.set(sk.mask(cube=mantle, face="bottom"), "#2c2015")
    # pouch: leather bag with a flap, a brass button and a red flower poking out (stolen from the Overworld)
    bag = [c for c in m["pouch"].cubes if c.tag == "bag"][0]
    flap = [c for c in m["pouch"].cubes if c.tag == "flap"][0]
    sk.set(sk.mask(cube=flap, face=["front", "left", "right", "back"]), "#7c522e")
    sk.set(sk.mask(cube=bag, face="left", cond=lambda t: (F(t.cy) == 1) & (F(t.cz) == 1)), "#e0b040")
    sk.set(sk.mask(cube=flap, face="top", cond=lambda t: (F(t.cx) == 1) & (F(t.cz) == 1)), "#ff5a7a")
    sk.set(sk.mask(cube=flap, face="top", cond=lambda t: (F(t.cx) == 2) & (F(t.cz) == 1)), "#6fbf4a")
    # strap of the pouch, painted diagonally across the body (front and back)
    body = m["body"].cubes[0]
    sk.set(sk.mask(cube=body, face="front", cond=lambda t: np.abs((t.fx) - (7 - t.fy * 7 / 11)) < 0.6), "#5c3a20")
    sk.set(sk.mask(cube=body, face="back", cond=lambda t: np.abs((t.fx) - (t.fy * 7 / 11)) < 0.6), "#5c3a20")
    # long Enderman limbs: a faint violet sheen down the front edge
    for p in ("right_arm", "right_leg"):
        c = m[p].cubes[0]
        sk.set(sk.mask(cube=c, face="front", cond=lambda t: (t.fx == 0) & (F(t.fy) % 6 == 2)), "#211c28")
