"""Leviathan Drifter - a colossal, slow sky creature of the Zenith, modelled at QUARTER scale (the renderer scales x4):
~11 blocks long in model space (~44 in game).  Pale silver-blue whale/manta body (#B9C7DD / #7F90B0), slow broad
pectoral fins, gold speckles and glowing gold seam lines (emissive), small calm eyes, and a flat back_ridge on top
where a living garden is drawn by code.
Parts: body (root), head -> jaw, left_fin -> left_fin_tip, right_fin -> right_fin_tip, tail_1 -> tail_2 -> tail_fluke,
back_ridge (child of body, flat top at the garden height).  (spec E)"""
import numpy as np

from mclib.geom import Model, part_world_matrices, xform
from mclib.mat import Mat
from models._util import mirror_part

F = np.floor
GOLD, GOLD_HI, GOLD_DIM, SPECK = "#e8d38a", "#fff1c2", "#b8963e", "#e8d38a"
FIN_SPAN = 62.0  # fin root -> tip


def build():
    m = Model("leviathan_drifter", (256, 256))
    b = m.part("body", None, (0, 6, 0))
    b.cube((-18, -11, -36), (36, 21, 24), mat={"*": "back", "bottom": "belly"}, tag="front")
    b.cube((-20, -12, -12), (40, 24, 26), mat={"*": "back", "bottom": "belly"}, tag="mid")
    b.cube((-15, 10, -30), (30, 4, 36), mat={"*": "belly"}, tag="keel")  # deep belly: whale-like profile
    b.cube((-16, -9, 14), (32, 17, 22), mat={"*": "back", "bottom": "belly"}, tag="rear")
    h = m.part("head", "body", (0, 1, -36))
    h.cube((-15, -8, -26), (30, 11, 26), mat={"*": "back", "bottom": "belly"}, tag="skull")
    h.cube((-12, -6, -30), (24, 9, 4), mat={"*": "back", "bottom": "belly"}, tag="snout")
    h.cube((-11, -11, -20), (22, 3, 18), mat={"*": "back", "bottom": "belly"}, tag="brow")  # sloping forehead
    j = m.part("jaw", "head", (0, 3, -2))
    j.cube((-14, 0, -28), (28, 5, 28), mat={"*": "belly", "top": "mouth", "left": "back", "right": "back"}, tag="jaw")
    lf = m.part("left_fin", "body", (18, 4, -16), (0, 0, -6))
    lf.cube((0, -1, -10), (34, 2, 30), mat={"*": "back", "bottom": "belly", "top": "fin"}, tag="fin")
    lt = m.part("left_fin_tip", "left_fin", (34, 0, 0), (0, 0, -4))
    lt.cube((0, -0.5, -4), (28, 1, 24), mat={"*": "back", "bottom": "belly", "top": "fin"}, tag="tip")
    mirror_part(m, "left_fin", "right_fin", "body")
    mirror_part(m, "left_fin_tip", "right_fin_tip", "right_fin")
    t1 = m.part("tail_1", "body", (0, -1, 36))
    t1.cube((-12, -7, 0), (24, 14, 30), mat={"*": "back", "bottom": "belly"}, tag="t1")
    t2 = m.part("tail_2", "tail_1", (0, 0, 30))
    t2.cube((-7, -5, 0), (14, 10, 26), mat={"*": "back", "bottom": "belly"}, tag="t2")
    fl = m.part("tail_fluke", "tail_2", (0, 0, 26))
    fl.cube((-26, -1, 0), (52, 2, 20), mat={"*": "back", "bottom": "belly", "top": "fin"}, tag="fluke")
    br = m.part("back_ridge", "body", (0, -12, -4))
    br.cube((-13, -2, -18), (26, 2, 34), mat={"*": "back", "top": "garden"}, tag="ridge")
    m.pack()
    wm = part_world_matrices(m)
    fin_origin = xform(wm["left_fin"], (0, 0, 0))
    return dict(model=m, mats=mats(fin_origin), textures=[
        dict(path="leviathan_drifter/leviathan_drifter", paint=lambda sk: paint(sk, m), emissive=True)],
        preview=dict(az=-38, el=26, lens=70, margin=0.92, pose={"jaw": (6, 0, 0)}), emit_strength=3.0, supersample=2)


