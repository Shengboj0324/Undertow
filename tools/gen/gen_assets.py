"""Generates blockstates, block models, item model definitions, item models and particle definitions."""
import copy, json, os, re
from common import *

BS = os.path.join(ASSETS, "blockstates")
BM = os.path.join(ASSETS, "models/block")
IM = os.path.join(ASSETS, "models/item")
ITEMS = os.path.join(ASSETS, "items")

def b(t):  # block texture id
    return f"{NS}:block/{t}"

def model(name, parent, textures=None, **extra):
    m = {"parent": parent}
    if textures:
        m["textures"] = textures
    m.update(extra)
    write(f"{BM}/{name}.json", m)
    return f"{NS}:block/{name}"

def simple_state(name, model_id):
    write(f"{BS}/{name}.json", {"variants": {"": {"model": model_id}}})

def item_def(name, model_id, extra=None):
    d = {"model": {"type": "minecraft:model", "model": model_id}}
    if extra:
        d.update(extra)
    write(f"{ITEMS}/{name}.json", d)

def item_sprite(name, layers, emissive_layers=()):
    tex = {}
    for i, t in enumerate(layers):
        if i in emissive_layers:
            tex[f"layer{i}"] = {"sprite": t, "neoforge_data": {"light_emission": 15}}
        else:
            tex[f"layer{i}"] = t
    write(f"{IM}/{name}.json", {"parent": "minecraft:item/generated", "textures": tex})
    item_def(name, f"{NS}:item/{name}")

def remap_state(vanilla_name, our_name, mapping):
    d = vanilla_json(f"assets/minecraft/blockstates/{vanilla_name}.json")
    s = json.dumps(d)
    for k, v in mapping.items():
        s = s.replace(f'"minecraft:block/{k}"', f'"{v}"')
    write(f"{BS}/{our_name}.json", json.loads(s))

# --------------------------------------------------------------------------- equipment assets
def gen_equipment():
    """Elytra upgrade wings (§9.7): textures/entity/equipment/wings/elytra_<upgrade>.png, swapped in by the smithing recipe."""
    for up in ("gale", "still", "hover"):
        write(os.path.join(ASSETS, f"equipment/elytra_{up}.json"), {"layers": {"wings": [{"texture": f"{NS}:elytra_{up}"}]}})

# --------------------------------------------------------------------------- emissive helpers
def cube_elements(tex_faces, frm=(0, 0, 0), to=(16, 16, 16), emission=0, cull=True):
    faces = {}
    for f, t in tex_faces.items():
        face = {"texture": t}
        if cull and frm == (0, 0, 0) and to == (16, 16, 16):
            face["cullface"] = f
        faces[f] = face
    e = {"from": list(frm), "to": list(to), "faces": faces}
    if emission:
        e["light_emission"] = emission
    return e

def cube_with_emissive(name, tex, emissive, particle=None):
    faces = {f: "#all" for f in ("down", "up", "north", "south", "west", "east")}
    efaces = {f: "#glow" for f in faces}
    m = {"parent": "minecraft:block/block", "textures": {"all": tex, "glow": emissive, "particle": particle or tex},
         "elements": [cube_elements(faces), cube_elements(efaces, emission=15)]}
    write(f"{BM}/{name}.json", m)
    return f"{NS}:block/{name}"

def emissive_copy(elements, slot_map):
    out = []
    for e in elements:
        e2 = copy.deepcopy(e)
        keep = {}
        for fname, face in e2["faces"].items():
            t = face["texture"]
            if t in slot_map:
                face["texture"] = slot_map[t]
                keep[fname] = face
        if keep:
            e2["faces"] = keep
            e2["light_emission"] = 15
            out.append(e2)
    return out

# --------------------------------------------------------------------------- generic block kinds
def cube_all(name, tex=None):
    m = model(name, "minecraft:block/cube_all", {"all": b(tex or name)})
    simple_state(name, m)
    item_def(name, m)
    return m

