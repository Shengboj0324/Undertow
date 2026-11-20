"""Authoritative list of Worldsfall sound events (design doc §19).

Each entry: event id -> (subtitle or None, list of file stems, character notes).
File stems are relative to assets/worldsfall/sounds/ (no extension).
Positional SFX are mono; music/loops may be stereo.
"""

BLOCK_TYPES = {
    # sound type: character
    "pale_loam": "soft moss, muffled crumble",
    "stillstone": "dense heavy stone, dry and low",
    "mirror_shard": "glass chime, bright tick",
    "lumen_crystal": "amethyst-like but lower and colder resonance",
    "voidroot": "creaking wood-fibre, fibrous snap",
    "glowbulb": "soft wet squelch plus faint glow hum",
    "driftstone": "dry papery stone",
    "null_obsidian": "deep thud, sub heavy",
    "fracture_glass": "dark glass, low crack",
    "spire_lantern": "soft metallic ring",
}

def block_events():
    ev = {}
    for t, note in BLOCK_TYPES.items():
        ev[f"block.{t}.break"] = (f"subtitles.block.generic.break", [f"block/{t}/break1", f"block/{t}/break2"], note)
        ev[f"block.{t}.step"] = (f"subtitles.block.generic.footsteps", [f"block/{t}/step1", f"block/{t}/step2", f"block/{t}/step3"], note + " (quiet footstep)")
        ev[f"block.{t}.place"] = (f"subtitles.block.generic.place", [f"block/{t}/place1", f"block/{t}/place2"], note)
        ev[f"block.{t}.hit"] = (f"subtitles.block.generic.hit", [f"block/{t}/step1", f"block/{t}/step2"], note)
        ev[f"block.{t}.fall"] = (None, [f"block/{t}/step1"], note)
    return ev