def mats(fin_origin):
    back = ["#4e5c7c", "#66779a", "#7f90b0", "#9cacc8", "#b9c7dd", "#d6e0ee"]
    return {
        "back": Mat(back, base=3, kind="lichen", feature=6.0, pattern_scale=4.0, lo=0.16, hi=0.18,
                    extra={"lichen": ["#8a9bb8", "#a9b8d0"]}, params={"cover": 0.1, "b_scale": 1.4}),
        "belly": Mat(["#9cacc8", "#b9c7dd", "#cfd9e8", "#e2e9f3", "#f2f6fb"], base=2, feature=6.0, lo=0.15, hi=0.15,
                     shade={"bottom": 0}),
        "fin": Mat(back, base=3, kind="veins", feature=7.0, lo=0.15, hi=0.2, shade={"top": 0}, rim=False,
                   extra={"vein": "#9cacc8"},
                   params={"origin": fin_origin, "count": 4.0, "warp": 0.4, "line_frac": 0.1}),
        "mouth": Mat(["#2a2f40", "#3a4256", "#4e5870"], base=1, feature=3.0, lo=0.1, hi=0.1),
        "garden": Mat(["#6b6a5c", "#807d6a", "#97927a", "#aea88c"], base=1, feature=3.0, lo=0.2, hi=0.2,
                      shade={"top": 0}),
    }


def _c(m, part, tag):
    return [c for c in m[part].cubes if c.tag == tag][0]


