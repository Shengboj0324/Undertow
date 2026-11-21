"""Warden shield pattern (expansion spec F): textures/entity/shield/warden.png - a 64x64 pattern mask in the VANILLA
shield pattern format (near-white on transparent, tinted by the dye), drawn inside the shield face's pattern area
(2,2)-(11,21).  Same wing-over-pillar motif as models/warden_banner.py, re-drawn by hand at shield resolution.
Preview: the vanilla shield geometry with the mask tinted warden gold over a dark board (composite in the temp dir)."""
import os

import numpy as np

from mclib.geom import Model
from mclib.mat import Mat
from models.warden_banner import tint_over, TMP


# hand-placed at shield resolution (10x20 pattern area): B wing bones, m membrane (alpha 191), P pillar
SHIELD_ART = [
    "..........",
    "......B...",
    ".....BB...",
    "....BmBmB.",
    "...BmmBmmB",
    "...BmmBmB.",
    ".PPPP.BmmB",
    ".PPPP.Bm..",
    "..PP..mB..",
    "..PP...B..",
    "..PP......",
    "..PP......",
    "..PP......",
    "..PP......",
    "..PP......",
    "..PP......",
    "..PP......",
    ".PPPP.....",
    "PPPPPP....",
    "..........",
]


def shield_mask():
    from models.warden_banner import BONE, MEMB
    out = np.zeros((64, 64, 4), np.int32)
    for r, row in enumerate(SHIELD_ART):
        for c, ch in enumerate(row):
            if ch in "BP":
                out[2 + r, 2 + c] = BONE
            elif ch == "m":
                out[2 + r, 2 + c] = MEMB
    return out


def build():
    m = Model("warden_shield", (64, 64))
    plate = m.part("plate", None, (0, 12, 0))
    plate.cube((-6, -11, -2), (12, 22, 1), uv=(0, 0), mat="board")
    handle = m.part("handle", None, (0, 12, 0))
    handle.cube((-1, -3, -1), (2, 6, 6), uv=(26, 0), mat="wood")
    m.pack()
    return dict(model=m, json=False, mats={
        "board": Mat(["#141018", "#1d1824", "#272030"], base=1, feature=2.0, lo=0.2, hi=0.2, rim=False,
                     shade={"top": 0, "bottom": 0}),
        "wood": Mat(["#4a3520", "#6b5030", "#8a6a40"], base=1, feature=2.0, lo=0.15, hi=0.15),
    }, textures=[
        dict(path=os.path.join(TMP, "warden_shield_preview"), paint=lambda sk: tint_over(sk, shield_mask(), (0xC9, 0xA2, 0x4A))),
        dict(path="shield/warden", paint=paint_mask),
    ], preview=dict(az=-20, el=8, lens=85, margin=0.7))


def paint_mask(sk):
    mk = shield_mask()
    sk.used = np.ones_like(sk.used)  # the mask is not a skin: write every texel as drawn
    sk.override[:] = mk
    sk.has_override[:] = True
    sk.alpha[:] = mk[:, :, 3]