def family(base_name, parent_block, tex, wall=True):
    t = b(tex)
    slab = model(f"{base_name}_slab", "minecraft:block/slab", {"bottom": t, "top": t, "side": t})
    slab_top = model(f"{base_name}_slab_top", "minecraft:block/slab_top", {"bottom": t, "top": t, "side": t})
    write(f"{BS}/{base_name}_slab.json", {"variants": {"type=bottom": {"model": slab}, "type=top": {"model": slab_top},
                                                       "type=double": {"model": f"{NS}:block/{parent_block}"}}})
    item_def(f"{base_name}_slab", slab)
    st = model(f"{base_name}_stairs", "minecraft:block/stairs", {"bottom": t, "top": t, "side": t})
    model(f"{base_name}_stairs_inner", "minecraft:block/inner_stairs", {"bottom": t, "top": t, "side": t})
    model(f"{base_name}_stairs_outer", "minecraft:block/outer_stairs", {"bottom": t, "top": t, "side": t})
    remap_state("purpur_stairs", f"{base_name}_stairs", {"purpur_stairs": st, "purpur_stairs_inner": st + "_inner",
                                                          "purpur_stairs_outer": st + "_outer"})
    item_def(f"{base_name}_stairs", st)
    if wall:
        post = model(f"{base_name}_wall_post", "minecraft:block/template_wall_post", {"wall": t})
        side = model(f"{base_name}_wall_side", "minecraft:block/template_wall_side", {"wall": t})
        tall = model(f"{base_name}_wall_side_tall", "minecraft:block/template_wall_side_tall", {"wall": t})
        inv = model(f"{base_name}_wall_inventory", "minecraft:block/wall_inventory", {"wall": t})
        remap_state("end_stone_brick_wall", f"{base_name}_wall", {"end_stone_brick_wall_post": post,
                    "end_stone_brick_wall_side": side, "end_stone_brick_wall_side_tall": tall})
        item_def(f"{base_name}_wall", inv)

def pillar(name, side_tex, top_tex):
    v = model(name, "minecraft:block/cube_column", {"side": b(side_tex), "end": b(top_tex)})
    h = model(name + "_horizontal", "minecraft:block/cube_column_horizontal", {"side": b(side_tex), "end": b(top_tex)})
    remap_state("purpur_pillar", name, {"purpur_pillar": v, "purpur_pillar_horizontal": h})
    item_def(name, v)

def cross_block(name, tex, emissive=None, item_tex=None):
    if emissive:
        m = model(name, "minecraft:block/cross_emissive", {"cross": b(tex), "cross_emissive": b(emissive)})
    else:
        m = model(name, "minecraft:block/cross", {"cross": b(tex)})
    return m

def facing_cluster(name, tex, emissive):
    m = cross_block(name, tex, emissive)
    remap_state("amethyst_cluster", name, {"amethyst_cluster": m})
    layers = [b(tex)] + ([b(emissive)] if emissive else [])
    item_sprite(name, layers, emissive_layers=(1,) if emissive else ())

# --------------------------------------------------------------------------- bespoke models
def box(frm, to, tex_faces, emission=0):
    return cube_elements(tex_faces, frm, to, emission, cull=False)

def all_faces(t):
    return {f: t for f in ("down", "up", "north", "south", "west", "east")}

def custom(name, textures, elements, ao=True):
    m = {"parent": "minecraft:block/block", "textures": textures, "elements": elements}
    if not ao:
        m["ambientocclusion"] = False
    write(f"{BM}/{name}.json", m)
    return f"{NS}:block/{name}"

def horizontal_variants(models_by_key, key_prefix=""):
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for facing, y in rot.items():
        for k, m in models_by_key.items():
            key = f"facing={facing}" + (("," + k) if k else "")
            v = {"model": m}
            if y:
                v["y"] = y
            variants[key] = v
    return variants

def chorus_pod():
    shapes = [((6, 6, 0), (10, 11, 4)), ((5, 4, 0), (11, 11, 6)), ((4, 2, 0), (12, 11, 8))]
    ms = {}
    for age, (frm, to) in enumerate(shapes):
        t = b(f"chorus_pod_stage{age}")
        els = [box(frm, to, all_faces("#pod")),
               box((7.5, 11, 0), (8.5, 14, 1), all_faces("#pod"))]
        tex = {"pod": t, "particle": t}
        if age == 2:
            els.append(box(frm, to, all_faces("#glow"), emission=15))
            tex["glow"] = b("chorus_pod_stage2_emissive")
        ms[age] = custom(f"chorus_pod_stage{age}", tex, els)
    fallen = custom("chorus_pod_fallen", {"pod": b("chorus_pod_stage2"), "glow": b("chorus_pod_stage2_emissive"), "particle": b("chorus_pod_stage2")},
                    [box((4, 0, 4), (12, 6, 12), all_faces("#pod")), box((4, 0, 4), (12, 6, 12), all_faces("#glow"), emission=15)])
    variants = {}
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    for facing, y in rot.items():
        for age in range(3):
            v = {"model": ms[age]}
            if y:
                v["y"] = y
            variants[f"age={age},facing={facing},fallen=false"] = v
            variants[f"age={age},facing={facing},fallen=true"] = {"model": fallen, "y": y} if y else {"model": fallen}
    write(f"{BS}/chorus_pod.json", {"variants": variants})