# event id: (subtitle text, [file stems], notes)
EVENTS = {
    # fluids
    "block.stillwater.swim": ("Stillwater ripples heavily", ["fluid/stillwater_swim1", "fluid/stillwater_swim2"], "slow heavy viscous swirl, muffled"),
    "item.bucket.fill_stillwater": ("Bucket fills", ["fluid/bucket_fill_stillwater"], "thick hollow glug"),
    "item.bucket.empty_stillwater": ("Bucket empties", ["fluid/bucket_empty_stillwater"], "thick hollow pour"),
    "block.stillwater.xp_drain": ("Experience drains", ["fluid/xp_drain"], "faint descending sparkle, 0.6 s"),
    # void skate
    "entity.void_skate.ambient": ("Void Skate calls", ["mob/void_skate/call1", "mob/void_skate/call2", "mob/void_skate/call3"], "deep gentle whale-like call, 1.5-2.5 s, pitch glide"),
    "entity.void_skate.flap": ("Wings whump", ["mob/void_skate/flap1", "mob/void_skate/flap2"], "soft low air whump"),
    "entity.void_skate.tame_progress": ("Void Skate chimes", ["mob/void_skate/chime"], "bright glassy chime arpeggio"),
    "entity.void_skate.hurt": ("Void Skate hurts", ["mob/void_skate/hurt1", "mob/void_skate/hurt2"], "short pained whale squeak"),
    "entity.void_skate.death": ("Void Skate dies", ["mob/void_skate/death"], "long falling whale moan"),
    # grazer
    "entity.chorus_grazer.ambient": ("Grazer hums", ["mob/chorus_grazer/hum1", "mob/chorus_grazer/hum2", "mob/chorus_grazer/hum3"], "hollow low hum, like blowing over a bottle"),
    "entity.chorus_grazer.eat": ("Grazer eats", ["mob/chorus_grazer/eat"], "soft munch with a tiny chime"),
    "entity.chorus_grazer.hurt": ("Grazer bleats", ["mob/chorus_grazer/hurt1", "mob/chorus_grazer/hurt2"], "short airy bleat"),
    "entity.chorus_grazer.death": ("Grazer dies", ["mob/chorus_grazer/death"], "airy falling bleat"),
    # lurker
    "entity.underside_lurker.click": ("Something clicks above", ["mob/lurker/click1", "mob/lurker/click2", "mob/lurker/click3"], "very soft dry chitin clicks"),
    "entity.underside_lurker.eyes_open": ("Lurker's eyes open", ["mob/lurker/eyes_open"], "dry rattle, rising, threatening, cuts through"),
    "entity.underside_lurker.snare": ("Snare cracks", ["mob/lurker/snare"], "whip crack"),
    "entity.underside_lurker.bite": ("Lurker bites", ["mob/lurker/bite"], "sharp chitin snap"),
    "entity.underside_lurker.retreat": ("Lurker retreats", ["mob/lurker/retreat"], "rapid skittering upward"),
    "entity.underside_lurker.hurt": ("Lurker hurts", ["mob/lurker/hurt"], "chitter screech short"),
    "entity.underside_lurker.death": ("Lurker dies", ["mob/lurker/death"], "chitter collapse"),
    # hoarder
    "entity.hoarder.ambient": ("Hoarder mutters", ["mob/hoarder/ambient1", "mob/hoarder/ambient2"], "enderman-like warble but warmer, pitched down"),
    "entity.hoarder.barter_accept": ("Hoarder accepts", ["mob/hoarder/accept"], "pleased warble plus portal shimmer"),
    "entity.hoarder.barter_refuse": ("Hoarder refuses", ["mob/hoarder/refuse"], "dismissive grunt warble"),
    "entity.hoarder.anger": ("Hoarder screams", ["mob/hoarder/anger"], "angry distorted scream"),
    "entity.hoarder.hurt": ("Hoarder hurts", ["mob/hoarder/hurt"], "pained warble"),
    "entity.hoarder.death": ("Hoarder dies", ["mob/hoarder/death"], "dying warble into static"),
    # spire warden
    "entity.spire_warden.grind": ("Plates grind", ["mob/spire_warden/grind1", "mob/spire_warden/grind2"], "stone plates grinding slowly"),
    "entity.spire_warden.step": ("Heavy footsteps", ["mob/spire_warden/step1", "mob/spire_warden/step2"], "heavy stone footstep, carries far"),
    "entity.spire_warden.slam": ("Spire Warden slams", ["mob/spire_warden/slam"], "huge stone impact with sub"),
    "entity.spire_warden.teleport_rise": ("Rising tone", ["mob/spire_warden/teleport_rise"], "1.0 s rising tone, magenta energy, threatening"),
    "entity.spire_warden.plate_shield": ("Plates flare", ["mob/spire_warden/plate_shield"], "bright resonant shield hum"),
    "entity.spire_warden.hurt": ("Spire Warden hurts", ["mob/spire_warden/hurt"], "stone crack plus deep groan"),
    "entity.spire_warden.death": ("Spire Warden dies", ["mob/spire_warden/death"], "collapse of stone plates, long"),
    "entity.spire_warden.breathe": ("Deep breathing", ["mob/spire_warden/breathe"], "slow deep breath"),
    # echo
    "entity.echo.hum": ("A block hums", ["mob/echo/hum"], "very faint static hum"),
    "entity.echo.unfold": ("Echo unfolds", ["mob/echo/unfold"], "glitchy snap unfold"),
    "entity.echo.scramble": ("Hotbar scrambles", ["mob/echo/scramble"], "quick digital shuffle"),
    "entity.echo.hurt": ("Echo hurts", ["mob/echo/hurt"], "glitch squeal"),
    "entity.echo.death": ("Echo dies", ["mob/echo/death"], "dissolving static"),
    # wild shulker / tidewisp / shardling
    "entity.wild_shulker.molt": ("Shulker molts", ["mob/wild_shulker/molt"], "shell clatter and soft pop"),
    "entity.tidewisp.pulse": ("Tidewisp pulses", ["mob/tidewisp/pulse1", "mob/tidewisp/pulse2"], "tiny soft glassy pulse"),
    "entity.shardling.hum": ("Shardling hums", ["mob/shardling/hum"], "crystalline hum, slightly dissonant"),
    "entity.shardling.dive": ("Shardling dives", ["mob/shardling/dive"], "fast whoosh with shimmer"),
    "entity.shardling.burst": ("Shardling bursts", ["mob/shardling/burst"], "crystal shatter"),
    # wyrm
    "entity.wyrm.chirp": ("Wyrm chirps", ["mob/wyrm/chirp1", "mob/wyrm/chirp2", "mob/wyrm/chirp3"], "small soft happy chirp, cute but not cartoony"),
    "entity.wyrm.warn": ("Wyrm chirps urgently", ["mob/wyrm/warn"], "fast repeated chirps, tide warning"),
    "entity.wyrm.flap": ("Wings flutter", ["mob/wyrm/flap"], "soft wing flutter"),
    "entity.wyrm.hurt": ("Wyrm whimpers", ["mob/wyrm/hurt"], "soft whimper"),
    "entity.wyrm.slumber": ("Wyrm curls up", ["mob/wyrm/slumber"], "gentle descending tone, curling up to sleep"),
    # systems
    "event.tide.stir": ("Void tide stirs", ["event/tide_stir"], "low hum fading in, 4 s, ominous, cuts through"),
    "event.tide.rise": ("Void tide rises", ["event/tide_rise"], "rising swell, 5 s"),
    "event.tide.fall": ("Void tide falls", ["event/tide_fall"], "long exhale, 5 s"),
    "event.erosion.static": ("Void erodes", ["event/erosion_static"], "hollow reversed whisper with teal static, 1.5 s"),
    "event.sundering.rumble": ("The island rumbles", ["event/sundering_rumble"], "very low rumble, 6 s"),
    "event.sundering.crack": ("The world cracks", ["event/sundering_crack"], "massive deep crack with tail, 4 s"),
    "event.reassembly.chord": ("A chord sounds", ["event/reassembly_chord"], "low warm chord, 4 s"),
    "event.reassembly.fly": ("Stone flies", ["event/reassembly_fly1", "event/reassembly_fly2"], "airy stone whoosh"),
    "event.reassembly.click": ("Stone clicks into place", ["event/reassembly_click1", "event/reassembly_click2"], "soft stone click"),
    "event.reassembly.lantern": ("Lantern ignites", ["event/reassembly_lantern"], "soft ignition ring"),
    "event.egg.pulse": ("Egg pulses", ["event/egg_pulse"], "warm soft heartbeat-like pulse"),
    "event.egg.crack": ("Egg cracks", ["event/egg_crack"], "shell crack with glowing shimmer"),
    "event.egg.hatch": ("Egg hatches", ["event/egg_hatch"], "burst plus soft chime chord, 4 s"),
    "event.gravity_anchor.hum": ("Gravity Anchor hums", ["event/gravity_anchor_hum"], "deep resonant hum loopable 3 s"),
    "event.rift.open": ("Rift opens", ["event/rift_open"], "tearing deep whoosh, crimson, 3 s"),
    "event.rift.close": ("Rift closes", ["event/rift_close"], "reverse implosion, 2 s"),
    "event.socket.hum": ("Keystone hums", ["event/socket_hum"], "magenta resonant hum, 2 s"),
    # items
    "item.rift_tether.fire": ("Tether fires", ["item/tether_fire"], "springy launch whoosh"),
    "item.rift_tether.latch": ("Tether latches", ["item/tether_latch"], "metallic claw clamp with pearl ping"),
    "item.rift_tether.reel": ("Tether reels", ["item/tether_reel"], "short ratchet reel"),
    "item.rift_tether.detach": ("Tether detaches", ["item/tether_detach"], "release snap"),
    "item.void_anchor.trigger": ("Void Anchor seals", ["item/void_anchor_trigger"], "glass capsule sealing with teal whoosh"),
    "item.capsule.hum": ("Capsule hums", ["item/capsule_hum"], "soft beacon hum, loopable"),
    "item.gateway_compass.bind": ("Compass binds", ["item/compass_bind"], "magical lock-in chime"),
    "item.satchel.open": ("Satchel opens", ["item/satchel_open"], "leather flap and shell clack"),
    "item.satchel.close": ("Satchel closes", ["item/satchel_close"], "shell clack and clasp click"),
    "item.echo_tablet.read": ("Glyphs shimmer", ["item/echo_tablet_read"], "soft glyph shimmer, 1.5 s"),
    "item.elytra.gale": ("Gale bursts", ["item/elytra_gale"], "wind burst"),
    "item.elytra.still": ("Air brake ripples", ["item/elytra_still"], "ripple brake whoosh"),
    "item.elytra.hover": ("Elytra hovers", ["item/elytra_hover"], "soft hover hum, 1 s"),
    "item.glowbulb.harvest": ("Glowbulb plucked", ["item/glowbulb_harvest"], "soft pluck pop"),
    "item.chorus_pod.pop": ("Pod pops", ["item/pod_pop"], "small ripe pop with sparkle"),
    # boss
    "entity.the_unmade.pulse": ("Core pulses", ["boss/core_pulse"], "deep throb with bright overtone"),
    "entity.the_unmade.shard_volley": ("Shards volley", ["boss/shard_volley"], "charge-up then crystalline volley, 1.2 s"),
    "entity.the_unmade.tendril_sweep": ("Tendril sweeps", ["boss/tendril_sweep"], "dark wet whoosh sweep"),
    "entity.the_unmade.lens_hum": ("Gravity warps", ["boss/lens_hum"], "warping wobble hum, 1 s"),
    "entity.the_unmade.void_pulse_inhale": ("The Unmade inhales", ["boss/void_pulse_inhale"], "2 s reverse inhale swell"),
    "entity.the_unmade.void_pulse_exhale": ("Void pulse erupts", ["boss/void_pulse_exhale"], "huge outward blast with teal shimmer"),
    "entity.the_unmade.swarm": ("Core cracks open", ["boss/swarm"], "cracking crystal opening"),
    "entity.the_unmade.tendril_grab": ("Tendril tracks you", ["boss/tendril_grab"], "creeping sinister rising tone, 1.5 s"),
    "entity.the_unmade.phase_freeze": ("The world holds still", ["boss/phase_freeze"], "single pure ringing tone, 2 s"),
    "entity.the_unmade.hurt": ("Core cracks", ["boss/hurt1", "boss/hurt2"], "glassy crack"),
    "entity.the_unmade.death": ("Everything stops", ["boss/final_silence"], "soft ringing that fades into silence, 4 s"),
    "entity.the_unmade.reform": ("Islands reform", ["boss/reform_chord"], "slow resolving major chord, 8 s, hopeful"),
    "entity.the_unmade.pylon_break": ("Pylon shatters", ["boss/pylon_break"], "crystal shatter with beam snap"),
}

