"""Blender entry point for one model:  Blender -b --factory-startup --python run_model.py -- <model> [--no-render]

Builds the cuboid scene from models/<model>.py, bakes the procedural materials with Cycles, paints the pixel skins
(numpy post-process), writes textures + JSON, renders the Eevee beauty preview and saves art/blender/<model>.blend.
"""
import importlib
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", "worldsfall")
TEX_DIR = os.path.join(ASSETS, "textures", "entity")
JSON_DIR = os.path.join(ASSETS, "models", "entity")
BLEND_DIR = os.path.join(REPO, "art", "blender")
PREVIEW_DIR = os.path.join(REPO, "art", "previews")
import tempfile  # noqa: E402
TMP = os.environ.get("WF_MODEL_TMP", os.path.join(tempfile.gettempdir(), "worldsfall_model_tmp"))

import bpy  # noqa: E402
import numpy as np  # noqa: E402

from mclib import scene as S  # noqa: E402
from mclib.skin import Skin  # noqa: E402
from mclib.png import write_png, upscale  # noqa: E402
from mclib.export import export_model  # noqa: E402


def log(*a):
    print("[%s]" % MODEL, *a, flush=True)


def main(name, render=True):
    t0 = time.time()
    mod = importlib.import_module("models." + name)
    spec = mod.build()
    model = spec["model"]
    mats = spec["mats"]
    os.makedirs(TMP, exist_ok=True)
    built = S.build_scene(model, mats)
    bake = S.bake_channels(built, ss=spec.get("supersample", 4))
    log("baked procedural channels in %.1fs" % (time.time() - t0))
    tex_paths = []
    for tex in spec["textures"]:
        sk = Skin(model, tex.get("mats") or mats, bake)
        tex["paint"](sk)
        rgba, em = sk.finish()
        png = os.path.join(TEX_DIR, tex["path"] + ".png")
        os.makedirs(os.path.dirname(png), exist_ok=True)
        write_png(png, rgba)
        em_png = None
        if tex.get("emissive", False):
            em_png = os.path.join(TEX_DIR, tex["path"] + "_emissive.png")
            write_png(em_png, em)
        tex_paths.append((png, em_png))
        base = os.path.basename(tex["path"])
        write_png(os.path.join(TMP, "skin_%s_x8.png" % base), upscale(_on_grey(rgba), 8))
        if em_png:
            write_png(os.path.join(TMP, "skin_%s_emissive_x8.png" % base), upscale(_on_grey(em), 8))
        log("texture %s  colours=%d  emissive texels=%d" % (tex["path"], sk.palette_count(rgba), int((em[:, :, 3] > 0).sum())))
    if spec.get("json", True):
        os.makedirs(JSON_DIR, exist_ok=True)
        jp = os.path.join(JSON_DIR, model.name + ".json")
        export_model(built.root.name, jp, model.tex_size)
        log("exported", jp)
    pv = spec.get("preview", {})
    skin_mat = S.make_skin_material("skin.%s" % model.name, tex_paths[0][0], tex_paths[0][1],
                                    blend=spec.get("blend", False), emit_strength=spec.get("emit_strength", 5.0))
    S.assign_material(built, skin_mat)
    hidden = set(pv.get("hidden", ()))
    for c, o in built.cube_objs:
        o.hide_render = c.part.name in hidden
        o.hide_viewport = c.part.name in hidden
    for pname, rot in pv.get("pose", {}).items():  # preview-only pose tweaks (not exported: export ran above)
        e = built.parts[pname]
        p = model[pname]
        from mathutils import Euler
        import math
        q = type(p)(p.name, p.parent, p.pivot, tuple(a + b for a, b in zip(p.rotation, rot)))
        e.matrix_basis = S.part_basis(q)
    scene = bpy.context.scene
    S.setup_render(scene, res=pv.get("res", 1024), samples=pv.get("samples", 48))
    S.frame_camera(built, az=pv.get("az", 35), el=pv.get("el", 22), lens=pv.get("lens", 70), margin=pv.get("margin", 0.84),
                   hidden=hidden)
    if render:
        os.makedirs(PREVIEW_DIR, exist_ok=True)
        S.render(os.path.join(PREVIEW_DIR, model.name + ".png"))
        S.render_silhouette(os.path.join(TMP, "sil_%s.png" % model.name))
        log("rendered preview")
        for ex in pv.get("extra", []):
            img_i = ex.get("texture", 0)
            skin = skin_mat.node_tree.nodes["skin"]
            skin.image = S.load_image(tex_paths[img_i][0])
            if tex_paths[img_i][1] and "emissive" in skin_mat.node_tree.nodes:
                skin_mat.node_tree.nodes["emissive"].image = S.load_image(tex_paths[img_i][1])
            show = set(ex.get("show", ()))
            for c, o in built.cube_objs:
                o.hide_render = (c.part.name in hidden) and (c.part.name not in show)
            scene.render.resolution_x = scene.render.resolution_y = ex.get("res", 640)
            S.render(os.path.join(PREVIEW_DIR, model.name + ex["suffix"] + ".png"))
            log("rendered extra preview", ex["suffix"])
        # restore the main look for the saved scene
        skin_mat.node_tree.nodes["skin"].image = S.load_image(tex_paths[0][0])
        if tex_paths[0][1] and "emissive" in skin_mat.node_tree.nodes:
            skin_mat.node_tree.nodes["emissive"].image = S.load_image(tex_paths[0][1])
        for c, o in built.cube_objs:
            o.hide_render = c.part.name in hidden
        scene.render.resolution_x = scene.render.resolution_y = pv.get("res", 1024)
    # keep the rest pose in the saved authoring file
    for p in model.parts:
        built.parts[p.name].matrix_basis = S.part_basis(p)
    S.save_blend(os.path.join(BLEND_DIR, model.name + ".blend"))
    log("done in %.1fs" % (time.time() - t0))


def _on_grey(rgba):
    """Composite on a checker so transparent texels are visible in review copies."""
    h, w, _ = rgba.shape
    yy, xx = np.mgrid[0:h, 0:w]
    bg = np.where(((xx // 4 + yy // 4) % 2)[..., None] == 0, 40, 56).astype(np.float64)
    bg = np.broadcast_to(bg, (h, w, 3))
    a = rgba[:, :, 3:4] / 255.0
    rgb = rgba[:, :, :3] * a + bg * (1 - a)
    return np.dstack([rgb, np.full((h, w), 255)]).astype(np.uint8)


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    MODEL = argv[0]
    main(MODEL, render="--no-render" not in argv)
