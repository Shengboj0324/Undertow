#!/usr/bin/env python3
"""Review sheets for the expansion entity art (art/SPEC_EXPANSION.md E, F, G):

    python3 tools/blender/models/expansion_sheet.py

  art/previews/expansion_entities.png  the eight new models (beauty preview + silhouette at 32 px when the build temp
                                       dir $WF_MODEL_TMP still holds sil_<name>.png)
  art/previews/expansion_variants.png  Wyrm lineage vs Wardenborn, drake gear, scaled/tempest elytra, Shulker
                                       Archivist, Warden banner + shield, and the raw F textures (nearest-neighbour)
Reads the previews/textures written by build_all.py; needs numpy + Pillow."""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
PV = os.path.join(REPO, "art", "previews")
TEX = os.path.join(REPO, "src", "main", "resources", "assets", "worldsfall", "textures", "entity")
BG = (10, 6, 20)
ENTITIES = ["lumen_heart", "gale_harrier", "leviathan_drifter", "tide_eel", "drowned_ascendant", "glimmer",
            "lantern_moth", "void_bell"]
VARIANT_ROWS = [
    ["wyrm_hatchling", "wyrm_hatchling_wardenborn", "wyrm_fledgling", "wyrm_fledgling_wardenborn"],
    ["wyrm_drake", "wyrm_drake_wardenborn", "wyrm_drake_gear_barding", "wyrm_drake_gear_saddlebags"],
    ["elytra_scaled", "elytra_scaled_gale", "elytra_scaled_still", "elytra_scaled_hover"],
    ["elytra_tempest", "elytra_tempest_scaled", "shulker_archivist", "warden_banner"],
    ["warden_shield"],
]
RAW = ["wyrm/wyrm_drake_barding.png", "wyrm/wyrm_drake_saddlebags.png", "equipment/wings/elytra_scaled.png",
       "equipment/wings/elytra_tempest.png", "shulker_archivist.png", "banner/warden.png", "shield/warden.png"]


def fonts():
    try:
        return (ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 18),
                ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 12))
    except Exception:
        f = ImageFont.load_default()
        return f, f


def silhouette(name):
    tmp = os.environ.get("WF_MODEL_TMP")
    p = os.path.join(tmp, "sil_%s.png" % name) if tmp else None
    if not p or not os.path.exists(p):
        return None
    a = np.array(Image.open(p).convert("RGBA"))[:, :, 3]
    ys, xs = np.nonzero(a > 20)
    if not len(ys):
        return None
    crop = Image.fromarray(a[ys.min():ys.max() + 1, xs.min():xs.max() + 1])
    s = 32.0 / max(crop.size)
    small = crop.resize((max(1, round(crop.size[0] * s)), max(1, round(crop.size[1] * s))), Image.BOX)
    img = Image.fromarray(np.where(np.array(small) > 90, 0, 235).astype(np.uint8)).convert("RGB")
    box = Image.new("RGB", (36, 36), (235, 235, 235))
    box.paste(img, ((36 - img.size[0]) // 2, (36 - img.size[1]) // 2))
    return box.resize((72, 72), Image.NEAREST)


def grid(rows, out, tile=384, label_h=56, footer=None):
    font, small = fonts()
    cols = max(len(r) for r in rows)
    extra = footer.size[1] + 20 if footer is not None else 0
    sheet = Image.new("RGB", (cols * tile, len(rows) * (tile + label_h) + extra), BG)
    dr = ImageDraw.Draw(sheet)
    for ri, row in enumerate(rows):
        for ci, n in enumerate(row):
            x, y = ci * tile, ri * (tile + label_h)
            p = os.path.join(PV, n + ".png")
            if os.path.exists(p):
                sheet.paste(Image.open(p).convert("RGB").resize((tile, tile), Image.LANCZOS), (x, y))
            dr.text((x + 10, y + tile + 8), n, fill=(230, 220, 255), font=font)
            sil = silhouette(n)
            if sil is not None:
                dr.text((x + 10, y + tile + 32), "silhouette @32px", fill=(150, 140, 180), font=small)
                sheet.paste(sil.resize((48, 48), Image.NEAREST), (x + tile - 56, y + tile + 4))
    if footer is not None:
        sheet.paste(footer, (10, len(rows) * (tile + label_h) + 10))
    sheet.save(out)
    print("  ->", out)


def raw_strip():
    font, small = fonts()
    ims = []
    for rel in RAW:
        p = os.path.join(TEX, rel)
        if not os.path.exists(p):
            continue
        im = Image.open(p).convert("RGBA")
        k = max(2, 192 // max(im.size))
        im = im.resize((im.size[0] * k, im.size[1] * k), Image.NEAREST)
        yy, xx = np.mgrid[0:im.size[1], 0:im.size[0]]
        chk = np.where(((xx // 8) + (yy // 8)) % 2 == 0, 40, 56).astype(np.uint8)  # checker = transparency
        bg = Image.fromarray(np.stack([chk, chk, chk, np.full_like(chk, 255)], -1))
        bg.alpha_composite(im)
        ims.append((rel, bg.convert("RGB")))
    w = sum(max(i.size[0], 7 * len(r)) + 16 for r, i in ims)
    h = max(i.size[1] for _, i in ims) + 30
    strip = Image.new("RGB", (max(w, 1), h), BG)
    dr = ImageDraw.Draw(strip)
    x = 0
    for rel, im in ims:
        strip.paste(im, (x, 24))
        dr.text((x, 4), rel, fill=(200, 190, 230), font=small)
        x += max(im.size[0], 7 * len(rel)) + 16
    return strip


def main():
    grid([ENTITIES[:4], ENTITIES[4:]], os.path.join(PV, "expansion_entities.png"))
    grid(VARIANT_ROWS, os.path.join(PV, "expansion_variants.png"), footer=raw_strip())


if __name__ == "__main__":
    main()
