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

## Presentation

- **Sky and tide are custom render pipelines** (`WFPipelines`) with GLSL shaders, registered through NeoForge's custom
  skybox environment attribute and `RenderLevelStageEvent`. The sky honours the `customSky` client option for players
  who prefer the vanilla sky.
- **The tide plane fades into the sky's own below-horizon glow** instead of fog colour, so it reads as an ocean of light
  reaching the horizon (§5.1 "the picture") with no visible edge at render distance.
- **Entity models are authored in Blender and exported to a small JSON format** (`art/SPEC_MODELS.md`) that is turned
  into vanilla `LayerDefinition`s at runtime, so artists can rebuild models without touching Java.
- **Visual review is automated.** `ShowcaseDirector` (dev only, enabled by a JVM property) builds a fresh world, stages
  each §18.10 picture and captures stills and orbit sequences. It never registers in a normal game.
