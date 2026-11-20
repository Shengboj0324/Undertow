"""Pure-python Minecraft entity geometry: model spec, exact vanilla box-UV, UV packing, rest-pose transforms.

Conventions (identical to vanilla LayerDefinition / Blockbench "Modded Entity"):
  units = pixels, +Y points DOWN, ground at y = 24, the creature faces -Z, its right side is -X.
  Part pivot is relative to the parent pivot; rotation (deg) applied as rotationZYX(z, y, x) like ModelPart.
  Cube origin = min corner relative to the owning part pivot.
"""
import math

# Region names follow the creature, not vanilla's Direction labels:
#   top    = minY face (vanilla "DOWN" polygon)      bottom = maxY face (vanilla "UP" polygon)
#   right  = minX face (vanilla "WEST", creature's right)   left = maxX face (vanilla "EAST")
#   front  = minZ face (NORTH)                        back   = maxZ face (SOUTH)
FACES = ("top", "bottom", "right", "front", "left", "back")


def face_rects(uv, size):
    """Texel rectangles (x, y, w, h) of the six box-UV regions for a cube of size (w, h, d) at uv (u, v)."""
    u, v = uv
    w, h, d = size
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def island_size(size):
    w, h, d = size
    return (2 * (d + w), d + h)


def box_polygons(origin, size, uv, inflate=0.0, mirror=False):
    """Exact port of net.minecraft.client.model.geom.ModelPart.Cube.

    Returns [(region_name, [(x, y, z, u, v) x4])] in model pixels / texels, vertex order as vanilla
    (counter-clockwise seen from outside in model space).  Zero-area faces are dropped."""
    minX, minY, minZ = origin
    width, height, depth = size
    maxX, maxY, maxZ = minX + width, minY + height, minZ + depth
    g = inflate
    minX -= g; minY -= g; minZ -= g
    maxX += g; maxY += g; maxZ += g
    if mirror:
        minX, maxX = maxX, minX
    t0 = (minX, minY, minZ); t1 = (maxX, minY, minZ); t2 = (maxX, maxY, minZ); t3 = (minX, maxY, minZ)
    l0 = (minX, minY, maxZ); l1 = (maxX, minY, maxZ); l2 = (maxX, maxY, maxZ); l3 = (minX, maxY, maxZ)
    xo, yo = uv
    u0 = xo; u1 = xo + depth; u2 = xo + depth + width; u22 = xo + depth + width + width
    u3 = xo + depth + width + depth; u4 = xo + depth + width + depth + width
    v0 = yo; v1 = yo + depth; v2 = yo + depth + height

    def poly(verts, a0, b0, a1, b1):
        # Polygon(vertices, u0, v0, u1, v1): [0]->(u1,v0) [1]->(u0,v0) [2]->(u0,v1) [3]->(u1,v1)
        uvs = [(a1, b0), (a0, b0), (a0, b1), (a1, b1)]
        out = [(p[0], p[1], p[2], q[0], q[1]) for p, q in zip(verts, uvs)]
        if mirror:
            out.reverse()
        return out

    polys = [
        ("top", poly([l1, l0, t0, t1], u1, v0, u2, v1), width * depth),
        ("bottom", poly([t2, t3, l3, l2], u2, v1, u22, v0), width * depth),
        ("right", poly([t0, l0, l3, t3], u0, v1, u1, v2), depth * height),
        ("front", poly([t1, t0, t3, t2], u1, v1, u2, v2), width * height),
        ("left", poly([l1, t1, t2, l2], u2, v1, u3, v2), depth * height),
        ("back", poly([l0, l1, l2, l3], u3, v1, u4, v2), width * height),
    ]
    return [(n, p) for n, p, area in polys if area > 0]


def texel_to_local(face, size, fx, fy):
    """Map texel-centre (fx, fy) inside a region to cube-local coordinates (0..w, 0..h, 0..d), non-mirrored.

    Region orientations (verified against the vanilla vertex/UV assignment):
      front: col -> +X, row -> +Y        back : col -> -X, row -> +Y
      right: col -> -Z, row -> +Y        left : col -> +Z, row -> +Y
      top  : col -> +X, row -> -Z (row 0 at the back)   bottom: same as top"""
    w, h, d = size
    if face == "front":
        return (fx, fy, 0.0)
    if face == "back":
        return (w - fx, fy, d)
    if face == "right":
        return (0.0, fy, d - fx)
    if face == "left":
        return (w, fy, fx)
    if face == "top":
        return (fx, 0.0, d - fy)
    if face == "bottom":
        return (fx, h, d - fy)
    raise KeyError(face)