def hanging_parts(name, tex_prefix, tip_emissive):
    variants = {}
    for part in ("base", "middle", "tip"):
        tex = f"{tex_prefix}_{part}" if tex_prefix == "lumen_crystal" else ("voidroot_tip" if part == "tip" else "voidroot_middle")
        if part == "tip":
            m = model(f"{name}_{part}", "minecraft:block/cross_emissive", {"cross": b(tex), "cross_emissive": b(tip_emissive)})
        else:
            m = model(f"{name}_{part}", "minecraft:block/cross", {"cross": b(tex)})
        variants[f"part={part}"] = {"model": m}
    write(f"{BS}/{name}.json", {"variants": variants})
    tip = f"{tex_prefix}_tip" if tex_prefix == "lumen_crystal" else "voidroot_tip"
    item_sprite(name, [b(tip), b(tip_emissive)], emissive_layers=(1,))

def lantern():
    tmpl = vanilla_json("assets/minecraft/models/block/template_lantern.json")
    tmplh = vanilla_json("assets/minecraft/models/block/template_hanging_lantern.json")
    out = {}
    for name, t in (("spire_lantern", tmpl), ("spire_lantern_hanging", tmplh)):
        els = t["elements"]
        glow = emissive_copy(els, {"#lantern": "#glow"})
        m = {"parent": "minecraft:block/block", "textures": {"lantern": b("spire_lantern"), "glow": b("spire_lantern_emissive"),
                                                              "particle": b("spire_lantern")}, "elements": els + glow,
             "ambientocclusion": False}
        write(f"{BM}/{name}.json", m)
        out[name] = f"{NS}:block/{name}"
    remap_state("lantern", "spire_lantern", {"lantern": out["spire_lantern"], "lantern_hanging": out["spire_lantern_hanging"]})
    item_sprite("spire_lantern", [b("spire_lantern_item")] if os.path.exists(os.path.join(ASSETS, "textures/block/spire_lantern_item.png"))
                else [f"{NS}:item/spire_lantern"], emissive_layers=())

def tide_gauge():
    variants = {}
    for lvl in range(16):
        h = 1 + lvl * 14 / 15
        els = [box((0, 0, 0), (16, 16, 16), {"down": "#top", "up": "#top", "north": "#side", "south": "#side", "west": "#side", "east": "#side"}),
               box((3, 1, 3), (13, max(1.5, h), 13), all_faces("#fluid")),
               box((3, max(1.5, h) - 0.5, 3), (13, max(1.5, h), 13), {"up": "#glow", "north": "#glow", "south": "#glow", "west": "#glow", "east": "#glow"}, emission=15)]
        m = custom(f"tide_gauge_level_{lvl}", {"side": b("tide_gauge_side"), "top": b("tide_gauge_top"), "fluid": b("tide_gauge_fluid"),
                                               "glow": b("tide_gauge_fluid_emissive"), "particle": b("tide_gauge_side")}, els)
        variants[f"power={lvl}"] = {"model": m}
    write(f"{BS}/tide_gauge.json", {"variants": variants})
    item_def("tide_gauge", f"{NS}:block/tide_gauge_level_7")

def oriented_box(name, frm, to, side, top, front, glow_front=None, bottom=None):
    faces = {"down": "#bottom", "up": "#top", "north": "#front", "south": "#side", "west": "#side", "east": "#side"}
    tex = {"side": b(side), "top": b(top), "front": b(front), "bottom": b(bottom or top), "particle": b(side)}
    els = [box(frm, to, faces)]
    if glow_front:
        tex["glow"] = b(glow_front)
        els.append(box(frm, to, {"north": "#glow"}, emission=15))
    return custom(name, tex, els)

