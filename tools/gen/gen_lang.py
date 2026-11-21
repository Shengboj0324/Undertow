"""Generates en_us.json: names, descriptions (Shift), flavor text, advancements, messages, hints, subtitles, config."""
import json, os, re
from common import *

L = {}
def name(kind, n, text=None):
    L[f"{kind}.{NS}.{n}"] = text or title(n)

BLOCKS = """pale_loam lilac_tuft chorus_pod stillstone polished_stillstone stillstone_bricks chiseled_stillstone mirror_shard rimglass stillglass
stillglass_pane weathered_purpur weathered_purpur_pillar cracked_purpur_pillar purpur_rubble spire_lantern undershale polished_undershale
undershale_bricks undershale_tiles ceiling_moss lumen_crystal lumen_cluster lumen_crystal_block voidroot glowbulb lumen_lamp driftstone
polished_driftstone driftstone_bricks tide_gauge null_obsidian fracture_glass unmade_shard ash_stone hoard_cache decorative_hoard_cache
keystone_socket spire_vault drift_cache sigil_pedestal egg_cradle gravity_anchor anchor_frame fracture_rift_frame fracture_rift still_throne
tidewisp_jar stillwater stillwater_cauldron""".split()
FAMS = ["stillstone", "polished_stillstone", "stillstone_brick", "weathered_purpur", "undershale", "polished_undershale", "undershale_brick",
        "undershale_tile", "driftstone", "polished_driftstone", "driftstone_brick"]
WALLS = {f for f in FAMS if f != "weathered_purpur"}
ITEMS = """skate_membrane glow_gland grazer_frill voidroot_fiber lumen_crystal_shard bottle_of_stillwater chorus_pod roasted_chorus_pod
grazer_flank cooked_grazer_flank drift_stew stillwater_tea void_anchor cracked_void_anchor rift_tether gateway_compass shulker_satchel void_lens
skate_harness spire_key spire_sigil tide_sigil under_sigil rift_key heart_of_stillness stillness_fragment dragon_egg_fragment
slumbering_egg stillwater_bucket bottled_tidewisp echo_dust lurker_lens""".split()
TEMPLATES = ["gale_upgrade_template", "still_upgrade_template", "hover_upgrade_template", "spire_armor_trim_smithing_template",
             "drift_armor_trim_smithing_template", "undersong_armor_trim_smithing_template"]
ENTITIES = """void_skate chorus_grazer underside_lurker hoarder spire_warden echo wild_shulker tidewisp shardling the_unmade wyrm void_capsule
flying_block rift_hook lurker_snare unmade_shard tether_pylon""".split()

