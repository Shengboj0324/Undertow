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
    + ["city_chart"]
    + ["mirrorfish", "cooked_mirrorfish", "driftsilt", "eclipse_dust", "courier_token"]
)
EMISSIVE_ITEM_SPRITES = []

# items whose names differ from the title-cased id
ITEM_NAMES = {f"pact_glyph_{p}": f"Pact Glyph of {PACT_NAMES[p]}" for p in PACTS}
ITEM_NAMES.update({f"pact_crystal_{p}": f"Pact Crystal of {PACT_NAMES[p]}" for p in PACTS})
ITEM_NAMES.update({"music_disc_warden": "Music Disc", "warden_banner_pattern": "Banner Pattern"})
BLOCK_NAMES = {"tide_bell": "Tide Bell", "wyrm_roost": "Wyrm Roost", "gravity_plate": "Gravity Plate", "courier_cache": "Courier Cache",
               "starfall_shard": "Starfall Shard", "tidewrack": "Tidewrack", "lantern_moth_jar": "Lantern Moth Jar",
               "anchor_frame_lamp": "Anchor Frame Lamp", "anchor_frame_slab": "Anchor Frame Slab", "anchor_frame_stairs": "Anchor Frame Stairs",
               "drift_helm": "Drift Helm", "ships_bell": "Ship's Bell", "wardens_lantern": "Warden's Lantern", "causeway_socket": "Causeway Socket", "causeway_lantern": "Causeway Lantern",
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
    ("the_archivist", "echoes_of_them", "city_chart", "task", False),
    ("attuned", "root", "tuning_fork", "task", False),
    ("lattice_jump", "attuned", "minecraft:ender_pearl", "goal", False),
    ("moored", "attuned", "drift_helm", "goal", False),
    ("high_water_mark", "a_hole_full_of_stars", "tidewrack", "goal", False),
    ("starstruck", "root", "starfall_shard", "task", False),
    ("a_quiet_hour", "echoes_of_them", "eclipse_dust", "challenge", True),
    ("dusk_flight", "patience_of_the_void", "skate_harness", "challenge", False),
    ("special_delivery", "fair_trade", "courier_token", "goal", False),
    ("tide_angler", "a_hole_full_of_stars", "mirrorfish", "task", False),
    ("still_angler", "tide_angler", "cooked_mirrorfish", "task", False),
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
    # 1.4 cities and navigation
    "entity.worldsfall.shulker_archivist": "Shulker Archivist",
    "item.worldsfall.shulker_archivist_spawn_egg": "Shulker Archivist Spawn Egg",
    "effect.worldsfall.sinking": "Sinking",
    "item.worldsfall.empty_void_chart": "Empty Void Chart",
    "item.worldsfall.void_chart": "Void Chart",
    "item.worldsfall.star_chart": "Star Chart",
    "message.worldsfall.archivist.waits": "The Archivist stays closed. It opens for an Echo Tablet.",
    "message.worldsfall.chart.only_end": "A Void Chart can only be drawn in the End.",
    "message.worldsfall.chart.no_cities": "There are no more End Cities near enough to chart.",
    "message.worldsfall.lattice.full": "The Lattice already holds %s nodes.",
    "message.worldsfall.lattice.attuned": "Attuned: %s (%s of %s nodes). Sneak into it to travel.",
    "message.worldsfall.lattice.learned": "Lattice node learned: %s",
    "message.worldsfall.lattice.gone": "%s no longer stands.",
    "message.worldsfall.lattice.covered": "%s is under the tide.",
    "message.worldsfall.lattice.no_pearl": "A Lattice jump costs one ender pearl.",
    "message.worldsfall.mooring.not_a_ship": "A Drift Helm only steers a true End Ship.",
    "message.worldsfall.mooring.already": "This ship is already a Mooring: %s.",
    "message.worldsfall.mooring.restored": "The ship answers the helm. %s joins the Gateway Lattice.",
    "message.worldsfall.mooring.bell_unmoored": "The bell rings, but no Mooring carries the sound.",
    "message.worldsfall.mooring.bell_called": "The bell carries across the void: %s companions come aboard.",
    "message.worldsfall.mooring.bell_nobody": "The bell carries across the void, but none of your companions are within 256 blocks.",
    "gui.worldsfall.lattice.title": "Gateway Lattice",
    "gui.worldsfall.lattice.empty": "You have not attuned or visited any other node yet. Use a Tuning Fork on a gateway, or restore an End Ship.",
    "gui.worldsfall.lattice.covered": "under the tide",
    "gui.worldsfall.lattice.at": "%s, %s, %s",
    "gui.worldsfall.lattice.skate": "A Skate is perched here",
    "gui.worldsfall.lattice.no_skate": "No Skate seen here",
    "gui.worldsfall.lattice.cost": "One ender pearl per jump",
    # 1.5 living systems
    "entity.worldsfall.lantern_moth": "Lantern Moth",
    "item.worldsfall.bottled_lantern_moth": "Bottled Lantern Moth",
    "message.worldsfall.roost.no_wyrm": "No Wyrm of yours is near enough to settle here.",
    "message.worldsfall.roost.settled": "%s settles on the roost. It will keep watch here.",
    "message.worldsfall.hoard.trusted": "The Hoarders know your face now. They might trust you with a Courier Token.",
    "message.worldsfall.hoard.distrusted": "Word travels among Hoarders: they no longer trust you.",
    "message.worldsfall.courier.only_end": "Couriers only work in the End.",
    "message.worldsfall.courier.standing": "The Hoarders don't trust you enough (standing %s, needs %s).",
    "message.worldsfall.courier.first": "First cache marked. Now use the token on the cache it should deliver to.",
    "message.worldsfall.courier.same": "Pick a different cache to deliver to.",
    "message.worldsfall.courier.too_far": "The caches must be within %s blocks of each other.",
    "message.worldsfall.courier.hired": "A Courier will carry one stack every five minutes from (%s, %s) to (%s, %s).",
    "message.worldsfall.courier.cancelled": "Your Courier contracts are cancelled. Hoarders don't work for thieves.",
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
    "tuning_fork": ("Strike an End Gateway to attune it to the Gateway Lattice. Sneak into an attuned gateway to travel.", None),
    "empty_void_chart": ("Use in the End to start a chart of the islands around you.", "Contours every five blocks; the tide in teal."),
    "city_chart": ("Use in the End to unroll a chart of the three nearest End Cities you have not been shown.", None),
    "drift_helm": ("Set on an End Ship's deck to make it a Mooring on the Gateway Lattice.", None),
    "ships_bell": ("Rung on a Mooring, it calls your tamed Skates and Drakes aboard from up to 256 blocks away.", None),
    "mirrorfish": ("Caught on the tide at High Tide. Eating it grants 20 seconds of Tidesight.", None),
    "driftsilt": ("Scatter on open end stone to seed a small patch of Pale Loam.", None),
    "eclipse_dust": ("Left behind by the memories that walk during an Eclipse of Stillness. A rare dye and Pact Glyph material.", None),
    "courier_token": ("Use on two Courier Caches up to 2,000 blocks apart: a Hoarder carries one stack between them every five minutes.",
                      "Theft from any Hoard cancels the contract."),
    "tide_bell": ("Rings and sends a redstone pulse at every tide phase change.", None),
    "wyrm_roost": ("Use it to settle your nearest Wyrm here. While it rests, Echoes can't disguise and Lurkers can't hide within 24 blocks.", None),
    "gravity_plate": ("Step on it to open a small low-gravity zone for ten seconds (End only).", None),
    "courier_cache": ("A small chest: the endpoint of a Hoarder Courier contract.", None),
    "anchor_frame_lamp": ("A solid Anchor Frame panel with a glowing core.", None),
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
    "the_archivist": ("Overdue", "Show an Echo Tablet to a Shulker Archivist"),
    "attuned": ("Perfect Pitch", "Attune an End Gateway with a Tuning Fork"),
    "lattice_jump": ("Shortcut Through Nothing", "Travel through the Gateway Lattice"),
    "moored": ("All Hands", "Turn an End Ship into a Mooring with a Drift Helm"),
    "high_water_mark": ("High Water Mark", "Be in the End when a King Tide reaches its height"),
    "starstruck": ("Starstruck", "Mine a shard that fell in a Starfall"),
    "a_quiet_hour": ("A Quiet Hour", "Meet a memory that walks during an Eclipse of Stillness"),
    "dusk_flight": ("Dusk Flight", "Tame a Dusk Skate from a passing migration"),
    "special_delivery": ("Special Delivery", "Hire a Hoarder Courier between two Courier Caches"),
    "tide_angler": ("Casting the Tide", "Catch something on the tide plane at High Tide"),
    "still_angler": ("Patient Angler", "Catch something in Stillwater"),
}

