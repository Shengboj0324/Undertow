"""QA helper: render a saved model .blend from any angle (Eevee, same look-dev as the previews).

Blender -b art/blender/<model>.blend --python tools/blender/models/view.py -- <az> <el> <out.png> [res]
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bpy  # noqa: E402

from mclib import scene as S  # noqa: E402

argv = sys.argv[sys.argv.index("--") + 1:]
az, el, out = float(argv[0]), float(argv[1]), argv[2]
res = int(argv[3]) if len(argv) > 3 else 512


class _B:
    pass


b = _B()
b.cube_objs = [(type("C", (), {"part": type("P", (), {"name": o.parent.name})()})(), o)
               for o in bpy.data.objects if o.get("mc_cube") and not o.hide_render]
cam = bpy.context.scene.camera
if cam:
    bpy.data.objects.remove(cam, do_unlink=True)
S.frame_camera(b, az=az, el=el, lens=85, margin=0.9)
sc = bpy.context.scene
sc.render.resolution_x = sc.render.resolution_y = res
S.render(out)
