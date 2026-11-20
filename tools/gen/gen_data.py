"""Generates data: loot tables, recipes, tags, advancements, damage types, trims, jukebox song, worldgen, dimensions."""
import json, os
from common import *

D = os.path.join(DATA, NS)
MC = os.path.join(DATA, "minecraft")
NEO = os.path.join(DATA, "neoforge")

def i(n): return f"{NS}:{n}"


# --------------------------------------------------------------------------- 26.3 loot format conversion
def _cond(c):
    if c is SILK or c == SILK:
        return "minecraft:tool/can_silk_touch"
    if c == SHEARS:
        return "minecraft:tool/can_shear"
    c = dict(c)
    t = c.pop("condition")
    if t == "minecraft:block_state_property":
        return {"type": "minecraft:match_block", "blocks": c["block"], "state": c["properties"]}
    if t == "minecraft:entity_properties" and "predicate" in c and "flags" in c["predicate"]:
        c["predicate"] = {"minecraft:flags": c["predicate"]["flags"]}
    out = {"type": t}
    out.update(c)
    return out

def _conds(lst):
    cs = [_cond(c) for c in lst]
    return cs[0] if len(cs) == 1 else {"type": "minecraft:all_of", "terms": cs}

def convert(o):
    if isinstance(o, list):
        return [convert(x) for x in o]
    if not isinstance(o, dict):
        return o
    o = dict(o)
    if "functions" in o:
        fns = []
        for f in o.pop("functions"):
            f = dict(f)
            t = f.pop("function")
            nf = {"type": t}
            if "conditions" in f:
                nf["condition"] = _conds(f.pop("conditions"))
            nf.update({k: convert(v) for k, v in f.items()})
            fns.append(nf)
        o["modifier"] = fns
    if "conditions" in o:
        o["condition"] = _conds(o.pop("conditions"))
    if "condition" in o and isinstance(o["condition"], dict) and "condition" in o["condition"]:
        o["condition"] = _cond(o["condition"])
    return {k: convert(v) for k, v in o.items()}

# --------------------------------------------------------------------------- loot: blocks
SELF_DROP = """pale_loam stillstone polished_stillstone stillstone_bricks chiseled_stillstone weathered_purpur weathered_purpur_pillar
cracked_purpur_pillar purpur_rubble spire_lantern undershale polished_undershale undershale_bricks undershale_tiles lumen_crystal_block
lumen_lamp driftstone polished_driftstone driftstone_bricks tide_gauge null_obsidian ash_stone decorative_hoard_cache egg_cradle
gravity_anchor anchor_frame tidewisp_jar""".split()
FAMILIES = {"stillstone": True, "polished_stillstone": True, "stillstone_brick": True, "weathered_purpur": False, "undershale": True,
            "polished_undershale": True, "undershale_brick": True, "undershale_tile": True, "driftstone": True, "polished_driftstone": True,
            "driftstone_brick": True}
SILK_ONLY = {"rimglass": None, "stillglass": None, "stillglass_pane": None, "fracture_glass": None}

def block_loot(name, pools):
    write(f"{D}/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": convert(pools), "random_sequence": i(f"blocks/{name}")})

def drop_self(name, item=None):
    block_loot(name, [{"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}], "entries": [{"type": "minecraft:item", "name": i(item or name)}]}])

SILK = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
SHEARS = {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}}

def silk(name, fallback=None):
    entries = [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": i(name), "conditions": [SILK]}] + ([fallback] if fallback else [])}]
    block_loot(name, [{"rolls": 1, "entries": entries}])

def gen_block_loot():
    for n in SELF_DROP:
        drop_self(n)
    for base, wall in FAMILIES.items():
        slab = f"{base}_slab"
        block_loot(slab, [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": i(slab), "functions": [
            {"function": "minecraft:set_count", "count": 2, "add": False, "conditions": [{"condition": "minecraft:block_state_property",
             "block": i(slab), "properties": {"type": "double"}}]}, {"function": "minecraft:explosion_decay"}]}]}])
        drop_self(f"{base}_stairs")
        if wall:
            drop_self(f"{base}_wall")
    for n in SILK_ONLY:
        silk(n)
    silk("lilac_tuft")
    block_loot("lilac_tuft", [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": i("lilac_tuft"), "conditions": [SHEARS]}]}])
    silk("ceiling_moss", {"type": "minecraft:item", "name": i("ceiling_moss"), "conditions": [SHEARS]})
    silk("mirror_shard", {"type": "minecraft:item", "name": i("mirror_shard_fragment"), "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}}]})
    silk("lumen_cluster", {"type": "minecraft:item", "name": i("lumen_crystal_shard"), "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 4}},
        {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"}]})
    silk("lumen_crystal", {"type": "minecraft:item", "name": i("lumen_crystal_shard")})
    block_loot("voidroot", [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": i("voidroot"), "conditions": [SHEARS]},
        {"type": "minecraft:item", "name": i("voidroot_fiber"), "conditions": [{"condition": "minecraft:random_chance", "chance": 0.5}]}]}]}])
    block_loot("glowbulb", [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": i("glowbulb"), "conditions": [
        {"condition": "minecraft:block_state_property", "block": i("glowbulb"), "properties": {"ripe": "true"}}]}]}])
    block_loot("chorus_pod", [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": i("chorus_pod"), "conditions": [
        {"condition": "minecraft:block_state_property", "block": i("chorus_pod"), "properties": {"age": "2"}}]}]}])