AMBIENT = {
    # biome: (loop stem, additions stems, notes)
    "chorus_reach": ("ambient/chorus_reach_loop", ["ambient/chorus_reach_add1", "ambient/chorus_reach_add2"], "soft glassy wind with warm low pad; additions: ripening chime, distant hum"),
    "stillwater_basins": ("ambient/stillwater_loop", ["ambient/stillwater_add1", "ambient/stillwater_add2"], "felt very low hum, dry; additions: single distant drop, breath-like swell"),
    "fallen_spires": ("ambient/fallen_spires_loop", ["ambient/fallen_spires_add1", "ambient/fallen_spires_add2"], "wind whistling through broken masonry; additions: stone settling, distant lantern chime"),
    "the_hoard": ("ambient/hoard_loop", ["ambient/hoard_add1", "ambient/hoard_add2"], "slowed pitched-down birdsong and cattle into wind; additions: wrong-sounding leaf rustle, whale-moo"),
    "the_underside": ("ambient/underside_loop", ["ambient/underside_add1", "ambient/underside_add2"], "low resonant drone with crystal tinks; additions: reversed drip, reverse swell"),
    "the_drift": ("ambient/drift_loop", ["ambient/drift_add1", "ambient/drift_add2"], "low wind; additions: distant whale call, tide hum"),
    "fracture_ring": ("ambient/fracture_loop", ["ambient/fracture_add1", "ambient/fracture_add2"], "sub-bass drone and a choir that only inhales; additions: shards grinding, ash hiss"),
}

