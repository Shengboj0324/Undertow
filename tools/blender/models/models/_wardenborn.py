"""Wardenborn Wyrm skins (expansion spec F): the same three lineage models (models/_wyrm.py) and the SAME UV layout as
wyrm_<stage>.png, repainted in the Warden's colours - slate (#2B2635) scales with warden-gold (#C9A24A) edges, an old
silver belly, silver horns, gold spines, and the lineage's glowing eyes turned warden magenta.

Texture-only: the geometry is rebuilt from _wyrm.py and checked against the shipped wyrm_<stage>.json, but no JSON is
exported (the game renders the existing model with this skin).  Preview: art/previews/wyrm_<stage>_wardenborn.png."""
import json
import os

import numpy as np

from mclib.mat import Mat, hex2rgb
from models import _wyrm

HERE = os.path.dirname(os.path.abspath(__file__))
JSON_DIR = os.path.join(HERE, "..", "..", "..", "..", "src", "main", "resources", "assets", "worldsfall", "models", "entity")

SLATE = ["#140e1a", "#201a2a", "#2b2635", "#3e3749", "#564e64"]
SLATE_DEEP = ["#0c0812", "#16101e", "#221c2c", "#2f2840", "#3d3550"]
GOLD, GOLD_DIM, GOLD_HI = "#c9a24a", "#8a6d32", "#e8cd7a"
SILVER = ["#7d8496", "#98a0b2", "#aeb6c8", "#c8cfdc", "#e2e6ee"]
MAGENTA, MAGENTA_HI = "#ff4fd8", "#ffc8f2"

# hand-placed colours of _wyrm.paint -> wardenborn equivalents (applied to the override layer after painting)
REMAP = {
    "#e0559e": "#c0309e",   # eye
    "#ffd6ef": "#ffd6f4",   # eye catch-light
    "#7189a6": GOLD,        # lid line -> gold brow
    "#26304a": "#0c0812",   # nostrils
    "#8ccac2": "#aeb6c8",   # belly row on the flanks -> old silver
    "#e5ddcb": "#c8cfdc",   # claw tips
    "#d8b6ff": MAGENTA,     # leaf midrib
    "#d2b4f8": GOLD_HI,     # glowing spine tips
    "#b496e0": GOLD,        # spine bases
    "#a98ad6": GOLD,        # hatchling spines (no glow)
}
EMIT_REMAP = {
    "#ff8fd0": MAGENTA, "#ffd6ef": MAGENTA_HI, "#d8b6ff": MAGENTA, "#c09cf4": "#ffe7a6", "#a080d0": GOLD,
}


def build(stage):
    spec = _wyrm.build(stage)
    m = spec["model"]
    _check_uv(m, stage)
    m.name = "wyrm_%s_wardenborn" % stage  # preview / .blend names; the UV layout is unchanged
    wm = None
    from mclib.geom import part_world_matrices, xform
    wm = part_world_matrices(m)
    mats = make_mats(stage, xform(wm["left_wing"], (0, 0, 0)))
    pv = dict(spec["preview"])
    return dict(model=m, mats=mats, json=False, textures=[
        dict(path="wyrm/wyrm_%s_wardenborn" % stage, paint=lambda sk: paint(sk, m, stage), emissive=True)],
        preview=pv, emit_strength=2.4)


def _check_uv(m, stage):
    """The skin must line up with the shipped model: same parts, same cubes, same box-UV offsets."""
    p = os.path.abspath(os.path.join(JSON_DIR, "wyrm_%s.json" % stage))
    if not os.path.exists(p):
        return
    doc = json.load(open(p))
    shipped = [(pt["name"], tuple(c["uv"]), tuple(c["size"])) for pt in doc["parts"] for c in pt["cubes"]]
    ours = [(c.part.name, tuple(int(v) for v in c.uv), tuple(c.size)) for c in m.cubes()]
    if shipped != ours or tuple(doc["texture_size"]) != tuple(m.tex_size):
        raise RuntimeError("wyrm_%s UV layout differs from the shipped JSON - wardenborn skin would not line up" % stage)


