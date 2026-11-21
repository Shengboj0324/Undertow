"""Generated resources for the expansion (1.2 → 2.0, proposal "Expansion & Dragon"). Called from gen_assets.py,
gen_data.py and gen_lang.py so `tools/gen/gen_all.sh` keeps regenerating everything."""
import copy
import json
import os

from common import *  # noqa: F401,F403

PACTS = ["tide", "shards", "echoes", "hoard", "memory", "stillness", "lone", "depths"]
PACT_NAMES = {"tide": "Tide", "shards": "Shards", "echoes": "Echoes", "hoard": "the Hoard", "memory": "Memory",
              "stillness": "Stillness", "lone": "the Lone Warden", "depths": "Depths"}
PACT_MATERIALS = {"tide": "worldsfall:bottle_of_stillwater", "shards": "worldsfall:unmade_shard", "echoes": "worldsfall:lurker_lens",
                  "hoard": "worldsfall:chorus_pod", "memory": "worldsfall:echo_tablet", "stillness": "minecraft:milk_bucket",
                  "lone": "minecraft:dragon_breath"}

# simple item sprites (texture item/<name>.png made by the art pipeline)
ITEM_SPRITES = (
    [f"pact_glyph_{p}" for p in PACTS] + [f"pact_crystal_{p}" for p in PACTS]
    + ["stillness_crystal", "dragon_scale", "wyrm_barding", "tuning_fork", "warden_banner_pattern", "wardenborn_seal", "music_disc_warden"]
    + ["causeway_keystone"]
)
EMISSIVE_ITEM_SPRITES = []

# items whose names differ from the title-cased id
ITEM_NAMES = {f"pact_glyph_{p}": f"Pact Glyph of {PACT_NAMES[p]}" for p in PACTS}
ITEM_NAMES.update({f"pact_crystal_{p}": f"Pact Crystal of {PACT_NAMES[p]}" for p in PACTS})
ITEM_NAMES.update({"music_disc_warden": "Music Disc", "warden_banner_pattern": "Banner Pattern"})
BLOCK_NAMES = {"wardens_lantern": "Warden's Lantern", "causeway_socket": "Causeway Socket", "causeway_lantern": "Causeway Lantern",
               "causeway_plaque": "Causeway Plaque", "drift_buoy": "Drift Buoy", "roost_post": "Roost Post"}

# (id, parent, icon, frame, hidden)
ADVANCEMENTS = [
    ("cycle_warning", "root", "stillness_crystal", "task", True),
    ("wardensong", "root", "minecraft:end_crystal", "goal", False),
    ("warden_trial", "root", "pact_glyph_tide", "task", False),
    ("all_four_seals", "warden_trial", "music_disc_warden", "challenge", False),
    ("the_long_way_down", "warden_trial", "pact_glyph_depths", "challenge", False),
    ("warden_released", "calm_waters", "stillness_crystal", "challenge", False),
    ("elder_passage", "warden_released", "gateway_compass", "goal", False),
    ("the_long_walk", "across_the_gap", "causeway_keystone", "challenge", False),
    ("the_pillars_remember", "nothing_holds", "pact_glyph_depths", "goal", False),
]

