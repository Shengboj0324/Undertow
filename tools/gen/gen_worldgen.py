"""Generates worldgen data: biomes, features, placed features, structures, structure sets, biome modifiers, dimensions."""
import json, os
from common import *

D = os.path.join(DATA, NS)
def i(n): return f"{NS}:{n}"

STEPS = ["raw_generation", "lakes", "local_modifications", "underground_structures", "surface_structures", "strongholds",
         "underground_ores", "underground_decoration", "fluid_springs", "vegetal_decoration", "top_layer_modification"]

def feature(name, type_=None):
    write(f"{D}/worldgen/feature/{name}.json", {"type": type_ or i(name)})

def placed(name, feat, placement):
    write(f"{D}/worldgen/placed_feature/{name}.json", {"feature": feat, "placement": placement})

def count(n): return {"type": "minecraft:count", "count": n}
def rarity(n): return {"type": "minecraft:rarity_filter", "chance": n}
SQUARE = {"type": "minecraft:in_square"}
BIOME = {"type": "minecraft:biome"}
def heightmap(h="MOTION_BLOCKING"): return {"type": "minecraft:heightmap", "heightmap": h}
def height(lo, hi): return {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}}
def dist(lo=0, hi=None):
    d = {"type": i("distance_from_origin"), "min": lo}
    if hi is not None:
        d["max"] = hi
    return d

def gen_features():
    for f in ("chorus_grove", "pale_loam_patch", "stillwater_basin", "underside_growth", "debris_field", "skate_roost", "wild_shulker_colony",
              "echo_shrine", "chorus_pod_scatter"):
        feature(f)
    placed("chorus_grove", i("chorus_grove"), [rarity(4), SQUARE, heightmap(), BIOME])
    placed("pale_loam_patch", i("pale_loam_patch"), [count(1), SQUARE, heightmap(), BIOME])
    placed("pale_loam_patch_sparse", i("pale_loam_patch"), [rarity(4), SQUARE, heightmap(), BIOME])
    placed("chorus_pod_scatter", i("chorus_pod_scatter"), [rarity(2), SQUARE, heightmap(), BIOME])
    placed("chorus_pod_scatter_dense", i("chorus_pod_scatter"), [count(1), SQUARE, heightmap(), BIOME])
    placed("stillwater_basin", i("stillwater_basin"), [rarity(3), SQUARE, heightmap(), BIOME])
    placed("underside_growth", i("underside_growth"), [count(1), BIOME])
    placed("underside_growth_outer", i("underside_growth"), [rarity(2), dist(1024), BIOME])
    placed("debris_field", i("debris_field"), [rarity(6), SQUARE, height(20, 90), BIOME])
    placed("debris_field_gap", i("debris_field"), [rarity(40), SQUARE, height(30, 80), dist(260, 1000)])
    placed("skate_roost", i("skate_roost"), [rarity(48), SQUARE, height(45, 70), BIOME])
    placed("skate_roost_gap", i("skate_roost"), [rarity(160), SQUARE, height(50, 70), dist(300, 980)])
    placed("wild_shulker_colony", i("wild_shulker_colony"), [rarity(24), SQUARE, heightmap(), dist(1024), BIOME])
    placed("echo_shrine", i("echo_shrine"), [rarity(220), SQUARE, height(55, 80), dist(1100), BIOME])

def mob(t, w, lo, hi):
    return {"type": t, "count": {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}, "weight": w}

# Per-biome ambient light (entities and unlit blocks in the skylight-less End are lit by this alone, §18.2)
AMBIENT = {
    "chorus_reach": "#584a70", "stillwater_basins": "#404866", "fallen_spires": "#5a4668", "the_hoard": "#5a5450",
    "the_underside": "#34505a", "the_drift": "#3e4a62", "fracture_ring": "#5a2638",
}