def paint(sk, m):
    sides = ["left", "right"]
    allsides = ["front", "back", "left", "right"]
    # --- countershading: the lowest rows of every flank blend into the pale belly
    for part, tag in (("body", "front"), ("body", "mid"), ("body", "rear"), ("tail_1", "t1"), ("tail_2", "t2"),
                      ("head", "skull")):
        c = _c(m, part, tag)
        sk.set(sk.mask(cube=c, face=allsides, cond=lambda t: t.fy >= t.fh - 2), "#cfd9e8")
        sk.set(sk.mask(cube=c, face=allsides, cond=lambda t: t.fy == t.fh - 3), "#b9c7dd")
        # glowing gold lateral seam along the flank (the line that "breathes" with the song)
        row = 7 if tag == "skull" else None  # on the head the seam runs below the eye
        seam = sk.mask(cube=c, face=sides, cond=lambda t, row=row: t.fy == (row if row else np.maximum(2, t.fh // 2 - 1)))
        sk.set(seam & (sk.t_fx % 9 != 4), GOLD, emit=GOLD, emit_alpha=170)  # dashed: the seam is broken by plates
        sk.set(seam & (sk.t_fx % 9 == 1), GOLD_HI, emit=GOLD_HI, emit_alpha=230)
        # gold speckles scattered over the back (constellation-like)
        spk = sk.mask(cube=c, face=["top"] + sides, cond=lambda t: t.fy < t.fh - 3) & (sk.B > np.quantile(sk.B[sk.used], 0.985))
        sk.set(spk, SPECK, emit=SPECK, emit_alpha=160)
    # --- back plates: chevron seams on the top faces (dim gold, faint glow)
    for part, tag in (("body", "front"), ("body", "rear"), ("tail_1", "t1"), ("tail_2", "t2")):
        c = _c(m, part, tag)
        w = c.size[0]
        chev = sk.mask(cube=c, face="top", cond=lambda t, w=w: (F(t.fy + np.abs(t.fx - (w - 1) / 2.0) * 0.5) % 8 == 0))
        sk.set(chev, GOLD_DIM, emit=GOLD, emit_alpha=110)
    # --- head: small calm eyes (dark, pale lid ring), mouth line, nostril pits, pale chin
    skull = _c(m, "head", "skull")
    for face in sides:
        ey = sk.mask(cube=skull, face=face, cond=lambda t: (F(t.cz) >= 7) & (F(t.cz) <= 9) & (t.fy >= 3) & (t.fy <= 5))
        sk.set(ey, "#141a26")
        sk.set(sk.mask(cube=skull, face=face, cond=lambda t: (F(t.cz) == 8) & (t.fy == 4)), "#2c3a5a")
        sk.set(sk.mask(cube=skull, face=face, cond=lambda t: (F(t.cz) == 7) & (t.fy == 3)), "#e6f4ff", emit="#e6f4ff",
               emit_alpha=200)
        sk.set(sk.mask(cube=skull, face=face, cond=lambda t: (F(t.cz) >= 6) & (F(t.cz) <= 10) & (t.fy == 2)), "#e2e9f3")
        sk.set(sk.mask(cube=skull, face=face, cond=lambda t: (F(t.cz) >= 6) & (F(t.cz) <= 10) & (t.fy == 6)), "#66779a")
        sk.set(sk.mask(cube=skull, face=face, cond=lambda t: ((F(t.cz) == 6) | (F(t.cz) == 10)) & (t.fy >= 3) & (t.fy <= 5)),
               "#9cacc8")
        # a gold seam arcing from the eye back along the head (the "smile" line of the creature)
        sk.set(sk.mask(cube=skull, face=face, cond=lambda t: (F(t.cz) >= 9) & (t.fy == t.fh - 1)), GOLD_DIM,
               emit=GOLD, emit_alpha=170)
    snout = _c(m, "head", "snout")
    sk.set(sk.mask(cube=snout, face="top", cond=lambda t: (t.fy == 1) & ((t.fx == 6) | (t.fx == 17))), "#3a4256")
    sk.set(sk.mask(cube=snout, face=["front"] + sides, cond=lambda t: t.fy == t.fh - 1), "#3a4256")
    jaw = _c(m, "jaw", "jaw")
    sk.set(sk.mask(cube=jaw, face=["front"] + sides, cond=lambda t: t.fy == 0), "#3a4256")
    # throat grooves on the belly of the jaw (baleen-whale pleats)
    sk.shift(sk.mask(cube=jaw, face="bottom", cond=lambda t: (t.fx % 3 == 1) & (t.fy > 4)), -1)
    # --- fins: alpha-cut planform (broad, swept back, rounded tip), glowing gold leading-edge seam + veins
    fin = _c(m, "left_fin", "fin")
    tip = _c(m, "left_fin_tip", "tip")

    def LE(X):  # leading edge (front, -z) relative to the fin root pivot
        t = np.clip(X / FIN_SPAN, 0, 1)
        return -10.0 + 22.0 * t ** 1.6

    def TE(X):  # trailing edge
        t = np.clip(X / FIN_SPAN, 0, 1)
        return 20.0 - 6.0 * t - 10.0 * t ** 3

    for cube, X0 in ((fin, 0.0), (tip, 34.0)):
        oz = cube.origin[2]
        sk.cut_shape(cube, lambda x, y, z, X0=X0, oz=oz: (z + oz >= LE(X0 + x) - 0.3) & (z + oz <= TE(X0 + x) + 0.3))
        edge = sk.outline_edge(cube)
        Z = sk.t_cz + oz
        X = sk.t_cx + X0
        lead = edge & (Z < (LE(X) + TE(X)) / 2)
        sk.set(lead & sk.mask(face="top"), GOLD, emit=GOLD, emit_alpha=230)
        sk.set(lead & sk.mask(face="bottom"), "#e2e9f3")
        sk.shift(edge & ~lead & sk.mask(face="top"), 1)
        # veins (baked radial lines) glow faintly gold
        vein = sk.mask(cube=cube, face="top") & sk.has_override & (sk.alpha > 0) & ~lead
        sk.set(vein, "#c9b27a", emit=GOLD_DIM, emit_alpha=120)
        # a row of gold speckles one texel inside the leading edge
        row = sk.mask(cube=cube, face="top", cond=lambda t, X0=X0, oz=oz: (np.abs((t.cz + oz) - LE(t.cx + X0) - 2.0) < 0.5)
                      & (F(t.cx + X0) % 4 == 1))
        sk.set(row & (sk.alpha > 0), GOLD_HI, emit=GOLD_HI, emit_alpha=220)
    # --- tail fluke: whale fluke outline (notched centre, swept tips) with a glowing gold trailing seam
    flk = _c(m, "tail_fluke", "fluke")

    def fluke(x, y, z):
        u = np.abs(x - 26.0) / 26.0  # 0 centre -> 1 tip
        front = 6.0 * u ** 1.4 + 2.0 * (u < 0.12)  # leading edge sweeps back towards the tips; stalk in the middle
        back = 20.0 - 6.0 * u ** 0.5 - 6.0 * (u < 0.1) * (1 - u / 0.1)  # trailing edge with a centre notch
        return (z >= front - 0.2) & (z <= back + 0.2) & (u <= 0.98)
    sk.cut_shape(flk, fluke)
    fe = sk.outline_edge(flk)
    sk.set(fe & sk.mask(face="top") & (sk.t_cz > 8), GOLD, emit=GOLD, emit_alpha=220)
    # --- back_ridge: flat dusty plateau where the garden grows; barnacle clusters around its rim
    ridge = _c(m, "back_ridge", "ridge")
    top = sk.mask(cube=ridge, face="top")
    rim = top & ((sk.t_fx <= 1) | (sk.t_fx >= sk.t_fw - 2) | (sk.t_fy <= 1) | (sk.t_fy >= sk.t_fh - 2))
    sk.set(rim, "#b9c7dd")
    barn = rim & (((sk.t_fx * 7 + sk.t_fy * 3) % 5) == 0)
    sk.set(barn, "#f2ecd8")
    sk.set(barn & (((sk.t_fx + sk.t_fy) % 2) == 0), "#e8d38a", emit=GOLD, emit_alpha=90)
    sk.set(sk.mask(cube=ridge, face=allsides, cond=lambda t: ((t.fx * 5) % 7) == 0), "#f2ecd8")