def egg_cradle():
    variants = {}
    posts = [(0, 0), (13, 0), (0, 13), (13, 13)]
    for lit in range(5):
        els = [box((0, 0, 0), (16, 6, 16), {"down": "#side", "up": "#top", "north": "#side", "south": "#side", "west": "#side", "east": "#side"})]
        tex = {"side": b("egg_cradle_side"), "top": b("egg_cradle_top"), "post": b("egg_cradle_post"), "lit": b("egg_cradle_post_lit"),
               "glow": b("egg_cradle_post_lit_emissive"), "particle": b("egg_cradle_side")}
        for i, (x, z) in enumerate(posts):
            on = i < lit
            els.append(box((x, 6, z), (x + 3, 13, z + 3), all_faces("#lit" if on else "#post")))
            if on:
                els.append(box((x, 6, z), (x + 3, 13, z + 3), all_faces("#glow"), emission=15))
        m = custom(f"egg_cradle_{lit}", tex, els)
        for egg in ("false", "true"):
            variants[f"egg={egg},posts={lit}"] = {"model": m}
    write(f"{BS}/egg_cradle.json", {"variants": variants})
    item_def("egg_cradle", f"{NS}:block/egg_cradle_4")

def gravity_anchor():
    f = "#frame"
    els = [box((1, 0, 1), (15, 2, 15), all_faces(f)), box((1, 14, 1), (15, 16, 15), all_faces(f))]
    for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
        els.append(box((x, 2, z), (x + 2, 14, z + 2), all_faces(f)))
    m = custom("gravity_anchor", {"frame": b("gravity_anchor_frame"), "particle": b("gravity_anchor_frame")}, els)
    simple_state("gravity_anchor", m)
    item_def("gravity_anchor", m)

def anchor_frame():
    els = [box((0, 14, 0), (16, 16, 16), all_faces("#bar"))]
    for x, z in ((0, 0), (14, 0), (0, 14), (14, 14)):
        els.append(box((x, 0, z), (x + 2, 14, z + 2), all_faces("#bar")))
    els.append(box((0, 14, 0), (16, 16, 16), {"up": "#glow", "down": "#glow"}, emission=15))
    m = custom("anchor_frame", {"bar": b("anchor_frame"), "glow": b("anchor_frame_emissive"), "particle": b("anchor_frame")}, els)
    simple_state("anchor_frame", m)
    item_def("anchor_frame", m)