LANG = {
    # 1.2 the Warden Cycle
    "entity.worldsfall.warden_dragon": "Ender Dragon · Weight %s",
    "entity.worldsfall.listening_dragon": "The Listening Warden",
    "entity.worldsfall.elder_warden": "Elder Warden",
    "entity.worldsfall.lumen_heart": "Lumen Heart",
    "block.worldsfall.banner.warden": "Warden",
    "block.minecraft.banner.worldsfall.warden.white": "White Warden",
    "item.worldsfall.warden_banner_pattern.desc": "Warden",
    "item.worldsfall.music_disc_warden.desc": "Worldsfall — Warden",
    "jukebox_song.worldsfall.warden": "Worldsfall — Warden",
    "pact.worldsfall.tide": "Pact of the Tide", "pact.worldsfall.shards": "Pact of Shards", "pact.worldsfall.echoes": "Pact of Echoes",
    "pact.worldsfall.hoard": "Pact of the Hoard", "pact.worldsfall.memory": "Pact of Memory", "pact.worldsfall.stillness": "Pact of Stillness",
    "pact.worldsfall.lone": "Pact of the Lone Warden", "pact.worldsfall.depths": "Pact of Depths",
    "move.worldsfall.gravity_breath": "Gravity Breath", "move.worldsfall.tide_dive": "Tide Dive", "move.worldsfall.wing_gale": "Wing Gale",
    "message.worldsfall.warden.pacts": "The crystals bind the Warden: %s (Weight %s).",
    "message.worldsfall.warden.tide_creeps": "The tide ring creeps inward.",
    "message.worldsfall.warden.thieves": "Hoarders have come for what you drop.",
    "message.worldsfall.warden.learns": "The dragon remembers: %s",
    "message.worldsfall.warden.dives": "The Warden folds its wings and dives into the Descent.",
    "message.worldsfall.warden.perches": "It clings to the ceiling — climb the shelves!",
    "message.worldsfall.warden.drift": "It breaks out into the Drift. The tide rises to meet it.",
    "message.worldsfall.warden.reward": "The Warden yields (Weight %s).",
    "message.worldsfall.warden.released": "The Warden is free. The exit portal no longer answers.",
    "message.worldsfall.warden.not_ready": "Stillness Crystals answer only when The Unmade is gone and the Heart sits in the Still Throne.",
    "message.worldsfall.warden.listening": "The dragon arrives calm. Keep all four Stillness Crystals whole while it listens.",
    "message.worldsfall.warden.vigil_struck": "You struck the listening Warden. It remembers what it was made for.",
    "message.worldsfall.warden.vigil_broken": "A Stillness Crystal broke. The Warden remembers what it was made for.",
    "message.worldsfall.warden.released_toast": "The Warden has nothing left to guard. It flies to the Far Rim.",
    "message.worldsfall.elder.wants": "The Elder Warden waits for an offering of Roasted Chorus Pods.",
    "message.worldsfall.elder.rested": "The Elder Warden has flown today. Come back tomorrow.",
    "message.worldsfall.elder.more": "The offering is %s Roasted Chorus Pods.",
    "message.worldsfall.elder.no_gateway": "Hold a Gateway Compass bound to the gateway you want to reach.",
    "message.worldsfall.elder.passage": "The Elder Warden lowers its head. Hold on.",
    "wardensong.worldsfall.0": "I was set here when the crack first opened.",
    "wardensong.worldsfall.1": "They called me Warden, and gave me ten lights to keep.",
    "wardensong.worldsfall.2": "The island is a lid. I am the weight upon it.",
    "wardensong.worldsfall.3": "Below, the stairs they cut. Below the stairs, the Heart.",
    "wardensong.worldsfall.4": "The Heart was meant to still the void. It learned to unmake instead.",
    "wardensong.worldsfall.5": "Every traveller who comes, I turn away. It is kinder.",
    "wardensong.worldsfall.6": "If you break the lights, the lid will crack.",
    "wardensong.worldsfall.7": "If the lid cracks, you will go down. Everyone goes down.",
    "wardensong.worldsfall.8": "I am tired, traveller. I have guarded this a very long time.",
    "wardensong.worldsfall.9": "When it is still at last, call me with quiet crystals. I will listen.",
    "command.worldsfall.warden.status": "Warden: release %s, pacts %s, depths stage %s",
    # 1.3 the Gap
    "message.worldsfall.causeway.already": "This span is already being rebuilt.",
    "message.worldsfall.causeway.restoring": "The Causeway remembers its shape (%s of %s spans).",
    "message.worldsfall.causeway.complete": "The Pilgrim's Causeway is whole again. Its lanterns burn calm silver.",
    "message.worldsfall.buoy.open_air": "A Drift Buoy needs open air: nothing within three blocks.",
    "message.worldsfall.pillars.read": "Pillar %s of %s: \"%s\"",
    "message.worldsfall.pillars.out_of_order": "\"%s\" — the words must be read in order, around the ring.",
    "message.worldsfall.pillars.phrase": "\"Where the Warden falls, follow it down through the dark.\" A glyph forms in your hand.",
}
_WORDS = ["Where", "the", "Warden", "falls,", "follow", "it", "down", "through", "the", "dark."]
_GLYPHS = ["ᚹ", "ᚦ", "ᚱ", "ᚠ", "ᛗ", "ᛁ", "ᛞ", "ᛏ", "ᚦ", "ᛟ"]
for _i, (_w, _g) in enumerate(zip(_WORDS, _GLYPHS)):
    LANG[f"pillar.worldsfall.word.{_i}"] = _w
    LANG[f"pillar.worldsfall.glyph.{_i}"] = _g