# -------------------------------------------------------------------------------------------------- assets
def assets(g):
    for n in ITEM_SPRITES:
        g.item_sprite(n, [f"{NS}:item/{n}"])
    for n in EMISSIVE_ITEM_SPRITES:
        g.item_sprite(n, [f"{NS}:item/{n}", f"{NS}:item/{n}_emissive"], emissive_layers=(1,))
    lantern(g, "wardens_lantern")
    gap_assets(g)
    nav_assets(g)
    life_assets(g)
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


def nav_assets(g):
    import expansion_icons
    expansion_icons.build()
    g.item_sprite("empty_void_chart", [f"{NS}:item/void_chart"])
    g.item_sprite("void_chart_filled", [f"{NS}:item/void_chart_filled"])
    g.item_sprite("star_chart", [f"{NS}:item/star_chart"])
    g.item_sprite("shulker_archivist_spawn_egg", [f"{NS}:item/shulker_archivist_spawn_egg"])
    # a filled chart picks its sprite by kind
    write(f"{g.ITEMS}/void_chart.json", {"model": {"type": "minecraft:select", "property": "minecraft:component", "component": f"{NS}:chart_kind",
                                                   "cases": [{"when": "city", "model": {"type": "minecraft:model", "model": f"{NS}:item/city_chart"}},
                                                             {"when": "star", "model": {"type": "minecraft:model", "model": f"{NS}:item/star_chart"}}],
                                                   "fallback": {"type": "minecraft:model", "model": f"{NS}:item/void_chart_filled"}}})
    for n in ("void_chart_filled", "star_chart"):  # models only; they are not items of their own
        if os.path.exists(f"{g.ITEMS}/{n}.json"):
            os.remove(f"{g.ITEMS}/{n}.json")
    side = {"down": "#post", "up": "#post", "north": "#post", "south": "#post", "west": "#post", "east": "#post"}
    # Drift Helm: a driftstone post carrying a ship's wheel (the wheel texture on both faces of a thin plate)
    helm = g.custom("drift_helm", {"post": g.b("driftstone"), "wheel": g.b("drift_helm"), "particle": g.b("drift_helm")},
                    [g.box((6, 0, 6), (10, 9, 10), side),
                     g.box((1, 6, 7), (15, 16, 9), {"north": "#wheel", "south": "#wheel", "up": "#post", "down": "#post", "west": "#post", "east": "#post"})],
                    ao=False)
    write(f"{g.BS}/drift_helm.json", {"variants": g.horizontal_variants({"moored=false": helm, "moored=true": helm})})
    g.item_def("drift_helm", helm)
    # Ship's Bell: a bronze bell hung in a driftstone frame
    bell_faces = {f: "#bell" for f in ("down", "up", "north", "south", "west", "east")}
    bell = g.custom("ships_bell", {"post": g.b("driftstone"), "bell": g.b("ships_bell"), "particle": g.b("ships_bell")},
                    [g.box((2, 0, 2), (14, 2, 14), side), g.box((2, 2, 7), (4, 16, 9), side), g.box((12, 2, 7), (14, 16, 9), side),
                     g.box((4, 14, 7), (12, 16, 9), side), g.box((5, 6, 5), (11, 13, 11), bell_faces), g.box((4, 4, 4), (12, 6, 12), bell_faces),
                     g.box((7, 13, 7), (9, 14, 9), bell_faces)], ao=False)
    write(f"{g.BS}/ships_bell.json", {"variants": g.horizontal_variants({"ringing=false": bell, "ringing=true": bell})})
    g.item_def("ships_bell", bell)


