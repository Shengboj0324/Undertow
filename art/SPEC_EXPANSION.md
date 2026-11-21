# Worldsfall expansion (1.2 → 2.0): art production spec

Companion to `art/SPEC_TEXTURES.md` and `art/SPEC_MODELS.md`. **Every rule in those two specs applies here unchanged**
(vanilla pixel-art style, light from the top left, chunky clustered noise, 4–8 colours per block, 3–6 per item, separate
`_emissive.png` for glowing pixels, Blender-authored and baked, deterministic scripts). Design source:
`/Users/jiangshengbo/Desktop/Where Worlds Go to Fall — Expansion & Dragon Proposal.md` (read the section a thing belongs to).

Paths are relative to `src/main/resources/assets/worldsfall/textures/`. File names are fixed: the game code already refers
to them. Animated textures need a `.png.mcmeta`.

## Palettes for the new places
| Place | Base | Accents | Glow |
|---|---|---|---|
| Warden (dragon) | obsidian black #120C18, slate #2B2635 | warden gold #C9A24A, old silver #AEB6C8 | magenta crack #FF4FD8, pale glyph white #F4E9FF |
| Zenith (sky layer) | Skystone pale silver-blue #B9C7DD / #93A6C4 / #6E7FA0 | pale gold dust #E8D38A | white-blue halo #E6F4FF → #9FD8FF |
| Undertow (below the tide) | teal-white haze #CFF3EC, near-black silhouettes #0B1416 | null silver #8F9AA6 | caustic teal #7FF2E2 |
| Gap / Causeway | weathered purpur, driftstone (existing) | lantern magenta #FF8BE0 → calm silver #DDE6F2 | |