DESC = {
    "pact_glyph_tide": ("Craft with an End Crystal: the next dragon's island is ringed by a creeping tide. Weight 2.", None),
    "pact_glyph_shards": ("Each destroyed pillar crystal leaves orbiting shards that fire volleys. Weight 2.", None),
    "pact_glyph_echoes": ("Echoes hide among the arena's end stone and obsidian. Weight 1.", None),
    "pact_glyph_hoard": ("Two Hoarders come to steal what you drop. Weight 1.", None),
    "pact_glyph_memory": ("After each perch the dragon learns a new move. Weight 3.", None),
    "pact_glyph_stillness": ("Potion effects stop working near the portal. Weight 2.", None),
    "pact_glyph_lone": ("Crystals no longer heal, but the dragon is stronger and faster. Weight 1.", None),
    "pact_glyph_depths": ("The Descending Warden: the fight goes down through the End. Weight 4.", "Read on the pillars, word by word."),
    "stillness_crystal": ("Four of these on the exit portal call the dragon calm, to be released for good.", "It was only ever a guard."),
    "dragon_scale": ("Reach and control, never damage: scaled elytra, Drake barding, Warden's Lanterns, Tuning Forks.", None),
    "wyrm_barding": ("Armour for a Drake: doubles its health.", None),
    "tuning_fork": ("Use on an End Gateway to attune it to the Gateway Lattice.", None),
    "wardenborn_seal": ("Press onto an egg in an Egg Cradle: the Wyrm that hatches has slate-and-gold scales.", None),
    "wardens_lantern": ("Endermen and Echoes cannot teleport within 32 blocks, and Echoes do not spawn.", None),
    "causeway_keystone": ("Set in a Causeway Socket to rebuild the missing span of the Pilgrim's Causeway.", "Cheaper than a Spire Key; the road wants to be walked."),
    "drift_buoy": ("Use in open air to set a floating lantern. Skates perch on it. It pops if the tide reaches it.", None),
    "roost_post": ("Tamed Skates perched on it recover stamina twice as fast.", None),
}
for _p in PACTS:
    DESC[f"pact_crystal_{_p}"] = ("Place on the exit portal (one of the four respawn crystals) to bind the next dragon to this pact.", None)

ADV_TEXT = {
    "cycle_warning": ("The Last Summons", "Stillness Crystals release the dragon forever; it cannot be summoned again"),
    "wardensong": ("Wardensong", "Hear all ten lines of the Warden's memory"),
    "warden_trial": ("Trial of the Warden", "Defeat a dragon bound by Crystal Pacts"),
    "all_four_seals": ("All Four Seals", "Defeat a dragon at the full Weight of 12"),
    "the_long_way_down": ("The Long Way Down", "Defeat the Descending Warden"),
    "warden_released": ("Where the Warden Rests", "Release the dragon with Stillness Crystals"),
    "elder_passage": ("Warden's Passage", "Ride the Elder Warden to a gateway"),
    "the_long_walk": ("The Long Walk", "Restore the last span of the Pilgrim's Causeway"),
    "the_pillars_remember": ("The Pillars Remember", "Read the ten pillar glyphs in order through a Void Lens"),
}

# -------------------------------------------------------------------------------------------------- assets
def assets(g):
    for n in ITEM_SPRITES:
        g.item_sprite(n, [f"{NS}:item/{n}"])
    for n in EMISSIVE_ITEM_SPRITES:
        g.item_sprite(n, [f"{NS}:item/{n}", f"{NS}:item/{n}_emissive"], emissive_layers=(1,))
    lantern(g, "wardens_lantern")
    gap_assets(g)
    # elytra looks: upgrade × Dragon Scale overlay
    for up in ("", "gale", "still", "hover", "tempest"):
        for scaled in (False, True):
            if not up and not scaled:
                continue
            name = "elytra" + (f"_{up}" if up else "") + ("_scaled" if scaled else "")
            write(os.path.join(ASSETS, f"equipment/{name}.json"), {"layers": {"wings": [{"texture": f"{NS}:{name}"}]}})