MUSIC = {
    "music.pale_pasture": ("music/pale_pasture", "Chorus Reach: gentle felt piano over warm pad, glass chimes; the only warm track; ~150 s"),
    "music.hold_still": ("music/hold_still", "Stillwater: near silence, one sustained glass tone slowly changing pitch; ~120 s"),
    "music.ladders_to_nowhere": ("music/ladders_to_nowhere", "Fallen Spires: slow descending piano phrases, bowed metal, unfinished feeling; ~150 s"),
    "music.undersong": ("music/undersong", "Underside: reversed piano notes swelling into themselves, teal synth pads, soft pulses; ~150 s"),
    "music.below_the_bottom": ("music/below_the_bottom", "Drift: wide low pads, whale-like synth calls, slow and vast; ~150 s"),
    "music.nothing_holds": ("music/nothing_holds", "After Sundering (once): a low crack then slow rising chord; ~60 s"),
    "music.unmade_1": ("music/unmade_1", "Boss phase 1: pulsing sub-bass and ticking glass; loopable ~64 s, 90 bpm"),
    "music.unmade_2": ("music/unmade_2", "Boss phase 2: phase 1 + breathing choir + falling-stone percussion; loopable ~64 s, same tempo/key"),
    "music.unmade_3": ("music/unmade_3", "Boss phase 3: phase 2 + distorted high sustained tone; loopable ~64 s"),
    "music.stillness": ("music/stillness", "After boss & music disc: melody of Ladders to Nowhere now rising and resolved; ~120 s"),
}
