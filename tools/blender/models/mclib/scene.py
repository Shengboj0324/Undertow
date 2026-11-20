"""Blender side of the pipeline: the scene IS the authoring source.

* one empty per model part (bone) at its pivot, parented exactly like the JSON hierarchy;
* one mesh object per cube, child of its part empty, with the exact vanilla box-UV unwrap ("box_uv");
* procedural bake materials per creature material (fBm / voronoi / radial veins / chitin bands / facets), baked
  with Cycles (EMIT pass) to a supersampled float image, read back with numpy;
* preview material = final pixel skin (Closest filtering) + emissive map, Eevee beauty render with bloom.

Axis map  model(px, +Y down, front -Z)  ->  Blender(m, Z up, front -Y):  (x, y, z) -> (x, z, -y) / 16,
with the model origin (y = 0, i.e. 24 px above the ground) at Blender z = 1.5 so the ground is z = 0.
"""
import math
import os

import bpy
import numpy as np
from mathutils import Matrix, Vector, Euler

from .geom import box_polygons, face_rects, FACES, part_world_matrices
from .mat import hex2rgb

C3 = Matrix(((1, 0, 0), (0, 0, 1), (0, -1, 0)))
C3I = C3.inverted()
PX = 1.0 / 16.0
ROOT_Z = 24 * PX


def m2b(v):
    return C3 @ Vector(v) * PX


def b2m(v):
    return C3I @ Vector(v) / PX


def part_basis(p):
    rot = Euler([math.radians(a) for a in p.rotation], "XYZ").to_matrix()  # == Rz*Ry*Rx (vanilla rotationZYX)
    rb = C3 @ rot @ C3I
    mb = rb.to_4x4()
    mb.translation = m2b(p.pivot)
    return mb


def reset_scene():
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)
    for coll in (bpy.data.meshes, bpy.data.materials, bpy.data.images, bpy.data.cameras, bpy.data.lights,
                 bpy.data.node_groups, bpy.data.worlds):
        for d in list(coll):
            try:
                coll.remove(d)
            except Exception:
                pass
    for c in list(bpy.data.collections):
        bpy.data.collections.remove(c)