def gap_assets(g):
    from PIL import Image
    # Causeway lanterns: magenta (the spire lantern sheet) and calm silver (the same sheet, recoloured)
    tex_dir = os.path.join(ASSETS, "textures/block")
    for src, dst in (("spire_lantern", "causeway_lantern_calm"), ("spire_lantern_emissive", "causeway_lantern_calm_emissive")):
        im = Image.open(os.path.join(tex_dir, src + ".png")).convert("RGBA")
        px = im.load()
        for y in range(im.height):
            for x in range(im.width):
                r, gg, bb, a = px[x, y]
                if a == 0:
                    continue
                lum = (r * 0.3 + gg * 0.59 + bb * 0.11) / 255
                if r > bb + 10 or (r > 150 and bb > 150 and gg < r - 20):
                    v = int(170 + 80 * lum)
                    px[x, y] = (min(255, int(v * 0.92)), min(255, int(v * 0.96)), min(255, v), a)
        im.save(os.path.join(tex_dir, dst + ".png"))
    tmpl = vanilla_json("assets/minecraft/models/block/template_lantern.json")
    tmplh = vanilla_json("assets/minecraft/models/block/template_hanging_lantern.json")
    variants = {}
    for calm, tex in ((False, "spire_lantern"), (True, "causeway_lantern_calm")):
        for hanging, t in ((False, tmpl), (True, tmplh)):
            name = "causeway_lantern" + ("_calm" if calm else "") + ("_hanging" if hanging else "")
            els = copy.deepcopy(t["elements"])
            glow = g.emissive_copy(els, {"#lantern": "#glow"})
            write(f"{g.BM}/{name}.json", {"parent": "minecraft:block/block", "textures": {"lantern": g.b(tex), "glow": g.b(tex + "_emissive"),
                                                                                         "particle": g.b(tex)}, "elements": els + glow,
                                          "ambientocclusion": False})
            for wl in ("false", "true"):
                variants[f"calm={str(calm).lower()},hanging={str(hanging).lower()},waterlogged={wl}"] = {"model": f"{NS}:block/{name}"}
    write(f"{g.BS}/causeway_lantern.json", {"variants": variants})
    g.item_def("causeway_lantern", f"{NS}:block/causeway_lantern")
    # the socket: a low purpur-and-driftstone box whose slot glows
    sock = g.custom("causeway_socket", {"side": g.b("causeway_socket_side"), "top": g.b("causeway_socket_top"),
                                        "glow": g.b("causeway_socket_top_emissive"), "particle": g.b("causeway_socket_side")},
                    [g.box((2, 0, 2), (14, 10, 14), {"down": "#side", "up": "#top", "north": "#side", "south": "#side", "west": "#side", "east": "#side"}),
                     g.box((2, 0, 2), (14, 10, 14), {"up": "#glow"}, emission=15)])
    sock_used = g.custom("causeway_socket_used", {"side": g.b("causeway_socket_side"), "top": g.b("causeway_socket_top"),
                                                  "particle": g.b("causeway_socket_side")},
                         [g.box((2, 0, 2), (14, 10, 14), {"down": "#side", "up": "#top", "north": "#side", "south": "#side", "west": "#side", "east": "#side"})])
    write(f"{g.BS}/causeway_socket.json", {"variants": {"used=false": {"model": sock}, "used=true": {"model": sock_used}}})
    g.item_def("causeway_socket", sock)
    # the plaque: a small slanted tablet on a purpur base
    pl = g.custom("causeway_plaque", {"face": g.b("causeway_plaque"), "base": "minecraft:block/purpur_block", "particle": g.b("causeway_plaque")},
                  [g.box((2, 0, 4), (14, 3, 12), g.all_faces("#base")),
                   {"from": [3, 3, 7], "to": [13, 11, 9], "rotation": {"angle": -22.5, "axis": "x", "origin": [8, 3, 8]},
                    "faces": {"north": {"texture": "#face"}, "south": {"texture": "#base"}, "up": {"texture": "#base"},
                              "west": {"texture": "#base"}, "east": {"texture": "#base"}, "down": {"texture": "#base"}}}])
    write(f"{g.BS}/causeway_plaque.json", {"variants": g.horizontal_variants({"": pl})})
    g.item_def("causeway_plaque", pl)
    # the Drift Buoy: a small floating lantern (lantern-template sheet)
    els = copy.deepcopy(tmpl["elements"])
    glow = g.emissive_copy(els, {"#lantern": "#glow"})
    write(f"{g.BM}/drift_buoy.json", {"parent": "minecraft:block/block", "textures": {"lantern": g.b("drift_buoy"), "glow": g.b("drift_buoy_emissive"),
                                                                                    "particle": g.b("drift_buoy")}, "elements": els + glow,
                                      "ambientocclusion": False})
    g.simple_state("drift_buoy", f"{NS}:block/drift_buoy")
    g.item_def("drift_buoy", f"{NS}:block/drift_buoy")
    # the Roost Post: a wrapped post with a soft pad on top
    rp = g.custom("roost_post", {"side": g.b("roost_post_side"), "top": g.b("roost_post_top"), "particle": g.b("roost_post_side")},
                  [g.box((5, 0, 5), (11, 14, 11), {"down": "#top", "up": "#top", "north": "#side", "south": "#side", "west": "#side", "east": "#side"}),
                   g.box((3, 14, 3), (13, 16, 13), {"down": "#top", "up": "#top", "north": "#side", "south": "#side", "west": "#side", "east": "#side"})])
    g.simple_state("roost_post", rp)
    g.item_def("roost_post", rp)