def gen_blocks():
    # Chorus Reach
    m = model("pale_loam", "minecraft:block/cube_bottom_top", {"top": b("pale_loam_top"), "side": b("pale_loam_side"), "bottom": "minecraft:block/end_stone"})
    simple_state("pale_loam", m); item_def("pale_loam", m)
    m = cross_block("lilac_tuft", "lilac_tuft"); simple_state("lilac_tuft", m); item_sprite("lilac_tuft", [b("lilac_tuft")])
    chorus_pod()
    # Stillwater
    for n in ("stillstone", "polished_stillstone", "stillstone_bricks", "chiseled_stillstone"):
        cube_all(n)
    family("stillstone", "stillstone", "stillstone"); family("polished_stillstone", "polished_stillstone", "polished_stillstone")
    family("stillstone_brick", "stillstone_bricks", "stillstone_bricks")
    facing_cluster("mirror_shard", "mirror_shard", None)
    cube_all("rimglass"); cube_all("stillglass")
    # Stillwater cauldron (§7.5): vanilla cauldron templates with the Stillwater surface as content
    caul = {"bottom": "minecraft:block/cauldron_bottom", "content": b("stillwater_still"), "inside": "minecraft:block/cauldron_inner",
            "particle": "minecraft:block/cauldron_side", "side": "minecraft:block/cauldron_side", "top": "minecraft:block/cauldron_top"}
    lv = {1: model("stillwater_cauldron_level1", "minecraft:block/template_cauldron_level1", caul),
          2: model("stillwater_cauldron_level2", "minecraft:block/template_cauldron_level2", caul),
          3: model("stillwater_cauldron_full", "minecraft:block/template_cauldron_full", caul)}
    write(f"{BS}/stillwater_cauldron.json", {"variants": {f"level={k}": {"model": v} for k, v in lv.items()}})
    pane = {"pane": b("stillglass"), "edge": b("stillglass_pane_top")}
    pm = {k: model(f"stillglass_pane_{k}", f"minecraft:block/template_glass_pane_{k}", pane) for k in ("noside", "noside_alt", "post", "side", "side_alt")}
    remap_state("black_stained_glass_pane", "stillglass_pane", {f"black_stained_glass_pane_{k}": v for k, v in pm.items()})
    item_sprite("stillglass_pane", [b("stillglass")])
    # Fallen Spires
    cube_all("weathered_purpur"); family("weathered_purpur", "weathered_purpur", "weathered_purpur", wall=False)
    pillar("weathered_purpur_pillar", "weathered_purpur_pillar", "weathered_purpur_pillar_top")
    pillar("cracked_purpur_pillar", "cracked_purpur_pillar", "cracked_purpur_pillar_top")
    t = b("purpur_rubble")
    m = model("purpur_rubble", "minecraft:block/slab", {"bottom": t, "top": t, "side": t}); simple_state("purpur_rubble", m); item_def("purpur_rubble", m)
    lantern()
    # Underside
    for n in ("undershale", "polished_undershale", "undershale_bricks", "undershale_tiles", "lumen_crystal_block"):
        cube_all(n)
    family("undershale", "undershale", "undershale"); family("polished_undershale", "polished_undershale", "polished_undershale")
    family("undershale_brick", "undershale_bricks", "undershale_bricks"); family("undershale_tile", "undershale_tiles", "undershale_tiles")
    m = cross_block("ceiling_moss", "ceiling_moss"); simple_state("ceiling_moss", m); item_sprite("ceiling_moss", [b("ceiling_moss")])
    hanging_parts("lumen_crystal", "lumen_crystal", "lumen_crystal_tip_emissive")
    facing_cluster("lumen_cluster", "lumen_cluster", "lumen_cluster_emissive")
    hanging_parts("voidroot", "voidroot", "voidroot_tip_emissive")
    ripe = cross_block("glowbulb", "glowbulb", "glowbulb_emissive"); picked = cross_block("glowbulb_picked", "glowbulb_picked")
    write(f"{BS}/glowbulb.json", {"variants": {"ripe=true": {"model": ripe}, "ripe=false": {"model": picked}}})
    off = model("lumen_lamp", "minecraft:block/cube_all", {"all": b("lumen_lamp")})
    on = cube_with_emissive("lumen_lamp_on", b("lumen_lamp_on"), b("lumen_lamp_on_emissive"))
    remap_state("redstone_lamp", "lumen_lamp", {"redstone_lamp": off, "redstone_lamp_on": on}); item_def("lumen_lamp", off)
    # Drift
    for n in ("driftstone", "polished_driftstone", "driftstone_bricks"):
        cube_all(n)
    family("driftstone", "driftstone", "driftstone"); family("polished_driftstone", "polished_driftstone", "polished_driftstone")
    family("driftstone_brick", "driftstone_bricks", "driftstone_bricks")
    tide_gauge()
    # Fracture
    for n, e in (("null_obsidian", "null_obsidian_emissive"), ("fracture_glass", "fracture_glass_emissive")):
        m = cube_with_emissive(n, b(n), b(e)); simple_state(n, m); item_def(n, m)
    facing_cluster("unmade_shard", "unmade_shard", "unmade_shard_emissive")
    cube_all("ash_stone")
    # Functional
    for n, kind in (("hoard_cache", "hoard_cache"), ("decorative_hoard_cache", "hoard_cache")):
        m = oriented_box(n, (1, 0, 1), (15, 14, 15), f"{kind}_side", f"{kind}_top", f"{kind}_front")
        write(f"{BS}/{n}.json", {"variants": horizontal_variants({"": m})}); item_def(n, m)
    for used in ("false", "true"):
        pass
    ks = custom("keystone_socket", {"side": b("keystone_socket_side"), "top": b("keystone_socket_top"), "glow": b("keystone_socket_top_emissive"),
                                    "particle": b("keystone_socket_side")},
                [box((2, 0, 2), (14, 14, 14), {"down": "#side", "up": "#top", "north": "#side", "south": "#side", "west": "#side", "east": "#side"}),
                 box((2, 0, 2), (14, 14, 14), {"up": "#glow"}, emission=15)])
    write(f"{BS}/keystone_socket.json", {"variants": {"used=false": {"model": ks}, "used=true": {"model": ks}}}); item_def("keystone_socket", ks)
    for n in ("spire_vault", "drift_cache"):
        m = oriented_box(n, (1, 0, 1), (15, 15, 15), f"{n}_side", f"{n}_top", f"{n}_front", glow_front=f"{n}_front_emissive")
        simple_state(n, m); item_def(n, m)
    sp = custom("sigil_pedestal", {"side": b("sigil_pedestal_side"), "top": b("sigil_pedestal_top"), "glow": b("sigil_pedestal_top_emissive"),
                                   "particle": b("sigil_pedestal_side")},
                [box((1, 0, 1), (15, 15, 15), {"down": "#side", "up": "#top", "north": "#side", "south": "#side", "west": "#side", "east": "#side"}),
                 box((1, 0, 1), (15, 15, 15), {"up": "#glow"}, emission=15)])
    simple_state("sigil_pedestal", sp); item_def("sigil_pedestal", sp)
    egg_cradle(); gravity_anchor(); anchor_frame()
    rf = custom("fracture_rift_frame", {"side": b("fracture_rift_frame_side"), "top": b("fracture_rift_frame_top"),
                                        "glow": b("fracture_rift_frame_top_emissive"), "particle": b("fracture_rift_frame_side")},
                [box((0, 0, 0), (16, 16, 16), {"down": "#side", "up": "#top", "north": "#side", "south": "#side", "west": "#side", "east": "#side"}),
                 box((0, 0, 0), (16, 16, 16), {"up": "#glow"}, emission=15)])
    simple_state("fracture_rift_frame", rf); item_def("fracture_rift_frame", rf)
    rift = custom("fracture_rift", {"rift": b("fracture_rift"), "particle": b("fracture_rift")},
                  [box((0, 9, 0), (16, 10, 16), {"up": "#rift", "down": "#rift"}, emission=15)], ao=False)
    simple_state("fracture_rift", rift)
    for filled in ("false", "true"):
        top = "still_throne_top_filled" if filled == "true" else "still_throne_top"
        els = [box((0, 0, 0), (16, 14, 16), {"down": "#side", "up": "#top", "north": "#side", "south": "#side", "west": "#side", "east": "#side"})]
        tex = {"side": b("still_throne_side"), "top": b(top), "particle": b("still_throne_side")}
        if filled == "true":
            tex["glow"] = b("still_throne_top_filled_emissive")
            els.append(box((0, 0, 0), (16, 14, 16), {"up": "#glow"}, emission=15))
        custom(f"still_throne_{'filled' if filled == 'true' else 'empty'}", tex, els)
    write(f"{BS}/still_throne.json", {"variants": {"filled=false": {"model": f"{NS}:block/still_throne_empty"},
                                                   "filled=true": {"model": f"{NS}:block/still_throne_filled"}}})
    item_def("still_throne", f"{NS}:block/still_throne_empty")
    jar = custom("tidewisp_jar", {"jar": b("tidewisp_jar"), "glow": b("tidewisp_jar_emissive"), "particle": b("tidewisp_jar")},
                 [box((5, 0, 5), (11, 9, 11), all_faces("#jar")), box((5, 0, 5), (11, 9, 11), all_faces("#glow"), emission=15)], ao=False)
    simple_state("tidewisp_jar", jar)
    write(f"{BM}/stillwater.json", {"textures": {"particle": b("stillwater_still")}})
    simple_state("stillwater", f"{NS}:block/stillwater")