def life_assets(g):
    from PIL import Image
    tex_dir = os.path.join(ASSETS, "textures/block")
    # Anchor Frame plate: the frame texture with its open cells filled in a deep purpur, for stairs and slabs
    im = Image.open(os.path.join(tex_dir, "anchor_frame.png")).convert("RGBA")
    opaque = [p for p in im.getdata() if p[3] > 0]
    avg = tuple(sum(p[i] for p in opaque) // len(opaque) for i in range(3))
    fill = (int(avg[0] * 0.42), int(avg[1] * 0.36), int(avg[2] * 0.5), 255)
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            if px[x, y][3] == 0:
                shade = 1.0 + 0.08 * (((x * 7 + y * 13) % 5) - 2) / 2
                px[x, y] = tuple(min(255, int(c * shade)) for c in fill[:3]) + (255,)
    im.save(os.path.join(tex_dir, "anchor_frame_plate.png"))
    t = g.b("anchor_frame_plate")
    plate = g.model("anchor_frame_plate", "minecraft:block/cube_all", {"all": t})
    slab = g.model("anchor_frame_slab", "minecraft:block/slab", {"bottom": t, "top": t, "side": t})
    slab_top = g.model("anchor_frame_slab_top", "minecraft:block/slab_top", {"bottom": t, "top": t, "side": t})
    write(f"{g.BS}/anchor_frame_slab.json", {"variants": {"type=bottom": {"model": slab}, "type=top": {"model": slab_top},
                                                         "type=double": {"model": plate}}})
    g.item_def("anchor_frame_slab", slab)
    st = g.model("anchor_frame_stairs", "minecraft:block/stairs", {"bottom": t, "top": t, "side": t})
    g.model("anchor_frame_stairs_inner", "minecraft:block/inner_stairs", {"bottom": t, "top": t, "side": t})
    g.model("anchor_frame_stairs_outer", "minecraft:block/outer_stairs", {"bottom": t, "top": t, "side": t})
    g.remap_state("purpur_stairs", "anchor_frame_stairs", {"purpur_stairs": st, "purpur_stairs_inner": st + "_inner", "purpur_stairs_outer": st + "_outer"})
    g.item_def("anchor_frame_stairs", st)
    lamp = g.cube_with_emissive("anchor_frame_lamp", g.b("anchor_frame_lamp"), g.b("anchor_frame_lamp_emissive"))
    g.simple_state("anchor_frame_lamp", lamp)
    g.item_def("anchor_frame_lamp", lamp)
    # Lantern Moth jar (bottled moth), like the Tidewisp jar
    jar = g.custom("lantern_moth_jar", {"jar": g.b("lantern_moth_jar"), "glow": g.b("lantern_moth_jar_emissive"), "particle": g.b("lantern_moth_jar")},
                   [g.box((5, 0, 5), (11, 9, 11), g.all_faces("#jar")), g.box((5, 0, 5), (11, 9, 11), g.all_faces("#glow"), emission=15)], ao=False)
    g.simple_state("lantern_moth_jar", jar)
    g.item_def("bottled_lantern_moth", jar)
    g.item_sprite("lantern_moth_spawn_egg", [f"{NS}:item/lantern_moth_spawn_egg"])
    # Gravity Plate: a thin stillstone plate with a glowing ring
    gp = g.custom("gravity_plate", {"plate": g.b("gravity_plate"), "glow": g.b("gravity_plate_emissive"), "particle": g.b("gravity_plate")},
                  [g.box((1, 0, 1), (15, 1, 15), {"down": "#plate", "up": "#plate", "north": "#plate", "south": "#plate", "west": "#plate", "east": "#plate"}),
                   g.box((1, 0, 1), (15, 1, 15), {"up": "#glow"}, emission=15)])
    gp_down = g.custom("gravity_plate_down", {"plate": g.b("gravity_plate"), "glow": g.b("gravity_plate_emissive"), "particle": g.b("gravity_plate")},
                       [g.box((1, 0, 1), (15, 0.5, 15), {"down": "#plate", "up": "#plate", "north": "#plate", "south": "#plate", "west": "#plate", "east": "#plate"}),
                        g.box((1, 0, 1), (15, 0.5, 15), {"up": "#glow"}, emission=15)])
    write(f"{g.BS}/gravity_plate.json", {"variants": {"powered=false": {"model": gp}, "powered=true": {"model": gp_down}}})
    g.item_def("gravity_plate", gp)
    # Courier Cache: a neat end-stone chest with a violet ender seal
    cc = g.oriented_box("courier_cache", (1, 0, 1), (15, 14, 15), "courier_cache_side", "courier_cache_top", "courier_cache_front",
                        glow_front="courier_cache_front_emissive")
    write(f"{g.BS}/courier_cache.json", {"variants": g.horizontal_variants({"": cc})})
    g.item_def("courier_cache", cc)
    # Starfall shard cluster (cross model, glowing tips) and Tidewrack pile
    sf = g.cross_block("starfall_shard", "starfall_shard", "starfall_shard_emissive")
    g.simple_state("starfall_shard", sf)
    g.item_sprite("starfall_shard", [g.b("starfall_shard"), g.b("starfall_shard_emissive")], emissive_layers=(1,))
    tw = g.custom("tidewrack", {"all": g.b("tidewrack"), "particle": g.b("tidewrack")}, [g.box((0, 0, 0), (16, 8, 16), g.all_faces("#all"))])
    g.simple_state("tidewrack", tw)
    g.item_def("tidewrack", tw)
    # Tide Bell: a teal-bronze bell plate hung in a driftstone yoke
    side = {f: "#post" for f in ("down", "up", "north", "south", "west", "east")}
    bell = {f: "#bell" for f in ("down", "up", "north", "south", "west", "east")}
    tb = g.custom("tide_bell", {"post": g.b("driftstone"), "bell": g.b("tide_bell"), "particle": g.b("tide_bell")},
                  [g.box((1, 0, 6), (15, 2, 10), side), g.box((1, 2, 7), (3, 15, 9), side), g.box((13, 2, 7), (15, 15, 9), side),
                   g.box((1, 13, 7), (15, 15, 9), side), g.box((5, 5, 5), (11, 12, 11), bell), g.box((7, 12, 7), (9, 13, 9), bell)], ao=False)
    variants = {}
    for k, v in g.horizontal_variants({"": tb}).items():
        for phase in range(4):
            for powered in ("false", "true"):
                variants[f"{k},phase={phase},powered={powered}"] = v
    write(f"{g.BS}/tide_bell.json", {"variants": variants})
    g.item_def("tide_bell", tb)
    # Wyrm Roost: a low woven nest
    roost = g.custom("wyrm_roost", {"top": g.b("wyrm_roost_top"), "side": g.b("wyrm_roost_side"), "particle": g.b("wyrm_roost_side")},
                     [g.box((0, 0, 0), (16, 6, 16), {"down": "#side", "up": "#top", "north": "#side", "south": "#side", "west": "#side", "east": "#side"})])
    g.simple_state("wyrm_roost", roost)
    g.item_def("wyrm_roost", roost)


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
    nav_data(d)
    life_data(d)
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


def nav_data(d):
    D = d.D
    d.shapeless("empty_void_chart", ["minecraft:paper", "minecraft:paper", d.i("echo_dust")], d.i("empty_void_chart"))
    d.shaped("drift_helm", [" M ", "DCD", " D "], {"M": d.i("skate_membrane"), "D": d.i("driftstone"), "C": "minecraft:compass"}, d.i("drift_helm"),
             category="building")
    d.shaped("ships_bell", [" D ", "GPG", "G G"], {"D": d.i("driftstone"), "G": "minecraft:gold_ingot", "P": d.i("weathered_purpur")},
             d.i("ships_bell"), category="building")
    # End City chests point onward (global loot modifier; no vanilla file replaced)
    d.chest("inject/end_city_treasure", [
        d.chance(0.35, d.tablet_entry()),
        d.chance(0.12, {"type": "minecraft:item", "name": d.i("spire_armor_trim_smithing_template")}),
        d.chance(0.2, {"type": "minecraft:tag", "items": f"#{NS}:pact_glyphs_craftable", "expand": True}),
        d.chance(0.15, d.count(d.i("causeway_keystone"), 1, 1)),
        d.chance(0.1, {"type": "minecraft:item", "name": d.i("city_chart")}),
    ])
    d.tag("item", NS, "pact_glyphs_craftable", [d.i(f"pact_glyph_{p}") for p in PACTS if p != "depths"])
    write(f"{D}/loot_modifiers/end_city_onward.json", {"type": "neoforge:add_table",
                                                      "condition": {"type": "neoforge:loot_table_id", "loot_table_id": "minecraft:chests/end_city_treasure"},
                                                      "table": d.i("inject/end_city_treasure")})
    write(f"{D}/loot_table/entities/shulker_archivist.json", {"type": "minecraft:entity", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": d.i("echo_dust"),
                                  "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]}]}],
        "random_sequence": d.i("entities/shulker_archivist")})


def life_data(d):
    D = d.D
    d.cooking("cooked_mirrorfish", d.i("mirrorfish"), d.i("cooked_mirrorfish"))
    d.shaped("tide_bell", ["DDD", "D D", " B "], {"D": d.i("driftstone"), "B": "minecraft:bell"}, d.i("tide_bell"), category="redstone")
    d.shaped("gravity_plate", ["PFP"], {"P": d.i("polished_stillstone"), "F": d.i("stillness_fragment")}, d.i("gravity_plate"), n=2, category="redstone")
    d.shaped("wyrm_roost", ["FCF", "MMM"], {"F": d.i("voidroot_fiber"), "C": d.i("chorus_pod"), "M": d.i("skate_membrane")}, d.i("wyrm_roost"),
             category="building")
    d.shaped("courier_cache", ["BBB", "BEB", "BBB"], {"B": "minecraft:end_stone_bricks", "E": "minecraft:ender_pearl"}, d.i("courier_cache"))
    d.shapeless("anchor_frame_lamp", [d.i("anchor_frame"), d.i("glowbulb")], d.i("anchor_frame_lamp"), category="building")
    d.shaped("anchor_frame_slab", ["AAA"], {"A": d.i("anchor_frame")}, d.i("anchor_frame_slab"), n=6, category="building")
    d.shaped("anchor_frame_stairs", ["A  ", "AA ", "AAA"], {"A": d.i("anchor_frame")}, d.i("anchor_frame_stairs"), n=4, category="building")
    item = lambda n, w, lo=1, hi=1: dict(d.count(n, lo, hi) if hi > 1 else {"type": "minecraft:item", "name": n}, weight=w)
    # Casting the Tide: Mirrorfish, Driftsilt, Glow Glands, Driftstone and rare tablets (never Sigils or boss items)
    write(f"{D}/loot_table/gameplay/tide_fishing.json", {"type": "minecraft:fishing", "pools": d.convert([{"rolls": 1, "entries": [
        dict(item(d.i("mirrorfish"), 45), quality=-1), item(d.i("driftsilt"), 15, 1, 3), item(d.i("glow_gland"), 10),
        item(d.i("driftstone"), 10, 2, 5), dict(d.tablet_entry(3), quality=2), dict(item(d.i("mirror_shard_fragment"), 4), quality=1)]}]),
        "random_sequence": d.i("gameplay/tide_fishing")})
    # Still angling: Mirror Shards, floating relics of Overworld blocks, a small chance of a Pact Glyph
    relics = ["minecraft:moss_block", "minecraft:oak_log", "minecraft:mossy_cobblestone", "minecraft:clay", "minecraft:dripstone_block",
              "minecraft:tuff", "minecraft:mud_bricks", "minecraft:copper_block"]
    write(f"{D}/loot_table/gameplay/still_angling.json", {"type": "minecraft:fishing", "pools": d.convert([{"rolls": 1, "entries": [
        item(d.i("mirror_shard_fragment"), 30, 1, 3), item(d.i("mirrorfish"), 25)] + [item(r, 4, 1, 4) for r in relics] + [
        dict({"type": "minecraft:tag", "items": f"#{NS}:pact_glyphs_craftable", "expand": True}, weight=2, quality=2)]}]),
        "random_sequence": d.i("gameplay/still_angling")})
    d.block_loot("tidewrack", [{"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
        item(d.i("driftstone"), 10, 2, 6), item(d.i("driftsilt"), 8, 1, 3), item(d.i("voidroot_fiber"), 6, 1, 3), item(d.i("glow_gland"), 4),
        item(d.i("mirrorfish"), 4), item("minecraft:ender_pearl", 3), dict(d.tablet_entry(2))]}])
    d.block_loot("starfall_shard", [{"rolls": 1, "entries": [item(d.i("lumen_crystal_shard"), 1, 2, 5)]},
                                    {"rolls": 1, "entries": [{"type": "minecraft:empty", "weight": 6}, dict(d.tablet_entry(1))]}])
    d.drop_self("lantern_moth_jar", "bottled_lantern_moth")
    d.tag("item", "minecraft", "fishes", [d.i("mirrorfish"), d.i("cooked_mirrorfish")])
    d.tag("block", "minecraft", "mineable/shovel", [d.i(n) for n in shovel_blocks()])
    d.block_loot("anchor_frame_slab", [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": d.i("anchor_frame_slab"), "functions": [
        {"function": "minecraft:set_count", "count": 2, "conditions": [{"condition": "minecraft:block_state_property",
         "block": d.i("anchor_frame_slab"), "properties": {"type": "double"}}]}, {"function": "minecraft:explosion_decay"}]}]}])


def block_loot(d):
    for n in ("wardens_lantern", "causeway_lantern", "causeway_plaque", "drift_buoy", "roost_post", "drift_helm", "ships_bell", "tide_bell",
              "wyrm_roost", "gravity_plate", "courier_cache", "anchor_frame_lamp", "anchor_frame_stairs"):
        d.drop_self(n)


def axe_blocks():
    return ["drift_helm", "wyrm_roost"]


def shovel_blocks():
    return ["tidewrack"]


def pickaxe_blocks():
    return ["wardens_lantern", "causeway_lantern", "causeway_plaque", "roost_post", "ships_bell", "tide_bell", "gravity_plate", "courier_cache",
            "anchor_frame_lamp", "anchor_frame_slab", "anchor_frame_stairs", "starfall_shard"]


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
    L["block.worldsfall.anchor_frame_slab"] = "Anchor Frame Slab"
    for ent in ("elder_warden", "lumen_heart"):
        L.setdefault(f"entity.{NS}.{ent}", title(ent))
