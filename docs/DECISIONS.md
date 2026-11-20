# Implementation decisions

Choices made while implementing `worldsfall-design-document.md` on Minecraft 26.3 / NeoForge 26.3.0.113, where the document
left room or where the platform forced a different route. Section numbers refer to the design document.

## Platform

- **NeoForge only for 1.0.** §20.2 allows NeoForge, Fabric or both; NeoForge is the stable long-term target named on the
  board. Game logic is kept out of loader-specific code where practical (worldgen, tides and gravity maths are plain Java),
  so a Fabric port can reuse it.
- **Minecraft 26.3 names.** 26.x ships unobfuscated with Mojang's names (`Identifier`, the renderpearl/Blaze3D pipeline,
  environment attributes on biomes and dimension types). The code targets those APIs directly; there are no mappings.

## Data

- **Generated resources come from Python scripts (`tools/gen/`) instead of NeoForge data generators.** The scripts write
  blockstates, models, loot tables, recipes, tags, advancements, worldgen and language files. They run without starting
  the game, keep every JSON reviewable in git, and were faster to iterate while 26.3's data formats were still being
  confirmed against the decompiled game.
- **Outer-End biomes through a custom biome source on `minecraft:the_end`.** §20 prefers adding biomes via
  `#minecraft:is_end`. In 26.3 the End's biome choice is hard-coded in `TheEndBiomeSource`, so the mod overrides only
  the dimension's *biome source* (`data/minecraft/dimension/the_end.json` → `worldsfall:end`). The noise settings, the
  terrain generator and the vanilla biomes stay unchanged; vanilla biomes keep their usual places (§4.0), and
  `#minecraft:is_end` still contains everything for compatibility.
- **Vanilla End biomes get the mod sky and a violet ambient light through a biome modifier** (`worldsfall:end_sky`),
  not by editing the vanilla files. Vanilla's End ambient (#3F473F) is green-grey and muddies the §18.2 palette; the
  modifier sets #4E4868 only when a biome has no ambient of its own.
- **Stillwater Basins are a structure, not a feature.** A feature may only touch the 3×3 chunks around the one being
  decorated, which cut basins into several small pools. As a structure (`worldsfall:stillwater_basin`) the start re-centres
  itself on the island from terrain heights, sizes the heart-shaped crater to 40–70% of the island top (§4.2), and runs
  the Stillfall notch toward the nearest edge; the piece carves it chunk by chunk.
- **Stillglass is crafted, not smelted.** The document lists Stillwater-to-glass smelting; a fluid block cannot be a
  furnace input, so Stillglass uses a crafting recipe from a Bottle of Stillwater and glass.
- **"Wings, Revised" advancement uses an `impossible` trigger** awarded from code when the first elytra upgrade action
  is used, because there is no vanilla criterion for "used a smithing-upgraded elytra ability".

## Gameplay

- **Shulker Satchel is a carried item** (as §9.5 already refines), opened from the inventory, never worn in the chest slot.
- **Server-authoritative tides** with one sync packet per phase change and a 30 s resync (§5.1). Payloads are only sent to
  players whose connection negotiated the channel, so vanilla clients on a server do not get disconnected.
- **Worldgen features are clipped to the chunk write window.** Features that would read or write more than one chunk
  away from the chunk being decorated clip silently at the edge (`GenUtil.get/set`). This removes the "unsafe terrain
  read" errors and keeps generation deterministic across thread scheduling.

- **Stillwater cauldron** is a vanilla `LayeredCauldronBlock` with its own interaction dispatcher (`worldsfall:stillwater`),
  registered through NeoForge's cauldron interaction events, so buckets, bottles and vanilla cauldron rules all work.

## Presentation

- **Sky and tide are custom render pipelines** (`WFPipelines`) with GLSL shaders, registered through NeoForge's custom
  skybox environment attribute and `RenderLevelStageEvent`. The sky honours the `customSky` client option for players
  who prefer the vanilla sky.
- **The tide plane fades into the sky's own below-horizon glow** instead of fog colour, so it reads as an ocean of light
  reaching the horizon (§5.1 "the picture") with no visible edge at render distance.
- **Entity models are authored in Blender and exported to a small JSON format** (`art/SPEC_MODELS.md`) that is turned
  into vanilla `LayerDefinition`s at runtime, so artists can rebuild models without touching Java.
- **Elytra upgrades change the wings through the equipment asset.** The smithing recipe also rewrites the elytra's
  `minecraft:equippable` component to `worldsfall:elytra_<upgrade>`, so the vanilla wings layer draws the upgrade texture;
  no renderer replacement is needed and other elytra mods keep working.
- **Armor trims use 26.3's paletted trim textures.** Patterns are greyscale in the vanilla `trim_base` ramp with a `palette`
  `.mcmeta`, and the three materials ship 8-colour palettes. Worn armor shows every pattern/material combination. The
  *item icons* of vanilla armor only list vanilla materials in their item model definitions; the mod does not override
  vanilla item model files (that would conflict with other trim mods), so a trimmed armor icon shows the untrimmed icon
  for the mod's three materials.
- **Void Lens** outlines Echoes and hidden Lurkers with the vanilla entity-outline pass (teal), and draws Driftwreck boxes
  and the tide line as a HUD layer projected with the frame's camera matrices. Driftwrecks come from the server (loaded
  chunks within 128 blocks, refreshed once a second while scoped), so the client never needs structure data.
- **A Wyrm on the shoulder** is stored in the vanilla shoulder slot like a parrot; a synced player attachment tells every
  client which shoulder to draw it on.
- **Stillglass muffling** is client-side: a sound whose straight line to the listener crosses Stillglass plays at half
  volume (§6.2).
 `ShowcaseDirector` (dev only, enabled by a JVM property) builds a fresh world, stages
  each §18.10 picture and captures stills and orbit sequences. It never registers in a normal game.
