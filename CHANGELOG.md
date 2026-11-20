# Changelog

## 1.1.0

New (closing the gaps left in 1.0.0 against the design document):

- **Armor trims** (§9.11): the Spire, Drift and Undersong patterns now render on worn armor, in the Lumen (teal),
  Still (near-black with a silver edge) and Frill (lavender) materials.
- **Void Lens overlay** (§9.6): while scoped, Echoes and hidden Lurkers get a teal outline, Driftwrecks within 128
  blocks are traced with lock-on brackets and their distance, and the tide plane is drawn as a line with its Y level and
  direction. The lens has its own teal crystal scope.
- **Elytra upgrade looks** (§9.7): Gale, Still and Hover elytras now show their own wing textures on the player.
- **Wyrm on the shoulder** (§10.3): a Hatchling Wyrm that lands on your shoulder is drawn there, for every player.
- **Stillwater in bottles and cauldrons** (§7.5): fill a glass bottle from a Stillwater source; cauldrons hold Stillwater
  (buckets in and out, bottles drawn one level at a time, bottles poured back).
- **Stillglass muffles sound** (§6.2): sounds heard through Stillglass play at half volume.

Changed:

- **Stillwater Basins** are rebuilt as a structure: one heart-shaped lake per island taking 40–70% of the island top,
  with a raised pale rim, floating relics, and a notch at the nearest edge where a Stillfall pours off the island
  (previously several small pools cut off at chunk borders).
- Chorus Reach ambient light is brighter so the groves read against the sky.
- Network protocol version bumped to 2 (clients and servers must both run 1.1.0).

Tests: 23 in-world GameTests (new: Stillwater cauldron/bottles, Wyrm shoulder sync, basin shape and trim registries;
the elytra upgrade test now also checks the wing look).

## 1.0.0

First release.
