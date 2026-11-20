"""Void Skate - manta-like glider, wingspan 80 px (5 blocks), body ~0.8 block.  (design doc 8.2, 18.4)"""
import numpy as np

from mclib.geom import Model
from mclib.mat import Mat
from models._util import mirror_part

SPAN = 33.0  # wing length from body edge (x = 7) to tip (x = 40)
JOINT = 16.0  # inner wing / tip joint (mid-span)


def LE(X):  # leading edge z (front = negative) as a function of distance from the wing root
    t = np.clip(X / SPAN, 0, 1)
    return -11.0 + 13.5 * t ** 1.35


def TE(X):  # trailing edge z
    t = np.clip(X / SPAN, 0, 1)
    return 10.0 - 7.5 * t ** 0.75


def build():
    m = Model("void_skate", (128, 128))
    body = m.part("body", None, (0, 20, 0))
    body.cube((-7, -3, -10), (14, 5, 20), mat={"top": "top", "bottom": "under", "side": "top"})
    body.cube((-5, -5, -7), (10, 2, 14), mat={"top": "top", "bottom": "under", "side": "top"})
    body.cube((-8, -2, -13), (16, 3, 3), mat={"top": "top", "bottom": "under", "side": "top"})
    body.cube((-4, -2, 10), (8, 3, 4), mat={"top": "top", "bottom": "under", "side": "top"})

    lw = m.part("left_wing", "body", (7, -0.5, 0), (0, 0, -3))
    lw.cube((0, 0, -11), (16, 1, 22), mat={"top": "wing", "bottom": "under", "side": "top"})
    lt = m.part("left_wing_tip", "left_wing", (JOINT, 0, 0), (0, 0, -5))
    lt.cube((0, 0, -7), (17, 1, 13), mat={"top": "wing", "bottom": "under", "side": "top"})
    mirror_part(m, "left_wing", "right_wing", "body")
    mirror_part(m, "left_wing_tip", "right_wing_tip", "right_wing")

    t1 = m.part("tail_1", "body", (0, -0.5, 13), (-3, 0, 0))
    t1.cube((-1, -1, 0), (2, 2, 10), mat="top")
    t2 = m.part("tail_2", "tail_1", (0, 0, 10), (-4, 0, 0))
    t2.cube((-0.5, -0.5, 0), (1, 1, 10), mat="top")
    t3 = m.part("tail_3", "tail_2", (0, 0, 10), (5, 0, 0))
    t3.cube((-0.5, -0.5, 0), (1, 1, 10), mat="top")

    lf = m.part("left_fin", "body", (6, -0.5, -12), (8, 14, 0))
    lf.cube((0, -1.5, -5), (1, 3, 5), mat={"*": "fin", "right": "under"})  # flat paddle pointing forward
    lf.cube((-2, 1.5, -5), (2, 1, 4), mat={"*": "fin", "top": "under"})  # lower edge rolled inwards (the curl)
    mirror_part(m, "left_fin", "right_fin", "body")

    # harness (child of body, rendered only when saddled): saddle pad, two straps with brass rings
    hz = m.part("harness", "body", (0, -5, 0))
    hz.cube((-5, -1, -5), (10, 1, 10), inflate=0.2, mat="leather")
    hz.cube((-5.5, -1.6, 3.5), (11, 1, 2), mat={"*": "leather", "top": "pale"})  # raised cantle
    for tag, o, sz, inf, mt in (("strap_a", (5, 0, -1), (1, 2, 2), 0.0, "leather"),
                                ("strap_b", (5, 1.5, -1), (3, 1, 2), 0.0, "leather"),
                                ("strap_c", (7, 2, -1), (1, 5, 2), 0.1, "leather"),
                                ("ring", (6.6, 6.5, -1.5), (1, 3, 3), 0.0, "brass")):
        hz.cube(o, sz, inflate=inf, mat=mt, share="harness_" + tag, tag=tag)
        hz.cube((-(o[0] + sz[0]), o[1], o[2]), sz, inflate=inf, mirror=True, mat=mt, share="harness_" + tag,
                bake=False, tag=tag)
    m.pack()
    return dict(model=m, mats=mats(False), textures=[
        dict(path="void_skate/void_skate", paint=lambda sk: paint(sk, m, False), emissive=True),
        dict(path="void_skate/void_skate_dusk", paint=lambda sk: paint(sk, m, True), mats=mats(True), emissive=True),
    ], preview=dict(az=-38, el=34, lens=60, hidden=["harness"], margin=0.9,
                    extra=[dict(suffix="_dusk", texture=1), dict(suffix="_saddled", show=["harness"])]),
        emit_strength=6.0)