def lantern(g, name):
    tmpl = vanilla_json("assets/minecraft/models/block/template_lantern.json")
    tmplh = vanilla_json("assets/minecraft/models/block/template_hanging_lantern.json")
    out = {}
    for model_name, t in ((name, tmpl), (name + "_hanging", tmplh)):
        els = copy.deepcopy(t["elements"])
        glow = g.emissive_copy(els, {"#lantern": "#glow"})
        m = {"parent": "minecraft:block/block", "textures": {"lantern": g.b(name), "glow": g.b(name + "_emissive"), "particle": g.b(name)},
             "elements": els + glow, "ambientocclusion": False}
        write(f"{g.BM}/{model_name}.json", m)
        out[model_name] = f"{NS}:block/{model_name}"
    g.remap_state("lantern", name, {"lantern": out[name], "lantern_hanging": out[name + "_hanging"]})
    g.item_def(name, out[name])

# -------------------------------------------------------------------------------------------------- data
def data(d):
    D = d.D
    for p in PACTS:
        if p in PACT_MATERIALS:
            d.shapeless(f"pact_glyph_{p}", [d.i("echo_dust"), d.i("mirror_shard_fragment"), PACT_MATERIALS[p]], d.i(f"pact_glyph_{p}"))
        d.shapeless(f"pact_crystal_{p}", ["minecraft:end_crystal", d.i(f"pact_glyph_{p}")], d.i(f"pact_crystal_{p}"))
    d.shapeless("stillness_crystal", ["minecraft:end_crystal", d.i("stillness_fragment")], d.i("stillness_crystal"))
    d.shaped("wardens_lantern", [" D ", "DLD", " D "], {"D": d.i("dragon_scale"), "L": d.i("spire_lantern")}, d.i("wardens_lantern"),
             category="building")
    d.shaped("wyrm_barding", ["D D", "DID", "DDD"], {"D": d.i("dragon_scale"), "I": "minecraft:iron_ingot"}, d.i("wyrm_barding"),
             category="equipment")
    d.shaped("tuning_fork", ["I I", " M ", " D "], {"I": "minecraft:iron_ingot", "M": d.i("mirror_shard_fragment"), "D": d.i("dragon_scale")},
             d.i("tuning_fork"), category="equipment")
    # elytra styles (upgrade or Dragon Scale overlay); the old smithing_transform upgrade recipes are replaced
    for up, mat in (("gale", d.i("skate_membrane")), ("still", d.i("mirror_shard_fragment")), ("hover", d.i("glow_gland"))):
        write(f"{D}/recipe/elytra_{up}_upgrade.json", {"type": "worldsfall:elytra_style", "template": d.i(f"{up}_upgrade_template"),
                                                       "addition": mat, "style": up})
    write(f"{D}/recipe/elytra_scaled.json", {"type": "worldsfall:elytra_style", "template": d.i("dragon_scale"), "addition": d.i("dragon_scale"),
                                             "style": "scaled"})
    # banner pattern, music disc
    write(f"{D}/banner_pattern/warden.json", {"asset_id": d.i("warden"), "translation_key": "block.worldsfall.banner.warden"})
    d.tag("banner_pattern", NS, "pattern_item/warden", [d.i("warden")])
    write(f"{D}/jukebox_song/warden.json", {"comparator_output": 14, "description": {"translate": "jukebox_song.worldsfall.warden"},
                                            "length_in_seconds": 150.0, "sound_event": d.i("music_disc.warden")})
    gap_data(d)
    d.tag("worldgen/structure", NS, "lore", [d.i("fallen_spire"), d.i("sunken_archive")] if os.path.exists(
        os.path.join(D, "worldgen/structure/sunken_archive.json")) else [d.i("fallen_spire")])