def biome(name, fog, sky_tint, particles, music, loop, additions, spawns, features, fog_end=None, extra=None):
    attrs = {
        "minecraft:audio/ambient_sounds": {"loop": i(f"ambient.{name}.loop"),
                                           "additions": {"sound": i(f"ambient.{name}.additions"), "tick_chance": 0.0111}},
        "minecraft:visual/fog_color": fog,
        "minecraft:visual/sky_color": sky_tint,
        "minecraft:visual/ambient_light_color": AMBIENT.get(name, "#4e4868"),
        "neoforge:custom_skybox": i("end_sky") if name != "fracture_ring" else i("fracture_sky"),
    }
    if music:
        attrs["minecraft:audio/background_music"] = {"default": {"max_delay": 24000, "min_delay": 9000, "sound": music, "replace_current_music": True}}
    if particles:
        attrs["minecraft:visual/ambient_particles"] = {"argument": particles, "modifier": "append"}
    if spawns:
        attrs["minecraft:gameplay/natural_mob_spawns"] = {"argument": {"spawn_costs": {}, "spawns_by_category": spawns}, "modifier": "overlay"}
    if extra:
        attrs.update(extra)
    feats = [[] for _ in range(11)]
    for step, f in features:
        feats[STEPS.index(step)].append(f)
    while feats and not feats[-1]:
        feats.pop()
    write(f"{D}/worldgen/biome/{name}.json", {"attributes": attrs, "carvers": [], "downfall": 0.0,
                                              "effects": {"water_color": "#3f76e4"}, "features": feats,
                                              "has_precipitation": False, "temperature": 0.5})

def p(name, prob):
    return {"particle": {"type": i(name)}, "probability": prob}

def gen_biomes():
    common_end = [("surface_structures", "minecraft:end_gateway_return")]
    ender = mob("minecraft:enderman", 10, 1, 4)
    biome("chorus_reach", "#3A2448", "#4B2A7A", [p("pollen_mote", 0.012)], i("music.pale_pasture"), None, None,
          {"creature": [mob(i("chorus_grazer"), 12, 3, 6)], "monster": [ender]},
          common_end + [("vegetal_decoration", i("pale_loam_patch")),
                        ("vegetal_decoration", i("chorus_grove")), ("top_layer_modification", i("chorus_pod_scatter_dense")),
                        ("underground_decoration", i("underside_growth_outer"))])
    biome("stillwater_basins", "#0B1024", "#21446B", [], i("music.hold_still"), None, None, {"monster": [ender]},
          common_end + [("lakes", i("stillwater_basin")), ("underground_decoration", i("underside_growth_outer"))],
          extra={"minecraft:visual/fog_end_distance": 256.0} if False else None)
    biome("fallen_spires", "#2A0F25", "#5A1F4A", [p("dust_fall", 0.006)], i("music.ladders_to_nowhere"), None, None, {"monster": [ender]},
          common_end + [("vegetal_decoration", "minecraft:chorus_plant"), ("underground_decoration", i("underside_growth_outer"))])
    biome("the_hoard", "#1A2420", "#2B3A33", [], None, None, None, {"monster": [mob("minecraft:enderman", 4, 1, 2)]},
          [("raw_generation", "minecraft:end_island_decorated"), ("surface_structures", i("echo_shrine"))])
    biome("the_underside", "#0A1C1E", "#0E3A3A", [p("up_mote", 0.02)], i("music.undersong"), None, None,
          {"monster": [mob(i("underside_lurker"), 6, 1, 1)], "ambient": [mob(i("tidewisp"), 2, 2, 4)]},
          [("underground_decoration", i("underside_growth"))])
    biome("the_drift", "#08121C", "#0B1A2A", [p("dust_fall", 0.002)], i("music.below_the_bottom"), None, None, {},
          [("raw_generation", "minecraft:end_island_decorated"), ("local_modifications", i("debris_field")),
           ("surface_structures", i("skate_roost")), ("surface_structures", i("echo_shrine"))])
    biome("fracture_ring", "#1A0510", "#2A0812", [p("ash_inflow", 0.012)], None, None, None, {}, [])

