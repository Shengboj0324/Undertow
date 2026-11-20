"""Export the authored Blender scene to the Worldsfall JSON entity-model format (read back from the scene itself).

Parts  = empties with custom prop mc_part (order: mc_index); parent = nearest mc_part ancestor (or null).
Pivot/rotation are read from each empty's local matrix and converted to model space (+Y down, front -Z, pixels).
Cubes  = mesh children with mc_cube: origin/size from the mesh bounds (minus inflate), uv/mirror/inflate from props.
The mesh UVs are validated against the box-UV layout implied by the props.
"""
import json
import math

import bpy
from mathutils import Vector

from .scene import C3, C3I, PX
from .geom import box_polygons


def _r(x, nd=4):
    v = round(float(x), nd)
    if v == 0:
        v = 0.0
    if abs(v - round(v)) < 1e-9:
        return int(round(v))
    return v


def _mc_parent(o):
    p = o.parent
    while p is not None and not p.get("mc_part"):
        p = p.parent if not p.get("mc_model") else None
    return p


def export_model(root_name, path, tex_size):
    root = bpy.data.objects[root_name]
    parts = []

    def walk(o):
        for ch in o.children:
            if ch.get("mc_part"):
                parts.append(ch)
            walk(ch)

    walk(root)
    parts.sort(key=lambda o: o["mc_index"])
    W, H = tex_size
    out_parts = []
    for e in parts:
        mb = e.matrix_basis
        par = _mc_parent(e)
        rot_m = C3I @ mb.to_3x3() @ C3
        eul = rot_m.to_euler("XYZ")  # == vanilla rotationZYX(z, y, x)
        piv = C3I @ mb.translation / PX
        cubes = []
        kids = [c for c in e.children if c.get("mc_cube")]
        kids.sort(key=lambda o: o["mc_index"])
        for o in kids:
            inf = float(o["mc_inflate"])
            vs = [C3I @ v.co / PX for v in o.data.vertices]
            lo = Vector((min(v.x for v in vs), min(v.y for v in vs), min(v.z for v in vs)))
            hi = Vector((max(v.x for v in vs), max(v.y for v in vs), max(v.z for v in vs)))
            origin = lo + Vector((inf, inf, inf))
            size = [round(s) for s in (hi - lo - Vector((2 * inf,) * 3))]
            uv = [int(o["mc_uv"][0]), int(o["mc_uv"][1])]
            mirror = bool(o["mc_mirror"])
            # validate the mesh UV layout against the vanilla box unwrap
            exp = sorted((round(u, 3), round(v, 3)) for _, poly in box_polygons(tuple(origin), size, uv, inf, mirror)
                         for (_, _, _, u, v) in poly)
            uvl = o.data.uv_layers["box_uv"]
            got = sorted((round(d.uv[0] * W, 3), round((1 - d.uv[1]) * H, 3)) for d in uvl.data)
            if exp != got:
                raise RuntimeError("UV layout of %s does not match its box-UV props" % o.name)
            cubes.append({"origin": [_r(c) for c in origin], "size": size, "uv": uv,
                          "inflate": _r(inf), "mirror": mirror})
        out_parts.append({
            "name": e.name,
            "parent": par.name if par is not None else None,
            "pivot": [_r(c) for c in piv],
            "rotation": [_r(math.degrees(a), 3) for a in eul],
            "cubes": cubes,
        })
    doc = {"texture_size": [W, H], "parts": out_parts}
    with open(path, "w") as f:
        f.write(dumps(doc))
    return doc


def dumps(doc):
    """Readable JSON: one line per cube."""
    lines = ["{", '  "texture_size": %s,' % json.dumps(doc["texture_size"]), '  "parts": [']
    for i, p in enumerate(doc["parts"]):
        lines.append("    {")
        lines.append('      "name": %s, "parent": %s,' % (json.dumps(p["name"]), json.dumps(p["parent"])))
        lines.append('      "pivot": %s, "rotation": %s,' % (json.dumps(p["pivot"]), json.dumps(p["rotation"])))
        if p["cubes"]:
            lines.append('      "cubes": [')
            for j, c in enumerate(p["cubes"]):
                lines.append("        " + json.dumps(c) + ("," if j < len(p["cubes"]) - 1 else ""))
            lines.append("      ]")
        else:
            lines.append('      "cubes": []')
        lines.append("    }" + ("," if i < len(doc["parts"]) - 1 else ""))
    lines.append("  ]")
    lines.append("}")
    return "\n".join(lines) + "\n"