# One-line "what it does" (Shift) and one flavor line (§9.1, §18.9, §2.2)
DESC = {
 "void_anchor": ("Carried: if you die in the void or below the tide, your belongings are sealed in a floating Void Capsule.", "It remembers what the void would forget."),
 "cracked_void_anchor": ("Like a Void Anchor, but only a random half of your stacks are saved.", "Better half than nothing."),
 "rift_tether": ("Use to fire (24 blocks). Hold use to reel in, release to swing, sneak to let go.", "Pearls were for children first."),
 "gateway_compass": ("Points to the nearest unused End Gateway. Use on a Gateway to bind it. Shows the tide while held.", None),
 "shulker_satchel": ("Use to open a 9-slot grid (18 after a smithing upgrade). Cannot hold boxes or satchels.", "Carry the world with you."),
 "void_lens": ("Scope to reveal Echoes, hidden Lurkers, Driftwrecks within 128 blocks and the tide's height.", None),
 "skate_harness": ("Use on a tamed Void Skate (or a grown Drake) to ride it.", None),
 "spire_key": ("Insert into a Keystone Socket to rebuild a sealed Spire room.", "The teeth are little towers."),
 "echo_tablet": ("Use to read.", None),
 "spire_sigil": ("One of three keys to the Fracture Rift.", "A tower, drawn by someone who missed them."),
 "tide_sigil": ("One of three keys to the Fracture Rift.", "A wave, frozen mid-fall."),
 "under_sigil": ("One of three keys to the Fracture Rift.", "A tree that grows down."),
 "rift_key": ("Opens the Fracture Rift for another descent.", None),
 "heart_of_stillness": ("Powers your first Gravity Anchor, or calms the tides in the Still Throne.", "It held for one breath. It still remembers how."),
 "stillness_fragment": ("Powers further Gravity Anchors and Anchor Frames.", None),
 "dragon_egg_fragment": ("Four make a new Dragon Egg.", None),
 "slumbering_egg": ("Place on an Egg Cradle to wake your Wyrm (10 minutes).", "Curled up, waiting for you."),
 "gale_upgrade_template": ("Elytra upgrade: double-tap jump while flying for a short boost.", None),
 "still_upgrade_template": ("Elytra upgrade: hold sneak while flying to brake to a slow glide.", None),
 "hover_upgrade_template": ("Elytra upgrade: hold sneak while nearly stopped to hover up to 3 seconds.", None),
 "chorus_pod": ("Food that does not teleport you. Skates, Grazers and Wild Shulkers love it.", None),
 "glowbulb": ("Food: grants Slow Falling for 8 seconds. Also a light.", None),
 "stillwater_tea": ("Clears Levitation and Void Erosion.", None),
 "bottled_tidewisp": ("A portable light that pulses before the tide turns. Place it.", None),
 "egg_cradle": ("Place a Dragon Egg here, feed it 8 Glow Glands, keep it company. Twice as fast in the End.", None),
 "gravity_anchor": ("Creates a Stable Field: no Void Erosion inside, Anchor Frames hold in midair.", None),
 "anchor_frame": ("Scaffolding that can float, but only inside a Stable Field.", None),
 "tide_gauge": ("Outputs redstone 0–15 with the height of the Void Tide.", None),
 "lumen_lamp": ("A teal redstone lamp.", None),
 "stillglass": ("Dark glass that softens sound.", None),
 "pale_loam": ("Chorus grows on it. Covering it turns it back to end stone.", None),
 "decorative_hoard_cache": ("A Hoard Cache for show. Hoarders will not barter at it.", None),
 "hoard_cache": ("Offer Overworld blocks to barter with the Hoarders.", None),
 "keystone_socket": ("Insert a Spire Key.", None),
 "spire_vault": ("Each player may claim its reward once.", None),
 "drift_cache": ("Each player may claim its reward once.", None),
 "sigil_pedestal": ("Each player may claim its reward once.", None),
 "still_throne": ("Place the Heart of Stillness here to calm the tides. Take it back to undo.", None),
 "fracture_rift_frame": ("Insert the three Ascendant Sigils, or a Rift Key.", None),
}
TABLETS = ["We counted the tides. Each year they came closer.", "The towers were not homes. They were ladders.",
           "Pearls were for children first. Then for the frightened.", "The grazers do not fear us. We were never hunters.",
           "Still water drinks the light. Do not let the young swim.", "We built the warden from what we loved most, so it would never leave.",
           "The undersides are quieter. We dug there to listen for stillness.", "Carry the world with you. Carry it up.",
           "The Heart held for one breath. Everyone heard it.", "Then nothing held.", "Our children forgot our faces. They remembered our hands.",
           "If you are reading this, you are standing on something that should have fallen."]
