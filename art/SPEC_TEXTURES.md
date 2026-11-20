# Worldsfall — Texture Production Spec (blocks, items, particles, GUI)

Source of truth for art direction: `worldsfall-design-document.md` §18 (read §18.1–§18.3, §18.6, §18.8, §18.9 fully; also the
per-block "Look" columns in §6.2/§6.3 and per-item "Look" in §9). Vanilla Minecraft art style at heart: 16×16 pixel art,
light from the top-left, clustered chunky noise (never single-pixel static), 4–8 colours per block texture, 3–6 per item.
Every new stone must sit between vanilla end stone and deepslate in contrast. Glowing parts get a SEPARATE emissive
texture `<name>_emissive.png` (RGBA; transparent everywhere except the glowing pixels) — the game renders it fullbright.

All paths below are relative to `src/main/resources/assets/worldsfall/textures/`. All PNG, RGBA, exact sizes given.
Animated textures need a sibling `.png.mcmeta` (`{"animation":{"frametime":N,"interpolate":true}}`), frames stacked vertically.

## Blocks (16×16 unless noted) — `block/`
| file | look |
|---|---|
| pale_loam_top | short lilac moss, two tones (#9B7BB0/#B493C4) with tiny pale flecks (#F1D6FF) |
| pale_loam_side | vanilla-like end stone (#E6DFA8 body, #BDB37C/#635C40 shading) with lilac moss draping 2–3 px down, irregular |
| lilac_tuft | cross plant: thin curly blades, two lilacs, pale tips, transparent background |
| chorus_pod_stage0, chorus_pod_stage1, chorus_pod_stage2 | rounded segmented pod used on a small box model (use the whole 16×16 as the unwrapped pod skin): stage0 pale violet small, stage1 medium, stage2 ripe lavender (#D8B6FF) |
| chorus_pod_stage2_emissive | bright lavender centre line of the ripe pod |
| stillstone | near-black violet (#2A2140/#3A2D58/#0C0B1C) with sparse 1–2 px silver flecks (#8FB0FF/#DFE8FF) like distant stars |
| polished_stillstone | same palette, smooth, subtle concentric rings |
| stillstone_bricks | bricks in stillstone palette |
| chiseled_stillstone | 4-point star motif in silver-blue on stillstone |
| mirror_shard | thin vertical black glass sliver (cross model), one bright white-blue edge highlight, transparent bg |
| rimglass | very dark smooth glass, faint blue sheen, partly translucent (alpha ~180) |
| stillglass | near-black glass with faint star speckle, translucent (alpha ~150 body, opaque 1px frame darker) |
| stillglass_pane_top | 16×16, the pane edge strip (vanilla pane-top style) in stillglass colours |
| weathered_purpur | vanilla purpur (#A87BB6/#8D649D) desaturated ~20%, uneven pale vertical water streaks, a few chipped corners |
| weathered_purpur_pillar, weathered_purpur_pillar_top | pillar side/top in weathered purpur |
| cracked_purpur_pillar, cracked_purpur_pillar_top | purpur pillar with a diagonal crack and missing chips |
| purpur_rubble | loose heap of purpur chunks (used on top and sides of a half-height block) |
| spire_lantern | lantern texture sheet laid out like vanilla `lantern.png` (16×16 sheet used by vanilla lantern template models): purpur cap/base, pale glass sides |
| spire_lantern_emissive | the vertical magenta-white core (#FFB6F0 → white) |
| undershale | horizontal layered bands of slate (#2E2A33/#3A3540) with thin violet lines (#3A2A4A) |
| polished_undershale | smooth bands |
| undershale_bricks | bricks |
| undershale_tiles | 2×2 tiles |
| ceiling_moss | dangling dark-teal strands, transparent bg, longer at centre (hangs DOWN from the top edge) |
| lumen_crystal_base, lumen_crystal_middle, lumen_crystal_tip | hanging dripstone-like faceted teal crystal (cross model), dark teal base (#2FC9BB→#0E5F59) toward top, transparent bg; the TIP points DOWN |
| lumen_crystal_tip_emissive | bright white-cyan (#C8FFF8) tip pixels only |
| lumen_cluster | amethyst-cluster-like teal crystal cluster (cross), transparent bg |
| lumen_cluster_emissive | bright crystal edges |
| lumen_crystal_block | faceted teal crystal block |
| voidroot_middle, voidroot_tip | twisted dark-violet roots (#3A2A4A, #241a30), cross model, transparent bg; tip has amber tips (#FFD27A) at the bottom |
| voidroot_tip_emissive | the amber tip pixels |
| glowbulb | round translucent magenta bulb (#FF7AD9) with darker rim, short dark stem at TOP (hangs), transparent bg (cross model) |
| glowbulb_emissive | the brighter core |
| glowbulb_picked | the stem with a small shrivelled, dull bulb |
| lumen_lamp, lumen_lamp_on | undershale frame holding a teal crystal; on = glowing |
| lumen_lamp_on_emissive | crystal glow |
| driftstone | weathered grey-violet stone (#4B3D66 base) with diagonal wind-scour streaks |
| polished_driftstone, driftstone_bricks | variants |
| tide_gauge_side | purpur-and-glass column side: purpur frame, glass window in the middle (transparent-ish) |
| tide_gauge_top | purpur cap |
| tide_gauge_fluid | teal liquid texture (#3AE0D0, crest #B8FFF7 top row) used for the internal level element |
| tide_gauge_fluid_emissive | the crest row and a few sparkles |
| null_obsidian | crimson-black obsidian (#1A0510 / #2b0a18) with thin dark-red veins |
| null_obsidian_emissive | faint red vein pixels (#7a1030) |
| fracture_glass | dark red translucent glass with a few bright crack lines |
| fracture_glass_emissive | crack lines (#FF3FB4) |
| unmade_shard | jagged black spike (cross), magenta glowing edges |
| unmade_shard_emissive | the edges |
| ash_stone | ash-grey island stone of the Fracture (#3A2F3A, #2a222a, #4a3d4a) |
| hoard_cache_side, hoard_cache_top, hoard_cache_front | lidded chest-like box made of end stone with Overworld blocks (grass, log, ore bits) crudely embedded; front has a latch |
| keystone_socket_top, keystone_socket_side | purpur pedestal; top has a key-shaped hollow |
| keystone_socket_top_emissive | the key hollow glowing magenta |
| spire_vault_side, spire_vault_top, spire_vault_front | an ornate purpur reliquary box with an inlaid magenta crystal lock |
| spire_vault_front_emissive | the crystal lock |
| drift_cache_side, drift_cache_top, drift_cache_front | a weathered driftstone/teal-glass cache |
| drift_cache_front_emissive | teal glass glow |
| sigil_pedestal_side, sigil_pedestal_top | undershale + chiseled stillstone pedestal with a disc recess |
| sigil_pedestal_top_emissive | recess glow (violet-white) |
| egg_cradle_top, egg_cradle_side, egg_cradle_post, egg_cradle_post_lit | ring altar of end stone bricks + obsidian; posts hold Lumen crystal (unlit dull teal; lit bright) |
| egg_cradle_post_lit_emissive | crystal glow |
| gravity_anchor_frame | purpur arcs/frame texture for the cage model |
| gravity_anchor_core | heavy obsidian sphere skin (dark, subtle purple sheen) |
| anchor_frame | thin purpur lattice, faint teal edges, transparent between bars |
| anchor_frame_emissive | faint teal edges |
| fracture_rift_frame_side, fracture_rift_frame_top | ring of Null Obsidian with magenta cracks |
| fracture_rift_frame_top_emissive | cracks, scaled up with sigils (one texture is fine) |
| fracture_rift | 16×256 ANIMATED (16 frames, frametime 2, interpolate): swirling crimson-black surface, dark centre spiral with magenta/crimson streaks |
| still_throne_side, still_throne_top, still_throne_top_filled | pale stone throne with a socket; filled shows a calm white-silver sphere |
| still_throne_top_filled_emissive | the sphere glow (#B9D3FF→white) |
| tidewisp_jar | glass jar (cross-ish small model sheet, 16×16) holding a teal-white wisp |
| tidewisp_jar_emissive | the wisp |
| stillwater_still | 16×512 ANIMATED (32 frames, frametime 2, interpolate): almost-black slightly violet base (#0C0B1C) with slow drifting star specks (#DFE8FF, #8FB0FF) — must tile seamlessly in X/Y and loop in time |
| stillwater_flow | 32×1024 ANIMATED (32 frames, frametime 2, interpolate): dark slow curtain with faint vertical streaks |
| stillwater_overlay | 16×16 dark edge overlay for adjacent glass |

## Items (16×16) — `item/`
skate_membrane, glow_gland (teal glowing gland), grazer_frill (crystal-like lavender plate), voidroot_fiber, lumen_crystal_shard,
mirror_shard_fragment, echo_dust (faint static-like dust pile), lurker_lens (dark lens with magenta eye glint), bottle_of_stillwater,
chorus_pod, roasted_chorus_pod, grazer_flank, cooked_grazer_flank, glowbulb, drift_stew, stillwater_tea,
void_anchor (grey-capped teal glass lantern, coiling strand, chain loop — §9.2), cracked_void_anchor (same, cracked glass, dimmer),
rift_tether (coil of dark cord + sea-green pearl in a 3-prong pale claw — §9.3), rift_tether_cast (claw without pearl/coil only),
gateway_compass_00 … gateway_compass_15 (16 frames: round dark dial, violet rim, glowing pink star needle rotating 22.5° per frame, tiny dark diamond at top — §9.4),
gateway_compass_bound_overlay is NOT needed,
shulker_satchel (violet leather bag, shell-plate flap, brass clasp with amber glow — §9.5), void_lens (spyglass with dark undershale barrel + teal lens),
skate_harness (§9.9), spire_key (long purpur key, magenta crystal bow, tower-shaped teeth), echo_tablet (palm slate, faint glowing glyphs),
spire_sigil (purpur disc, tower glyph), tide_sigil (teal glass disc, wave glyph), under_sigil (undershale disc, inverted-tree glyph),
rift_key, heart_of_stillness (smooth pale sphere, calm white core), stillness_fragment, dragon_egg_fragment (shard of dark egg shell with pink crack),
slumbering_egg (curled slate-blue wyrm egg with soft pink glow), gale_upgrade_template, still_upgrade_template, hover_upgrade_template
(smithing-template style, each with its motif: teal veins / mirror edge / two teal nodes), spire_armor_trim_smithing_template,
drift_armor_trim_smithing_template, undersong_armor_trim_smithing_template (vanilla trim-template style, purpur/teal tints),
music_disc_unmade (black disc, magenta-pink label), stillwater_bucket, bottled_tidewisp,
and spawn eggs (vanilla 26.x style unique egg sprites with the creature's colours): void_skate_spawn_egg, chorus_grazer_spawn_egg,
underside_lurker_spawn_egg, hoarder_spawn_egg, spire_warden_spawn_egg, echo_spawn_egg, wild_shulker_spawn_egg, tidewisp_spawn_egg,
shardling_spawn_egg, wyrm_spawn_egg.
Emissive item layers (fullbright overlays, 16×16): void_anchor_emissive, glow_gland_emissive, lumen_crystal_shard_emissive, echo_tablet_emissive,
heart_of_stillness_emissive, gateway_compass_needle is part of the frames (no separate emissive), spire_key_emissive, tide_sigil_emissive,
bottled_tidewisp_emissive, slumbering_egg_emissive.

## Particles (8×8 unless noted) — `particle/`
pollen_mote_0..3, pod_pop_0..3, still_ring_0..3 (expanding pale ring), stillfall_mist_0..3, dust_fall_0..1, socket_spark_0..3, up_mote_0..3,
up_drip_0..1, skate_wake_0..3 (teal dots fading), tide_crest_0..3 (cyan sparkles), erosion_static_0..3 (teal static), ash_inflow_0..3 (grey ash),
shard_spark_0..3 (magenta), tidewisp_trail_0..3 (teal-white streamer), shell_fragment_0..3 (dark egg shell bits with pink edge).

## GUI — `gui/`
- `gui/advancements/backgrounds/worldsfall.png` 16×16 tile: dark Undershale with faint teal crystal glints (§18.9).
- `gui/sprites/hud/skate_stamina_background.png` 9×38 and `skate_stamina_progress.png` 9×38: a slim vertical teal arc (draw an arc shape).
- `gui/sprites/hud/tide_indicator_background.png` 18×18 and `tide_indicator_fill.png` 18×18: tiny wave icon frame + teal fill.
- `gui/sprites/boss_bar/unmade_background.png` 182×5 and `unmade_progress.png` 182×5 (magenta) — optional.

## Environment — `environment/`
- `environment/tide_plane.png` 256×256 tileable: flowing teal sheet detail (luminance map; the shader tints it).
- `environment/nebula_noise.png` 256×256 tileable greyscale fBm noise (the sky shader also generates procedurally; this is a fallback).