# --------------------------------------------------------------------------- loot: entities
def ent_loot(name, pools):
    write(f"{D}/loot_table/entities/{name}.json", {"type": "minecraft:entity", "pools": convert(pools), "random_sequence": i(f"entities/{name}")})

def item_entry(item, lo=1, hi=1, looting=True, extra_fns=()):
    fns = []
    if (lo, hi) != (1, 1):
        fns.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if looting:
        fns.append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting", "count": {"type": "minecraft:uniform", "min": 0, "max": 1}})
    fns.extend(extra_fns)
    e = {"type": "minecraft:item", "name": item}
    if fns:
        e["functions"] = fns
    return e

def gen_entity_loot():
    ent_loot("void_skate", [{"rolls": 1, "entries": [item_entry(i("skate_membrane"), 1, 2)]},
                            {"rolls": 1, "entries": [item_entry(i("glow_gland"), 0, 1)]}])
    smelt = {"function": "minecraft:furnace_smelt", "conditions": [{"condition": "minecraft:entity_properties", "entity": "this",
             "predicate": {"flags": {"is_on_fire": True}}}]}
    ent_loot("chorus_grazer", [{"rolls": 1, "entries": [item_entry(i("grazer_flank"), 1, 2, extra_fns=(smelt,))]},
                               {"rolls": 1, "entries": [item_entry(i("grazer_frill"), 0, 1)]}])
    ent_loot("underside_lurker", [{"rolls": 1, "entries": [item_entry(i("lurker_lens"), 0, 1)]},
                                  {"rolls": 1, "entries": [item_entry(i("voidroot_fiber"), 1, 2)]}])
    ent_loot("hoarder", [{"rolls": 1, "entries": [item_entry("minecraft:ender_pearl", 0, 1)]},
                         {"rolls": 1, "entries": [{"type": "minecraft:item", "name": n, "weight": w} for n, w in
                          [("minecraft:grass_block", 4), ("minecraft:oak_log", 3), ("minecraft:dirt", 3), ("minecraft:poppy", 2),
                           ("minecraft:dandelion", 2), ("minecraft:iron_ore", 1), ("minecraft:coal_ore", 2), ("minecraft:oak_sapling", 2)]]}])
    ent_loot("spire_warden", [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": i("spire_key")}]},
                              {"rolls": 1, "entries": [item_entry(i("weathered_purpur"), 2, 4, looting=False)]},
                              {"rolls": 1, "entries": [item_entry("minecraft:ender_pearl", 1, 2)]}])
    ent_loot("echo", [{"rolls": 1, "entries": [item_entry(i("echo_dust"), 1, 2)]}])
    write(f"{D}/loot_table/entities/wild_shulker.json", {"type": "minecraft:entity", "pools": [], "random_sequence": i("entities/wild_shulker")})

# --------------------------------------------------------------------------- loot: chests / vaults / barter
def chest(name, pools, type_="minecraft:chest"):
    write(f"{D}/loot_table/{name}.json", {"type": type_, "pools": convert(pools), "random_sequence": i(name)})

def tablet_entry(weight=1):
    return {"type": "minecraft:item", "name": i("echo_tablet"), "weight": weight, "functions": [
        {"function": "worldsfall:random_tablet"}]}

def chance(p, entry):
    e = dict(entry)
    e.setdefault("conditions", []).append({"condition": "minecraft:random_chance", "chance": p})
    return {"rolls": 1, "entries": [e]}