# --------------------------------------------------------------------------- spec classes
class Cube:
    def __init__(self, origin, size, uv=None, inflate=0.0, mirror=False, mat="base", share=None, bake=True,
                 tag=None, **opts):
        self.origin = tuple(float(c) for c in origin)
        self.size = tuple(int(s) for s in size)
        assert all(abs(s - round(s)) < 1e-9 for s in size), "cube sizes must be whole texels"
        self.uv = tuple(uv) if uv is not None else None
        self.inflate = float(inflate)
        self.mirror = bool(mirror)
        # mat: material key for all faces, or dict {face|'side'|'*': key}
        self.mat = mat
        self.share = share
        self.bake = bake
        self.tag = tag
        self.opts = opts
        self.part = None
        self.index = -1
        self.owner = None  # cube whose UV island this cube reuses (None = owns its island)

    def mat_for(self, face):
        m = self.mat
        if isinstance(m, str):
            return m
        if face in m:
            return m[face]
        if face in ("right", "left", "front", "back") and "side" in m:
            return m["side"]
        return m.get("*", "base")


class Part:
    def __init__(self, name, parent, pivot, rotation=(0, 0, 0)):
        self.name = name
        self.parent = parent
        self.pivot = tuple(float(c) for c in pivot)
        self.rotation = tuple(float(c) for c in rotation)
        self.cubes = []

    def cube(self, origin, size, **kw):
        c = Cube(origin, size, **kw)
        c.part = self
        self.cubes.append(c)
        return c


class Model:
    def __init__(self, name, tex_size):
        self.name = name
        self.tex_size = tuple(tex_size)
        self.parts = []
        self.by_name = {}

    def part(self, name, parent=None, pivot=(0, 0, 0), rot=(0, 0, 0)):
        assert name not in self.by_name, name
        if parent is not None:
            assert parent in self.by_name, "parent %s must be defined before %s" % (parent, name)
        p = Part(name, parent, pivot, rot)
        self.parts.append(p)
        self.by_name[name] = p
        return p

    def __getitem__(self, name):
        return self.by_name[name]

    def cubes(self):
        out = []
        for p in self.parts:
            out.extend(p.cubes)
        for i, c in enumerate(out):
            c.index = i
        return out

    # ------------------------------------------------------------------ UV packing
    def pack(self, pad=0, order=None):
        """Assign box-UV offsets with a deterministic skyline packer. Cubes with a preset uv keep it;
        cubes sharing a `share` key reuse the first cube's island (sizes must match)."""
        W, H = self.tex_size
        cubes = self.cubes()
        owners = {}
        todo = []
        fixed = []
        for c in cubes:
            if c.share is not None and c.share in owners:
                o = owners[c.share]
                assert o.size == c.size, "shared UV %s needs equal sizes" % c.share
                c.owner = o
                continue
            if c.share is not None:
                owners[c.share] = c
            if c.uv is not None:
                fixed.append(c)
            else:
                todo.append(c)
        import numpy as np
        occ = np.zeros((H, W), dtype=np.int32)
        for c in fixed:
            for (x, y, w, h) in face_rects(c.uv, c.size).values():
                if w > 0 and h > 0:
                    occ[int(y):int(y + h), int(x):int(x + w)] = 1
        todo.sort(key=lambda c: (-(island_size(c.size)[1]), -(island_size(c.size)[0]), c.index))
        for c in todo:
            iw, ih = island_size(c.size)
            sat = np.zeros((H + 1, W + 1), dtype=np.int64)
            sat[1:, 1:] = occ.cumsum(0).cumsum(1)
            pw, ph = min(iw + pad, W), min(ih + pad, H)
            if iw > W or ih > H:
                raise RuntimeError("island too large for %s cube %s size %s" % (self.name, c.part.name, c.size))
            ys = np.arange(0, H - ih + 1)[:, None]
            xs = np.arange(0, W - iw + 1)[None, :]
            y2 = np.minimum(ys + ph, H); x2 = np.minimum(xs + pw, W)
            s_ = sat[y2, x2] - sat[ys, x2] - sat[y2, xs] + sat[ys, xs]
            cand = np.argwhere(s_ == 0)
            if len(cand) == 0:
                raise RuntimeError("UV pack failed for %s cube %s size %s in %s" % (self.name, c.part.name, c.size, self.tex_size))
            y, x = int(cand[0][0]), int(cand[0][1])
            c.uv = (x, y)
            occ[y:y + ph, x:x + pw] = 1
        for c in cubes:
            if c.owner is not None:
                c.uv = c.owner.uv
        return self


# --------------------------------------------------------------------------- rest-pose math (pixels)
def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def rot_zyx(rx, ry, rz):
    """4x4 matrix of Quaternionf().rotationZYX(z, y, x) == Rz * Ry * Rx (radians)."""
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]]
    Ry = [[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]]
    Rz = [[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
    return mat_mul(Rz, mat_mul(Ry, Rx))


def part_local_matrix(p):
    t = [[1, 0, 0, p.pivot[0]], [0, 1, 0, p.pivot[1]], [0, 0, 1, p.pivot[2]], [0, 0, 0, 1]]
    r = rot_zyx(*[math.radians(a) for a in p.rotation])
    return mat_mul(t, r)


def part_world_matrices(model):
    out = {}
    for p in model.parts:
        m = part_local_matrix(p)
        if p.parent is not None:
            m = mat_mul(out[p.parent], m)
        out[p.name] = m
    return out


def xform(m, v):
    return tuple(m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2] + m[i][3] for i in range(3))
