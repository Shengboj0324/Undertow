"""Renders the mod icon (worldsfall_icon.png): a voxel End island hanging over the glowing tide, with teal crystals
beneath, a leaning purpur spire and a violet nebula sky.  Run: Blender -b --python tools/blender/icon/build_icon.py -- <out.png>"""
import math
import random
import sys

import bpy
from mathutils import Vector

OUT = sys.argv[sys.argv.index("--") + 1] if "--" in sys.argv else "worldsfall_icon.png"
random.seed(1026)

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.engine = "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items.keys() else "BLENDER_EEVEE"
scene.render.resolution_x = scene.render.resolution_y = 512
scene.render.film_transparent = False
scene.view_settings.view_transform = "Standard"
scene.view_settings.look = "Medium High Contrast"
for attr, val in (("use_bloom", True), ("use_gtao", True), ("use_shadows", True), ("taa_render_samples", 64)):
    try:
        setattr(scene.eevee, attr, val)
    except AttributeError:
        pass


def mat(name, color, emit=None, strength=0.0, rough=0.85):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Roughness"].default_value = rough
    if emit:
        b.inputs["Emission Color"].default_value = (*emit, 1)
        b.inputs["Emission Strength"].default_value = strength
    return m


M = {
    "stone": mat("end_stone", (0.78, 0.74, 0.58)),
    "stone_dark": mat("end_stone_dark", (0.55, 0.50, 0.40)),
    "loam": mat("lilac", (0.62, 0.48, 0.86)),
    "purpur": mat("purpur", (0.62, 0.40, 0.66)),
    "purpur_dark": mat("purpur_dark", (0.42, 0.26, 0.48)),
    "crystal": mat("crystal", (0.04, 0.50, 0.45), (0.05, 0.85, 0.72), 0.9, 0.2),
    "lantern": mat("lantern", (1.0, 0.6, 0.9), (1.0, 0.45, 0.85), 4.0),
    "under": mat("undershale", (0.16, 0.14, 0.20)),
}

blocks = {}


def put(x, y, z, m):
    blocks[(x, y, z)] = m


# island: noisy disc on top, inverted cone below
R = 7
for x in range(-R - 1, R + 2):
    for y in range(-R - 1, R + 2):
        d = math.hypot(x, y * 1.15)
        edge = R + math.sin(x * 1.3) * 0.8 + math.cos(y * 1.7) * 0.7
        if d > edge:
            continue
        depth = int((edge - d) * 1.25 + 1 + random.random() * 1.5)
        top = 0 if d < edge - 2 else -1
        for z in range(top - depth, top + 1):
            m = M["stone"] if z == top else (M["under"] if z < top - depth + 2 else M["stone_dark"])
            put(x, y, z, m)
        if z == top and random.random() < 0.35 and d < edge - 1:
            put(x, y, top, M["loam"])

# leaning spire
for i in range(8):
    lean = int(i * 0.35)
    for dx in (0, 1):
        for dy in (0, 1):
            put(-2 + dx + lean, -1 + dy, 1 + i, M["purpur"] if (i + dx) % 3 else M["purpur_dark"])

for x, y in ((4, 3), (-5, 2), (3, -4)):
    put(x, y, 1, M["purpur_dark"])
    put(x, y, 2, M["lantern"])

# hanging crystals under the island
for _ in range(14):
    x, y = random.randint(-4, 4), random.randint(-4, 4)
    zs = [k[2] for k in blocks if k[0] == x and k[1] == y]
    if not zs:
        continue
    bottom = min(zs)
    for k in range(random.randint(2, 6)):
        put(x, y, bottom - 1 - k, M["crystal"])

bpy.ops.mesh.primitive_cube_add(size=1)
proto = bpy.context.object
proto.name = "proto"
mesh = proto.data
for (x, y, z), m in blocks.items():
    ob = bpy.data.objects.new(f"b{x}_{y}_{z}", mesh.copy())
    ob.location = (x, y, z)
    ob.data.materials.append(m)
    bpy.context.collection.objects.link(ob)
bpy.data.objects.remove(proto)