def count(item, lo, hi):
    return {"type": "minecraft:item", "name": item, "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]}

def enchanted_book():
    return {"type": "minecraft:item", "name": "minecraft:book", "functions": [{"function": "minecraft:enchant_randomly", "options": [
        "minecraft:feather_falling", "minecraft:unbreaking", "minecraft:mending"]}]}

def gen_chests():
    templates = {"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": i("gale_upgrade_template"), "conditions": [{"condition": "minecraft:random_chance", "chance": 0.5}]},
        {"type": "minecraft:item", "name": i("still_upgrade_template")}]}
    spire_vault = [chance(0.8, count("minecraft:ender_pearl", 2, 6)), chance(0.35, tablet_entry()),
                   chance(0.25, templates), chance(0.2, {"type": "minecraft:item", "name": i("spire_armor_trim_smithing_template")}),
                   chance(0.3, count("minecraft:shulker_shell", 1, 2)), chance(0.5, count(i("stillglass"), 4, 12)),
                   chance(0.3, {"type": "minecraft:item", "name": i("cracked_void_anchor")}), chance(0.25, enchanted_book())]
    chest("vault/spire_vault", spire_vault)
    deep_elytra = chance(0.08, {"type": "minecraft:item", "name": "minecraft:elytra"})
    chest("vault/grand_vault", [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": i("spire_sigil")}]},
                                chance(0.3, {"type": "minecraft:item", "name": i("hover_upgrade_template")}), deep_elytra] + spire_vault[:4])
    chest("vault/spire_sigil", [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": i("spire_sigil")}]}])
    chest("vault/tide_sigil", [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": i("tide_sigil")}]}, chance(1.0, tablet_entry())])
    chest("vault/under_sigil", [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": i("under_sigil")}]}])
    chest("vault/peaceful_heart", [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": i("heart_of_stillness")}]},
                                   {"rolls": 1, "entries": [count(i("stillness_fragment"), 2, 4)]}])
    chest("vault/echo_shrine", [{"rolls": 1, "entries": [tablet_entry()]}])
    chest("vault/driftwreck_ladder", [chance(0.4, {"type": "minecraft:item", "name": i("spire_armor_trim_smithing_template")}),
                                      chance(0.9, count("minecraft:ender_pearl", 2, 5)), chance(0.3, tablet_entry()),
                                      chance(0.4, count(i("mirror_shard_fragment"), 1, 3))])
    chest("vault/driftwreck_pod_store", [{"rolls": 1, "entries": [count(i("chorus_pod"), 16, 32)]},
                                         chance(0.7, count(i("roasted_chorus_pod"), 4, 8)), chance(0.6, count(i("glowbulb"), 2, 6)),
                                         chance(0.5, count(i("cooked_grazer_flank"), 2, 5)),
                                         chance(0.35, {"type": "minecraft:item", "name": i("drift_armor_trim_smithing_template")})])
    chest("vault/driftwreck_shrine", [{"rolls": 1, "entries": [tablet_entry()]}, chance(0.5, count(i("lumen_crystal_shard"), 2, 5))])
    chest("vault/driftwreck_hulk", [{"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
        dict(count(i("mirror_shard_fragment"), 1, 4), weight=10), dict(count(i("glow_gland"), 1, 2), weight=5),
        dict(count("minecraft:ender_pearl", 1, 3), weight=8), dict(count(i("stillglass"), 2, 6), weight=6),
        dict(count(i("skate_membrane"), 1, 2), weight=5), dict(tablet_entry(2)), dict(count("minecraft:iron_ingot", 2, 6), weight=6)]}])
    chest("chests/spire_library", [chance(0.4, tablet_entry()), {"rolls": {"type": "minecraft:uniform", "min": 1, "max": 3}, "entries": [
        dict(count("minecraft:paper", 2, 6), weight=8), dict(count("minecraft:book", 1, 3), weight=6), dict(enchanted_book(), weight=2),
        dict(count("minecraft:ender_pearl", 1, 2), weight=4)]}])
    chest("chests/spire_workshop", [chance(0.25, templates), {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
        dict(count("minecraft:iron_ingot", 1, 4), weight=8), dict(count("minecraft:iron_nugget", 3, 9), weight=8),
        dict(count(i("stillglass"), 2, 6), weight=5), dict(count("minecraft:purpur_block", 4, 12), weight=6),
        dict(count(i("mirror_shard_fragment"), 1, 2), weight=3)]}])
    chest("chests/root_hollow", [chance(0.15, {"type": "minecraft:item", "name": i("undersong_armor_trim_smithing_template")}),
                                 {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
        dict(count(i("glow_gland"), 1, 2), weight=4), dict(count(i("lumen_crystal_shard"), 2, 6), weight=8),
        dict(count(i("voidroot_fiber"), 2, 6), weight=8), dict(count(i("glowbulb"), 1, 4), weight=6)]}])
    # Hoard barter (§16.4) — weights from the table, "nothing" as empty weight 7
    barter = [(count(i("chorus_pod"), 4, 8), 25), (count("minecraft:ender_pearl", 1, 3), 15), (count(i("stillglass"), 4, 8), 12),
              (count(i("mirror_shard_fragment"), 2, 4), 10), (count(i("lumen_crystal_shard"), 2, 4), 10),
              ({"type": "minecraft:item", "name": i("skate_membrane")}, 8), ({"type": "minecraft:item", "name": i("glow_gland")}, 5),
              (tablet_entry(), 4), ({"type": "minecraft:item", "name": i("gateway_compass")}, 3),
              ({"type": "minecraft:item", "name": i("spire_key")}, 1), ({"type": "minecraft:empty"}, 7)]
    chest("barter/hoard", [{"rolls": 1, "entries": [dict(e, weight=w) for e, w in barter]}])

# --------------------------------------------------------------------------- recipes
def shaped(name, pattern, key, result, n=1, category="misc"):
    write(f"{D}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern,
                                     "result": {"id": result, **({"count": n} if n > 1 else {})}})

def shapeless(name, ingredients, result, n=1, category="misc"):
    write(f"{D}/recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "category": category, "ingredients": ingredients,
                                     "result": {"id": result, **({"count": n} if n > 1 else {})}})

def cooking(name, ing, result, xp=0.35, kinds=("smelting", "smoking", "campfire_cooking")):
    times = {"smelting": 200, "smoking": 100, "campfire_cooking": 600, "blasting": 100}
    for k in kinds:
        write(f"{D}/recipe/{name}_{k}.json", {"type": f"minecraft:{k}", "category": "food", "cookingtime": times[k], "experience": xp,
                                              "ingredient": ing, "result": {"id": result}})

def stonecut(src, result, n=1):
    write(f"{D}/recipe/{result.split(':')[1]}_from_{src.split(':')[1]}_stonecutting.json",
          {"type": "minecraft:stonecutting", "ingredient": src, "result": {"id": result, **({"count": n} if n > 1 else {})}})

def smithing(name, template, base, addition, result, components=None):
    r = {"type": "minecraft:smithing_transform", "template": template, "base": base, "addition": addition, "result": {"id": result}}
    if components:
        r["result"]["components"] = components
    write(f"{D}/recipe/{name}.json", r)

def gen_recipes():
    shaped("skate_harness", ["LLL", "MFM", "N N"], {"L": "minecraft:leather", "M": i("skate_membrane"), "F": i("grazer_frill"), "N": "minecraft:iron_nugget"}, i("skate_harness"))
    shaped("cracked_void_anchor", [" C ", "GEG", " I "], {"C": "minecraft:iron_chain", "G": i("stillglass"), "E": "minecraft:ender_eye", "I": "minecraft:iron_ingot"}, i("cracked_void_anchor"))
    smithing("void_anchor", "minecraft:dragon_breath", i("cracked_void_anchor"), i("glow_gland"), i("void_anchor"))
    shaped("rift_tether", [" P ", "VIV", "V  "], {"P": "minecraft:ender_pearl", "V": i("voidroot_fiber"), "I": "minecraft:iron_ingot"}, i("rift_tether"), category="equipment")
    shaped("gateway_compass", [" D ", "DCD", " D "], {"D": i("echo_dust"), "C": "minecraft:compass"}, i("gateway_compass"), category="equipment")
    shaped("shulker_satchel", [" S ", "LCL", "LLL"], {"S": "minecraft:shulker_shell", "L": "minecraft:leather", "C": "minecraft:chest"}, i("shulker_satchel"), category="equipment")
    smithing("void_lens", i("echo_dust"), "minecraft:spyglass", i("lurker_lens"), i("void_lens"))
    smithing("shulker_satchel_upgrade", "minecraft:shulker_shell", i("shulker_satchel"), "minecraft:shulker_shell", i("shulker_satchel"),
             {"worldsfall:satchel_size": 18})
    shaped("lumen_lamp", ["UUU", "ULU", "URU"], {"U": i("undershale"), "L": i("lumen_crystal_shard"), "R": "minecraft:redstone"}, i("lumen_lamp"), category="redstone")
    shaped("tide_gauge", ["PGP", "PWP", "PRP"], {"P": "minecraft:purpur_block", "G": i("stillglass"), "W": i("bottle_of_stillwater"), "R": "minecraft:comparator"}, i("tide_gauge"), category="redstone")
    shaped("egg_cradle", ["LBL", "BOB", "LBL"], {"L": i("lumen_lamp"), "B": "minecraft:end_stone_bricks", "O": "minecraft:obsidian"}, i("egg_cradle"))
    shaped("gravity_anchor", ["POP", "OHO", "POP"], {"P": "minecraft:purpur_pillar", "O": i("null_obsidian"), "H": i("heart_of_stillness")}, i("gravity_anchor"))
    shaped("gravity_anchor_from_fragments", ["POP", "OHO", "POP"], {"P": "minecraft:purpur_pillar", "O": i("null_obsidian"), "H": i("stillness_fragment")}, i("gravity_anchor"))
    shaped("anchor_frame", ["P P", " S ", "P P"], {"P": "minecraft:purpur_slab", "S": i("stillness_fragment")}, i("anchor_frame"), n=8)
    shapeless("drift_stew", ["minecraft:bowl", i("roasted_chorus_pod"), i("cooked_grazer_flank"), i("glowbulb")], i("drift_stew"), category="misc")
    shapeless("rift_key", [i("stillness_fragment"), i("stillness_fragment"), i("spire_sigil"), i("tide_sigil"), i("under_sigil")], i("rift_key"))
    shapeless("dragon_egg_from_fragments", [i("dragon_egg_fragment")] * 4, "minecraft:dragon_egg")
    shaped("lumen_crystal_block", ["SS", "SS"], {"S": i("lumen_crystal_shard")}, i("lumen_crystal_block"), category="building")
    shaped("decorative_hoard_cache", ["EGE", "ECE", "EEE"], {"E": "minecraft:end_stone", "G": "minecraft:grass_block", "C": "minecraft:chest"}, i("decorative_hoard_cache"))
    shaped("stillglass_pane", ["GGG", "GGG"], {"G": i("stillglass")}, i("stillglass_pane"), n=16, category="building")
    shaped("tidewisp_jar_from_bottle", ["B"], {"B": i("bottled_tidewisp")}, i("bottled_tidewisp"))
    shapeless("stillglass", [i("stillwater_bucket"), "#minecraft:smelts_to_glass", "#minecraft:smelts_to_glass", "#minecraft:smelts_to_glass",
              "#minecraft:smelts_to_glass"], i("stillglass"), n=4, category="building")
    cooking("roasted_chorus_pod", i("chorus_pod"), i("roasted_chorus_pod"))
    cooking("cooked_grazer_flank", i("grazer_flank"), i("cooked_grazer_flank"))
    for n, src in (("polished_stillstone", "stillstone"), ("polished_undershale", "undershale"), ("polished_driftstone", "driftstone")):
        shaped(n, ["SS", "SS"], {"S": i(src)}, i(n), n=4, category="building")
    for n, src in (("stillstone_bricks", "polished_stillstone"), ("undershale_bricks", "polished_undershale"), ("undershale_tiles", "undershale_bricks"),
                   ("driftstone_bricks", "polished_driftstone")):
        shaped(n, ["SS", "SS"], {"S": i(src)}, i(n), n=4, category="building")
    shaped("chiseled_stillstone", ["S", "S"], {"S": i("stillstone_slab")}, i("chiseled_stillstone"), category="building")
    shaped("weathered_purpur_pillar", ["S", "S"], {"S": i("weathered_purpur_slab")}, i("weathered_purpur_pillar"), category="building")
    # families: slab/stairs/wall crafting + stonecutting
    fam_src = {"stillstone": "stillstone", "polished_stillstone": "polished_stillstone", "stillstone_brick": "stillstone_bricks",
               "weathered_purpur": "weathered_purpur", "undershale": "undershale", "polished_undershale": "polished_undershale",
               "undershale_brick": "undershale_bricks", "undershale_tile": "undershale_tiles", "driftstone": "driftstone",
               "polished_driftstone": "polished_driftstone", "driftstone_brick": "driftstone_bricks"}
    for base, src in fam_src.items():
        s = i(src)
        shaped(f"{base}_slab", ["SSS"], {"S": s}, i(f"{base}_slab"), n=6, category="building")
        shaped(f"{base}_stairs", ["S  ", "SS ", "SSS"], {"S": s}, i(f"{base}_stairs"), n=4, category="building")
        stonecut(s, i(f"{base}_slab"), 2)
        stonecut(s, i(f"{base}_stairs"))
        if FAMILIES[base]:
            shaped(f"{base}_wall", ["SSS", "SSS"], {"S": s}, i(f"{base}_wall"), n=6, category="building")
            stonecut(s, i(f"{base}_wall"))
    for chain in (("stillstone", ["polished_stillstone", "stillstone_bricks", "chiseled_stillstone"]),
                  ("undershale", ["polished_undershale", "undershale_bricks", "undershale_tiles"]),
                  ("driftstone", ["polished_driftstone", "driftstone_bricks"]),
                  ("weathered_purpur", ["weathered_purpur_pillar"])):
        for target in chain[1]:
            stonecut(i(chain[0]), i(target))
    stonecut("minecraft:purpur_block", i("weathered_purpur"))
    # elytra upgrades (§9.7): smithing writes the data component
    for up, mat in (("gale", i("skate_membrane")), ("still", i("mirror_shard_fragment")), ("hover", i("glow_gland"))):
        smithing(f"elytra_{up}_upgrade", i(f"{up}_upgrade_template"), "minecraft:elytra", mat, "minecraft:elytra",
                 {"worldsfall:elytra_upgrade": up})
    # trims
    for p in ("spire", "drift", "undersong"):
        write(f"{D}/recipe/{p}_armor_trim_smithing_template_smithing_trim.json", {"type": "minecraft:smithing_trim",
              "addition": "#minecraft:trim_materials", "base": "#minecraft:trimmable_armor", "pattern": i(p),
              "template": i(f"{p}_armor_trim_smithing_template")})
    # brewing (§9.13): data-driven brewing recipes
    def brew(name, inp, reagent, out_potion, item="minecraft:potion"):
        write(f"{D}/recipe/brewing/{name}.json", {"type": "minecraft:brewing",
              "input": {"item": item, "potion_contents": {"potions": inp}}, "reagent": {"item": reagent},
              "output": {"id": item, "components": {"minecraft:potion_contents": {"potion": out_potion}}}})
    for item in ("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion"):
        pre = item.split(":")[1]
        brew(f"{pre}_steadiness", "minecraft:awkward", i("mirror_shard_fragment"), i("steadiness"), item)
        brew(f"{pre}_long_steadiness", i("steadiness"), "minecraft:redstone", i("long_steadiness"), item)
        brew(f"{pre}_tidesight", "minecraft:awkward", i("lumen_crystal_shard"), i("tidesight"), item)
        brew(f"{pre}_long_tidesight", i("tidesight"), "minecraft:redstone", i("long_tidesight"), item)
    write(f"{D}/recipe/brewing/stillwater_tea.json", {"type": "minecraft:brewing", "input": {"item": i("bottle_of_stillwater")},
          "reagent": {"item": i("lilac_tuft")}, "output": {"id": i("stillwater_tea")}})

# --------------------------------------------------------------------------- tags
def tag(kind, ns, name, values, replace=False):
    write(os.path.join(DATA, ns, "tags", kind, f"{name}.json"), {"replace": replace, "values": values})

def gen_tags():
    stone_like = ["pale_loam", "stillstone", "polished_stillstone", "stillstone_bricks", "chiseled_stillstone", "weathered_purpur",
                  "weathered_purpur_pillar", "cracked_purpur_pillar", "purpur_rubble", "spire_lantern", "undershale", "polished_undershale",
                  "undershale_bricks", "undershale_tiles", "lumen_crystal", "lumen_cluster", "lumen_crystal_block", "mirror_shard", "driftstone",
                  "polished_driftstone", "driftstone_bricks", "null_obsidian", "ash_stone", "unmade_shard", "decorative_hoard_cache",
                  "egg_cradle", "gravity_anchor", "lumen_lamp", "tide_gauge"]
    fam = []
    for base, wall in FAMILIES.items():
        fam += [f"{base}_slab", f"{base}_stairs"] + ([f"{base}_wall"] if wall else [])
    tag("block", "minecraft", "mineable/pickaxe", [i(n) for n in stone_like + fam])
    tag("block", "minecraft", "mineable/axe", [i("voidroot")])
    tag("block", "minecraft", "mineable/hoe", [i("ceiling_moss"), i("lilac_tuft")])
    tag("block", "minecraft", "needs_diamond_tool", [i("null_obsidian")])
    tag("block", "minecraft", "needs_iron_tool", [i("egg_cradle"), i("gravity_anchor")])
    tag("block", "minecraft", "climbable", [i("voidroot")])
    tag("block", "minecraft", "slabs", [i(f"{b}_slab") for b in FAMILIES])
    tag("block", "minecraft", "stairs", [i(f"{b}_stairs") for b in FAMILIES])
    tag("block", "minecraft", "walls", [i(f"{b}_wall") for b, w in FAMILIES.items() if w])
    tag("block", "minecraft", "dragon_immune", [i("null_obsidian"), i("keystone_socket"), i("spire_vault"), i("drift_cache"), i("sigil_pedestal"),
                                               i("fracture_rift_frame"), i("fracture_rift"), i("still_throne"), i("hoard_cache")])
    tag("block", "minecraft", "wither_immune", [i("null_obsidian"), i("fracture_rift_frame"), i("fracture_rift")])
    tag("block", "minecraft", "replaceable_by_trees", [i("lilac_tuft"), i("ceiling_moss")])
    tag("block", "minecraft", "sword_efficient", [i("lilac_tuft"), i("chorus_pod"), i("glowbulb"), i("ceiling_moss")])
    tag("block", NS, "tether_unlatchable", [i("stillglass"), i("stillglass_pane"), i("fracture_glass"), i("null_obsidian"), i("rimglass")])
    tag("block", NS, "stillwater_converts", ["minecraft:end_stone"])
    tag("block", NS, "large_island_surface", ["minecraft:end_stone", i("pale_loam")])
    tag("block", NS, "hoard_pile", ["minecraft:grass_block", "minecraft:dirt", "minecraft:stone", "minecraft:cobblestone", "minecraft:sand",
                                    "minecraft:oak_log", "minecraft:birch_log", "#minecraft:flowers", "#minecraft:saplings", "minecraft:coal_ore",
                                    "minecraft:iron_ore", "minecraft:copper_ore", "minecraft:gold_ore", "minecraft:lapis_ore"])
    # items
    tag("item", NS, "hoard_value_1", ["minecraft:grass_block", "minecraft:dirt", "minecraft:sand", "minecraft:stone", "minecraft:cobblestone",
                                      "minecraft:gravel", "minecraft:red_sand", "minecraft:coarse_dirt", "minecraft:moss_block"])
    tag("item", NS, "hoard_value_2", ["#minecraft:flowers", "#minecraft:saplings", "#minecraft:logs"])
    tag("item", NS, "hoard_value_4", ["minecraft:coal_ore", "minecraft:copper_ore", "minecraft:deepslate_coal_ore", "minecraft:deepslate_copper_ore"])
    tag("item", NS, "hoard_value_6", ["minecraft:iron_ore", "minecraft:deepslate_iron_ore"])
    tag("item", NS, "hoard_value_10", ["minecraft:gold_ore", "minecraft:lapis_ore", "minecraft:deepslate_gold_ore", "minecraft:deepslate_lapis_ore"])
    tag("item", NS, "hoard_accepts", [f"#{NS}:hoard_value_{v}" for v in (1, 2, 4, 6, 10)])
    tag("item", NS, "hoard_refuses", ["minecraft:diamond", "minecraft:emerald", "minecraft:netherite_ingot", "minecraft:diamond_ore",
                                      "minecraft:emerald_ore", "minecraft:deepslate_diamond_ore", "minecraft:deepslate_emerald_ore",
                                      "minecraft:end_stone", "minecraft:purpur_block", "minecraft:chorus_fruit"] + [i(n) for n in stone_like])
    tag("item", "minecraft", "brewing_potion_inputs", [i("bottle_of_stillwater")])
    tag("item", NS, "skate_food", [i("chorus_pod")])
    tag("item", NS, "skate_breeding", [i("glowbulb")])
    tag("item", NS, "grazer_food", [i("chorus_pod")])
    tag("item", NS, "wyrm_food", [i("roasted_chorus_pod"), i("glow_gland")])
    tag("item", NS, "shulker_lure", [i("chorus_pod")])
    tag("item", NS, "satchel_forbidden", ["#minecraft:shulker_boxes", i("shulker_satchel")])
    tag("item", NS, "rift_openers", [i("spire_sigil"), i("tide_sigil"), i("under_sigil"), i("rift_key")])
    tag("item", NS, "void_anchors", [i("void_anchor"), i("cracked_void_anchor")])
    tag("item", "minecraft", "trim_materials", [i("grazer_frill"), i("lumen_crystal_shard"), i("mirror_shard_fragment")])
    tag("item", "minecraft", "trim_templates", [i(f"{p}_armor_trim_smithing_template") for p in ("spire", "drift", "undersong")])
    tag("item", "minecraft", "slabs", [i(f"{b}_slab") for b in FAMILIES])
    tag("item", "minecraft", "stairs", [i(f"{b}_stairs") for b in FAMILIES])
    tag("item", "minecraft", "walls", [i(f"{b}_wall") for b, w in FAMILIES.items() if w])
    tag("item", "minecraft", "trim_materials", [i("grazer_frill"), i("lumen_crystal_shard"), i("mirror_shard_fragment")])
    # entities
    tag("entity_type", NS, "gravity_immune", [i("the_unmade"), i("tether_pylon"), i("flying_block"), i("void_capsule"), i("void_skate"), i("tidewisp"), i("shardling")])
    tag("entity_type", NS, "erosion_immune", [i("the_unmade"), i("shardling"), i("tidewisp"), i("void_skate"), i("wyrm"), "minecraft:ender_dragon",
                                              "minecraft:enderman", "minecraft:endermite", "minecraft:shulker"])
    tag("entity_type", NS, "stillwater_sensitive", ["minecraft:enderman", i("hoarder"), i("spire_warden"), i("echo")])
    tag("entity_type", NS, "new_end_mobs", [i(n) for n in ("void_skate", "chorus_grazer", "underside_lurker", "hoarder", "spire_warden", "echo",
                                                           "wild_shulker", "tidewisp", "shardling")])
    tag("entity_type", "minecraft", "fall_damage_immune", [i("void_skate"), i("tidewisp"), i("shardling"), i("the_unmade")])
    # damage types
    tag("damage_type", "minecraft", "bypasses_armor", [i("void_erosion"), i("void_pulse")])
    tag("damage_type", "minecraft", "bypasses_shield", [i("void_erosion"), i("void_pulse")])
    tag("damage_type", "minecraft", "no_knockback", [i("void_erosion")])
    tag("damage_type", "minecraft", "bypasses_cooldown", [i("void_erosion")])
    # structures
    tag("worldgen/structure", NS, "driftwrecks", [i("driftwreck")])
    tag("worldgen/structure", NS, "fallen_spires", [i("fallen_spire")])
    for s in ("fallen_spire", "hoard", "driftwreck", "root_hollow"):
        tag("worldgen/structure", NS, s, [i(s)])
    tag("worldgen/structure", NS, "deep_root_hollow", [i("root_hollow")])
    tag("worldgen/structure", NS, "skate_roost", [])
    tag("worldgen/structure", NS, "echo_shrine", [])
    # biomes
    ours = ["chorus_reach", "stillwater_basins", "fallen_spires", "the_hoard", "the_underside", "the_drift"]
    tag("worldgen/biome", "minecraft", "is_end", [i(b) for b in ours])
    tag("worldgen/biome", NS, "outer_islands", [i(b) for b in ours] + ["minecraft:end_highlands", "minecraft:end_midlands", "minecraft:end_barrens",
                                                                        "minecraft:small_end_islands"])
    tag("worldgen/biome", NS, "has_structure/fallen_spire", [i("fallen_spires"), i("stillwater_basins"), "minecraft:end_highlands"])
    tag("worldgen/biome", NS, "has_structure/hoard", [i("the_hoard"), i("the_drift"), "minecraft:small_end_islands"])
    tag("worldgen/biome", NS, "has_structure/driftwreck", [i("the_drift"), "minecraft:small_end_islands", "minecraft:end_barrens"])
    tag("worldgen/biome", NS, "has_structure/root_hollow", [i("the_underside"), "minecraft:end_highlands", i("chorus_reach"), i("fallen_spires"),
                                                            i("stillwater_basins")])
    tag("worldgen/biome", "minecraft", "has_structure/end_city", [i("fallen_spires"), i("chorus_reach"), i("stillwater_basins")])

# --------------------------------------------------------------------------- advancements (§12.3)
ADVANCEMENTS = [
    # id, parent, icon, frame, hidden
    ("root", None, "lumen_crystal", "task", False),
    ("nothing_holds", "root", "fracture_rift_frame", "task", False),
    ("underneath", "nothing_holds", "lumen_cluster", "task", False),
    ("something_clicks", "underneath", "lurker_lens", "task", False),
    ("patience_of_the_void", "root", "chorus_pod", "goal", False),
    ("across_the_gap", "patience_of_the_void", "skate_harness", "challenge", False),
    ("pale_pasture", "root", "grazer_frill", "task", False),
    ("fair_trade", "root", "decorative_hoard_cache", "task", False),
    ("light_fingers", "fair_trade", "hoard_cache", "task", True),
    ("a_hole_full_of_stars", "root", "stillwater_bucket", "task", False),
    ("the_tide_remembers", "a_hole_full_of_stars", "drift_cache", "goal", False),
    ("ladders_to_nowhere", "root", "spire_key", "goal", False),
    ("wings_revised", "ladders_to_nowhere", "gale_upgrade_template", "task", False),
    ("carry_the_world", "fair_trade", "shulker_satchel", "task", False),
    ("anchored", "a_hole_full_of_stars", "void_anchor", "task", False),
    ("echoes_of_them", "ladders_to_nowhere", "echo_tablet", "goal", False),
    ("the_whole_story", "echoes_of_them", "echo_tablet", "challenge", False),
    ("three_keys", "the_tide_remembers", "tide_sigil", "goal", False),
    ("below_the_bottom", "three_keys", "null_obsidian", "goal", False),
    ("unmade", "below_the_bottom", "heart_of_stillness", "challenge", False),
    ("stillness", "unmade", "gravity_anchor", "task", False),
    ("calm_waters", "unmade", "still_throne", "goal", False),
    ("warm_hands", "root", "egg_cradle", "goal", False),
    ("fully_grown", "warm_hands", "slumbering_egg", "challenge", False),
]

def gen_advancements():
    for aid, parent, icon, frame, hidden in ADVANCEMENTS:
        criteria = {"done": {"trigger": "minecraft:impossible"}}
        if aid == "root":
            criteria = {"entered_end": {"trigger": "minecraft:changed_dimension", "conditions": {"to": "minecraft:the_end"}}}
        if aid == "wings_revised":
            criteria["crafted"] = {"trigger": "minecraft:recipe_crafted", "conditions": {"recipe_id": i("elytra_gale_upgrade")}}
            criteria = {"gale": {"trigger": "minecraft:recipe_crafted", "conditions": {"recipe_id": i("elytra_gale_upgrade")}},
                        "still": {"trigger": "minecraft:recipe_crafted", "conditions": {"recipe_id": i("elytra_still_upgrade")}},
                        "hover": {"trigger": "minecraft:recipe_crafted", "conditions": {"recipe_id": i("elytra_hover_upgrade")}}}
        if aid == "three_keys":
            criteria = {"all": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": i("spire_sigil")},
                         {"items": i("tide_sigil")}, {"items": i("under_sigil")}]}}}
        display = {"icon": {"id": i(icon)}, "title": {"translate": f"advancements.worldsfall.{aid}.title"},
                   "description": {"translate": f"advancements.worldsfall.{aid}.description"}, "frame": frame,
                   "show_toast": aid != "root", "announce_to_chat": aid != "root", "hidden": hidden}
        if aid == "root":
            display["background"] = "worldsfall:gui/advancements/backgrounds/worldsfall"
        adv = {"criteria": criteria, "display": display, "requirements": [[k] for k in criteria] if aid != "wings_revised" else [list(criteria)]}
        if parent:
            adv["parent"] = i(parent)
        write(f"{D}/advancement/{aid}.json", adv)

