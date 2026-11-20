"""Blender-side core: procedural shader-node graph DSL, Cycles emission baking of tileable / animated fields,
and orthographic toon rendering of small low-poly models for item sprites.

Only importable inside Blender (needs bpy)."""
import math
import os
import bpy
import numpy as np
from mathutils import Vector, Matrix, Euler

from . import pix

TMP = os.environ.get('WF_TEX_TMP', '/tmp/wf_tex_tmp')
os.makedirs(TMP, exist_ok=True)
TAU = math.pi * 2


# =============================================================================================== scene bootstrap
def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    sc.cycles.device = 'CPU'
    sc.cycles.samples = 1
    sc.cycles.use_denoising = False
    sc.cycles.use_adaptive_sampling = False
    sc.cycles.seed = 0
    sc.render.film_transparent = True
    sc.view_settings.view_transform = 'Standard'
    sc.view_settings.look = 'None'
    sc.view_settings.exposure = 0
    sc.view_settings.gamma = 1
    sc.render.dither_intensity = 0.0
    sc.display_settings.display_device = 'sRGB'
    sc.render.bake.margin = 0
    sc.render.threads_mode = 'AUTO'
    return sc


def srgb_to_lin(c):
    c = np.asarray(c, dtype=np.float64) / 255.0
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