def srgb_to_lin(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def hex_lin(h, a=1.0):
    return tuple(srgb_to_lin(v) for v in hex2rgb(h)) + (a,)


# --------------------------------------------------------------------------- scene construction
class Built:
    def __init__(self, model):
        self.model = model
        self.root = None
        self.parts = {}
        self.cube_objs = []  # (cube, obj)
        self.coll = None


def build_scene(model, mats):
    reset_scene()
    scene = bpy.context.scene
    b = Built(model)
    coll = bpy.data.collections.new(model.name)
    scene.collection.children.link(coll)
    b.coll = coll
    root = bpy.data.objects.new(model.name + "_root", None)
    root.empty_display_type = "ARROWS"
    root.empty_display_size = 0.5
    root.location = (0, 0, ROOT_Z)
    root["mc_model"] = model.name
    root["mc_texture_size"] = list(model.tex_size)
    coll.objects.link(root)
    b.root = root
    # bake materials (one per creature material key)
    bake_mats = {k: make_bake_material(model.name, k, m) for k, m in mats.items()}
    keys = list(mats.keys())
    W, H = model.tex_size
    for pi, p in enumerate(model.parts):
        e = bpy.data.objects.new(p.name, None)
        e.empty_display_type = "SPHERE"
        e.empty_display_size = 0.04
        e.parent = b.parts[p.parent] if p.parent else root
        e.matrix_parent_inverse = Matrix.Identity(4)
        e.rotation_mode = "XYZ"
        e.matrix_basis = part_basis(p)
        e["mc_part"] = True
        e["mc_index"] = pi
        coll.objects.link(e)
        b.parts[p.name] = e
    for c in model.cubes():
        p = c.part
        verts, faces, uvs, mat_idx = [], [], [], []
        for region, poly in box_polygons(c.origin, c.size, c.uv, c.inflate, c.mirror):
            base = len(verts)
            for (x, y, z, u, v) in poly:
                verts.append(tuple(m2b((x, y, z))))
                uvs.append((u / W, 1.0 - v / H))
            faces.append((base, base + 1, base + 2, base + 3))
            mat_idx.append(keys.index(c.mat_for(region)))
        name = "%s.%02d" % (p.name, p.cubes.index(c))
        me = bpy.data.meshes.new(name)
        me.from_pydata(verts, [], faces)
        uvl = me.uv_layers.new(name="box_uv")
        for li, loop in enumerate(me.loops):
            uvl.data[li].uv = uvs[loop.vertex_index]
        for k in keys:
            me.materials.append(bake_mats[k])
        for poly, mi in zip(me.polygons, mat_idx):
            poly.material_index = mi
        me.update()
        o = bpy.data.objects.new(name, me)
        o.parent = b.parts[p.name]
        o.matrix_parent_inverse = Matrix.Identity(4)
        o["mc_cube"] = True
        o["mc_index"] = c.index
        o["mc_uv"] = list(c.uv)
        o["mc_inflate"] = c.inflate
        o["mc_mirror"] = c.mirror
        o["mc_shares_uv_of"] = ("%s.%02d" % (c.owner.part.name, c.owner.part.cubes.index(c.owner))) if c.owner else ""
        o["mc_mat"] = c.mat if isinstance(c.mat, str) else repr(c.mat)
        coll.objects.link(o)
        b.cube_objs.append((c, o))
    return b


# --------------------------------------------------------------------------- procedural bake materials
def _n(nt, t, loc, **kw):
    n = nt.nodes.new(t)
    n.location = loc
    for k, v in kw.items():
        setattr(n, k, v)
    return n


def make_bake_material(model_name, key, mat):
    """Procedural shader whose emission encodes R = fBm tone, G = distance to pattern lines, B = cell/secondary."""
    m = bpy.data.materials.new("bake.%s.%s" % (model_name, key))
    m.use_fake_user = True
    m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    L = nt.links
    out = _n(nt, "ShaderNodeOutputMaterial", (1200, 0))
    em = _n(nt, "ShaderNodeEmission", (1000, 0))
    L.new(em.outputs[0], out.inputs["Surface"])
    comb = _n(nt, "ShaderNodeCombineColor", (800, 0))
    L.new(comb.outputs[0], em.inputs["Color"])
    geo = _n(nt, "ShaderNodeNewGeometry", (-1000, 0))
    px = _n(nt, "ShaderNodeVectorMath", (-800, 0), operation="SCALE")
    px.inputs["Scale"].default_value = 16.0  # world metres -> model pixels
    L.new(geo.outputs["Position"], px.inputs[0])
    seed = (sum(ord(ch) for ch in model_name + key) % 97) * 3.17
    off = _n(nt, "ShaderNodeVectorMath", (-600, 0), operation="ADD")
    off.inputs[1].default_value = (seed, seed * 0.37, seed * 0.71)
    L.new(px.outputs[0], off.inputs[0])
    P = off.outputs[0]
    p = mat.params
    # R: fBm noise (clustered, chunky; feature size in pixels)
    nz = _n(nt, "ShaderNodeTexNoise", (-300, 300))
    nz.noise_dimensions = "3D"
    nz.inputs["Scale"].default_value = 1.0 / mat.feature
    nz.inputs["Detail"].default_value = p.get("detail", 2.0)
    nz.inputs["Roughness"].default_value = p.get("roughness", 0.5)
    L.new(P, nz.inputs["Vector"])
    R = nz.outputs["Fac"]
    if "grad_axis" in p:  # add a smooth gradient along a model axis (e.g. belly-to-back, base-to-tip)
        sep = _n(nt, "ShaderNodeSeparateXYZ", (-300, 500))
        L.new(px.outputs[0], sep.inputs[0])
        ax = {"x": "X", "y": "Z", "z": "Y"}[p["grad_axis"][0]]
        sgn = -1.0 if p["grad_axis"][0] == "y" else 1.0
        sgn *= p["grad_axis"][1]
        mul = _n(nt, "ShaderNodeMath", (-100, 500), operation="MULTIPLY")
        mul.inputs[1].default_value = sgn
        L.new(sep.outputs[ax], mul.inputs[0])
        add = _n(nt, "ShaderNodeMath", (100, 400), operation="ADD")
        L.new(R, add.inputs[0])
        L.new(mul.outputs[0], add.inputs[1])
        R = add.outputs[0]
    G = None
    B = None
    kind = mat.kind
    ps = mat.pattern_scale
    if kind in ("scales", "crystal", "cracks"):
        vin = P
        if kind == "cracks":  # domain-warped voronoi edges = hairline cracks
            wn = _n(nt, "ShaderNodeTexNoise", (-500, -300))
            wn.inputs["Scale"].default_value = 1.0 / (ps * 0.8)
            wn.inputs["Detail"].default_value = 3.0
            L.new(P, wn.inputs["Vector"])
            sc = _n(nt, "ShaderNodeVectorMath", (-400, -450), operation="SCALE")
            sc.inputs["Scale"].default_value = ps * 1.5
            L.new(wn.outputs["Color"], sc.inputs[0])
            ad = _n(nt, "ShaderNodeVectorMath", (-300, -450), operation="ADD")
            L.new(P, ad.inputs[0]); L.new(sc.outputs[0], ad.inputs[1])
            vin = ad.outputs[0]
        ve = _n(nt, "ShaderNodeTexVoronoi", (-100, -200))
        ve.voronoi_dimensions = "3D"
        ve.feature = "DISTANCE_TO_EDGE"
        ve.inputs["Scale"].default_value = 1.0 / ps
        L.new(vin, ve.inputs["Vector"])
        G = ve.outputs["Distance"]
        vc = _n(nt, "ShaderNodeTexVoronoi", (-100, -500))
        vc.voronoi_dimensions = "3D"
        vc.feature = "F1"
        vc.inputs["Scale"].default_value = 1.0 / ps
        L.new(vin, vc.inputs["Vector"])
        sepc = _n(nt, "ShaderNodeSeparateColor", (100, -500))
        L.new(vc.outputs["Color"], sepc.inputs[0])
        B = sepc.outputs[0]
        if kind == "cracks":
            gn = _n(nt, "ShaderNodeTexNoise", (100, -700))
            gn.inputs["Scale"].default_value = 1.0 / (ps * 2.5)
            gn.inputs["Detail"].default_value = 1.0
            L.new(P, gn.inputs["Vector"])
            B = gn.outputs["Fac"]
    elif kind == "chitin":  # wave bands across the body + noise distortion
        wv = _n(nt, "ShaderNodeTexWave", (-100, -200))
        wv.wave_type = "BANDS"
        wv.bands_direction = p.get("band_dir", "Z")
        wv.wave_profile = "SIN"
        wv.inputs["Scale"].default_value = 1.0 / ps
        wv.inputs["Distortion"].default_value = p.get("distortion", 2.0)
        wv.inputs["Detail"].default_value = 1.0
        L.new(P, wv.inputs["Vector"])
        G = wv.outputs["Fac"]
    elif kind in ("veins", "membrane"):
        # radial veins fanning out from the wing root: |sin(count * angle + warp * noise)|
        o = p.get("origin", (0, 0, 0))
        ob = m2b(o) / PX + Vector((0, 0, ROOT_Z / PX))
        sub = _n(nt, "ShaderNodeVectorMath", (-600, -300), operation="SUBTRACT")
        sub.inputs[1].default_value = tuple(ob)
        L.new(px.outputs[0], sub.inputs[0])
        sep = _n(nt, "ShaderNodeSeparateXYZ", (-400, -300))
        L.new(sub.outputs[0], sep.inputs[0])
        at = _n(nt, "ShaderNodeMath", (-200, -300), operation="ARCTAN2")
        L.new(sep.outputs["Y"], at.inputs[0])
        L.new(sep.outputs["X"], at.inputs[1])
        wn = _n(nt, "ShaderNodeTexNoise", (-400, -550))
        wn.inputs["Scale"].default_value = 1.0 / p.get("warp_scale", 10.0)
        L.new(P, wn.inputs["Vector"])
        wm = _n(nt, "ShaderNodeMath", (-200, -550), operation="MULTIPLY")
        wm.inputs[1].default_value = p.get("warp", 0.6)
        L.new(wn.outputs["Fac"], wm.inputs[0])
        mul = _n(nt, "ShaderNodeMath", (0, -300), operation="MULTIPLY")
        mul.inputs[1].default_value = p.get("count", 6.0)
        L.new(at.outputs[0], mul.inputs[0])
        ad = _n(nt, "ShaderNodeMath", (150, -300), operation="ADD")
        L.new(mul.outputs[0], ad.inputs[0]); L.new(wm.outputs[0], ad.inputs[1])
        sn = _n(nt, "ShaderNodeMath", (300, -300), operation="SINE")
        L.new(ad.outputs[0], sn.inputs[0])
        ab = _n(nt, "ShaderNodeMath", (450, -300), operation="ABSOLUTE")
        L.new(sn.outputs[0], ab.inputs[0])
        G = ab.outputs[0]
    if B is None:  # secondary large-scale noise (lichen patches, macro variation)
        n2 = _n(nt, "ShaderNodeTexNoise", (-100, -800))
        n2.inputs["Scale"].default_value = 1.0 / (ps * p.get("b_scale", 1.6))
        n2.inputs["Detail"].default_value = p.get("b_detail", 3.0)
        n2.inputs["Roughness"].default_value = 0.6
        L.new(P, n2.inputs["Vector"])
        B = n2.outputs["Fac"]
    L.new(R, comb.inputs[0])
    if G is not None:
        L.new(G, comb.inputs[1])
    else:
        comb.inputs[1].default_value = 1.0
    L.new(B, comb.inputs[2])
    img = _n(nt, "ShaderNodeTexImage", (800, 300))
    img.name = "bake_target"
    nt.nodes.active = img
    m["mc_kind"] = kind
    return m


def bake_channels(built, ss=4):
    model = built.model
    W, H = model.tex_size
    scene = bpy.context.scene
    img = bpy.data.images.new("bake_%s" % model.name, W * ss, H * ss, alpha=True, float_buffer=True)
    img.colorspace_settings.name = "Non-Color"
    for m in bpy.data.materials:
        if m.name.startswith("bake.%s." % model.name):
            n = m.node_tree.nodes["bake_target"]
            n.image = img
            m.node_tree.nodes.active = n
    scene.render.engine = "CYCLES"
    scene.cycles.samples = 1
    scene.cycles.device = "CPU"
    scene.render.bake.margin = 0
    scene.render.bake.use_clear = True
    vl = bpy.context.view_layer
    for o in scene.objects:
        o.select_set(False)
    objs = [o for c, o in built.cube_objs if c.owner is None and c.bake]
    for o in objs:
        o.select_set(True)
    vl.objects.active = objs[0]
    bpy.ops.object.bake(type="EMIT", margin=0, use_clear=True)
    a = np.empty(W * ss * H * ss * 4, np.float32)
    img.pixels.foreach_get(a)
    a = a.reshape(H * ss, W * ss, 4)[::-1]
    blk = a.reshape(H, ss, W, ss, 4)
    out = {
        "R": blk[..., 0].mean(axis=(1, 3)),
        "G": blk[..., 1].mean(axis=(1, 3)),
        "Gmin": blk[..., 1].min(axis=(1, 3)),
        "B": blk[..., 2].mean(axis=(1, 3)),
    }
    bpy.data.images.remove(img)
    return out


# --------------------------------------------------------------------------- preview material & render
def make_skin_material(name, skin_path, emit_path=None, blend=False, emit_strength=5.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    L = nt.links
    out = _n(nt, "ShaderNodeOutputMaterial", (900, 0))
    bs = _n(nt, "ShaderNodeBsdfPrincipled", (500, 0))
    bs.inputs["Roughness"].default_value = 0.88
    bs.inputs["Specular IOR Level"].default_value = 0.25
    L.new(bs.outputs[0], out.inputs["Surface"])
    uv = _n(nt, "ShaderNodeUVMap", (-600, 0), uv_map="box_uv")
    ti = _n(nt, "ShaderNodeTexImage", (-300, 100), interpolation="Closest")
    ti.image = load_image(skin_path)
    ti.name = "skin"
    L.new(uv.outputs[0], ti.inputs[0])
    L.new(ti.outputs["Color"], bs.inputs["Base Color"])
    if blend:
        L.new(ti.outputs["Alpha"], bs.inputs["Alpha"])
        m.surface_render_method = "BLENDED"
        m.use_transparency_overlap = True
    else:
        gt = _n(nt, "ShaderNodeMath", (100, -100), operation="GREATER_THAN")
        gt.inputs[1].default_value = 0.5
        L.new(ti.outputs["Alpha"], gt.inputs[0])
        L.new(gt.outputs[0], bs.inputs["Alpha"])
        m.surface_render_method = "DITHERED"
    if emit_path and os.path.exists(emit_path):
        te = _n(nt, "ShaderNodeTexImage", (-300, -300), interpolation="Closest")
        te.image = load_image(emit_path)
        te.name = "emissive"
        L.new(uv.outputs[0], te.inputs[0])
        mm = _n(nt, "ShaderNodeMix", (100, -350), data_type="RGBA", blend_type="MULTIPLY")
        mm.inputs["Factor"].default_value = 1.0
        L.new(te.outputs["Color"], mm.inputs[6])
        L.new(te.outputs["Alpha"], mm.inputs[7])  # colour * alpha (non-colour float broadcast)
        L.new(mm.outputs[2], bs.inputs["Emission Color"])
        bs.inputs["Emission Strength"].default_value = emit_strength
    m.use_backface_culling = True
    return m


def load_image(path):
    ap = os.path.abspath(path)
    for im in bpy.data.images:
        if im.filepath and os.path.abspath(bpy.path.abspath(im.filepath)) == ap:
            im.reload()
            return im
    im = bpy.data.images.load(ap)
    im.colorspace_settings.name = "sRGB"
    im.alpha_mode = "STRAIGHT"
    return im


def assign_material(built, mat):
    for c, o in built.cube_objs:
        o.data.materials.clear()
        o.data.materials.append(mat)
        for p in o.data.polygons:
            p.material_index = 0


def setup_world(scene):
    w = bpy.data.worlds.new("void_backdrop")
    scene.world = w
    w.use_nodes = True
    nt = w.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    L = nt.links
    out = _n(nt, "ShaderNodeOutputWorld", (900, 0))
    mix = _n(nt, "ShaderNodeMixShader", (700, 0))
    lp = _n(nt, "ShaderNodeLightPath", (300, 300))
    amb = _n(nt, "ShaderNodeBackground", (400, 100))
    amb.inputs["Color"].default_value = hex_lin("#2a2150")
    amb.inputs["Strength"].default_value = 0.55
    tc = _n(nt, "ShaderNodeTexCoord", (-500, -200))
    sep = _n(nt, "ShaderNodeSeparateXYZ", (-300, -200))
    L.new(tc.outputs["Window"], sep.inputs[0])
    # radial-ish vignette: lighter violet glow behind the creature, near-black at the frame edges
    sub = _n(nt, "ShaderNodeVectorMath", (-300, -400), operation="SUBTRACT")
    sub.inputs[1].default_value = (0.5, 0.58, 0.0)
    L.new(tc.outputs["Window"], sub.inputs[0])
    mul = _n(nt, "ShaderNodeVectorMath", (-150, -400), operation="MULTIPLY")
    mul.inputs[1].default_value = (1.0, 1.0, 0.0)
    L.new(sub.outputs[0], mul.inputs[0])
    ln = _n(nt, "ShaderNodeVectorMath", (0, -400), operation="LENGTH")
    L.new(mul.outputs[0], ln.inputs[0])
    ramp = _n(nt, "ShaderNodeValToRGB", (150, -300))
    ramp.color_ramp.elements[0].position = 0.0
    ramp.color_ramp.elements[0].color = hex_lin("#2b1850")
    ramp.color_ramp.elements[1].position = 0.75
    ramp.color_ramp.elements[1].color = hex_lin("#05030c")
    e = ramp.color_ramp.elements.new(0.35)
    e.color = hex_lin("#160a28")
    L.new(ln.outputs["Value"], ramp.inputs[0])
    cam_bg = _n(nt, "ShaderNodeBackground", (400, -200))
    L.new(ramp.outputs[0], cam_bg.inputs["Color"])
    L.new(lp.outputs["Is Camera Ray"], mix.inputs[0])
    L.new(amb.outputs[0], mix.inputs[1])
    L.new(cam_bg.outputs[0], mix.inputs[2])
    L.new(mix.outputs[0], out.inputs[0])


def look_rot(direction):
    return Vector(direction).to_track_quat("-Z", "Y").to_euler()


def add_sun(name, direction, strength, color, angle_deg=6.0):
    ld = bpy.data.lights.new(name, "SUN")
    ld.energy = strength
    ld.color = color
    ld.angle = math.radians(angle_deg)
    o = bpy.data.objects.new(name, ld)
    o.rotation_euler = look_rot(direction)
    bpy.context.scene.collection.objects.link(o)
    return o


def setup_render(scene, res=1024, samples=64):
    scene.render.engine = "BLENDER_EEVEE"
    scene.eevee.taa_render_samples = samples
    scene.render.resolution_x = res
    scene.render.resolution_y = res
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = False
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGB"
    try:
        scene.view_settings.view_transform = "Standard"
        scene.view_settings.look = "None"
    except Exception:
        pass
    setup_world(scene)
    # key light from the front-left-top (vanilla top-left light), cool fill, strong violet/teal rim from behind
    add_sun("key", (0.55, 0.75, -0.75), 2.6, (1.0, 0.95, 0.97), 10)
    add_sun("fill", (-0.8, 0.4, -0.25), 0.55, (0.70, 0.66, 1.0), 20)
    add_sun("rim", (-0.25, -1.0, -0.35), 3.2, (0.62, 0.78, 1.0), 4)
    add_sun("rim2", (0.7, -0.8, -0.2), 1.6, (0.85, 0.6, 1.0), 4)
    tree = bpy.data.node_groups.new("bloom", "CompositorNodeTree")
    scene.compositing_node_group = tree
    tree.interface.new_socket("Image", in_out="OUTPUT", socket_type="NodeSocketColor")
    rl = tree.nodes.new("CompositorNodeRLayers")
    rl.scene = scene
    g = tree.nodes.new("CompositorNodeGlare")
    g.inputs["Type"].default_value = "Bloom"
    g.inputs["Threshold"].default_value = 0.9
    g.inputs["Strength"].default_value = 0.85
    g.inputs["Size"].default_value = 0.7
    o = tree.nodes.new("NodeGroupOutput")
    tree.links.new(rl.outputs["Image"], g.inputs["Image"])
    tree.links.new(g.outputs[0], o.inputs[0])
    scene.render.use_compositing = True


def visible_points(built, hidden=()):
    pts = []
    dg = bpy.context.evaluated_depsgraph_get()
    for c, o in built.cube_objs:
        if c.part.name in hidden or o.hide_render:
            continue
        mw = o.matrix_world
        for v in o.data.vertices:
            pts.append(mw @ v.co)
    return pts


def frame_camera(built, az=35.0, el=22.0, lens=70.0, margin=0.86, hidden=(), center_bias=(0, 0)):
    from bpy_extras.object_utils import world_to_camera_view
    scene = bpy.context.scene
    pts = visible_points(built, hidden)
    lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
    hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
    ctr = (lo + hi) / 2
    cd = bpy.data.cameras.new("cam")
    cd.lens = lens
    cd.sensor_width = 36
    cam = bpy.data.objects.new("camera", cd)
    scene.collection.objects.link(cam)
    scene.camera = cam
    a, e = math.radians(az), math.radians(el)
    d = Vector((-math.sin(a) * math.cos(e), -math.cos(a) * math.cos(e), math.sin(e)))
    dist = (hi - lo).length * 2.0 + 1.0
    target = ctr.copy()
    for _ in range(6):
        cam.location = target + d * dist
        cam.rotation_euler = look_rot(-d)
        bpy.context.view_layer.update()
        ndc = [world_to_camera_view(scene, cam, p) for p in pts]
        xs = [q.x for q in ndc]; ys = [q.y for q in ndc]
        ext = max(max(xs) - min(xs), max(ys) - min(ys))
        cx = (max(xs) + min(xs)) / 2 - 0.5 - center_bias[0]
        cy = (max(ys) + min(ys)) / 2 - 0.5 - center_bias[1]
        # shift target in the camera plane to centre the projection
        right = cam.matrix_world.to_3x3() @ Vector((1, 0, 0))
        up = cam.matrix_world.to_3x3() @ Vector((0, 1, 0))
        width_world = 2 * dist * math.tan(math.atan(cd.sensor_width / (2 * cd.lens)))
        target += right * cx * width_world + up * cy * width_world
        dist *= ext / margin
    cam.location = target + d * dist
    cam.rotation_euler = look_rot(-d)
    cd.clip_start = 0.01
    cd.clip_end = 500
    return cam


def render(path):
    scene = bpy.context.scene
    scene.render.filepath = os.path.abspath(path)
    bpy.ops.render.render(write_still=True)


def render_silhouette(path, res=256):
    scene = bpy.context.scene
    old = (scene.render.engine, scene.render.film_transparent, scene.render.resolution_x, scene.render.resolution_y,
           scene.render.use_compositing, scene.render.image_settings.color_mode)
    scene.render.engine = "BLENDER_WORKBENCH"
    sh = scene.display.shading
    sh.light = "FLAT"
    sh.color_type = "TEXTURE"
    scene.render.film_transparent = True
    scene.render.use_compositing = False
    scene.render.resolution_x = res
    scene.render.resolution_y = res
    scene.render.image_settings.color_mode = "RGBA"
    render(path)
    (scene.render.engine, scene.render.film_transparent, scene.render.resolution_x, scene.render.resolution_y,
     scene.render.use_compositing, scene.render.image_settings.color_mode) = old


def save_blend(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    for im in list(bpy.data.images):
        if im.name.startswith("bake_") or im.users == 0:
            bpy.data.images.remove(im)
    try:
        bpy.ops.file.make_paths_relative()
    except Exception:
        pass
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(path), compress=True, relative_remap=True)
    if os.path.exists(os.path.abspath(path) + "1"):  # no .blend1 backups next to the authoring files
        os.remove(os.path.abspath(path) + "1")
