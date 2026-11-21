# Changelog

## 2.0.1: tested in the real game

Found by a full playtest in the real client (official launcher, NeoForge 26.3.0.113, a survival player pressing the real
keys): see `docs/PLAYTEST.md`.

**Fixes**
- **Crash: diving a Tide Maw.** Being swallowed by a maw crashed the server (the maw loop walked the End's player list
  while the dive moved the player out of it). Maws are now gathered first, and a maw two players stand by pulls once.
- **The Void Bell crawled in the Undertow.** The Undertow's drifting gravity nudged every creature upward; for a bell the
  player steers, each nudge was sent back to the pilot's client and cancelled their speed (a third of normal). Vehicles a
  player steers are no longer nudged: the dive to an Archive and the climb out now take seconds, not minutes.
- **Nothing could be used aboard a Void Bell.** Right-clicking hit the bell's own hull and "boarded" it again; a Codex,
  a chart or food in hand now works from inside (a Stillness Fragment still charges it).
- **The last Empty Void Chart vanished.** Using your last blank chart consumed it and cleared your hand instead of
  giving you the drawn chart.
- **Falling through a Leviathan.** As a Leviathan eased its climb or dive, a player on its back could sink into it and
  fall through; riders are now held on the back.
- **Sky Currents a moment late.** The currents near you now reach your client the tick you arrive, not up to a second
  later (a glider launched at once could miss the current).
- **The Void Bell from inside.** In first person the bell's own hull no longer covers a third of the pilot's view.
- **The Leviathan was drawn too high.** Its body rendered about five blocks above its solid back, so riders stood inside
  it and its garden was buried. The model now sits on the hitbox, with the garden bed on top.

**Testing**
- New real-client playtest driver (`-Dworldsfall.playtest=<dir>` or a `worldsfall-playtest.properties` file in the game
  folder): 13 scenarios, 62 checks, a report and a clip per scenario.

## 2.0.0: the Expansion

The whole "Expansion & Dragon" proposal, in one release (it was planned as 1.2 → 2.0; every part is in).

**The Warden Cycle (the dragon)**
- **Wardensong**: the first fight now tells a story. Each pillar crystal you break reveals one line of the dragon's song;
  the main island cracks at half and quarter health, and the exit portal hums before it opens.
- **Crystal Pacts**: craft a Pact Crystal (End Crystal + Pact Glyph) and use it to respawn the dragon under a rule:
  the Tide closes in, Shard volleys, disguised Echoes, Hoarder thieves, a dragon that learns your tactics, Stillness that
  strips your buffs, the Lone Warden, or the Depths. Each pact adds Weight; heavier fights pay more Dragon Scales, a
  banner pattern, an egg fragment, a Wardenborn Seal and the "Warden" music disc.
- **The Descending Warden** (Pact of Depths): the fight dives through the Descent into a hidden Gallery under the
  Undercroft, then breaks out into the Drift while the tide rises.
- **The Release**: after The Unmade, four Stillness Crystals call a calm dragon. Keep them whole for a minute and it
  bows, then flies to the tallest Fallen Spire to become the **Elder Warden**, who carries you to a gateway for an
  offering of Roasted Chorus Pods. (Respawning ends in that world unless the server allows it.)
- **Dragon Scales**: Wyrm barding (double Drake health), the Scaled elytra look (stacks with any upgrade), the Warden's
  Lantern (no Enderman or Echo teleports within 32 blocks), the Tuning Fork. Wardenborn Wyrms hatch from sealed eggs;
  Wyrms now have a temperament (Calm, Bold or Dreaming) and Drakes can carry saddlebags.

**The Gap**
- **The Pilgrim's Causeway**: a broken floating road from the main island toward the nearest Fallen Spires. Restore each
  missing span with a Causeway Keystone; the restorer's name is set on a plaque, and the finished road's lanterns burn
  silver. Waystations offer roost posts, a Hoard Cache and a Tide Gauge.
- **The Debris Belt** and **Gapwrecks**: denser debris 500–750 blocks out, with small, safe wrecks above the tide.
- **Drift Buoys**: floating route lights; Skates perch on them; the tide pops them.
- **The pillars remember**: through a Void Lens each obsidian pillar shows a glyph; read all ten in order for the phrase
  that unlocks the Pact of Depths.

**Cities and navigation**
- **Shulker Archivists**: peaceful Shulkers in End Cities that trade Echo Tablets for City Charts, Shulker Shells, Pact
  Glyphs and the tablet you're missing.
- **Moorings**: a Drift Helm turns an End Ship into a Mooring (its dragon head glows); its **Ship's Bell** calls your
  Skates and Drakes aboard from 256 blocks.
- **The Gateway Lattice**: strike gateways with a Tuning Fork, then sneak into one to travel to any node you know (one
  ender pearl; not while the tide covers the destination).
- **Void Charts**: maps of the End with height contours, a starry void, teal tide shading and icons for every place you
  pass. City and Star Charts come pre-drawn.
- **Sinking Bullets**: at High Tide, city Shulkers pull you down instead of up. End City chests gain a thread back into
  the mod (tablets, glyphs, keystones, a Spire template, a City Chart).

**Living systems**
- **World events**, announced by sound and sky only: **Starfall** (a shard cluster lands nearby), **King Tide** (one tide
  in eight climbs to Y 60 and leaves Tidewrack loot), **Eclipse of Stillness** (the tide freezes, stars go dark, and
  harmless memories walk; they leave Eclipse Dust), **Skate Migration** (a flock with Dusk Skates, the only ones you can
  tame).