# ================================================================================================= node graph DSL
class G:
    """Tiny DSL over a material node tree. Every helper returns an output socket."""

    def __init__(self, name='wf_field'):
        self.mat = bpy.data.materials.new(name)
        self.mat.use_nodes = True
        self.nt = self.mat.node_tree
        self.nt.nodes.clear()
        self._uv = None
        self._obj = None

    def node(self, t, **props):
        n = self.nt.nodes.new(t)
        for k, v in props.items():
            setattr(n, k, v)
        return n

    def link(self, a, b):
        self.nt.links.new(a, b)

    def _in(self, sock, val):
        if hasattr(val, 'is_output'):
            self.link(val, sock)
        else:
            try:
                sock.default_value = val
            except TypeError:
                sock.default_value = (val, val, val, 1.0) if len(sock.default_value) == 4 else (val, val, val)

    # ---------------------------------------------------------------- math
    def m(self, op, a, b=0.0, c=0.0, clamp=False):
        n = self.node('ShaderNodeMath', operation=op, use_clamp=clamp)
        self._in(n.inputs[0], a)
        self._in(n.inputs[1], b)
        self._in(n.inputs[2], c)
        return n.outputs[0]

    def add(self, a, b): return self.m('ADD', a, b)
    def sub(self, a, b): return self.m('SUBTRACT', a, b)
    def mul(self, a, b): return self.m('MULTIPLY', a, b)
    def div(self, a, b): return self.m('DIVIDE', a, b)
    def sin(self, a): return self.m('SINE', a)
    def cos(self, a): return self.m('COSINE', a)
    def pw(self, a, b): return self.m('POWER', a, b)
    def absv(self, a): return self.m('ABSOLUTE', a)
    def mn(self, a, b): return self.m('MINIMUM', a, b)
    def mx(self, a, b): return self.m('MAXIMUM', a, b)
    def lerp(self, a, b, t): return self.add(a, self.mul(self.sub(b, a), t))
    def smooth(self, e0, e1, x):
        n = self.node('ShaderNodeMapRange', interpolation_type='SMOOTHSTEP')
        self._in(n.inputs['Value'], x)
        n.inputs['From Min'].default_value = e0
        n.inputs['From Max'].default_value = e1
        return n.outputs['Result']

    def maprange(self, x, a, b, c=0.0, d=1.0, clamp=True):
        n = self.node('ShaderNodeMapRange', clamp=clamp)
        self._in(n.inputs['Value'], x)
        n.inputs['From Min'].default_value = a
        n.inputs['From Max'].default_value = b
        n.inputs['To Min'].default_value = c
        n.inputs['To Max'].default_value = d
        return n.outputs['Result']

    def combine(self, x, y, z):
        n = self.node('ShaderNodeCombineXYZ')
        self._in(n.inputs[0], x)
        self._in(n.inputs[1], y)
        self._in(n.inputs[2], z)
        return n.outputs[0]

    def separate(self, v):
        n = self.node('ShaderNodeSeparateXYZ')
        self._in(n.inputs[0], v)
        return n.outputs[0], n.outputs[1], n.outputs[2]

    # ------------------------------------------------------------- coordinates
    def uv(self):
        if self._uv is None:
            tc = self.node('ShaderNodeTexCoord')
            self._uv = self.separate(tc.outputs['UV'])[:2]
        return self._uv

    def objco(self):
        tc = self.node('ShaderNodeTexCoord')
        return tc.outputs['Object']

    def torus(self, u, v, fx, fy, off=(0, 0, 0, 0)):
        """Clifford-torus embedding of the unit square: any 4D field sampled here tiles perfectly in U and V.
        fx / fy ~ number of unit-scale features across the tile."""
        a = self.mul(u, TAU)
        b = self.mul(v, TAU)
        rx = fx / TAU
        ry = fy / TAU
        x = self.add(self.mul(self.cos(a), rx), off[0])
        y = self.add(self.mul(self.sin(a), rx), off[1])
        z = self.add(self.mul(self.cos(b), ry), off[2])
        w = self.add(self.mul(self.sin(b), ry), off[3])
        return self.combine(x, y, z), w

    def tile(self, fx, fy=None, off=(0, 0, 0, 0), shear=0):
        """Tileable coordinates from the bake UV. shear=k mixes v into u by an integer k (diagonal anisotropy, still periodic)."""
        u, v = self.uv()
        if shear:
            u = self.add(u, self.mul(v, float(shear)))
        return self.torus(u, v, fx, fy if fy is not None else fx, off)

    # ---------------------------------------------------------------- textures
    def noise(self, co, w=0.0, scale=1.0, detail=3.0, rough=0.55, lac=2.0, distortion=0.0, ntype='FBM',
              out='Fac', dims='4D', offset=0.0, gain=1.0):
        n = self.node('ShaderNodeTexNoise', noise_dimensions=dims, noise_type=ntype)
        try:
            n.normalize = True
        except Exception:
            pass
        self._in(n.inputs['Vector'], co)
        if dims == '4D':
            self._in(n.inputs['W'], w)
        n.inputs['Scale'].default_value = scale
        n.inputs['Detail'].default_value = detail
        n.inputs['Roughness'].default_value = rough
        n.inputs['Lacunarity'].default_value = lac
        n.inputs['Distortion'].default_value = distortion
        if 'Offset' in n.inputs:
            n.inputs['Offset'].default_value = offset
            n.inputs['Gain'].default_value = gain
        return n.outputs[out]

    def voronoi(self, co, w=0.0, scale=1.0, feature='F1', metric='EUCLIDEAN', rnd=1.0, out='Distance',
                dims='4D', detail=0.0, smooth=0.5):
        n = self.node('ShaderNodeTexVoronoi', voronoi_dimensions=dims, feature=feature, distance=metric)
        self._in(n.inputs['Vector'], co)
        if dims in ('4D', '1D'):
            self._in(n.inputs['W'], w)
        n.inputs['Scale'].default_value = scale
        n.inputs['Randomness'].default_value = rnd
        if 'Detail' in n.inputs:
            n.inputs['Detail'].default_value = detail
        if feature == 'SMOOTH_F1':
            n.inputs['Smoothness'].default_value = smooth
        return n.outputs[out]

    def bw(self, col):
        n = self.node('ShaderNodeRGBToBW')
        self._in(n.inputs[0], col)
        return n.outputs[0]

    # ------------------------------------------------------------------ outputs
    def out(self, r, g=None, b=None):
        """Emission output. Single socket -> grey; three sockets -> packed RGB channels (three fields per bake)."""
        g = r if g is None else g
        b = r if b is None else b
        col = self.combine(r, g, b)
        em = self.node('ShaderNodeEmission')
        self.link(col, em.inputs['Color'])
        em.inputs['Strength'].default_value = 1.0
        o = self.node('ShaderNodeOutputMaterial')
        self.link(em.outputs[0], o.inputs['Surface'])

    def out_color(self, col_sock, strength=1.0):
        em = self.node('ShaderNodeEmission')
        self.link(col_sock, em.inputs['Color'])
        em.inputs['Strength'].default_value = strength
        o = self.node('ShaderNodeOutputMaterial')
        self.link(em.outputs[0], o.inputs['Surface'])


# ======================================================================================================== baking
_BAKE_OBJ = None