ADV = {
 "root": ("Where Worlds Go to Fall", "The Nether gives power. The End gives reach."),
 "nothing_holds": ("Nothing Holds", "Witness the Sundering"), "underneath": ("Underneath", "Enter the Underside"),
 "something_clicks": ("Something Clicks", "Kill an Underside Lurker"), "patience_of_the_void": ("Patience of the Void", "Tame a Void Skate"),
 "across_the_gap": ("Across the Gap", "Ride a Void Skate from the main island to the outer islands"),
 "pale_pasture": ("Pale Pasture", "Breed two Chorus Grazers"), "fair_trade": ("Fair Trade", "Barter with a Hoarder"),
 "light_fingers": ("Light Fingers", "Anger an entire Hoard"), "a_hole_full_of_stars": ("A Hole Full of Stars", "Swim in Stillwater"),
 "the_tide_remembers": ("The Tide Remembers", "Loot a Driftwreck at Low Tide"), "ladders_to_nowhere": ("Ladders to Nowhere", "Complete a Reassembly"),
 "wings_revised": ("Wings, Revised", "Apply an elytra upgrade"), "carry_the_world": ("Carry the World", "Fill a Shulker Satchel"),
 "anchored": ("Anchored", "Survive a void death with a Void Anchor"), "echoes_of_them": ("Echoes of Them", "Collect 6 Echo Tablets"),
 "the_whole_story": ("The Whole Story", "Collect all 12 Echo Tablets"), "three_keys": ("Three Keys", "Hold all three Ascendant Sigils"),
 "below_the_bottom": ("Below the Bottom", "Enter the Fracture"), "unmade": ("Unmade", "Defeat The Unmade"),
 "stillness": ("Stillness", "Place a Gravity Anchor"), "calm_waters": ("Calm Waters", "Place the Heart of Stillness in the Still Throne"),
 "warm_hands": ("Warm Hands", "Hatch the Dragon Egg"), "fully_grown": ("Fully Grown", "Raise a Drake"),
}
MESSAGES = {
 "command.worldsfall.wyrm.shoulder": "A Hatchling Wyrm settles on %s's shoulder.",
 "command.worldsfall.wyrm.shoulder_failed": "No free shoulder (stand on the ground, not riding or swimming).",
 "hud.worldsfall.void_lens.wreck": "Driftwreck · %s m",
 "hud.worldsfall.void_lens.tide": "Tide · Y %s %s",
 "message.worldsfall.vault.sealed": "Sealed. Something here was taken apart.",
 "message.worldsfall.vault.claimed": "You have already claimed this.",
 "message.worldsfall.hoard.refused": "The Hoarders do not want that.",
 "message.worldsfall.hoard.value": "The Hoarders consider it… (%s / %s)",
 "message.worldsfall.hoard.friendly": "The Hoarders let you look.",
 "message.worldsfall.cradle.too_soon": "The egg is still digesting. (%s s)",
 "message.worldsfall.cradle.not_ready": "The egg is not ready for that.",
 "message.worldsfall.cradle.stage.empty": "An empty cradle.",
 "message.worldsfall.cradle.stage.attuning": "The posts are waking.",
 "message.worldsfall.cradle.stage.nourishing": "The egg is hungry. (%s / %s Glow Glands)",
 "message.worldsfall.cradle.stage.vigil": "Stay close. The egg is listening.",
 "message.worldsfall.cradle.stage.hatching": "It is happening.",
 "message.worldsfall.cradle.stage.slumber": "Your Wyrm is sleeping.",
 "message.worldsfall.rift.already_open": "The Rift is already open.",
 "message.worldsfall.rift.sigil_present": "That Sigil is already in place.",
 "message.worldsfall.capsule.not_yours": "This capsule is sealed to someone else.",
 "message.worldsfall.skate.nothing_below": "Nothing below.",
 "message.worldsfall.tide.phase": "Tide: %s · %s",
 "container.worldsfall.shulker_satchel": "Shulker Satchel",
 "itemGroup.worldsfall": "Where Worlds Go to Fall",
 "item.worldsfall.echo_tablet.numbered": "Echo Tablet %s",
 "jukebox_song.worldsfall.unmade": "Worldsfall — Stillness",
 "item.worldsfall.music_disc_unmade": "Music Disc",
 "tide.worldsfall.low": "Low", "tide.worldsfall.rising": "Rising", "tide.worldsfall.high": "High", "tide.worldsfall.falling": "Falling",
 "tooltip.worldsfall.hold_shift": "Hold Shift for details",
 "tooltip.worldsfall.distance": "%s blocks away",
 "tooltip.worldsfall.upgrade": "Upgrade: %s",
 "tooltip.worldsfall.upgrade.gale": "Gale", "tooltip.worldsfall.upgrade.still": "Still", "tooltip.worldsfall.upgrade.hover": "Hover",
 "tooltip.worldsfall.satchel_size": "%s slots",
 "hint.worldsfall.end_arrival": "The End is quieter than it looks. Watch the void below.",
 "hint.worldsfall.skate_ride": "Hold jump to climb, sneak to dive or land. Double-tap sneak to let go.",
 "hint.worldsfall.skate_food": "Skates like Chorus Pods.",
 "hint.worldsfall.tide": "The void tide is stirring. Stay above it.",
 "hint.worldsfall.lurker": "Something is waiting where the light ends.",
 "hint.worldsfall.stillwater": "Stillwater drinks your experience.",
 "command.worldsfall.tide.get": "Tide %s at Y %s, next phase in %s s (paused: %s)",
 "command.worldsfall.tide.bad_phase": "Unknown tide phase: %s",
 "command.worldsfall.sundering.status": "Sundering done: %s, Descent: %s, Rift: %s",
 "command.worldsfall.sundering.already": "The Sundering already happened.",
 "command.worldsfall.sundering.started": "The Sundering begins.",
 "command.worldsfall.sundering.busy": "The Sundering is already running.",
 "command.worldsfall.gravity.debug": "Zone %s, gravity %s, fall damage ×%s",
 "command.worldsfall.wyrm.stage": "Set %s Wyrm(s) to %s",
 "command.worldsfall.boss.reset": "The Fracture is rebuilt.",
 "death.attack.worldsfall.void_erosion": "%1$s was unmade by the void",
 "death.attack.worldsfall.void_erosion.player": "%1$s was unmade by the void while fleeing %2$s",
 "death.attack.worldsfall.lurker_snare": "%1$s was snared by %2$s",
 "death.attack.worldsfall.lurker_snare.player": "%1$s was snared by %2$s",
 "death.attack.worldsfall.warden_slam": "%1$s was crushed by %2$s",
 "death.attack.worldsfall.warden_slam.player": "%1$s was crushed by %2$s",
 "death.attack.worldsfall.unmade_shard": "%1$s was pierced by %2$s",
 "death.attack.worldsfall.unmade_shard.player": "%1$s was pierced by %2$s",
 "death.attack.worldsfall.unmade_tendril": "%1$s was taken apart by %2$s",
 "death.attack.worldsfall.unmade_tendril.player": "%1$s was taken apart by %2$s",
 "death.attack.worldsfall.void_pulse": "%1$s stood still when the void breathed out",
 "death.attack.worldsfall.void_pulse.player": "%1$s stood still when the void breathed out",
 "effect.worldsfall.void_erosion": "Void Erosion", "effect.worldsfall.steadiness": "Steadiness", "effect.worldsfall.tidesight": "Tidesight",
 "item.minecraft.potion.effect.steadiness": "Potion of Steadiness", "item.minecraft.splash_potion.effect.steadiness": "Splash Potion of Steadiness",
 "item.minecraft.lingering_potion.effect.steadiness": "Lingering Potion of Steadiness", "item.minecraft.tipped_arrow.effect.steadiness": "Arrow of Steadiness",
 "item.minecraft.potion.effect.tidesight": "Potion of Tidesight", "item.minecraft.splash_potion.effect.tidesight": "Splash Potion of Tidesight",
 "item.minecraft.lingering_potion.effect.tidesight": "Lingering Potion of Tidesight", "item.minecraft.tipped_arrow.effect.tidesight": "Arrow of Tidesight",
 "trim_pattern.worldsfall.spire": "Spire Armor Trim", "trim_pattern.worldsfall.drift": "Drift Armor Trim", "trim_pattern.worldsfall.undersong": "Undersong Armor Trim",
 "trim_material.worldsfall.lumen": "Lumen Material", "trim_material.worldsfall.still": "Still Material", "trim_material.worldsfall.frill": "Frill Material",
 "biome.worldsfall.chorus_reach": "Chorus Reach", "biome.worldsfall.stillwater_basins": "Stillwater Basins", "biome.worldsfall.fallen_spires": "Fallen Spires",
 "biome.worldsfall.the_hoard": "The Hoard", "biome.worldsfall.the_underside": "The Underside", "biome.worldsfall.the_drift": "The Drift",
 "biome.worldsfall.fracture_ring": "The Fracture",
 "worldsfall.configuration.title": "Where Worlds Go to Fall",
 "key.worldsfall.category": "Where Worlds Go to Fall",
 "fluid_type.worldsfall.stillwater": "Stillwater",
}