def mats(dusk):
    if not dusk:
        top = ["#0e0c26", "#1a1840", "#2a2560", "#21446b", "#2e5b86"]
        wing = top
        under = ["#3a2f6e", "#54479a", "#6c5db6", "#8b7bcc"]
    else:
        top = ["#1a0e24", "#2c1838", "#45224f", "#5c3060", "#784272"]
        wing = top
        under = ["#55365e", "#77507f", "#9a6a9a", "#b98bb3"]
    return {
        "top": Mat(top, base=2, feature=5.0, lo=0.14, hi=0.16, shade={"top": 1, "bottom": -1}),
        "wing": Mat(wing, base=2, feature=7.0, lo=0.12, hi=0.2, shade={"top": 0}, rim=False),
        "under": Mat(under, base=1, feature=6.0, lo=0.15, hi=0.25, shade={"bottom": 0, "top": 0}, rim=False),
        "fin": Mat(top, base=2, feature=3.0, lo=0.2, hi=0.2),
        "leather": Mat(["#3a2214", "#5a3820", "#7d5230", "#9c6c40"], base=2, feature=3.0, lo=0.2, hi=0.15),
        "brass": Mat(["#6b4a12", "#a8781e", "#e0b040", "#f7dd85"], base=2, feature=3.0, lo=0.1, hi=0.1),
        "pale": Mat(["#7d74a0", "#a39ac4", "#c4bbdf", "#e0d9f2"], base=1, feature=3.0, lo=0.15, hi=0.1),
    }