def _bake_plane():
    global _BAKE_OBJ
    if _BAKE_OBJ is not None and _BAKE_OBJ.name in bpy.data.objects:
        return _BAKE_OBJ
    mesh = bpy.data.meshes.new('wf_bake_plane')
    mesh.from_pydata([(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], [], [(0, 1, 2, 3)])
    uvl = mesh.uv_layers.new(name='UVMap')
    for i, lp in enumerate(mesh.loops):
        co = mesh.vertices[lp.vertex_index].co
        uvl.data[i].uv = (co.x, co.y)
    obj = bpy.data.objects.new('wf_bake_plane', mesh)
    bpy.context.scene.collection.objects.link(obj)
    _BAKE_OBJ = obj
    return obj


_CACHE = {}


def bake(build, size=64, key=None, **kw):
    """Build a material with `build(g, **kw)` (which must call g.out(...)), bake its emission onto a UV
    plane at `size`x`size` with Cycles, return float array (size, size, 3), row 0 = TOP of the texture."""
    ck = None
    if key is not None:
        ck = (key, size, tuple(sorted(kw.items())))
        if ck in _CACHE:
            return _CACHE[ck]
    obj = _bake_plane()
    g = G('wf_bake_%s' % (key or 'f'))
    build(g, **kw)
    img = bpy.data.images.new('wf_bake_img', size, size, alpha=False, float_buffer=True)
    img.colorspace_settings.name = 'Non-Color'
    tn = g.node('ShaderNodeTexImage')
    tn.image = img
    g.nt.nodes.active = tn
    obj.data.materials.clear()
    obj.data.materials.append(g.mat)
    bpy.context.view_layer.objects.active = obj
    for o in bpy.context.scene.objects:
        o.select_set(o == obj)
    bpy.context.scene.render.engine = 'CYCLES'
    bpy.context.scene.cycles.samples = 1
    bpy.ops.object.bake(type='EMIT', margin=0, use_clear=True)
    buf = np.empty(size * size * 4, dtype=np.float32)
    img.pixels.foreach_get(buf)
    arr = buf.reshape(size, size, 4)[::-1, :, :3].astype(np.float64)  # flip: Blender rows are bottom-up
    bpy.data.images.remove(img)
    bpy.data.materials.remove(g.mat)
    if ck is not None:
        _CACHE[ck] = arr
    return arr


def bake_down(build, out=16, ss=4, key=None, **kw):
    """Bake at out*ss and box-average down to `out` (anti-aliased field ready for quantisation)."""
    f = bake(build, size=out * ss, key=key, **kw)
    return pix.box_down(f, ss)


# ===================================================================================================== rendering
class Toon:
    """A flat 'toon' material: N.L quantised through a constant colour ramp of sRGB palette entries."""

    def __init__(self, palette, cuts=None, emissive=False, pattern=None, palette2=None, pattern_cut=0.5,
                 rim=None):
        self.palette = [pix.hx(c) if isinstance(c, str) else np.asarray(c) for c in palette]
        self.palette2 = [pix.hx(c) if isinstance(c, str) else np.asarray(c) for c in (palette2 or [])]
        n = len(self.palette)
        self.cuts = cuts if cuts is not None else [(-0.9 + 1.7 * i / n) for i in range(n)]
        self.emissive = emissive
        self.pattern = pattern
        self.pattern_cut = pattern_cut
        self.rim = rim


def _ramp(g, fac, palette, cuts):
    cr = g.node('ShaderNodeValToRGB')
    cr.color_ramp.interpolation = 'CONSTANT'
    els = cr.color_ramp.elements
    while len(els) > 1:
        els.remove(els[-1])
    for i, (c, p) in enumerate(zip(palette, cuts)):
        lin = srgb_to_lin(c)
        e = els[0] if i == 0 else els.new(min(0.999, max(0.0, p)))
        if i == 0:
            e.position = 0.0
        e.color = (lin[0], lin[1], lin[2], 1.0)
    g.link(fac, cr.inputs['Fac'])
    return cr.outputs['Color']


def make_toon(spec, light_world):
    g = G('wf_toon')
    if spec.emissive or len(spec.palette) == 1:
        lin = srgb_to_lin(spec.palette[0] if len(spec.palette) == 1 else spec.palette[-1])
        rgb = g.node('ShaderNodeRGB')
        rgb.outputs[0].default_value = (lin[0], lin[1], lin[2], 1)
        g.out_color(rgb.outputs[0])
        return g.mat
    geo = g.node('ShaderNodeNewGeometry')
    dp = g.node('ShaderNodeVectorMath', operation='DOT_PRODUCT')
    g.link(geo.outputs['Normal'], dp.inputs[0])
    dp.inputs[1].default_value = tuple(light_world)
    ndl = dp.outputs['Value']
    fac = g.maprange(ndl, -1.0, 1.0, 0.0, 1.0)
    cuts01 = [(c + 1) / 2 for c in spec.cuts]
    col = _ramp(g, fac, spec.palette, cuts01)
    if spec.pattern is not None and spec.palette2:
        pf = spec.pattern(g)
        sel = g.m('GREATER_THAN', pf, spec.pattern_cut)
        col2 = _ramp(g, fac, spec.palette2, cuts01)
        mixn = g.node('ShaderNodeMix', data_type='RGBA')
        g.link(sel, mixn.inputs['Factor'])
        g.link(col, mixn.inputs[6])
        g.link(col2, mixn.inputs[7])
        col = mixn.outputs[2]
    g.out_color(col)
    return g.mat


class Stage:
    """Orthographic toon stage for item sprites. Build objects with the add_* helpers, then render()."""

    def __init__(self, view=(0, 0, 0), ortho=2.0, light_cam=(-0.55, 0.62, 0.56)):
        reset_scene()
        global _BAKE_OBJ
        _BAKE_OBJ = None
        self.sc = bpy.context.scene
        cam_data = bpy.data.cameras.new('cam')
        cam_data.type = 'ORTHO'
        cam_data.ortho_scale = ortho
        cam = bpy.data.objects.new('cam', cam_data)
        self.sc.collection.objects.link(cam)
        # camera looks down -Z of its local frame; place it on +Y looking toward origin, then apply view rotation
        rot = Euler((math.radians(90 + view[0]), 0, math.radians(view[1])), 'XYZ')
        cam.rotation_euler = rot
        d = rot.to_matrix() @ Vector((0, 0, 10))
        cam.location = d
        self.sc.camera = cam
        self.cam = cam
        L = Vector(light_cam).normalized()
        self.light = rot.to_matrix() @ L
        self.mats = {}
        self.specs = {}

    def mat(self, name, spec):
        self.specs[name] = spec
        self.mats[name] = make_toon(spec, self.light)
        self.mats[name].name = 'wf_' + name
        return self.mats[name]

    def _finish(self, obj, mat, loc, rot, scale, smooth):
        obj.location = loc
        obj.rotation_euler = [math.radians(a) for a in rot]
        obj.scale = scale
        if mat is not None:
            obj.data.materials.clear()
            obj.data.materials.append(self.mats[mat])
        if smooth and obj.type == 'MESH':
            for p in obj.data.polygons:
                p.use_smooth = True
        return obj

    def cube(self, mat, loc=(0, 0, 0), size=(1, 1, 1), rot=(0, 0, 0), bevel=0.0):
        bpy.ops.mesh.primitive_cube_add(size=1)
        o = bpy.context.active_object
        o.data.transform(Matrix.Diagonal((size[0], size[1], size[2], 1.0)))  # bake size into the mesh
        if bevel:
            md = o.modifiers.new('bv', 'BEVEL')
            md.width = bevel
            md.segments = 1
        return self._finish(o, mat, loc, rot, (1, 1, 1), False)

    def cyl(self, mat, loc=(0, 0, 0), r=0.5, h=1.0, rot=(0, 0, 0), verts=16, smooth=True, scale=(1, 1, 1)):
        bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=h)
        o = bpy.context.active_object
        o.data.update()
        return self._finish(o, mat, loc, rot, scale, smooth)

    def cone(self, mat, loc=(0, 0, 0), r1=0.5, r2=0.0, h=1.0, rot=(0, 0, 0), verts=16, smooth=False,
             scale=(1, 1, 1)):
        bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r1, radius2=r2, depth=h)
        return self._finish(bpy.context.active_object, mat, loc, rot, scale, smooth)

    def sphere(self, mat, loc=(0, 0, 0), r=0.5, scale=(1, 1, 1), rot=(0, 0, 0), seg=24, smooth=True):
        bpy.ops.mesh.primitive_uv_sphere_add(segments=seg, ring_count=seg // 2, radius=r)
        return self._finish(bpy.context.active_object, mat, loc, rot, scale, smooth)

    def ico(self, mat, loc=(0, 0, 0), r=0.5, scale=(1, 1, 1), rot=(0, 0, 0), sub=1, smooth=False):
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=sub, radius=r)
        return self._finish(bpy.context.active_object, mat, loc, rot, scale, smooth)

    def torus(self, mat, loc=(0, 0, 0), R=0.5, r=0.1, rot=(0, 0, 0), maj=24, minr=8, scale=(1, 1, 1)):
        bpy.ops.mesh.primitive_torus_add(major_radius=R, minor_radius=r, major_segments=maj, minor_segments=minr)
        return self._finish(bpy.context.active_object, mat, loc, rot, scale, True)

    def mesh(self, mat, verts, faces, loc=(0, 0, 0), rot=(0, 0, 0), scale=(1, 1, 1), smooth=False,
             solidify=0.0):
        me = bpy.data.meshes.new('m')
        me.from_pydata(verts, [], faces)
        me.update()
        o = bpy.data.objects.new('m', me)
        self.sc.collection.objects.link(o)
        if solidify:
            md = o.modifiers.new('sd', 'SOLIDIFY')
            md.thickness = solidify
            md.offset = 0
        return self._finish(o, mat, loc, rot, scale, smooth)

    def tube(self, mat, pts, r=0.05, loc=(0, 0, 0), rot=(0, 0, 0), res=6, closed=False):
        """Poly-curve tube through points (cords, coils, roots, chains)."""
        cu = bpy.data.curves.new('c', 'CURVE')
        cu.dimensions = '3D'
        cu.bevel_depth = r
        cu.bevel_resolution = 2
        cu.resolution_u = res
        sp = cu.splines.new('POLY')
        sp.points.add(len(pts) - 1)
        for p, q in zip(sp.points, pts):
            p.co = (q[0], q[1], q[2], 1)
        sp.use_cyclic_u = closed
        o = bpy.data.objects.new('c', cu)
        self.sc.collection.objects.link(o)
        o.location = loc
        o.rotation_euler = [math.radians(a) for a in rot]
        o.data.materials.append(self.mats[mat])
        return o

    def lathe(self, mat, profile, loc=(0, 0, 0), rot=(0, 0, 0), seg=20, scale=(1, 1, 1), smooth=True):
        """Revolve a (radius, z) profile around Z."""
        verts, faces = [], []
        n = len(profile)
        for i in range(seg):
            a = TAU * i / seg
            for r, z in profile:
                verts.append((r * math.cos(a), r * math.sin(a), z))
        for i in range(seg):
            j = (i + 1) % seg
            for k in range(n - 1):
                faces.append((i * n + k, j * n + k, j * n + k + 1, i * n + k + 1))
        return self.mesh(mat, verts, faces, loc, rot, scale, smooth)

    def parent_group(self, objs, loc=(0, 0, 0), rot=(0, 0, 0), scale=(1, 1, 1)):
        e = bpy.data.objects.new('grp', None)
        self.sc.collection.objects.link(e)
        for o in objs:
            o.parent = e
        e.location = loc
        e.rotation_euler = [math.radians(a) for a in rot]
        e.scale = scale
        return e

    def render(self, res=64, name='item'):
        sc = self.sc
        sc.render.resolution_x = res
        sc.render.resolution_y = res
        sc.render.resolution_percentage = 100
        sc.cycles.samples = 1
        sc.cycles.pixel_filter_type = 'BOX'
        sc.cycles.filter_width = 0.01
        sc.render.image_settings.file_format = 'PNG'
        sc.render.image_settings.color_mode = 'RGBA'
        sc.render.image_settings.color_depth = '8'
        p = os.path.join(TMP, 'render_%s.png' % name)
        sc.render.filepath = p
        bpy.ops.render.render(write_still=True)
        return pix.load_png(p)

    def palette(self):
        """All palette colours used on this stage (with emissive flags)."""
        cols, emis = [], []
        for k, s in self.specs.items():
            for c in list(s.palette) + list(s.palette2):
                cols.append(np.asarray(c, dtype=np.float64))
                emis.append(bool(s.emissive))
        return cols, emis


def downsample_sprite(img, out=16, cover=0.45, palette=None):
    """Point-sampled hi-res toon render -> crisp sprite: per cell alpha by coverage, colour by MODE of samples
    (toon colours are discrete, so the mode is a real palette colour)."""
    H = img.shape[0]
    k = H // out
    res = np.zeros((out, out, 4), np.uint8)
    for y in range(out):
        for x in range(out):
            cell = img[y * k:(y + 1) * k, x * k:(x + 1) * k].reshape(-1, 4)
            op = cell[cell[:, 3] >= 128]
            if len(op) < cover * k * k:
                continue
            cols, cnt = np.unique(op[:, :3], axis=0, return_counts=True)
            res[y, x, :3] = cols[np.argmax(cnt)]
            res[y, x, 3] = 255
    if palette is not None:
        idx = pix.quantize_rgba(res, palette)
        res = pix.render_index(idx, palette)
    return res