def build():
    for b in BLOCKS:
        name("block", b)
    for f in FAMS:
        nice = title(f).replace("Brick", "Brick").replace("Tile", "Tile")
        L[f"block.{NS}.{f}_slab"] = f"{nice} Slab"
        L[f"block.{NS}.{f}_stairs"] = f"{nice} Stairs"
        if f in WALLS:
            L[f"block.{NS}.{f}_wall"] = f"{nice} Wall"
    for it in ITEMS:
        name("item", it)
    name("item", "mirror_shard_fragment", "Mirror Shard")
    name("item", "glowbulb", "Glowbulb")
    L[f"block.{NS}.stillwater"] = "Stillwater"
    L[f"block.{NS}.tidewisp_jar"] = "Bottled Tidewisp"
    for t in TEMPLATES:
        L[f"item.{NS}.{t}"] = "Smithing Template"
    L[f"item.{NS}.music_disc_unmade.desc"] = "Worldsfall — Stillness"
    for e in ENTITIES:
        name("entity", e)
    L[f"entity.{NS}.the_unmade"] = "The Unmade"
    for e in ("void_skate", "chorus_grazer", "underside_lurker", "hoarder", "spire_warden", "echo", "wild_shulker", "tidewisp", "shardling", "wyrm"):
        L[f"item.{NS}.{e}_spawn_egg"] = f"{title(e)} Spawn Egg"
    for k, (desc, flavor) in DESC.items():
        kind = "block" if k in BLOCKS else "item"
        L[f"{kind}.{NS}.{k}.desc"] = desc
        if flavor:
            L[f"{kind}.{NS}.{k}.flavor"] = flavor
    for idx, t in enumerate(TABLETS, 1):
        L[f"tablet.{NS}.{idx}"] = t
    for k, (t, d) in ADV.items():
        L[f"advancements.{NS}.{k}.title"] = t
        L[f"advancements.{NS}.{k}.description"] = d
    L.update(MESSAGES)
    # upgrade template descriptions in vanilla smithing-template style
    for up in ("gale", "still", "hover"):
        L[f"upgrade.{NS}.{up}_upgrade"] = f"{title(up)} Upgrade"
    sub = json.load(open(os.path.join(ROOT, "tools/gen/sound_lang.json")))
    L.update(sub)
    import expansion
    expansion.lang(L, title)
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(L.items())))

if __name__ == "__main__":
    build()
    print(f"lang keys: {len(L)}")
