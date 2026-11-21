"""Drake gear overlays (expansion spec F): wyrm/wyrm_drake_barding.png and wyrm/wyrm_drake_saddlebags.png.

Both use the exact UV of wyrm_drake.png (geometry rebuilt from models/_wyrm.py and checked against the shipped JSON)
and are transparent everywhere except the gear, so the game can draw them as extra layers over any drake skin.
  * barding    - overlapping slate scale-plates rimmed in warden gold over the back, chest and neck;
  * saddlebags - two leather bags with purpur shulker-shell clasps hung on the flanks behind the saddle, joined by a
                 strap over the back.
Previews composite the overlays on the standard drake skin (art/previews/wyrm_drake_gear*.png); the composites are
written to the build temp folder only."""
import os
import tempfile

import numpy as np

from mclib.png import read_png
from mclib.skin import Skin
from models import _wyrm, _wardenborn

F = np.floor
HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.abspath(os.path.join(HERE, "..", "..", "..", "..", "src", "main", "resources", "assets", "worldsfall",
                                   "textures", "entity"))
TMP = os.environ.get("WF_MODEL_TMP", os.path.join(tempfile.gettempdir(), "worldsfall_model_tmp"))

PLATE = ["#1d1726", "#2b2635", "#3a3446", "#4b4558"]
GOLD, GOLD_DIM, GOLD_HI, SILVER = "#c9a24a", "#8a6d32", "#e8cd7a", "#aeb6c8"
LEATHER = ["#2e1a10", "#4a2e1a", "#6a4226", "#8a5a34", "#a87444"]
SHELL, SHELL_HI, SHELL_DK = "#a985b0", "#e2cdea", "#6b4e74"


def build():
    spec = _wyrm.build("drake")
    m = spec["model"]
    _wardenborn._check_uv(m, "drake")
    m.name = "wyrm_drake_gear"
    mats = spec["mats"]
    base = read_png(os.path.join(TEX, "wyrm", "wyrm_drake.png"))
    base_em = read_png(os.path.join(TEX, "wyrm", "wyrm_drake_emissive.png"))

    def overlay(sk, which):
        s2 = Skin(m, mats, dict(R=sk.R, G=sk.G, Gmin=sk.Gmin, B=sk.B))
        (paint_barding if which == "barding" else paint_saddlebags)(s2, m)
        return s2.finish()[0]

    def composite(sk, which):
        out = base.astype(np.float64)
        em = base_em.copy()
        for w in which:
            ov = overlay(sk, w).astype(np.float64)
            a = ov[:, :, 3:4] / 255.0
            out[:, :, :3] = ov[:, :, :3] * a + out[:, :, :3] * (1 - a)
            em[ov[:, :, 3] > 0] = 0
        sk.override[:] = np.round(out).astype(np.int32)
        sk.has_override[:] = True
        sk.alpha[:] = base[:, :, 3]
        sk.emit[:] = em

    tmp = lambda n: os.path.join(TMP, n)  # noqa: E731  (absolute path: preview-only composites stay out of the assets)
    return dict(model=m, mats=mats, json=False, textures=[
        dict(path=tmp("gear_preview_all"), paint=lambda sk: composite(sk, ("barding", "saddlebags")), emissive=True),
        dict(path="wyrm/wyrm_drake_barding", paint=lambda sk: paint_barding(sk, m)),
        dict(path="wyrm/wyrm_drake_saddlebags", paint=lambda sk: paint_saddlebags(sk, m)),
        dict(path=tmp("gear_preview_barding"), paint=lambda sk: composite(sk, ("barding",)), emissive=True),
        dict(path=tmp("gear_preview_saddlebags"), paint=lambda sk: composite(sk, ("saddlebags",)), emissive=True),
    ], preview=dict(spec["preview"], az=-62, el=22, margin=0.84,
                    pose={"left_wing": (0, 0, -38), "left_wing_tip": (0, 0, -10), "right_wing": (0, 0, 38),
                          "right_wing_tip": (0, 0, 10)},
                    extra=[dict(suffix="_barding", texture=3, res=1024),
                                            dict(suffix="_saddlebags", texture=4, res=1024)]),
        emit_strength=2.4)


