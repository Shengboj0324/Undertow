"""Pixel-art icons for the expansion that are too small for Blender: Void Chart map icons (8x8), the Sinking effect
icon (18x18) and spawn eggs recoloured from vanilla. Deterministic; run from gen_assets via expansion.assets()."""
import os
from PIL import Image
from common import ASSETS, VANILLA

PAL = {"K": (26, 16, 32), "P": (176, 106, 208), "M": (227, 79, 214), "T": (63, 201, 192), "Y": (232, 224, 168),
       "W": (244, 236, 255), "G": (216, 176, 64), "S": (200, 208, 224), "B": (90, 45, 120)}

ICONS = {
    "end_city": ["...KK...", "..KPPK..", "..KPPK..", ".KPMMPK.", "..KPPK..", "..KPPK..", ".KPPPPK.", "KKKKKKKK"],
    "fallen_spire": ["......KM", ".....KPK", "....KPK.", "...KPK..", "..KPK...", ".KPPK...", "KYYYYK..", "KKKKKK.."],
    "hoard": ["........", "..KKKK..", ".KGGGGK.", "KBBBBBBK", "KBGGGGBK", "KBBBBBBK", "KBBBBBBK", ".KKKKKK."],
    "stillwater_basin": ["........", "..KKKK..", ".KTTTTK.", "KTWTTTTK", "KTTTTWTK", ".KTTTTK.", "..KKKK..", "........"],
    "driftwreck": ["........", "...K....", "..KPK...", ".KPPPK..", "KYYYYYYK", ".KYYYYK.", "..KKKK..", "........"],
    "skate_roost": ["........", "...KK...", ".KKMMKK.", "KMMMMMMK", ".KKMMKK.", "...KK...", "...K....", "........"],
    "tide_maw": [".KKKKKK.", "KTTTTTTK", "KTKKKKTK", "KTKWWKTK", "KTKWWKTK", "KTKKKKTK", "KTTTTTTK", ".KKKKKK."],
    "mooring": ["...KK...", "..KSSK..", "...KK...", "...KS...", "KK.KS.KK", "KSKKSKSK", ".KSSSSK.", "..KKKK.."],
    "gateway": ["...KK...", "..KTTK..", ".KTWWTK.", "KTWBBWTK", "KTWBBWTK", ".KTWWTK.", "..KTTK..", "...KK..."],
    "void_capsule": ["........", "..KKKK..", ".KTTWTK.", ".KTTTTK.", ".KMMMMK.", ".KMMMMK.", "..KKKK..", "........"],
    "orrery": ["...KK...", "..KGGK..", ".KG..GK.", "KG.WW.GK", "KG.WW.GK", ".KG..GK.", "..KGGK..", "...KK..."],
    "sky_current": ["........", "....K...", "....KK..", "KKKKKSK.", "KSSSSSSK", "KKKKKSK.", "....KK..", "....K..."],
}

SINKING = [
    "..................",
    "......KKKKKK......",
    "......KTTTTK......",
    "......KTWTTK......",
    "......KTTTTK......",
    "......KTTTTK......",
    "......KTTTTK......",
    "..KKKKKTTTTKKKKK..",
    "..KTTTTTTTTTTTTK..",
    "...KTTTTTTTTTTK...",
    "....KTTTTTTTTK....",
    ".....KTTTTTTK.....",
    "......KTTTTK......",
    ".......KTTK.......",
    "........KK........",
    "..................",
    "..KKKKKKKKKKKKKK..",
    "..................",
]


def grid(rows, path):
    im = Image.new("RGBA", (len(rows[0]), len(rows)), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in PAL:
                im.putpixel((x, y), PAL[ch] + (255,))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def recolour_egg(src, path, light, dark):
    im = Image.open(src).convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
            spot = lum < 0.42
            base = dark if spot else light
            k = 0.55 + 0.6 * lum
            px[x, y] = (min(255, int(base[0] * k)), min(255, int(base[1] * k)), min(255, int(base[2] * k)), a)
    im.save(path)


def build():
    for name, rows in ICONS.items():
        grid(rows, os.path.join(ASSETS, f"textures/map/decorations/{name}.png"))
    grid(SINKING, os.path.join(ASSETS, "textures/mob_effect/sinking.png"))
    egg = os.path.join(VANILLA, "assets/minecraft/textures/item/shulker_spawn_egg.png")
    if os.path.exists(egg):
        recolour_egg(egg, os.path.join(ASSETS, "textures/item/shulker_archivist_spawn_egg.png"), (224, 214, 196), (122, 74, 160))


if __name__ == "__main__":
    build()
