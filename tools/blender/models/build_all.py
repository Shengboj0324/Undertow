#!/usr/bin/env python3
"""Regenerate every Worldsfall entity model, skin, preview and the contact sheet, then validate.

    python3 tools/blender/models/build_all.py [model ...] [--no-render] [--keep-tmp]

Each model runs headless in Blender (tools/blender/models/run_model.py): build cuboid scene -> Cycles bake of the
procedural materials -> numpy pixel post-process -> PNG skins + emissive maps -> JSON export -> Eevee preview ->
art/blender/<model>.blend.  Requires Blender 5.x (override with $BLENDER) and numpy/Pillow for the contact sheet.
"""
import os
import shutil
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
BLENDER = os.environ.get("BLENDER", "/Applications/Blender.app/Contents/MacOS/Blender")
PREVIEWS = os.path.join(REPO, "art", "previews")

MODELS = ["void_skate", "chorus_grazer", "underside_lurker", "hoarder", "spire_warden", "echo",
          "wyrm_hatchling", "wyrm_fledgling", "wyrm_drake", "shardling", "unmade_shard", "void_capsule",
          "rift_hook", "tether_pylon", "wild_shulker", "elytra_upgrades",
          # expansion (art/SPEC_EXPANSION.md E + F)
          "lumen_heart", "gale_harrier", "leviathan_drifter", "tide_eel", "drowned_ascendant", "glimmer",
          "lantern_moth", "void_bell", "wyrm_hatchling_wardenborn", "wyrm_fledgling_wardenborn",
          "wyrm_drake_wardenborn", "wyrm_drake_gear", "elytra_scaled", "elytra_tempest", "shulker_archivist",
          "warden_banner", "warden_shield"]


def run_model(name, tmp, render=True):
    env = dict(os.environ, WF_MODEL_TMP=tmp)
    cmd = [BLENDER, "-b", "--factory-startup", "--python", os.path.join(HERE, "run_model.py"), "--", name]
    if not render:
        cmd.append("--no-render")
    t0 = time.time()
    p = subprocess.run(cmd, env=env, capture_output=True, text=True)
    out = p.stdout + p.stderr
    for line in out.splitlines():
        if line.startswith("[%s]" % name) or "Error" in line or "Traceback" in line:
            print("   ", line)
    if p.returncode != 0 or "Traceback" in out:
        print(out[-4000:])
        raise SystemExit("model %s failed" % name)
    print("  %-18s ok (%.1fs)" % (name, time.time() - t0))


def contact_sheet(tmp, names):
    from PIL import Image, ImageDraw, ImageFont
    import numpy as np
    tile, cols = 384, 4
    label_h = 84
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * tile, rows * (tile + label_h) + 220), (10, 6, 20))
    dr = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 18)
        small = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 12)
    except Exception:
        font = small = ImageFont.load_default()
    for i, n in enumerate(names):
        x, y = (i % cols) * tile, (i // cols) * (tile + label_h)
        p = os.path.join(PREVIEWS, n + ".png")
        if os.path.exists(p):
            sheet.paste(Image.open(p).convert("RGB").resize((tile, tile), Image.LANCZOS), (x, y))
        dr.text((x + 10, y + tile + 8), n, fill=(230, 220, 255), font=font)
        dr.text((x + 10, y + tile + 34), "silhouette @32px", fill=(150, 140, 180), font=small)
        sp = os.path.join(tmp, "sil_%s.png" % n)
        if os.path.exists(sp):
            a = np.array(Image.open(sp).convert("RGBA"))[:, :, 3]
            ys, xs = np.nonzero(a > 20)
            if len(ys):
                crop = Image.fromarray(a[ys.min():ys.max() + 1, xs.min():xs.max() + 1])
                s = 32.0 / max(crop.size)
                small_sil = crop.resize((max(1, round(crop.size[0] * s)), max(1, round(crop.size[1] * s))), Image.BOX)
                b = (np.array(small_sil) > 90)
                img = Image.fromarray(np.where(b, 0, 235).astype(np.uint8)).convert("RGB")
                box = Image.new("RGB", (36, 36), (235, 235, 235))
                box.paste(img, ((36 - img.size[0]) // 2, (36 - img.size[1]) // 2))
                sheet.paste(box.resize((72, 72), Image.NEAREST), (x + tile - 82, y + tile + 6))
    # sprite row (texture-only assets), upscaled with nearest neighbour
    tex = os.path.join(REPO, "src", "main", "resources", "assets", "worldsfall", "textures", "entity")
    y0 = rows * (tile + label_h) + 10
    dr.text((10, y0), "sprites / texture-only", fill=(230, 220, 255), font=font)
    xx = 10
    for rel, k in (("tidewisp/tidewisp.png", 5), ("the_unmade/core.png", 2), ("the_unmade/tendril.png", 3),
                   ("equipment/wings/elytra_gale.png", 3), ("equipment/wings/elytra_still.png", 3),
                   ("equipment/wings/elytra_hover.png", 3)):
        p = os.path.join(tex, rel)
        if not os.path.exists(p):
            continue
        im = Image.open(p).convert("RGBA")
        im = im.resize((im.size[0] * k, im.size[1] * k), Image.NEAREST)
        if im.size[1] > 180:
            im = im.resize((im.size[0] * 180 // im.size[1], 180), Image.NEAREST)
        bg = Image.new("RGBA", im.size, (24, 14, 40, 255))
        bg.alpha_composite(im)
        sheet.paste(bg.convert("RGB"), (xx, y0 + 30))
        xx += im.size[0] + 16
    out = os.path.join(PREVIEWS, "models_contact_sheet.png")
    sheet.save(out)
    print("  contact sheet ->", out)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    render = "--no-render" not in sys.argv
    names = args or MODELS
    tmp = os.environ.get("WF_MODEL_TMP") or tempfile.mkdtemp(prefix="worldsfall_models_")
    os.makedirs(tmp, exist_ok=True)
    print("sprites:")
    subprocess.run([sys.executable, os.path.join(HERE, "sprites.py")], check=True)
    print("models (Blender: %s):" % BLENDER)
    for n in names:
        run_model(n, tmp, render)
    if render:
        contact_sheet(tmp, [n for n in MODELS if os.path.exists(os.path.join(PREVIEWS, n + ".png"))])
    print("verify:")
    rc = subprocess.run([sys.executable, os.path.join(HERE, "verify_models.py")]).returncode
    if "--keep-tmp" not in sys.argv and not os.environ.get("WF_MODEL_TMP"):
        shutil.rmtree(tmp, ignore_errors=True)
    sys.exit(rc)


if __name__ == "__main__":
    main()