# --------------------------------------------------------------------------- misc registries
def gen_misc():
    for name, msg, scaling in (("void_erosion", "worldsfall.void_erosion", "never"), ("lurker_snare", "worldsfall.lurker_snare", "when_caused_by_living_non_player"),
                               ("warden_slam", "worldsfall.warden_slam", "when_caused_by_living_non_player"),
                               ("unmade_shard", "worldsfall.unmade_shard", "always"), ("unmade_tendril", "worldsfall.unmade_tendril", "always"),
                               ("void_pulse", "worldsfall.void_pulse", "always")):
        write(f"{D}/damage_type/{name}.json", {"exhaustion": 0.0 if name in ("void_erosion", "void_pulse") else 0.1, "message_id": msg, "scaling": scaling})
    write(f"{D}/jukebox_song/unmade.json", {"comparator_output": 15, "description": {"translate": "jukebox_song.worldsfall.unmade"},
                                            "length_in_seconds": 118.0, "sound_event": i("music_disc.unmade")})
    for p in ("spire", "drift", "undersong"):
        write(f"{D}/trim_pattern/{p}.json", {"asset_id": i(p), "decal": False, "description": {"translate": f"trim_pattern.worldsfall.{p}"}})
    for m, color in (("lumen", "#2FC9BB"), ("still", "#3A3550"), ("frill", "#B493C4")):
        write(f"{D}/trim_material/{m}.json", {"description": {"color": color, "translate": f"trim_material.worldsfall.{m}"},
                                              "palette_id": f"{NS}:trim/{m}"})

if __name__ == "__main__":
    gen_block_loot(); gen_entity_loot(); gen_chests(); gen_recipes(); gen_tags(); gen_advancements(); gen_misc()
    print(f"data written: {len(WRITTEN)}")