ITEM_SPRITES = """skate_membrane glow_gland grazer_frill voidroot_fiber lumen_crystal_shard mirror_shard_fragment echo_dust lurker_lens
bottle_of_stillwater chorus_pod roasted_chorus_pod grazer_flank cooked_grazer_flank glowbulb drift_stew stillwater_tea void_anchor
cracked_void_anchor skate_harness spire_key spire_sigil tide_sigil under_sigil rift_key heart_of_stillness stillness_fragment
dragon_egg_fragment slumbering_egg gale_upgrade_template still_upgrade_template hover_upgrade_template spire_armor_trim_smithing_template
drift_armor_trim_smithing_template undersong_armor_trim_smithing_template music_disc_unmade stillwater_bucket bottled_tidewisp shulker_satchel
echo_tablet""".split()
EMISSIVE_ITEMS = {"void_anchor", "glow_gland", "lumen_crystal_shard", "echo_tablet", "heart_of_stillness", "spire_key", "tide_sigil",
                  "bottled_tidewisp", "slumbering_egg"}
EGGS = "void_skate chorus_grazer underside_lurker hoarder spire_warden echo wild_shulker tidewisp shardling wyrm".split()

def gen_items():
    for n in ITEM_SPRITES:
        if n in EMISSIVE_ITEMS:
            item_sprite(n, [f"{NS}:item/{n}", f"{NS}:item/{n}_emissive"], emissive_layers=(1,))
        else:
            item_sprite(n, [f"{NS}:item/{n}"])
    for e in EGGS:
        item_sprite(f"{e}_spawn_egg", [f"{NS}:item/{e}_spawn_egg"])
    # Gateway compass: needle points at the lodestone target (bound or nearest gateway), spins when none.
    frames = []
    for i in range(16):
        write(f"{IM}/gateway_compass_{i:02d}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/gateway_compass_{i:02d}"}})
        frames.append({"threshold": (i - 0.5) if i else 0.0, "model": {"type": "minecraft:model", "model": f"{NS}:item/gateway_compass_{i:02d}"}})
    frames[0]["threshold"] = 0.0
    entries = [{"threshold": float(i) - 0.5 if i else 0.0, "model": {"type": "minecraft:model", "model": f"{NS}:item/gateway_compass_{i:02d}"}} for i in range(16)]
    entries.append({"threshold": 15.5, "model": {"type": "minecraft:model", "model": f"{NS}:item/gateway_compass_00"}})
    disp = {"type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": "lodestone", "scale": 16.0, "entries": entries}
    spin = {"type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": "none", "scale": 16.0, "entries": entries}
    write(f"{ITEMS}/gateway_compass.json", {"model": {"type": "minecraft:condition", "property": "minecraft:has_component",
                                                     "component": "minecraft:lodestone_tracker", "on_true": disp, "on_false": spin}})
    # Rift Tether: cast state when the hook is out (property registered client side)
    write(f"{IM}/rift_tether.json", {"parent": "minecraft:item/handheld_rod", "textures": {"layer0": f"{NS}:item/rift_tether"}})
    write(f"{IM}/rift_tether_cast.json", {"parent": "minecraft:item/handheld_rod", "textures": {"layer0": f"{NS}:item/rift_tether_cast"}})
    write(f"{ITEMS}/rift_tether.json", {"model": {"type": "minecraft:condition", "property": "worldsfall:tether_cast",
                                                 "on_true": {"type": "minecraft:model", "model": f"{NS}:item/rift_tether_cast"},
                                                 "on_false": {"type": "minecraft:model", "model": f"{NS}:item/rift_tether"}}})
    # Void Lens: like the spyglass (in-hand model + scope)
    write(f"{IM}/void_lens.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/void_lens"}})
    write(f"{ITEMS}/void_lens.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/void_lens"}})
    write(f"{IM}/spire_lantern.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/spire_lantern"}})
    item_def("spire_lantern", f"{NS}:item/spire_lantern")
    item_sprite("tidewisp_jar", [f"{NS}:item/bottled_tidewisp"])

PARTICLES = {"pollen_mote": 4, "pod_pop": 4, "still_ring": 4, "stillfall_mist": 4, "dust_fall": 2, "socket_spark": 4, "up_mote": 4,
             "up_drip": 2, "skate_wake": 4, "tide_crest": 4, "erosion_static": 4, "ash_inflow": 4, "shard_spark": 4, "tidewisp_trail": 4,
             "shell_fragment": 4}

def gen_particles():
    for name, n in PARTICLES.items():
        write(os.path.join(ASSETS, f"particles/{name}.json"), {"textures": [f"{NS}:{name}_{i}" for i in range(n)]})
    # mod particle textures live in textures/particle and must be on the particle atlas
    write(os.path.join(ASSETS, "../minecraft/atlases/particles.json"),
          {"sources": [{"type": "minecraft:directory", "source": "particle", "prefix": ""}]}) if False else None

if __name__ == "__main__":
    gen_blocks(); gen_items(); gen_particles(); gen_equipment()
    print(f"assets written: {len(WRITTEN)}")