def gen_modifiers():
    mod = f"{D}/neoforge/biome_modifier"
    write(f"{mod}/end_sky.json", {"type": i("end_sky"), "biomes": "#minecraft:is_end"})
    write(f"{mod}/gap_debris.json", {"type": "neoforge:add_features", "biomes": "minecraft:the_end",
                                     "features": [i("debris_field_gap"), i("skate_roost_gap")], "step": "surface_structures"})
    write(f"{mod}/small_islands_roosts.json", {"type": "neoforge:add_features", "biomes": ["minecraft:small_end_islands", "minecraft:end_barrens"],
                                               "features": [i("debris_field"), i("skate_roost")], "step": "surface_structures"})
    write(f"{mod}/highlands_shulkers.json", {"type": "neoforge:add_features", "biomes": "minecraft:end_highlands",
                                             "features": [i("wild_shulker_colony")], "step": "top_layer_modification"})
    write(f"{mod}/midlands_loam.json", {"type": "neoforge:add_features", "biomes": "minecraft:end_midlands",
                                        "features": [i("pale_loam_patch_sparse"), i("chorus_pod_scatter")], "step": "vegetal_decoration"})
    write(f"{mod}/outer_underside.json", {"type": "neoforge:add_features", "biomes": ["minecraft:end_highlands", "minecraft:end_midlands"],
                                          "features": [i("underside_growth_outer")], "step": "underground_decoration"})
    write(f"{mod}/midlands_grazers.json", {"type": "neoforge:add_spawns", "biomes": "minecraft:end_midlands",
                                           "spawners": [mob(i("chorus_grazer"), 2, 1, 2)]})

def gen_structures():
    for name, step, biomes, spacing, sep, salt in (
            ("fallen_spire", "surface_structures", f"#{NS}:has_structure/fallen_spire", 38, 18, 71203911),
            ("hoard", "surface_structures", f"#{NS}:has_structure/hoard", 25, 10, 33119247),
            ("driftwreck", "surface_structures", f"#{NS}:has_structure/driftwreck", 19, 8, 95511023),
            ("root_hollow", "underground_structures", f"#{NS}:has_structure/root_hollow", 14, 6, 40517781)):
        write(f"{D}/worldgen/structure/{name}.json", {"type": i("blueprint"), "generator": name, "biomes": biomes,
                                                      "spawn_overrides": {}, "step": step, "terrain_adaptation": "none"})
        write(f"{D}/worldgen/structure_set/{name}.json", {"placement": {"type": "minecraft:random_spread", "salt": salt, "separation": sep,
                                                                        "spacing": spacing, "spread_type": "triangular"},
                                                          "structures": [{"structure": i(name), "weight": 1}]})

def gen_dimensions():
    # The End keeps its terrain (minecraft:end noise) and dimension type; only the biome source gains our biomes (§20.4).
    write(os.path.join(DATA, "minecraft/dimension/the_end.json"), {"type": "minecraft:the_end", "generator": {
        "type": "minecraft:noise", "biome_source": {"type": i("end")}, "settings": "minecraft:end"}})
    end_type = vanilla_json("data/minecraft/dimension_type/the_end.json")
    ftype = dict(end_type)
    ftype["has_ender_dragon_fight"] = False
    ftype["has_skylight"] = False
    ftype["ambient_light"] = 0.12
    ftype["attributes"] = dict(end_type["attributes"])
    ftype["attributes"]["minecraft:visual/fog_color"] = "#1a0510"
    ftype["attributes"]["minecraft:visual/sky_color"] = "#2a0812"
    ftype["attributes"]["minecraft:visual/ambient_light_color"] = "#5a2638"
    ftype["attributes"]["minecraft:audio/background_music"] = {"default": {"max_delay": 1200, "min_delay": 600, "sound": i("music.unmade_1"),
                                                                          "replace_current_music": True}}
    ftype["attributes"]["neoforge:custom_skybox"] = i("fracture_sky")
    ftype.pop("default_clock", None)
    ftype.pop("timelines", None)
    write(f"{D}/dimension_type/the_fracture.json", ftype)
    write(f"{D}/dimension/the_fracture.json", {"type": i("the_fracture"), "generator": {"type": "minecraft:flat", "settings": {
        "biome": i("fracture_ring"), "features": False, "lakes": False, "layers": [], "structure_overrides": []}}})

if __name__ == "__main__":
    gen_features(); gen_biomes(); gen_modifiers(); gen_structures(); gen_dimensions()
    print(f"worldgen written: {len(WRITTEN)}")