def paint(sk, m, dusk):
    spot = "#ffb347" if dusk else "#3ae0d0"
    spot_hi = "#ffe2a6" if dusk else "#b8fff7"
    spot_dim = "#c07a2a" if dusk else "#2fc9bb"
    eye = "#d9ecff" if not dusk else "#ffe9d6"
    v = sk.v
    f = np.floor
    lw, lt = m["left_wing"].cubes[0], m["left_wing_tip"].cubes[0]

    # --- wing planform (alpha cut-out), leading-edge glow line, spot row along the trailing edge
    for cube, X0 in ((lw, 0.0), (lt, JOINT)):
        oz = cube.origin[2]
        sk.cut_shape(cube, lambda x, y, z, X0=X0, oz=oz: (z + oz >= LE(X0 + x) - 0.25) & (z + oz <= TE(X0 + x) + 0.25))
        edge = sk.outline_edge(cube)
        X = v.cx + X0
        Z = v.cz + oz
        lead = edge & (Z < (LE(X) + TE(X)) / 2)
        trail = edge & ~lead
        sk.set(lead & sk.mask(face="top"), spot, emit=spot)
        sk.set(lead & sk.mask(face="bottom"), spot_dim, emit=spot_dim, emit_alpha=170)
        sk.shift(trail & sk.mask(face="top"), -1)
        # a soft lighter band inside the leading edge (top), like a manta's shoulder
        inner = sk.mask(cube=cube, face="top", cond=lambda t, X0=X0, oz=oz: (t.cz + oz - LE(t.cx + X0) < 2.2) & (t.cz + oz - LE(t.cx + X0) >= 1.0))
        sk.shift(inner, 1)
        # trailing-edge spot row, every 3 px, 2 px inside the edge, single crisp texels (bigger near the body)
        for Xs in np.arange(2.5, SPAN - 2, 3.0):
            if not (X0 <= Xs < X0 + cube.size[0]):
                continue
            zs = TE(Xs) - 2.2
            mk = sk.mask(cube=cube, face="top", cond=lambda t, Xs=Xs, zs=zs, X0=X0, oz=oz:
                         (f(t.cx + X0) == f(Xs)) & (f(t.cz + oz) == f(zs)))
            sk.set(mk, spot_hi if Xs < 12 else spot, emit=spot_hi if Xs < 12 else spot)
            if Xs < 9:  # paired spot in front of the big ones
                mk2 = sk.mask(cube=cube, face="top", cond=lambda t, Xs=Xs, zs=zs, X0=X0, oz=oz:
                              (f(t.cx + X0) == f(Xs)) & (f(t.cz + oz) == f(zs) - 1))
                sk.set(mk2, spot, emit=spot)
        # second, sparser row along the leading edge
        for Xs in np.arange(4.5, SPAN - 4, 4.0):
            if not (X0 <= Xs < X0 + cube.size[0]):
                continue
            zs = LE(Xs) + 3.2
            mk = sk.mask(cube=cube, face="top", cond=lambda t, Xs=Xs, zs=zs, X0=X0, oz=oz:
                         (f(t.cx + X0) == f(Xs)) & (f(t.cz + oz) == f(zs)))
            sk.set(mk, spot, emit=spot, emit_alpha=200)

    body, hump, head, rear = m["body"].cubes
    # --- spots down the centre: one row along the spine (paired texels), continuing onto the rear
    for z in range(1, 14, 3):
        sk.set(sk.mask(cube=hump, face="top", cond=lambda t, z=z: ((f(t.cx) == 4) | (f(t.cx) == 5)) & (f(t.cz) == z)),
               spot, emit=spot)
    for z in (0, 3):
        sk.set(sk.mask(cube=rear, face="top", cond=lambda t, z=z: ((f(t.cx) == 3) | (f(t.cx) == 4)) & (f(t.cz) == z)),
               spot_hi, emit=spot_hi)
    # countershading: lowest row of every body side reads as the pale underside
    sk.set(sk.mask(cube=[body, head, rear], face=["right", "left", "front", "back"], cond=lambda t: t.fy == t.fh - 1),
           "#54479a" if not dusk else "#77507f")
    # mouth: wide dark slit across the head front, with a pale lip line above
    sk.set(sk.mask(cube=head, face="front", cond=lambda t: (t.fy == 2) & (t.fx >= 3) & (t.fx <= 12)), "#0a0818")
    # calm pale eyes on the head flanks, just behind the fins
    for face in ("right", "left"):
        sk.set(sk.mask(cube=body, face=face, cond=lambda t, face=face: (t.fy == 1) & (f(t.cz) == 1)), eye)
        sk.set(sk.mask(cube=body, face=face, cond=lambda t, face=face: (t.fy == 1) & (f(t.cz) == 2)), "#0a0818")
        sk.set(sk.mask(cube=body, face=face, cond=lambda t, face=face: (t.fy == 0) & (f(t.cz) <= 2)), "#2e5b86" if not dusk else "#784272")
    # gill slits on the belly
    for i, z in enumerate((3, 5, 7, 9, 11)):
        sk.set(sk.mask(cube=body, face="bottom", cond=lambda t, z=z: (f(t.cz) == z) & (((t.cx >= 2) & (t.cx < 5)) | ((t.cx >= 9) & (t.cx < 12)))),
               "#3a2f6e" if not dusk else "#55365e")
    # --- whip tail: glowing tip (last 3 px), faint spots along it
    t3 = m["tail_3"].cubes[0]
    sk.set(sk.mask(cube=t3, cond=lambda t: t.cz >= 7), spot, emit=spot)
    sk.set(sk.mask(cube=t3, cond=lambda t: t.cz >= 9), spot_hi, emit=spot_hi)
    t1 = m["tail_1"].cubes[0]
    sk.set(sk.mask(cube=t1, face="top", cond=lambda t: (f(t.cz) % 3 == 1) & (f(t.cx) == 0)), spot_dim, emit=spot_dim, emit_alpha=200)
    # cephalic fins: lighter inner curl edge
    fin = m["left_fin"].cubes[0]
    sk.shift(sk.mask(cube=fin, face="front"), 1)
    # --- harness details: stitched pale membrane edge, stitches on the pad, ring hole
    pad, cantle = m["harness"].cubes[0], m["harness"].cubes[1]
    sk.set(sk.mask(cube=pad, face="top", cond=lambda t: (t.fx == 0) | (t.fx == t.fw - 1) | (t.fy == 0) | (t.fy == t.fh - 1)), "#bdb3da")
    sk.set(sk.mask(cube=pad, face="top", cond=lambda t: ((t.fx == 1) | (t.fx == t.fw - 2) | (t.fy == 1) | (t.fy == t.fh - 2))
                   & ((t.fx + t.fy) % 2 == 0) & (t.fx >= 1) & (t.fx <= t.fw - 2) & (t.fy >= 1) & (t.fy <= t.fh - 2)), "#8f86b0")
    ring = [c for c in m["harness"].cubes if c.tag == "ring"][0]
    sk.cut(sk.mask(cube=ring, face=["right", "left"], cond=lambda t: (t.fx == 1) & (t.fy == 1)))
    sk.set(sk.mask(cube=ring, face=["right", "left"], cond=lambda t: (t.fx != 1) | (t.fy != 1)), "#e0b040")
    sk.set(sk.mask(cube=ring, face=["right", "left"], cond=lambda t: (t.fy == 0)), "#f7dd85")