- **Casting the Tide**: fish on the tide plane at High Tide (Mirrorfish, Driftsilt, tablets, even lost things) and in
  Stillwater (slow, safe, Mirror Shards and relics).
- **Hoarder standing and Couriers**: fair barters raise your standing (better trades); at good standing, Courier Tokens
  hire a Hoarder to carry a stack between two Courier Caches every five minutes. Theft cancels it.
- **A food web you can see**: Lurkers eat Tidewisps, overgrazed islands go bare, Leviathans seed loam, Harriers hunt
  Skatelings, Tide Eels follow Glimmer.
- **Lantern Moths** around Fallen Spire lanterns (bottle them for light), the **Wyrm Roost**, the **Tide Bell** (a
  redstone pulse each tide phase), the **Gravity Plate** (a low-gravity jump pad) and Anchor Frame lamps, slabs and stairs.

**The Zenith (Y 150–250)**
- **Zenith Shoals** of Skystone and Halo Glass (which glows with the tide). **Sky Currents** carry gliders at 25 blocks a
  second; **Downdrafts** push them down. **Leviathan Drifters** carry gardens on their backs, and you can ride one;
  they sing before every tide rise. **Gale Harriers** shove gliders out of the sky (a shield or the Still brake makes
  them miss) and drop plumes for the **Current Vane** and the **Tempest** elytra upgrade.
- **The Orrery**: free its three jammed rings with Spire Keys at Low, Rising and High Tide. The lens focuses, you get a
  Star Chart, and a new star appears in the sky for everyone. The music track "Ladders".

**The Undertow (a new dimension)**
- At High Tide **Tide Maws** open in the outer tide plane and swallow anything that enters. Below lies the Undertow:
  endless teal-white haze, dark drifting silhouettes, Void Erosion everywhere at double speed.
- The **Void Bell**: a diving bell (Gravity Anchor + Stillglass + Anchor Frames) for two players and a Skate, inside a
  Stable Field bubble; it flies where you look, runs on Stillness Fragments, and a **Bell Winch** recalls it.
- **Sunken Archives** hold six **Archive Codices** that finish the story (and reveal the Overmoon); one is hidden where a
  Drowned Ascendant points. **Tide Eels** drain lit bells; **Glimmer** shoals show the current. **Null Coral**, the
  **Archive Shelf** (reads records aloud), the **Abyssal Anchor** (not consumed), the **Undertow Pearl** (return to your
  last Void Capsule), "lost things" from every void death, and the track "Undertow".

**The Restoration of the End**
- Every act of repair raises one world-wide Stillness score. Five stages change the world for everyone: lanterns in the
  ruins and a new Undercroft hall (with a mural that fills as you go), loam and herds returning, a steadier tide, brighter
  currents and a new constellation, and finally the **Overmoon** swelling above the main island with Leviathans gathered
  around it. A Skate stable and a trophy hall of everything your server has beaten open along the way.

**Under the hood**
- 55 GameTests (was 23), all passing; 72 new sound stems; every new model, texture and icon generated by the art pipeline.
- **Tested on a real production server** with two clients: the Undertow dimension loads, every new creature ticks and
  renders on both clients, Starfall and the Eclipse run, and the server and both clients log no errors.
- Fixed during in-game testing: Glimmer shoals crashed the client (they moved themselves on the client side); the
  Restoration Mural could stay dark when its panels were still being placed; Eclipse memories vanished in Peaceful.
- New config keys for every system (`dragonPacts`, `causeway`, `latticeMaxNodes`, `sinkingBullets`, `tideFishing`,
  `couriers`, the four events, `zenith`, `undertow`, `lostThingsCap`, the Restoration switches).

## 1.1.3

- **Fixed:** the mod's HUD elements (tide indicator, Skate stamina arc, Echo Tablet text) stayed on screen when the HUD
  was hidden with F1. They now hide with the rest of the HUD.
- README rewritten for players: a gameplay guide with looping in-game previews of every scene and links to full-HD clips.
- Showcase: the wardrobe scene had its own studio spot taken by the item wall; they no longer overlap. Tour re-recorded.

## 1.1.2

- **Smithing template icons redrawn in the vanilla convention**: a tilted, worn stone tablet with visible thickness, lit
  from the top left, carrying a single inset teal glyph. Spire (weathered purpur, a tower), Drift (driftstone, waves),
  Undersong (undershale, hanging roots), and the elytra upgrades Gale (navy, a gust), Still (mirror black, a calm ring)
  and Hover (pale lilac, two nodes over a column of light). Generated by `tools/gen/gen_templates.py`.
- Showcase: the item wall was mirrored (the displays faced away from the camera); it now reads left to right and
  includes the three trim templates. Full tour video re-recorded.

## 1.1.1

- **Trimmed armor icons** now show the Lumen, Still and Frill materials: the items atlas gains paletted variants of the
  vanilla trim overlays, and all 29 vanilla armor item definitions gain a case per material.
- New operator command `/worldsfall wyrm shoulder <player>` puts a Hatchling Wyrm on a player's shoulder.
- **Tested on a real production server**: NeoForge 26.3.0.113 installed with the official installer, the release jar in
  `mods/`, and two clients joined over the network. `tools/mptest/mp_test.py` checks from the second client that the mod
  channel, tide state, every trim, the elytra upgrade and its wing look, and the shoulder Wyrm all sync, takes screenshots
  from both clients, and fails on any error in the server or client logs.
- Showcase: the capture now waits for every screenshot read-back before exiting.

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