def _clear(sk):
    sk.alpha[:] = 0
    sk.emit[:] = 0


def _put(sk, mask, color):
    sk.set(mask, color, alpha=255)


def _plates(sk, mask, along, across, period=3):
    """Staggered overlapping scale-plates: rows run along `along` (towards the tail / downwards), plates are `period`
    texels wide across `across`, every other row offset by half a plate.  Each plate: lit leading row, slate body
    (textured by the baked fBm tone), gold rear rim, a dark gap to its neighbour."""
    if not mask.any():
        return
    row = F(along / period)
    ra = np.mod(along, period)
    ca = np.mod(across + (period / 2.0) * (row % 2), period)
    tone = sk.R
    q1, q2 = np.quantile(tone[mask], [0.3, 0.75])
    body = np.where(tone < q1, PLATE[0], np.where(tone > q2, PLATE[2], PLATE[1]))
    for col in set(body[mask].tolist()):
        _put(sk, mask & (body == col), col)
    _put(sk, mask & (ra < 1.0), PLATE[3])
    _put(sk, mask & (ca < 1.0) & (ra >= 1.0), "#120c18")
    rim = mask & (ra >= period - 1.0)
    _put(sk, rim, GOLD_DIM)
    _put(sk, rim & sk.mask(face="top"), GOLD)
    _put(sk, rim & (ca >= 1.0) & (ca < 2.0) & sk.mask(face="top"), GOLD_HI)


def paint_barding(sk, m):
    _clear(sk)
    sides = ["left", "right"]
    body = [c for c in m["body"].cubes if c.tag == "body"][0]
    chest = [c for c in m["body"].cubes if c.tag == "chest"][0]
    # back: whole top face + the upper five rows of the flanks, plate rows running nose -> tail
    back = sk.mask(cube=body, face="top") | sk.mask(cube=body, face=sides, cond=lambda t: t.fy <= 4)
    top_f = sk.t_face == 0  # top faces: plate rows run nose -> tail; flanks: rows hang downwards like a skirt
    _plates(sk, back, np.where(top_f, sk.t_cz, sk.t_cy), np.where(top_f, sk.t_cx, sk.t_cz))
    # scalloped gold hem along the lower edge of the flank armour, silver rivets above it
    hem = sk.mask(cube=body, face=sides, cond=lambda t: t.fy == 5)
    _put(sk, hem & (F(sk.t_cz) % 3 != 2), GOLD_DIM)
    _put(sk, sk.mask(cube=body, face=sides, cond=lambda t: (t.fy == 4) & (F(t.cz) % 3 == 1)), SILVER)
    # spine channel: the crest spines rise through a dark gold-edged slot down the middle of the back
    w = body.size[0]
    ch = sk.mask(cube=body, face="top", cond=lambda t: np.abs(t.fx - (w - 1) / 2.0) < 1.1)
    _put(sk, ch, "#120c18")
    _put(sk, sk.mask(cube=body, face="top", cond=lambda t: (np.abs(t.fx - (w - 1) / 2.0) >= 1.1) &
                     (np.abs(t.fx - (w - 1) / 2.0) < 2.1)), GOLD)
    # rump guard: upper part of the back face
    _plates(sk, sk.mask(cube=body, face="back", cond=lambda t: t.fy <= 4), sk.t_cy, sk.t_cx)
    # chest: breastplate over the chest cube and the upper body front, horizontal plate rows top -> bottom
    cp = sk.mask(cube=chest, face=["front", "top"] + sides) | sk.mask(cube=body, face="front", cond=lambda t: t.fy <= 6)
    _plates(sk, cp, np.where(sk.t_face == 0, sk.t_cz, sk.t_cy), np.where(sk.t_face == 3, sk.t_cx, sk.t_cz))
    cw = chest.size[0]
    _put(sk, sk.mask(cube=chest, face="front", cond=lambda t: (np.abs(t.fx - (cw - 1) / 2.0) < 0.6)), GOLD)
    _put(sk, sk.mask(cube=chest, face="front", cond=lambda t: (np.abs(t.fx - (cw - 1) / 2.0) < 0.6) & (t.fy == 2)),
         GOLD_HI)
    # neck: plates over the top and the upper flanks of both neck cubes, rows running along the neck
    for c in [c for c in m["neck"].cubes if c.tag == "neck"]:
        nm = sk.mask(cube=c, face="top") | sk.mask(cube=c, face=sides, cond=lambda t: t.fy < t.fh - 1)
        top_f = sk.t_face == 0
        _plates(sk, nm, np.where(top_f, sk.t_cz, sk.t_cy), np.where(top_f, sk.t_cx, sk.t_cz))
        _put(sk, sk.mask(cube=c, face=sides, cond=lambda t: (t.fy == t.fh - 1)), GOLD_DIM)


