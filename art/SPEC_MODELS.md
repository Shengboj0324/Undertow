# Worldsfall — Entity Model Production Spec (Blender → JSON + textures)

Art direction: `worldsfall-design-document.md` §8 (every creature's "Look"), §10.5 (Wyrm), §13.10 (The Unmade),
§18.4 (model guidelines + per-creature visual keys table), §18.5 (animation principles), §18.2 (colour script).
Cuboids only, vanilla-like pixel density (1 texel = 1/16 block). Silhouette must read as a flat black shape at 32 px.
Soft rather than cartoonish; the Wyrm must NOT resemble the Ender Dragon.

## Deliverables per model `<name>`
1. `art/blender/<name>.blend` — the authored model (one object per cube, parented to empties = bones, named exactly as below).
2. `src/main/resources/assets/worldsfall/models/entity/<name>.json` — exported geometry (format below).
3. `src/main/resources/assets/worldsfall/textures/entity/<texture>.png` — box-UV skin (RGBA, size = `texture_size`).
4. `..._emissive.png` sibling for glowing parts (transparent except glowing texels), where listed.
5. `art/previews/<name>.png` — a 3/4 turntable-style beauty render (Eevee, dark violet backdrop, soft rim light, emissive bloom)
   at 1024×1024 so the look can be reviewed.

## JSON format (loaded at runtime by `dev.worldsfall.client.model.JsonModels`)
```json
{
  "texture_size": [128, 64],
  "parts": [
    { "name": "body", "parent": null, "pivot": [0, 18, 0], "rotation": [0, 0, 0],
      "cubes": [ { "origin": [-4, -3, -6], "size": [8, 6, 12], "uv": [0, 0], "inflate": 0.0, "mirror": false } ] },
    { "name": "head", "parent": "body", "pivot": [0, -2, -6], "rotation": [10, 0, 0], "cubes": [ ... ] }
  ]
}
```
Conventions = vanilla Java entity models (identical to Blockbench "Modded Entity" / vanilla LayerDefinition):
- units are pixels; **Y points DOWN**; the ground is **y = 24** in root space; the creature faces **−Z** (north); its right side is −X.
- `pivot` is relative to the parent's pivot (root-level parts: relative to the model origin). `rotation` in degrees (X, Y, Z), applied Z→Y→X like vanilla.
- cube `origin` is the min corner relative to the owning part's pivot; `size` may contain 0 for flat planes (wings, fins, frills).
- `uv` is the top-left of the standard Minecraft box-UV unwrap: for size (w,h,d): top = (u+d, v, w×d); bottom = (u+d+w, v, w×d);
  east/right-side = (u, v+d, d×h); north/front = (u+d, v+d, w×h); west = (u+d+w, v+d, d×h); south/back = (u+2d+w, v+d, w×h).
  Pack UV islands without overlap inside `texture_size`. `mirror` mirrors the unwrap (vanilla semantics).
- Every part listed below MUST exist with that exact name (animations look them up). Extra parts are allowed.

## Texturing method (Blender, "use it properly")
Build the cuboids in Blender with a real box-UV layout matching the export. Paint/bake skins in Blender: procedural shader
materials (noise/voronoi/gradient, bioluminescent spot patterns, chitin/membrane/scale looks) baked to the low-res UV image
(Cycles bake, emission/diffuse colour pass), then post-process in Python inside Blender: quantise to a 6–12 colour palette per
creature taken from §18.2, add top-left light (lighter top faces, darker bottoms), hand-place pixel details (eyes, spots,
edge lines) at known UV coordinates. Emissive maps are exported separately. Keep it pixel-crisp (no blur) and vanilla-like.

## Models (names, required parts, scale, texture)

### void_skate — texture `void_skate/void_skate.png` + `void_skate/void_skate_dusk.png` (+ `_emissive` for each), size 128×128
Manta-like glider, wingspan ≈ 5 blocks (80 px), body ≈ 0.8 block tall. Deep indigo top (#21446B/#2a2560), lighter violet
underside, rows of teal bioluminescent spots along wing edges + down the centre, glowing teal leading-edge line, whip tail with
glowing tip, two curled cephalic fins at the front, small pale calm eyes. Dusk variant: warmer violet with amber spots.
Parts: `body` (root, pivot near y≈20), `left_wing` → `left_wing_tip`, `right_wing` → `right_wing_tip` (wings are flat-ish
plates 1–2 px thick; inner pivots at the body edge, tip pivots at mid-span so they ripple), `tail_1` → `tail_2` → `tail_3`
(thin whip, tip glows), `left_fin`, `right_fin` (cephalic fins), `harness` (child of body: leather saddle-pad with straps and brass
rings, edged with pale stitched membrane — visible only when saddled).

### chorus_grazer — `chorus_grazer/chorus_grazer.png` (+`_emissive`), 128×64
Cow-sized six-legged browser, pale lilac (#B493C4/#D9C6E6) with lighter belly; long curved neck; small head with dark round eyes
and short soft snout; back frill of crystal-like plates glowing lavender at the tips; thin dark-violet legs.
Parts: `body`, `neck_1` → `neck_2` → `neck_3` → `head`, `frill` (child of body; plates), `leg_front_left`, `leg_front_right`,
`leg_mid_left`, `leg_mid_right`, `leg_back_left`, `leg_back_right`, `tail`.
Also export `chorus_grazer_sheared.png` variant (frill plates stubby/dull).

### underside_lurker — `underside_lurker/underside_lurker.png` (+`_emissive` for the 4 magenta eyes), 64×64
Rounded armored teardrop body of near-black chitin (≈1 block) with a pointed crown on top (thread attaches there); ring of FOUR
small magenta eyes (#FF3FB4/#FF7AD9) around its middle; SEVEN long thin jointed legs splayed downward like an open hand, reach ≈2 blocks.
Parts: `body`, `crown` (child of body), `leg_0`…`leg_6` each with child `leg_0_lower`…`leg_6_lower`.

### hoarder — `hoarder/hoarder.png` (+`_emissive` amber eyes), 64×64
Enderman-like (vanilla proportions, slightly shorter and stooped), black body, ragged patchwork cloak of Overworld materials
(grass green, bark brown, wool colours) over the shoulders, a pouch at its side, amber eyes.
Parts: `head`, `body`, `cloak` (child of body), `pouch` (child of body), `right_arm`, `left_arm`, `right_leg`, `left_leg`.

### spire_warden — `spire_warden/spire_warden.png` (+`_emissive` magenta binding light + eyes), 128×128
Tall heavy Enderman figure ≈3.5 blocks (56 px), plates of cracked purpur on shoulders, chest and forearms held by faint magenta
light; head partly enclosed by a purpur crest; magenta eyes.
Parts: `head` → `crest`, `body`, `right_arm` → `right_forearm`, `left_arm` → `left_forearm`, `right_leg`, `left_leg`,
plates: `plate_shoulder_right`, `plate_shoulder_left`, `plate_chest`, `plate_forearm_right`, `plate_forearm_left`
(children of the matching limb/body). Also export `spire_warden_cracked.png` and `spire_warden_shattered.png` skins (damage states).

### echo — `echo/echo.png` (+`_emissive` two white eye points), 64×32
Thin, faint, semi-transparent Enderman shape with static-like shimmer at the edges (alpha 120–200 in the skin), no eyes visible
except two white points (emissive). Parts: `head`, `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`.

### wyrm_hatchling, wyrm_fledgling, wyrm_drake — `wyrm/wyrm_hatchling.png`, `wyrm/wyrm_fledgling.png`, `wyrm/wyrm_drake.png` (+`_emissive` each)
See §10.5. Hatchling: cat-sized, round body, oversized glowing pink eyes, two stubby horns, tiny fast wings, slate-blue scales
(#4b5d7a/#3a4a66) with pale teal belly. Fledgling: wolf-sized, longer, wings larger than body (indigo membranes, pale veins),
horns curving back, short lavender-glowing crest. Drake: horse-sized, sleek long neck, slate-blue→deep indigo, wide violet wings
with pale veins, two swept-back pale horns, glowing pink eyes, row of small lavender spines. Gentle, never menacing.
Parts (all three): `body`, `neck` → `head` (children `horn_left`, `horn_right`, `jaw`), `left_wing` → `left_wing_tip`,
`right_wing` → `right_wing_tip`, `tail_1` → `tail_2` → `tail_3`, `leg_front_left`, `leg_front_right`, `leg_back_left`,
`leg_back_right`, `crest` (child of body; spines).

### shardling — `shardling/shardling.png` (+`_emissive` magenta core), 32×32
Floating jagged black crystal ≈1 block tall with magenta glowing core and two smaller orbiting splinters.
Parts: `core`, `shard` (main crystal, child of core), `splinter_a`, `splinter_b`.

### unmade_shard — `the_unmade/shard.png` (+`_emissive` thin magenta edges), 32×32
One black diamond-shaped crystal (≈1.2 blocks) used to build the Unmade's three orbiting rings. Parts: `shard`.

### void_capsule — `void_capsule/void_capsule.png` (+`_emissive` teal swirl), 64×32
Glass-and-metal capsule ≈0.8×1.2 blocks: grey metal caps top/bottom, teal glass body, a coiling teal strand inside.
Parts: `cap_top`, `glass`, `cap_bottom`, `strand`.

### rift_hook — `rift_hook/rift_hook.png` (+`_emissive` pearl), 32×32
Small sea-green pearl in a pale metal claw with three curved prongs. Parts: `claw`, `pearl`, `prong_0`, `prong_1`, `prong_2`.

### tether_pylon — `tether_pylon/tether_pylon.png` (+`_emissive`), 64×64
Magenta crystal (≈1.4 wide × 2 tall) crowning a Null Obsidian pillar: a cluster of 3–4 angled crystal cuboids.
Parts: `crystal` (+ any children).

### wild_shulker — textures only, vanilla shulker UV layout (64×64, same as `textures/entity/shulker/shulker.png`):
`wild_shulker/wild_shulker.png` (weathered, lichen-dusted shell in muted purpur and pale end-stone tones) and
`wild_shulker/wild_shulker_molted.png` (thin, pale shell).

### tidewisp — sprite only: `tidewisp/tidewisp.png` 32×32 soft teal-white light blob with three faint trailing streamers (fullbright, alpha).

### the_unmade core — sprites: `the_unmade/core.png` 64×64 radial glow (white centre → hot pink #FFB3EC → magenta #FF3FB4 edge, alpha
falloff) and `the_unmade/tendril.png` 16×64 (dark ribbon, soft magenta edge glow, alpha).

### elytra upgrades — `textures/entity/equipment/wings/` 64×32 (vanilla elytra layout, copy vanilla elytra.png as base):
`elytra_gale.png` (teal glowing veins), `elytra_still.png` (dark mirror-like wing-tip edge), `elytra_hover.png` (two teal nodes near shoulders).

## Final checks
- Run the provided exporter for every model; validate JSON parses, every required part exists, UVs fit, PNG sizes match.
- Render previews and actually look at them; iterate until they match the written looks above.