## A. Blocks — `block/` (16×16)
| file | look |
|---|---|
| wardens_lantern (+ `_emissive`) | lantern sheet laid out exactly like `spire_lantern.png` (vanilla lantern template); obsidian cap/base banded with warden gold, pale silver glass; emissive = soft gold-white flame core |
| causeway_socket_top (+ `_top_emissive`), causeway_socket_side | like `keystone_socket_*` but weathered purpur + driftstone rim; top has a keystone-shaped slot whose edge glows magenta |
| causeway_plaque | small purpur plaque face: dark inset panel with a thin gold border (text is drawn by code on the panel) |
| drift_buoy (+ `_emissive`) | sheet for a small floating lantern (lantern-template layout): driftstone float, voidroot-fibre ties, a glowbulb core (emissive warm lilac-white) |
| drift_helm | driftstone hub with eight membrane-wrapped spokes (ship's-wheel motif), used on all faces of a thin model |
| ships_bell | aged purpur-bronze bell face (#9A6E8E/#C9A24A), dark mouth band |
| courier_cache_front (+ `_front_emissive`), courier_cache_side, courier_cache_top | end-stone chest-like box like `hoard_cache_*`, but neat: a violet ender-eye sigil on the front glowing (emissive) |
| tide_bell | small teal-bronze bell plate on a driftstone yoke, tide-wave engraving |
| roost_post_side, roost_post_top | weathered purpur post wrapped in voidroot fibre; top = soft membrane pad |
| gravity_plate (+ `_emissive`) | stillstone pressure plate with a teal ring inlay (ring emissive) |
| anchor_frame_lamp (+ `_emissive`) | like `anchor_frame` with a glowing teal core panel |
| wyrm_roost_top, wyrm_roost_side | nest: woven voidroot and chorus twigs, pale shell fragments on top |
| tidewrack | heap of driftstone chips, teal silt and tangled voidroot (half-height pile, like `purpur_rubble`) |
| lantern_moth_jar (+ `_emissive`) | same layout as `tidewisp_jar.png`; a small magenta moth glow inside instead of a wisp |
| starfall_shard (+ `_emissive`) | cluster (cross model) of pale gold-white crystal shards, emissive tips |
| skystone | pale silver-blue stone (#B9C7DD/#93A6C4/#6E7FA0) with sparse pale-gold specks (#E8D38A), soft layered strata |
| polished_skystone, skystone_bricks, skystone_tiles, chiseled_skystone | the usual family; chiseled = a rising ladder/star motif in pale gold |
| halo_glass (+ `_emissive`) | translucent white-blue glass (alpha ~140) with a thin bright ring motif; emissive = ring |
| orrery_ring | heavy purpur block banded with Halo Glass strips and gold rivets (used for the giant rings) |
| orrery_lens (+ `_emissive`) | dark polished lens face: black centre, concentric silver-gold rings, emissive pale-white pinpoint centre |
| orrery_socket_top (+ `_top_emissive`), orrery_socket_side | skystone socket with a key slot; emissive slot edge pale gold |
| null_coral, null_coral_fan | cross plants: branching black-and-silver coral (#0E1114/#8F9AA6, silver tips), transparent background |
| null_coral_block, null_coral_bricks, polished_null_coral | dense black coral with silver branch veins; bricks; polished = smooth dark with silver flecks |
| archive_shelf_front (+ `_front_emissive`), archive_shelf_side, archive_shelf_top | bookshelf-like: driftstone frame, slots holding pale tablets and dark codices; emissive = faint teal glyphs on a few spines |
| bell_winch_top, bell_winch_side | driftstone winch drum with dark chain coils and an iron crank |
| mural_panel_dark | dark undershale panel with a carved outline |
| mural_panel_lit (+ `_emissive`) | same carving filled with warm purpur colour; emissive = faint glow along the carved lines |
| archive_tiles | pale, water-worn stone tiles (#C9D6D2/#9FB3AE) with faint teal stains (Sunken Archive floor and walls) |

## B. Items — `item/` (16×16)
| file | look |
|---|---|
| pact_glyph_tide, _shards, _echoes, _hoard, _memory, _stillness, _lone, _depths | a small round slate token (pact glyph) in the vanilla smithing-template spirit: dark stone disc, one glowing glyph whose colour names the pact: tide teal #3AE0D0 (wave), shards pink-white (three shards), echoes violet (a hollow block outline), hoard amber (a pile), memory gold (an eye), stillness silver (a ring), lone pale red (a single dragon wing), depths deep blue (a downward spiral) |
| pact_crystal_tide … pact_crystal_depths (same 8 suffixes) | an End-Crystal-like item: a faceted crystal in a dark cage, the cage glyph tinted the pact colour |
| stillness_crystal | the same crystal, calm white with a silver cage |
| dragon_scale | a single overlapping dark scale, obsidian with a gold rim and a faint magenta shimmer line |
| wyrm_barding | folded scale-plate armour piece, slate and gold |
| tuning_fork | a two-pronged silver fork with a small magenta crystal at the base |
| warden_banner_pattern | banner-pattern item look (like vanilla patterns): paper with a dragon-wing-over-pillar motif |
| wardenborn_seal | a slate-and-gold wax seal with a wing imprint |
| music_disc_warden, music_disc_ladders, music_disc_undertow | discs in the style of the existing `music_disc_unmade`: warden = obsidian with gold label; ladders = pale silver with a ladder glyph; undertow = teal-white with a dark spiral |
| causeway_keystone | a wedge-shaped purpur keystone with a driftstone band and a lumen chip |
| city_chart | rolled map with a purple End City tower silhouette |
| void_chart, void_chart_filled | dark parchment chart (empty: blank grid; filled: pale island contours and teal tide shading) |
| star_chart | the filled chart with gold star points and a pale-gold border |
| courier_token | a small amber Hoarder token with an arrow glyph |
| mirrorfish, cooked_mirrorfish | a flat, mirror-silver fish with dark star specks; cooked = warm toasted silver |
| driftsilt | a small pile of teal-grey glittering silt |
| harrier_plume | long slate-blue feather with silver-gold bars |
| current_vane | a compass-like dial: silver ring, pale-gold vane needle |
| leviathan_barnacle | knobbly pale shell cluster with a soft gold inside |
| eclipse_dust | dark powder with silver glints (dye-like) |
| archive_codex | a thick dark book with a teal glowing glyph on the cover (cover glyph emissive) |
| abyssal_anchor | like `void_anchor` but darker teal-black glass and a silver chain, a bright white core |
| undertow_pearl | a teal-white pearl with a dark swirl inside |
| void_bell | small icon of the diving bell: dark frame, glass window, teal core on top |
| null_pigment | a small heap of black-silver pigment |
| bottled_lantern_moth | uses the block item model of the jar, no sprite needed |

## C. Particles — `particle/` (8×8 unless noted)
`wardensong_glyph_0..3` (pale glyph strokes), `sky_current_0..2` (soft elongated white-gold streak, 16×8), `downdraft_dust_0..2`
(grey dust puffs), `starfall_spark_0..1` (gold-white spark), `maw_swirl_0..3` (teal spiral wisps), `eclipse_mote` (tiny silver point).

## D. GUI and environment
- `gui/lattice.png` 256×256: a 176×166 panel in the End palette (dark undershale frame, thin teal line, magenta corner gems).
- `gui/void_chart.png` 256×256: chart frame (dark parchment border 8 px, inner area 240×240 left transparent).
- `environment/overmoon.png` 64×64: pale silver-white disc, soft limb darkening, faint gold halo (alpha falloff).

## E. Entity models (Blender → JSON + skins + previews), per `SPEC_MODELS.md`
Every listed part must exist with that exact name.

### lumen_heart — `lumen_heart/lumen_heart.png` (+ `_emissive`), 64×64
A Lumen crystal cluster that hangs from a ceiling and heals the dragon (replaces End Crystals in the Underside fight). Faceted
teal crystal (#2FC9BB→#C8FFF8), dark voidroot roots on top, a white-hot core visible through the facets.
Parts: `core` (root, pivot y=8; the main 6×10×6 crystal), `shard_1`..`shard_4` (children of core, smaller crystals angled
outward), `roots` (child of core, hanging strands above).

### gale_harrier — `gale_harrier/gale_harrier.png` (+ `_emissive`), 64×64
Hawk-like Zenith predator, wingspan ≈ 2 blocks. Slate-blue (#4A5B7A) with silver-gold barred flight feathers, pale-gold
hooked beak, amber eyes (emissive).
Parts: `body`, `head` → `beak`, `left_wing` → `left_wing_tip`, `right_wing` → `right_wing_tip`, `tail`, `left_leg`, `right_leg`.

### leviathan_drifter — `leviathan_drifter/leviathan_drifter.png` (+ `_emissive`), 256×256
A colossal slow sky creature; modelled at **¼ scale** (the renderer scales ×4): about 12 blocks long in model space (≈ 48 in
game). Pale silver-blue whale/manta body (#B9C7DD/#7F90B0), slow broad pectoral fins, gold speckles and glowing gold seam
lines (emissive), small calm eyes. A flat `back_ridge` area on top where a living garden is drawn by code.
Parts: `body` (root), `head` → `jaw`, `left_fin` → `left_fin_tip`, `right_fin` → `right_fin_tip`, `tail_1` → `tail_2` →
`tail_fluke`, `back_ridge` (child of body, flat top at the garden height).

### tide_eel — `tide_eel/tide_eel.png` (+ `_emissive`), 128×64
A long pale serpent of the Undertow, white-teal and slightly translucent-looking, glowing teal ring markings (emissive),
a narrow head with a hinged jaw. About 6 blocks long.
Parts: `head` (root) → `jaw`; `body_1` (child of head) → `body_2` → … → `body_8` (each a child of the previous), `tail_fin`
(child of body_8).

### drowned_ascendant — `drowned_ascendant/drowned_ascendant.png` (+ `_emissive`), 64×64
A silent, translucent echo of the old civilization: tall, thin robed humanoid (≈ 2.4 blocks), hood, pale teal-white robe,
glowing eyes under the hood, holding a small tablet.
Parts: `body`, `robe` (child of body), `head` → `hood`, `left_arm`, `right_arm` → `tablet`.

### glimmer — `glimmer/glimmer.png` (+ `_emissive`), 32×32
A tiny fish of light (about 4×2×6 px). Almost entirely emissive pale teal.
Parts: `body`, `tail`.

### lantern_moth — `lantern_moth/lantern_moth.png` (+ `_emissive`), 32×32
A small moth drawn to Spire Lanterns: dusty purpur-grey wings with magenta eyespots (emissive), fuzzy pale body, feathery
antennae.
Parts: `body`, `left_wing`, `right_wing`, `antennae` (child of body).

### void_bell — `void_bell/void_bell.png` (+ `_emissive`), 128×128
The player-built diving bell (≈ 3 blocks wide, 3 tall): a dark iron-and-purpur frame with Stillglass window panels (paint
them dark translucent-looking), a Gravity Anchor core glowing teal on top (emissive), a lamp underneath (emissive), a hatch.
Parts: `shell` (root), `core` (child), `lamp` (child), `hatch` (child).

## F. Texture variants on existing or vanilla models
- `entity/shulker_archivist.png` 64×64 — **vanilla shulker UV layout**: a peaceful archivist: pale dusty bone-white shell
  (#D8D2C4/#B8B0A0) with a faint carved glyph on the lid, a grey-violet head with calm pale eyes.
- `entity/wyrm/wyrm_hatchling_wardenborn.png`, `wyrm_fledgling_wardenborn.png`, `wyrm_drake_wardenborn.png` (+ `_emissive` each)
  — same UV as the existing Wyrm skins; slate (#2B2635) scales with warden-gold (#C9A24A) edges, silver belly.
- `entity/wyrm/wyrm_drake_barding.png` — same UV as `wyrm_drake.png`, transparent except scale-plate armour on the back,
  chest and neck (slate and gold).
- `entity/wyrm/wyrm_drake_saddlebags.png` — same UV, transparent except two leather saddlebags with shell clasps hung on the
  flanks behind the saddle.
- `entity/equipment/wings/elytra_scaled.png`, `elytra_gale_scaled.png`, `elytra_still_scaled.png`, `elytra_hover_scaled.png`,
  `elytra_tempest.png`, `elytra_tempest_scaled.png` — 64×32, same layout as the existing `elytra_*.png`. Scaled = overlapping
  obsidian-and-gold scales over the membrane (keeping each upgrade's identifying feature); tempest = Gale's teal veins
  become bright silver-white storm streaks.
- `entity/banner/warden.png` and `entity/shield/warden.png` — banner/shield pattern masks in the **vanilla pattern format**
  (64×64, white on transparent at the vanilla banner/shield UV positions): a dragon wing folded over a pillar.

## G. Review
Render `art/previews/expansion_<group>.png` contact sheets and a 1024×1024 beauty render per model (`art/previews/<name>.png`).