def gap_data(d):
    D = d.D
    d.shaped("causeway_keystone", ["P", "D", "L"], {"P": d.i("weathered_purpur"), "D": d.i("driftstone"), "L": d.i("lumen_crystal_shard")},
             d.i("causeway_keystone"))
    d.shaped("drift_buoy", ["F", "G", "D"], {"F": d.i("voidroot_fiber"), "G": d.i("glowbulb"), "D": d.i("driftstone")}, d.i("drift_buoy"), n=2)
    d.shaped("roost_post", ["M", "P", "P"], {"M": d.i("skate_membrane"), "P": d.i("weathered_purpur")}, d.i("roost_post"), n=2, category="building")
    d.shaped("causeway_plaque", ["PPP"], {"P": "minecraft:purpur_slab"}, d.i("causeway_plaque"), category="building")
    # Gapwrecks in the Debris Belt (500–750 blocks out), always above the tide
    write(f"{D}/worldgen/structure/gapwreck.json", {"type": d.i("blueprint"), "generator": "gapwreck",
                                                    "biomes": f"#{NS}:has_structure/gapwreck", "spawn_overrides": {},
                                                    "step": "surface_structures", "terrain_adaptation": "none"})
    write(f"{D}/worldgen/structure_set/gapwreck.json", {"placement": {"type": "minecraft:random_spread", "salt": 44901731, "separation": 9,
                                                                      "spacing": 22, "spread_type": "linear"},
                                                        "structures": [{"structure": d.i("gapwreck"), "weight": 1}]})
    d.tag("worldgen/biome", NS, "has_structure/gapwreck", ["minecraft:the_end"])  # the Gap (< 1024 blocks) is all the_end
    d.tag("worldgen/structure", NS, "driftwrecks_and_gapwrecks", [d.i("driftwreck"), d.i("gapwreck")])
    d.chest("vault/gapwreck", [d.chance(0.9, d.count("minecraft:ender_pearl", 1, 3)), d.chance(0.45, d.tablet_entry()),
                               d.chance(0.6, d.count(d.i("driftstone"), 4, 10)), d.chance(0.35, d.count(d.i("glowbulb"), 1, 3)),
                               d.chance(0.3, d.count(d.i("causeway_keystone"), 1, 2)), d.chance(0.25, d.count(d.i("mirror_shard_fragment"), 1, 2)),
                               d.chance(0.12, {"type": "minecraft:item", "name": d.i("drift_armor_trim_smithing_template")})])
    # the Debris Belt: dense debris fields 500–750 blocks out
    write(f"{D}/worldgen/placed_feature/debris_field_belt.json", {"feature": d.i("debris_field"), "placement": [
        {"type": "minecraft:count", "count": 2}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 36}, "max_inclusive": {"absolute": 86}}},
        {"type": d.i("distance_from_origin"), "min": 500, "max": 750}]})
    write(f"{D}/neoforge/biome_modifier/debris_belt.json", {"type": "neoforge:add_features",
                                                            "biomes": "minecraft:the_end",
                                                            "features": [d.i("debris_field_belt")], "step": "surface_structures"})


def block_loot(d):
    for n in ("wardens_lantern", "causeway_lantern", "causeway_plaque", "drift_buoy", "roost_post"):
        d.drop_self(n)


def pickaxe_blocks():
    return ["wardens_lantern", "causeway_lantern", "causeway_plaque", "roost_post"]


def advancement_criteria(aid, i):
    return None


# -------------------------------------------------------------------------------------------------- lang
def lang(L, title):
    for n in ITEM_SPRITES:
        L[f"item.{NS}.{n}"] = ITEM_NAMES.get(n, title(n))
    for n, t in BLOCK_NAMES.items():
        L[f"block.{NS}.{n}"] = t
    L.update(LANG)
    for k, (desc, flavor) in DESC.items():
        kind = "block" if k in BLOCK_NAMES else "item"
        L[f"{kind}.{NS}.{k}.desc"] = desc
        if flavor:
            L[f"{kind}.{NS}.{k}.flavor"] = flavor
    for k, (t, dsc) in ADV_TEXT.items():
        L[f"advancements.{NS}.{k}.title"] = t
        L[f"advancements.{NS}.{k}.description"] = dsc
    for ent in ("elder_warden", "lumen_heart"):
        L.setdefault(f"entity.{NS}.{ent}", title(ent))