# sky: violet gradient + stars
world = bpy.data.worlds.new("sky")
scene.world = world
world.use_nodes = True
wn = world.node_tree
bg = wn.nodes["Background"]
coord = wn.nodes.new("ShaderNodeTexCoord")
sep = wn.nodes.new("ShaderNodeSeparateXYZ")
wn.links.new(coord.outputs["Generated"], sep.inputs[0])
# world "Generated" coords span 0..1, so Z = 0.5 is the horizon: tide glow below, violet night above
grad = wn.nodes.new("ShaderNodeValToRGB")
cr = grad.color_ramp
cr.elements[0].position = 0.0
cr.elements[0].color = (0.0, 0.03, 0.04, 1)
cr.elements[1].position = 1.0
cr.elements[1].color = (0.008, 0.004, 0.025, 1)
for pos, col in ((0.12, (0.03, 0.30, 0.28, 1)), (0.2, (0.10, 0.55, 0.50, 1)), (0.3, (0.06, 0.05, 0.16, 1)), (0.45, (0.14, 0.06, 0.28, 1)), (0.7, (0.05, 0.02, 0.12, 1))):
    e = cr.elements.new(pos)
    e.color = col
remap = wn.nodes.new("ShaderNodeMapRange")
remap.inputs["From Min"].default_value = -1.0
remap.inputs["From Max"].default_value = 1.0
wn.links.new(sep.outputs["Z"], remap.inputs["Value"])
wn.links.new(remap.outputs["Result"], grad.inputs["Fac"])
neb = wn.nodes.new("ShaderNodeTexNoise")
neb.inputs["Scale"].default_value = 2.2
neb.inputs["Detail"].default_value = 8
mix = wn.nodes.new("ShaderNodeMix")
mix.data_type = "RGBA"
mix.blend_type = "ADD"
mix.inputs["Factor"].default_value = 0.7
wn.links.new(grad.outputs["Color"], mix.inputs[6])
nebc = wn.nodes.new("ShaderNodeValToRGB")
nebc.color_ramp.elements[0].position = 0.5
nebc.color_ramp.elements[0].color = (0, 0, 0, 1)
nebc.color_ramp.elements[1].color = (0.45, 0.15, 0.75, 1)
wn.links.new(neb.outputs["Fac"], nebc.inputs["Fac"])
wn.links.new(nebc.outputs["Color"], mix.inputs[7])
stars = wn.nodes.new("ShaderNodeTexVoronoi")
stars.inputs["Scale"].default_value = 90
sramp = wn.nodes.new("ShaderNodeValToRGB")
sramp.color_ramp.elements[0].position = 0.0
sramp.color_ramp.elements[0].color = (1, 1, 1, 1)
sramp.color_ramp.elements[1].position = 0.06
sramp.color_ramp.elements[1].color = (0, 0, 0, 1)
wn.links.new(stars.outputs["Distance"], sramp.inputs["Fac"])
mix2 = wn.nodes.new("ShaderNodeMix")
mix2.data_type = "RGBA"
mix2.blend_type = "ADD"
mix2.inputs["Factor"].default_value = 0.8
wn.links.new(mix.outputs[2], mix2.inputs[6])
wn.links.new(sramp.outputs["Color"], mix2.inputs[7])
wn.links.new(mix2.outputs[2], bg.inputs["Color"])
bg.inputs["Strength"].default_value = 1.0

# lights: cool key from above, teal bounce from the tide, magenta rim
key = bpy.data.lights.new("key", "SUN")
key.energy = 2.2
key.color = (0.85, 0.8, 1.0)
k = bpy.data.objects.new("key", key)
k.rotation_euler = (math.radians(40), math.radians(10), math.radians(30))
bpy.context.collection.objects.link(k)
bounce = bpy.data.lights.new("bounce", "AREA")
bounce.energy = 1200
bounce.size = 30
bounce.color = (0.3, 1.0, 0.9)
b = bpy.data.objects.new("bounce", bounce)
b.location = (0, 0, -18)
bpy.context.collection.objects.link(b)
rim = bpy.data.lights.new("rim", "POINT")
rim.energy = 1500
rim.color = (1.0, 0.35, 0.8)
r = bpy.data.objects.new("rim", rim)
r.location = (-10, 12, 8)
bpy.context.collection.objects.link(r)

cam = bpy.data.cameras.new("cam")
cam.lens = 52
c = bpy.data.objects.new("cam", cam)
c.location = (25, -29, 13)
target = Vector((0, 0, -1.0))
c.rotation_euler = (target - c.location).to_track_quat("-Z", "Y").to_euler()
bpy.context.collection.objects.link(c)
scene.camera = c

scene.render.filepath = OUT
bpy.ops.render.render(write_still=True)
print("wrote", OUT)