def paint_saddlebags(sk, m):
    _clear(sk)
    body = [c for c in m["body"].cubes if c.tag == "body"][0]
    d, h = body.size[2], body.size[1]
    z0, z1 = 8, 14  # behind the saddle (rider sits over the shoulders), clear of the hind legs
    y0, y1 = 2, 8
    tone = sk.R
    for face in ("left", "right"):
        bag = sk.mask(cube=body, face=face, cond=lambda t: (F(t.cz) >= z0) & (F(t.cz) <= z1) & (t.fy >= y0) & (t.fy <= y1))
        # rounded bottom corners
        corner = bag & (sk.t_fy == y1) & ((F(sk.t_cz) == z0) | (F(sk.t_cz) == z1))
        bag &= ~corner
        if not bag.any():
            continue
        q1, q2 = np.quantile(tone[bag], [0.3, 0.75])
        _put(sk, bag, LEATHER[2])
        _put(sk, bag & (tone < q1), LEATHER[1])
        _put(sk, bag & (tone > q2), LEATHER[3])
        # outline: darker seams round the bag, lighter top edge (light from above)
        edge = bag & ((F(sk.t_cz) == z0) | (F(sk.t_cz) == z1) | (sk.t_fy == y1) |
                      ((sk.t_fy == y1 - 1) & ((F(sk.t_cz) == z0 + 1) | (F(sk.t_cz) == z1 - 1))))
        _put(sk, edge, LEATHER[0])
        # flap: the top three rows, a shade lighter, stitched lower edge
        flap = bag & (sk.t_fy <= y0 + 2) & ~edge
        _put(sk, flap, LEATHER[3])
        _put(sk, bag & (sk.t_fy == y0), LEATHER[4])
        _put(sk, bag & (sk.t_fy == y0 + 3) & (F(sk.t_cz) % 2 == 0) & ~edge, LEATHER[1])
        # purpur shulker-shell clasp in the middle of the flap
        zc = (z0 + z1) // 2
        clasp = bag & (sk.t_fy >= y0 + 2) & (sk.t_fy <= y0 + 4) & (F(sk.t_cz) >= zc) & (F(sk.t_cz) <= zc + 1)
        _put(sk, clasp, SHELL)
        _put(sk, clasp & (sk.t_fy == y0 + 2), SHELL_HI)
        _put(sk, clasp & (sk.t_fy == y0 + 4), SHELL_DK)
        # buckle strap from the bag up over the back
        strap = sk.mask(cube=body, face=face, cond=lambda t: (F(t.cz) == z0 + 2) & (t.fy < y0))
        _put(sk, strap, LEATHER[1])
    # the strap that joins the bags across the back, with a brass ring at each side
    top = sk.mask(cube=body, face="top", cond=lambda t: F(t.cz) == z0 + 2)
    _put(sk, top, LEATHER[1])
    _put(sk, top & ((sk.t_fx == 0) | (sk.t_fx == sk.t_fw - 1)), "#e0b040")