def make_mats(stage, wing_origin):
    return {
        "scales": Mat(SLATE, base=2, kind="scales", feature=3.0, pattern_scale=2.4, lo=0.12, hi=0.2,
                      params={"line_frac": 0.14}),
        "scales_deep": Mat(SLATE_DEEP if stage == "drake" else SLATE, base=2, kind="scales", feature=3.0,
                           pattern_scale=2.4, lo=0.12, hi=0.14, params={"line_frac": 0.14}),
        "belly": Mat(SILVER[:4], base=2, feature=3.0, lo=0.12, hi=0.1, shade={"bottom": 0, "top": 0}),
        "horn": Mat(["#6e7488", "#98a0b2", "#c8cfdc", "#eef1f6"], base=2, feature=2.0, lo=0.15, hi=0.15),
        "claw": Mat(["#3a3446", "#5c6070", "#98a0b2"], base=1, feature=2.0, lo=0.1, hi=0.1),
        "membrane": Mat(["#170f1c", "#221729", "#30213a", "#3f2c4c"], base=1, kind="veins", feature=5.0, lo=0.12,
                        hi=0.15, shade={"top": 0, "bottom": 0}, rim=False, extra={"vein": GOLD_DIM},
                        params={"origin": wing_origin, "count": 3.2 if stage != "hatchling" else 2.0, "warp": 0.35,
                                "line_frac": 0.12}),
        "spine": Mat(["#8a6d32", "#a8873c", "#c9a24a", "#e8cd7a"], base=2, feature=2.0, lo=0.1, hi=0.1),
        "leaf": Mat(["#6e7488", "#98a0b2", "#c8cfdc", "#e2e6ee"], base=1, feature=3.0, lo=0.1, hi=0.1,
                    shade={"top": 0, "bottom": 0}, rim=False),
    }


def paint(sk, m, stage):
    _wyrm.paint(sk, m, stage)  # identical shapes, cut-outs and detail placement as the base lineage
    ov = sk.override
    for src, dst in REMAP.items():
        hit = sk.has_override & np.all(ov[:, :, :3] == hex2rgb(src), axis=-1)
        ov[hit, :3] = hex2rgb(dst)
    em = sk.emit
    for src, dst in EMIT_REMAP.items():
        hit = (em[:, :, 3] > 0) & np.all(em[:, :, :3] == hex2rgb(src), axis=-1)
        em[hit, :3] = hex2rgb(dst)
    # warden-gold scale edges: a sparse staggered scallop motif (2-texel rim under every 5x3 scale), dim gold, with
    # bright gold only on lit top faces; plus a crisp gold edge where the slate flanks meet the silver belly
    sc = (sk.mask(mat="scales") | sk.mask(mat="scales_deep")) & ~sk.has_override
    fx, fy = sk.t_fx.astype(int), sk.t_fy.astype(int)
    row = fy // 3
    u = (fx + 2 * (row % 2) + 3 * (row % 3 == 2)) % 5
    rim = sc & (fy % 3 == 2) & ((u == 1) | (u == 2)) & (sk.t_fw >= 3)
    top = sk.mask(face="top")
    sk.set(rim & ~top, GOLD_DIM)
    sk.set(rim & top & (row % 2 == 0), GOLD)
    sk.set(rim & top & (row % 2 == 1), GOLD_DIM)
    bod = [c for c in m["body"].cubes if c.tag == "body"][0]
    sk.set(sk.mask(cube=bod, face=["left", "right", "front", "back"], cond=lambda t: t.fy == t.fh - 2), GOLD_DIM)
    nk = [c for c in m["neck"].cubes if c.tag == "neck"]
    sk.set(sk.mask(cube=nk, face=["left", "right"], cond=lambda t: t.fy == t.fh - 1), GOLD_DIM)
    # wing arm (drake: horn-coloured bone) gets a gold leading edge on every stage
    for p in ("left_wing", "left_wing_tip"):
        arm = [c for c in m[p].cubes if c.tag == "arm"]
        for c in arm:
            sk.set(sk.mask(cube=c, face="top"), GOLD)
    # faint magenta crack-light along the drake's spine band (the Warden's glyph light)
    if stage == "drake":
        bod = [c for c in m["body"].cubes if c.tag == "body"][0]
        crack = sk.mask(cube=bod, face="top", cond=lambda t: (np.abs(t.fx - (t.fw - 1) / 2.0) < 0.6) & (t.fy % 4 == 1))
        sk.set(crack, "#a0307e", emit=MAGENTA, emit_alpha=120)
