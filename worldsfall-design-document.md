# Where Worlds Go to Fall
### Mod Mechanism & Design Document — an End dimension expansion for Minecraft: Java Edition 26.3+

| | |
|---|---|
| **Working title** | Where Worlds Go to Fall |
| **Mod ID (proposed)** | `worldsfall` |
| **Target game version** | Minecraft: Java Edition 26.3 ("Wilderness Bound") and later drops |
| **Primary loader** | NeoForge (Fabric via a multiloader build is optional, see §20.2) |
| **Document status** | Design baseline v1.0 — the source of truth for the implementation sessions |
| **Companion** | The design Whiteboard (Appendix A) and `worldsfall-whiteboard-companion.md` |

> **Thesis.** *The Nether gives you power. The End gives you reach.*
> Every feature in this mod is judged against that sentence. If a feature makes the player hit harder or tank more, it belongs to the Nether and is cut. If it lets the player go further, carry more, recover what they lost, or control space itself, it belongs here.

---

## How to read this document

- **MUST / SHOULD / MAY** are used in the RFC sense. MUST is a design requirement; SHOULD is a strong default that can be changed with a written reason; MAY is optional polish.
- Numbers are given for **Normal difficulty** unless stated. Easy multiplies mob damage by roughly 0.6, Hard by roughly 1.5, matching how vanilla scales.
- "b/s" means blocks per second. "s" means real-time seconds at 20 ticks per second.
- Health and damage are in **hit points** (1 heart = 2 HP). The player has 20 HP.
- Every number in this document is a **starting value for playtesting**, not a promise. Balancing passes (§16) are expected to move them.
- Names of vanilla registries, attributes and components refer to the game as of 26.x. Anything marked **[verify]** must be checked against the actual 26.3 code before implementation, because Mojang refactors internals every drop.

## Table of contents

1. Vision, pillars and anti-goals
2. Lore and narrative framework
3. World structure: the four layers
4. Biomes (full specifications)
5. Dynamic world systems: Void Tides, gravity, erosion, the Sundering
6. Blocks and materials
7. Fluids: Stillwater
8. Creatures (full specifications)
9. Items, equipment and food
10. The Dragon Egg and the Wyrm companion
11. Structures
12. Progression, gating and advancements
13. Final boss: The Unmade
14. Integration with vanilla content
15. Crafting and recipe reference
16. Balance and economy
17. Playability, onboarding, accessibility and multiplayer
18. Aesthetics: the art, lighting, sky, particle, animation and UI bible
19. Audio bible: sound design and music
20. Technical architecture and integration
21. Configuration reference
22. Roadmap and milestones
23. Risks, open questions and decisions log
- Appendix A — The design Whiteboard
- Appendix B — Glossary
- Appendix C — ID and naming conventions
- Appendix D — Asset checklist

---

## 1. Vision, pillars and anti-goals

### 1.1 Elevator pitch

The End stops being a place you visit twice — once to kill the dragon, once to grab an elytra — and becomes a vertical, hostile, beautiful frontier where **the void itself is the terrain**. Players learn to read the tides of nothingness, tame gliding creatures to cross gaps before they ever touch an elytra, rebuild the ruins of the civilization that tried to escape the void, and finally descend below the bottom of the world to face what that civilization became.

### 1.2 Design pillars

**Pillar 1 — The Void Is a Place.**
The space between and beneath islands is not a death plane with decoration around it. It has layers, weather (the Void Tides), creatures that live in it, things hidden in it, and rules the player can learn. A player who understands the void moves through it with confidence; a player who ignores it dies.
*Test:* Does this feature give the player a reason to look **down** or **across**, not just at the island in front of them?

**Pillar 2 — Reach Over Power.**
Rewards expand movement, storage, recovery and control of space. There is no new ore tier, no new armor tier with higher protection, and no weapon that out-damages Netherite.
*Test:* After obtaining this item, can the player do something they **could not do before**, rather than doing the same thing slightly better?

**Pillar 3 — Desolation with Purpose.**
The End stays quiet, sparse and cold. New life is rare enough to feel precious. Every lush pocket is an exception that the eye is drawn to. Empty space is a design material, not a failure to fill.
*Test:* If you stand on any island and look around, is at least two-thirds of your view still sky and void?

**Pillar 4 — Earned Mobility.**
The player's movement options grow in clear steps: feet and pearls → Void Skate → elytra → upgraded elytra and Rift Tether → stabilized building in the Drift. Each step opens new places. No step makes the previous one useless.
*Test:* Is there a place that is reachable at this step and was not reachable at the previous one?

**Pillar 5 — Respect the Vanilla Game.**
The vanilla dragon fight, End Cities, Shulkers, Endermen and elytra all remain and keep their roles. The mod adds around them, reads like something Mojang could have shipped, and plays well with other End mods.
*Test:* Would a player who has never heard of the mod feel the vanilla End they know is still there?

### 1.3 Experience goals (what the player should feel)

| Moment | Intended feeling |
|---|---|
| First arrival after the Sundering | Awe and unease — "the world just broke open under me" |
| First look over an island edge into the Drift | Vertigo; curiosity about the faint shapes below |
| Taming the first Void Skate | Freedom, earned through patience rather than combat |
| Low tide revealing a Driftwreck | Opportunity with a clock running — greed vs. safety |
| Entering the Underside | Wrongness; beauty that hides danger |
| Rebuilding a spire room | Archaeology; the satisfaction of restoring something |
| Descending into the Fracture | Dread; the sense of leaving the map |
| Defeating The Unmade | Catharsis, then calm — the void stops pulling |
| Raising the Wyrm | Attachment and long-term care |

### 1.4 Differentiation

- **Versus the vanilla End:** adds a reason to stay after the elytra, food and renewable resources, a second endgame, and a mobility arc.
- **Versus The Sift (Mojang, arriving 2027):** The Sift is presented as vibrant, livable and full of souls. This mod keeps the End cold, cosmic and hostile. Pink meadows belong to the Sift; bone-pale stone and black void belong to us.
- **Versus existing End mods:** Nullscape owns vast desolate terrain, BetterEnd owns lush alien biomes, Enderscape owns a polished vanilla+ overhaul, and several mods own an "Enderite" tier. This mod owns **the void as a space, mobility before elytra, and reach-based rewards**. It is designed to stack with Nullscape-style terrain rather than replace it.

### 1.5 Anti-goals (things this mod will not do)

- No new tool or armor material that beats Netherite in raw stats.
- No replacement of the vanilla dragon fight by default.
- No deletion or overwrite of player builds by any world event, ever.
- No lush, colorful biome that dominates the End's palette. The brightest biome (The Hoard) is small and deliberately out of place.
- No walls of lore text. Story is told through places, objects and short item descriptions.
- No grind loops longer than the vanilla equivalent (for example, shulker shells must not take longer to get than with a vanilla duplication farm).
- No dependence on shaders for the art direction to work. Shaders MAY enhance it.

---

## 2. Lore and narrative framework

### 2.1 The core myth

Long before players arrived, a civilization — which the mod calls **the Ascendancy** — lived on the outer islands. They built the purpur towers. They learned to bend gravity and to fold distance (the first ender pearls were their tools). They saw the void slowly rising — the tides were higher then — and they tried to escape it by climbing ever higher and by pulling the islands together into a stable whole.

Their great work was the **Heart of Stillness**, a machine meant to hold the void still forever. It failed. The Heart was dissolved into the void and became **The Unmade**: a thing that wants to un-make everything that still has a shape. The Ascendancy scattered. Their cities fell. Their descendants, the Endermen, forgot everything except the habit of carrying pieces of the world from place to place — they are still trying, without knowing it, to rebuild.

The **Ender Dragon** is the last warden the Ascendancy left behind: a guardian bound to the main island to keep travellers away from the crack where the Heart fell. When the dragon dies, its binding breaks, and the island breaks with it (the Sundering, §5.4).

### 2.2 Narrative rules

1. **Show, don't tell.** Story is delivered by structures, murals, object placement, mob behavior, item flavor text and advancement titles.
2. **Never more than two sentences** of flavor text on any one item or block tooltip.
3. **No named characters with dialogue.** The Ascendancy is anonymous; individuals are implied by rooms (a study, a nursery, a workshop).
4. **Every biome tells one chapter:** Chorus Reach (how they ate), Stillwater (what they feared), Fallen Spires (how they lived and fell), The Hoard (what their descendants became), The Underside (where they dug for stillness), The Fracture (what they made).
5. **The player reconstructs, never receives, the full story.** Fragments are collectible; collecting all of them completes an advancement and fills a lore book, but no single fragment explains everything.

### 2.3 Lore delivery objects

| Object | Where | What it carries |
|---|---|---|
| **Echo Tablets** (item) | Spire vaults, Driftwrecks, the Undercroft | One short fragment each (12 in total). Right-click to read a single line on screen, styled as faint glyph text that resolves into the player's language. |
| **Murals** (structure detail) | Spire walls, Undercroft | Pixel-art panels built from colored purpur variants depicting tides, towers, the Heart. Purely visual. |
| **Room composition** | Spires | Beds made of purpur slabs, tables with shulker-shell bowls, empty cradles — implying daily life. |
| **Advancement titles** | Everywhere | Poetic, short ("The Tide Remembers", "Nothing Holds"). |
| **Mob behavior** | Hoarder Endermen | They carry blocks to hoards and arrange them — a ritual whose meaning has been lost. |

### 2.4 The twelve Echo Tablet fragments (draft text)

1. "We counted the tides. Each year they came closer."
2. "The towers were not homes. They were ladders."
3. "Pearls were for children first. Then for the frightened."
4. "The grazers do not fear us. We were never hunters."
5. "Still water drinks the light. Do not let the young swim."
6. "We built the warden from what we loved most, so it would never leave."
7. "The undersides are quieter. We dug there to listen for stillness."
8. "Carry the world with you. Carry it up."
9. "The Heart held for one breath. Everyone heard it."
10. "Then nothing held."
11. "Our children forgot our faces. They remembered our hands."
12. "If you are reading this, you are standing on something that should have fallen."

---

## 3. World structure: the four layers

The End is reorganized into four vertical bands. Three live in the vanilla End dimension; the fourth is a separate, small dimension.

### 3.1 Layer overview

| Layer | Dimension | Approx. Y range | What lives there | How you reach it |
|---|---|---|---|---|
| **The Rim** | `minecraft:the_end` | Island tops, roughly Y 45–110 | Vanilla islands plus the four surface biomes | Normal End travel |
| **The Underside** | `minecraft:the_end` | Beneath large islands, roughly Y 15–55 | Hanging biome, low gravity, Lurkers | The Descent (under the main island) or by flying/climbing under any large outer island |
| **The Drift** | `minecraft:the_end` | Open air between and below islands, down to the tide plane | Void Skates, debris fields, Driftwrecks, Tidewisps | Any island edge |
| **The Fracture** | `worldsfall:the_fracture` (new) | Self-contained | The Unmade's arena | The Fracture Rift in the Undercroft |

**Decision: keep the vanilla End dimension height.** The vanilla End uses a 0–256 build range. Changing `minecraft:the_end`'s dimension type conflicts with terrain mods (Nullscape raises it to 384). This mod therefore fits its layers inside the vanilla range and puts the deepest layer in its own dimension. A **Tall End** compatibility option (§21) MAY expand the range when no other mod has changed it.

### 3.2 Horizontal layout (distance rings)

Vanilla places the main island at the center, a void gap of roughly 1,000 blocks, and the outer islands beyond. The mod keeps this and assigns new content by distance from the origin (0, 0):

| Ring | Distance from origin | Content |
|---|---|---|
| **Center** | 0–200 | Main island, vanilla dragon fight, after the Sundering: the Descent and the Undercroft |
| **The Gap** | 200–1,000 | Vanilla void gap. The mod adds sparse **Drift debris fields** and rare **Skate Roosts** on tiny islets (so Skates can be found before reaching the outer islands, giving a non-elytra way across the gap once players learn to bridge to them) |
| **Near Rim** | 1,000–2,500 | Chorus Reach (common), Hoard pockets (uncommon), small Stillwater Basins (rare), first Underside caverns beneath larger islands |
| **Far Rim** | 2,500–6,000 | All biomes; Fallen Spires appear from 2,000 and become common after 3,000; Driftwrecks become frequent |
| **Deep Rim** | 6,000+ | Larger Underside systems, the richest Spires and Driftwrecks, higher Spire Warden density |

### 3.3 Vertical cross-section (reading guide for level designers)

From top to bottom at a typical Far Rim location:

1. **Sky** (Y 110+): nebula layers, distant debris silhouettes, the occasional Skate passing overhead.
2. **Island tops** (Y 60–90): biomes, structures, chorus, mobs.
3. **Island bodies** (Y 40–60): end stone, ore-free (by design — the End has no mining tier).
4. **The Underside** (Y 15–55, only under islands at least ~40 blocks across): hanging crystals, roots, bulbs, gravity zones.
5. **The Drift** (open air): debris clusters at all heights, Driftwrecks hanging at Y 8–28.
6. **The tide plane**: a moving, glowing horizontal surface between Y 0 and Y 36 (§5.1). Below it, Void Erosion.
7. **The vanilla kill plane** (far below Y 0): unchanged.

---

## 4. Biomes

Every biome follows the same specification template so the implementation sessions can build them consistently:

- **Identity** — one sentence that defines it.
- **The mechanic** — the one gameplay rule that makes it more than a new color.
- **Placement** — where and how often it appears.
- **Terrain** — shape of the land.
- **Palette** — blocks that make up its surface and body.
- **Features** — plants, crystals, decorations.
- **Life** — mobs that spawn there.
- **Structures** — what can generate there.
- **Resources** — what the player gets from it.
- **Hazards** — what can hurt the player.
- **Atmosphere** — fog, sky tint, particles, light.
- **Audio** — ambience loop, additional sounds, music.
- **The picture** — what a screenshot of this biome MUST look like, described in words. This is the visual acceptance test.

### 4.0 Vanilla biome treatment

The vanilla End biomes stay and keep their names. Light-touch additions only:

| Vanilla biome | Additions |
|---|---|
| The End (main island) | Unchanged until the Sundering (§5.4). Afterwards: the Descent shaft and crack decorations. |
| End Highlands | Rare Wild Shulker colonies on exposed ridges; occasional Hoard pocket islets nearby; Skate flyovers. |
| End Midlands | Sparse **Pale Loam** patches and single chorus pods; Grazer stragglers (1–2) at low weight. |
| End Barrens | Drift debris fields generate more often off its edges. |
| Small End Islands | Some become **Skate Roosts** (§11.6) or Drift debris anchors. |

All additions MUST be toggleable in config (`vanillaBiomeAdditions`).

---

### 4.1 Chorus Reach

**Identity.** The only place in the End where something grows on purpose: a quiet, lilac grazing ground where tall chorus groves sway over soft moss.

**The mechanic — a living food web.** Chorus Reach is the End's renewable food source. **Chorus Pods** grow on chorus plants here (and only here naturally). **Chorus Grazers** eat fallen pods and leave **Pale Loam** behind as they move, which slowly spreads, which lets more chorus root. Players who farm here sustainably (leaving some pods) see the biome thrive; players who strip it see grazers wander off. The loop is visible and gentle, not punishing.

**Placement.** Near Rim and beyond, on medium-to-large islands. Weight: common (roughly 30% of new outer-island area). Never on islands smaller than 24 blocks across.

**Terrain.** Gently rolling tops with 1–3 block undulations, shallow bowls where pods collect, and occasional low end-stone outcrops. Island bodies are thick (15–20 blocks deep at center) and taper into a rounded underside — Chorus Reach islands are the "healthiest" looking.

**Palette.**
- Top: **Pale Loam** (end stone with a lilac moss top), mixed ~60/40 with plain end stone.
- Subsurface: end stone.
- Accents: **Chorus Pod** blocks lying on the ground, small **Lilac Tufts** (short grass-like plant).

**Features.**
- **Chorus Groves** — clusters of 4–9 vanilla chorus plants, taller on average than vanilla (6–12 blocks), with **Chorus Pod** fruit hanging from side branches.
- **Lilac Tufts** — ground cover, replaceable like grass.
- **Pod Drifts** — shallow patches in bowls where 3–8 pods have gathered (a natural "harvest spot" landmark).
- **Seed Motes** — non-colliding drifting particles of pale pollen above the groves.

**Life.** Chorus Grazers (herds of 3–6, weight high), occasional vanilla Endermen, Tidewisps at island edges before high tide.

**Structures.** Grazer Wallows (cosmetic: a ring of trampled loam and a pod pile), rare **Echo Shrines** (a single tablet on a purpur plinth).

**Resources.** Chorus Pods (food, breeding, Skate taming), Grazer Flank (food), Grazer Frill (crafting), Pale Loam (decorative, plantable for chorus).

**Hazards.** Low. Endermen as usual. This is the "safe harbor" biome — deliberately so, because it is the first new biome most players will find.

**Atmosphere.**
- Fog: faint lilac haze close to the ground, clearing above Y +8 from the surface.
- Sky tint: warm violet nebula, the warmest sky in the End.
- Particles: rising pollen motes (slow, upward, pale lilac), occasional pod "pop" when ripe.
- Light: chorus flowers and ripe pods emit low light (level 4), so groves glow softly.

**Audio.** A soft, glassy wind; distant low chimes when pods ripen; grazers' hollow, gentle hums. Music: "Pale Pasture" (§19.4).

**The picture.** A bone-pale island floating in a deep violet sky. The ground is a soft patchwork of cream end stone and lilac moss. Tall, segmented chorus stalks rise in groves at the back of the island, their tips glowing a soft lavender. In the foreground, a small herd of pale, long-necked grazers with frilled backs browse among scattered pods. Pollen motes drift upward. Beyond the island edge, the sky is dark and almost empty. **The island looks cared for; everything around it does not.**

---

### 4.2 Stillwater Basins

**Identity.** Black mirror-lakes that hold the stars upside down, and quietly drink the life out of anything that wades in.

**The mechanic — the fluid of the void.** The basins hold **Stillwater** (§7): a heavy, dark, mirror-like fluid. Items float on it indefinitely (they never sink into the void). It slowly drains a player's experience while they are in it, it silences sound, and it is the base material for the mod's glass and anchor technology. The basins are both a resource and a warning about what the void does.

**Placement.** Near Rim (rare, small basins), Far Rim and Deep Rim (uncommon, large basins). Weight: about 10% of outer-island area. Only on islands at least 32 blocks across.

**Terrain.** A wide crater taking 40–70% of the island top, 3–6 blocks deep, ringed by a raised lip. One or two **spill points** where Stillwater pours off the island edge in a slow, dark fall that thins and vanishes into the Drift.

**Palette.**
- Lip: end stone with patches of **Stillstone** (very dark violet-black stone formed where Stillwater meets end stone).
- Basin floor: Stillstone.
- Fluid: Stillwater source blocks filling the basin.
- Accents: **Mirror Shards** (thin glassy decorations on the lip, catching light).

**Features.**
- **Floating Relics** — items and small blocks frozen floating on the surface, generated at worldgen: a few Overworld blocks (carried by Endermen long ago), occasional loot (bottles, arrows, an Echo Tablet in about 1 in 8 basins).
- **Stillfalls** — the spill points, rendered as a falling fluid curtain that fades with distance.
- **Rimglass** — natural Stillglass formations (smooth, reflective) at the lip.

**Life.** Few. Tidewisps hover over the surface. Endermen avoid the fluid (they treat it like water).

**Structures.** Rare **Drowned Spire** variant (a Fallen Spire half-sunk into a basin — §11.1).

**Resources.** Stillwater (bucketable), Stillstone, Mirror Shards, floating relic loot.

**Hazards.**
- **Experience drain** while submerged (§7).
- **Sound dampening** — mobs approaching are harder to hear (counterplay: subtitles still show, but are labeled "(muffled)").
- Stillfalls push entities downward over the edge.

**Atmosphere.**
- Fog: cold blue-violet, very low density, so the mirror effect reads clearly.
- Sky tint: cold navy and indigo.
- Particles: none over the surface except occasional tiny rings when something floats; faint mist at the Stillfall.
- Light: the surface reflects sky and nearby light sources (implementation: screen-space or planar reflection is optional; the baseline is a star-speckled animated texture, §18.3).

**Audio.** The biome's ambience is **near silence**: a low, felt hum and the absence of reverb. Standing at the lip, sounds behind you remain normal while the basin feels like a hole in sound. Music: none by default; "Hold Still" plays rarely.

**The picture.** A heart-shaped ring of pale stone around a perfectly black lake. The lake is full of stars — a second sky lying flat. A few colorful blocks float motionless on its surface, each with a faint pale ring beneath it. At one edge, a dark curtain of fluid slides off the island and fades into the void below. **The basin should look like a hole in the world that happens to be full of stars.**

---

### 4.3 Fallen Spires

**Identity.** The toppled towers of the Ascendancy, leaning, buried and broken across the outer islands — the End's archaeology sites.

**The mechanic — rebuilding the past.** Fallen Spires contain **Keystone Sockets**. Inserting a **Spire Key** (dropped by Spire Wardens) into a socket triggers a **Reassembly**: purpur blocks lift out of the rubble and fly back into place over about 10 seconds, restoring a sealed room and opening its vault. Rebuilding is the main source of elytra upgrade templates, Ascendant Sigils (needed for the Fracture, §12) and lore.

**Placement.** From 2,000 blocks out; common after 3,000. One spire complex per roughly 600×600 area at most. Prefers large islands.

**Terrain.** The island itself is normal Far Rim terrain (rolling end stone) with rubble scattered in a fan around the fallen tower.

**Palette.**
- Structure: purpur, **Weathered Purpur**, **Cracked Purpur Pillars**, **Purpur Rubble** (a half-height debris block), end stone bricks.
- Light: **Spire Lanterns** (magenta-white light, level 13) on surviving posts.
- Accents: **Ascendant Murals** (colored purpur inlays).

**Features.** Rubble fields, lantern posts, partially buried rooms visible through gaps, broken staircases leading to nothing.

**Life.** **Spire Wardens** guard Keystone Sockets (1–2 per complex). Vanilla Shulkers MAY generate in surviving rooms (lower count than End Cities). **Echoes** hide among rubble (§8.5.3).

**Structures.** The Fallen Spire complex itself (§11.1), with three variants: **Leaning Spire**, **Shattered Spire** (fallen horizontally), **Drowned Spire** (in a Stillwater basin).

**Resources.** Spire Keys (from Wardens), vault loot, Echo Tablets, Ascendant Sigils, elytra upgrade templates, Spire armor trim template, purpur building blocks.

**Hazards.** Spire Wardens, Echoes (ambush), unstable floors (Purpur Rubble blocks over gaps fall when walked on, like a scaffold collapse), Shulker levitation near edges.

**Atmosphere.**
- Fog: dusky magenta at distance.
- Sky tint: wine-red and violet nebula.
- Particles: dust falling from overhangs; slow purple sparks around Keystone Sockets.
- Light: pools of magenta lantern light against cream stone.

**Audio.** Wind whistling through broken masonry, occasional stone settling, faint distant chime of an intact lantern. Spire Warden heavy footsteps carry far. Music: "Ladders to Nowhere".

**The picture.** A cream island with a tall purple tower leaning hard to one side, its top broken off and its windows dark. A second tower lies on its side across the island like a fallen tree, rooms opened along its length. Pale lantern posts glow magenta. Loose purple blocks drift off the island into the sky, as if the tower is still slowly coming apart. **It must read instantly as "something big fell here."**

---

### 4.4 The Hoard

**Identity.** Small pocket islands where Endermen stash the pieces of the Overworld they have stolen: a patchwork of grass, ore, flowers, logs and even whole trees, absurd and colorful in the colorless void.

**The mechanic — Ender bartering.** Each Hoard has a **Hoard Cache** (a container block) watched by **Hoarder** Endermen. Players can **barter**: place Overworld blocks into the Cache (natural blocks the Hoarders value — grass, flowers, logs, ores, saplings), and Hoarders respond by leaving End goods in return (§16.4). Taking from a Hoard without trading angers its Hoarders. The Hoard turns the oldest Enderman habit — picking up blocks — into an economy and gives Overworld materials a new value in the End.

**Placement.** Anywhere in the outer End, as small islands (10–20 blocks across) floating near larger ones. Uncommon: about 1 per 400×400 area. Also as rare patches in End Highlands.

**Terrain.** A small, flattish island with blocks stacked 1–3 high in random, almost-arranged piles — as if someone was trying to build something and forgot what.

**Palette.** End stone base, with grass blocks, dirt, stone, sand, logs, flowers, saplings, a few ore blocks (coal, iron, copper, rarely gold or lapis — **never diamond or emerald** to avoid an exploit), and sometimes a small oak or birch tree.

**Features.** Block piles, a central **Hoard Cache**, occasional "arrangements" (blocks in a ring or a crude tower), a rare tiny tree.

**Life.** 2–4 **Hoarders** (Enderman caste) that patrol and occasionally teleport away to "fetch" and return. Vanilla Endermen are attracted to Hoards and may drop their carried block there.

**Structures.** None beyond the Cache, but very rarely a **Grand Hoard** (a larger island with a recognizable crude replica of an Overworld structure — a well, a hut).

**Resources.** Barter goods (Chorus Pods, Stillglass, Mirror Shards, ender pearls, Echo Tablets, Skate Harness parts, rarely a Gateway Compass), plus whatever Overworld blocks are in the pile.

**Hazards.** Angry Hoarders if you loot without trading. Otherwise low.

**Atmosphere.**
- Fog: very faint, slightly greener tint — the only hint of Overworld air in the End.
- Sky tint: neutral.
- Particles: occasional leaf particles from the tree; Ender portal particles around Hoarders.
- Light: normal; flowers do not glow.

**Audio.** Warped, slowed Overworld sounds — a bird call stretched into a whale-like moan, distant cow lowing pitched down. The uncanny feeling of the familiar in the wrong place. Music: none; the ambience is enough.

**The picture.** A small cream island covered in a riot of Overworld color — green grass tops, brown dirt, grey stone, yellow sand, red and yellow flowers, brown logs — all stacked in clumsy little piles, with a single leafy tree in the middle. It floats in the black void like a scrap of a different world. **It should look slightly wrong and very funny.**

---

### 4.5 The Underside

**Identity.** An upside-down biome hanging beneath the large islands: a ceiling-forest of glowing crystals, roots and lantern-bulbs, where gravity is weak and something in the dark is watching.

**The mechanic — low gravity and the ceiling.** The Underside sits inside **Gravity Zones** (§5.2): players fall slowly, jump higher and can drift between hanging features. **Voidroots** are climbable in any direction. Glowbulbs and Lumen Crystals provide light but also hide **Underside Lurkers**. The Underside is where the player learns that the void has rules that can help them.

**Placement.** Beneath outer islands at least 40 blocks across (the bigger the island, the bigger the Underside). Near Rim: rare and small. Deep Rim: large and connected across clusters of islands. Always beneath the main island after the Sundering (the **Undercroft**, §11.4).

**Terrain.** The island's lower body is reshaped into a deep, rough, inverted dome. Hanging from it: columns, crystal clusters, root curtains and bulb clusters, from 2 to 14 blocks long. Some hanging features connect to small floating "shelves" (flat rock slabs hovering a few blocks below the ceiling) that serve as platforms.

**Palette.**
- Ceiling body: **Undershale** (dark, layered stone) blending into end stone higher up.
- Hanging features: **Lumen Crystal** (teal), **Voidroot** (dark violet roots with amber tips), **Glowbulb** (magenta fruit-like light).
- Shelves: Undershale with **Ceiling Moss** (grows on the *underside* of blocks).

**Features.**
- **Lumen Crystal Clusters** (light level 12, teal).
- **Voidroot Curtains** (climbable, light level 0 except tips at 6).
- **Glowbulb Clusters** (light level 10, harvestable, regrow).
- **Floating Shelves** (platforms).
- **Gravity Wells** (stronger low-gravity spheres around large crystal clusters, visible as slow upward-drifting motes).

**Life.** **Underside Lurkers** (ambush predators), **Tidewisps** (gather here before high tide), rarely **Echoes**.

**Structures.** **Root Hollows** (small caves in the ceiling with loot), the **Undercroft** beneath the main island.

**Resources.** Lumen Crystal Shards (light, trim material, crafting), Glowbulbs (food item that grants short slow falling; also a light block), Voidroot fiber (string substitute, Rift Tether cord), Undershale (building), Lurker Lens (from Lurkers).

**Hazards.** Lurkers; disorientation (no clear "down" visually); falling out of the gravity zone into the normal-gravity Drift below.

**Atmosphere.**
- Fog: teal, thicker than elsewhere, with a darker band at the bottom.
- Sky: visible only through gaps — the ceiling dominates the view.
- Particles: motes drifting **upward** (the strongest visual cue for low gravity), crystal sparkles, slow drips falling *up* from crystal tips.
- Light: pools of teal, magenta and amber against near-black rock.

**Audio.** Crystal tinks, slow drips, reverse-reverb swells (sounds that rise into themselves), a deep resonant hum near Gravity Wells, Lurker clicking (very soft). Music: "Undersong".

**The picture.** Looking up at the bottom of an island that fills the top of the frame. From it hang dozens of glowing teal crystal columns, dark roots with amber tips, and clusters of magenta bulbs, all at different lengths like a reversed forest. Below, tiny motes of light drift upward through dark teal fog. A dark, many-legged shape hangs on a thread among the crystals, with a row of small pink eyes. **The scene must feel upside down even in a still screenshot.**

---

### 4.6 The Drift

**Identity.** The open void between and beneath islands, treated as a biome: wind, debris, gliding creatures and the moving tide below.

**The mechanic — the void has weather.** The Drift is where **Void Tides** (§5.1) are felt. At low tide, **Driftwrecks** — chunks of the old world hanging low in the void — become reachable. Void Skates live here and are the first way to cross it.

**Placement.** Everywhere that is not island. It is implemented as the default biome of open air in the outer End (a replacement for vanilla's Small End Islands biome only where no islands are generated) — see §20.4 for the technical approach.

**Terrain.** No ground. **Debris Fields**: loose clusters of 3–40 small floating blocks (end stone, Driftstone, purpur fragments) at all heights, sometimes forming stepping-stone paths between islands.

**Palette.** Driftstone (weathered grey-violet stone), end stone fragments, occasional purpur.

**Features.** Debris Fields, **Skate Roosts** (rocky spikes where Skates perch), **Driftwrecks** (low, tide-gated structures).

**Life.** Void Skates (groups of 2–3), Tidewisps (swarm before high tide), rarely Phantom-like nothing — the Drift should feel mostly empty.

**Resources.** Driftstone, Driftwreck loot, Skate taming opportunities.

**Hazards.** Falling. The tide. That's enough.

**Atmosphere.**
- Fog: deep navy, fading to teal close to the tide plane.
- Sky: the full nebula sky, with debris silhouettes at multiple depths.
- Particles: sparse drifting dust; Skate wake trails (glowing teal dots).
- Light: dark, lit by distant islands and the tide's glow.

**Audio.** Low wind that rises when moving fast; distant whale-like Skate calls; the tide's hum, louder near the plane. Music: "Below the Bottom" (rare).

**The picture.** A wide view of dark space with a few small islands floating at different heights. Far below, a glowing teal surface stretches to the horizon like the top of an ocean made of light, with a faint dashed line above it where the tide was a moment ago. Faint, drowned-looking islands are visible beneath the surface. **Depth must be readable: near things sharp, far things hazy, and the tide always the brightest thing below the horizon.**

---

### 4.7 The Fracture (dimension)

**Identity.** The place beneath the bottom of the world, where the Heart of Stillness came apart — a black-crimson void with a ring of broken islands around a burning wound in space.

**The mechanic — the arena dies with you.** The Fracture exists only for the final encounter (§13). Its arena breaks apart during the fight.

**Placement.** A separate dimension, `worldsfall:the_fracture`, reached only through the Fracture Rift.

**Terrain.** A ring of 9 island fragments (each 10–16 blocks across) at radius ~40 around a central point, plus outer debris. Below: nothing, forever.

**Palette.** **Null Obsidian** (dark crimson-black), **Fracture Glass** (dark red translucent glass), **Unmade Shards** (decorative, black with magenta edges).

**Life.** The Unmade; its **Shardlings** during the fight.

**Atmosphere.**
- Fog: crimson-black, dense at distance.
- Sky: a single colossal magenta-white glow at the center, smeared nebula in reds.
- Particles: ash drifting **inward** toward the center; sparks.
- Light: everything lit from the center, long shadows pointing outward.

**Audio.** Sub-bass drone, distant choir that sounds like it is breathing in and never out. Music: "Unmade" (boss track, §19.4).

**The picture.** A ring of dark, ash-grey island fragments floating in crimson darkness. Above the center hangs a blinding white-pink core surrounded by rings of black crystal shards. Dark tendrils reach from the core down to the islands. Debris drifts everywhere. **It must look like the end of everything — and like something holding its breath.**

---

## 5. Dynamic world systems

### 5.1 Void Tides

**What it is.** A dimension-wide horizontal plane of glowing void — the **tide plane** — that rises and falls on a timer. Anything below it suffers **Void Erosion** (§5.3). Driftwrecks and other low structures are only safely reachable at low tide.

**Cycle (default):**

| Phase | Duration | Tide height |
|---|---|---|
| Low Tide | 10 min | Y 0 |
| Rising | 3 min | Y 0 → Y 36 (eased) |
| High Tide | 10 min | Y 36 |
| Falling | 3 min | Y 36 → Y 0 (eased) |

Full cycle: 26 minutes. All values configurable (§21). The tide is **global per world** (one plane for the whole End), computed from world game time so it is deterministic and survives restarts.

**Telegraphing (MUST be readable without color):**
- **Two minutes before Rising:** Tidewisps gather and pulse; a low hum begins (subtitle: "Void tide stirs").
- **At Rising:** a rising swell sound, the plane brightens, and a short, tasteful screen-edge vignette (disable-able).
- **Tide Gauge** block (§6) gives an exact redstone reading.
- **HUD option:** an optional small tide indicator (off by default; shown automatically while holding a Gateway Compass or Tide Gauge item).

**Effects:**
- Below the plane: Void Erosion.
- Items dropped below the plane are destroyed after 2 seconds (as vanilla void does), unless captured by a Void Anchor.
- Void Skates refuse to fly below the plane (they pull up), which protects riders.
- Driftwrecks at Y 8–28 become safe at Low Tide and are submerged at High Tide.

**Visual of the plane.** A single, enormous horizontal surface rendered as a translucent, slowly flowing teal sheet with a bright cyan crest line where it meets the air, faint ripples, and a soft glow beneath. When the tide moves, a **ghost line** (dashed, faint) remains for 60 seconds where it was, so the player sees which way it is moving. Islands below the plane appear muted and tinted, as if seen through water.

**Multiplayer and servers.** The server owns the tide; clients interpolate. One packet at phase changes plus a periodic resync every 30 seconds.

**Performance.** No blocks are placed; the plane is a rendered effect plus a per-entity Y comparison every 10 ticks.

### 5.2 Gravity system

**Goal.** Let parts of the End feel weightless without breaking vanilla physics.

**Implementation approach.** Vanilla Java Edition exposes a `gravity` attribute and fall-related attributes (`safe_fall_distance`, `fall_damage_multiplier`) on living entities **[verify names in 26.3]**. The mod applies **attribute modifiers** while an entity is inside a **Gravity Zone**, and removes them on exit. This keeps the system compatible with vanilla physics and with other mods.

**Zone types:**

| Zone | Where | Effect |
|---|---|---|
| **Light Zone** | The whole Underside | Gravity ×0.45, fall damage ×0.3, jump height noticeably higher |
| **Gravity Well** | Spheres (radius 6–12) around large crystal clusters | Gravity ×0.15; entities slowly float upward when not touching ground |
| **Stable Field** | Around a Gravity Anchor (post-boss, §9.8) | No Void Erosion; slow-fall below the field; building in midair allowed |
| **Lens** | Temporary, created by The Unmade (§13) | Gravity reversed (×−0.3) for 4 seconds |

**Visual language of gravity.** Low gravity is shown by **particles that drift upward**, a slight blue-teal fog tint and slower, floatier animations of mobs inside. Reversed gravity (Lens) is shown by a ring of distorted light on the ground and particles streaming up.

**Rules.**
- Zones affect players, mobs and item entities. They do not affect falling blocks (to avoid sand/gravel exploits).
- The camera never flips. "Upside-down" is conveyed by environment, not by rotating the view (a full ceiling-walking mode is a stretch goal and is out of scope for v1.0).
- An option "Reduced floatiness" lowers the effect strength for players prone to motion discomfort.

### 5.3 Void Erosion

**What it is.** The status effect applied below the tide plane and in other void-touched places.

| Property | Value |
|---|---|
| Damage | 1 HP per second at first, +1 every 3 seconds, capped at 4 HP/s |
| Armor | Ignores armor (like vanilla void damage) |
| Removal | Leaving the zone clears it after 1 second |
| Protection | Inside a Stable Field; riding a Void Skate (Skates won't go below the plane anyway); with the adult Wyrm's Void Ward aura (§10) |
| Visuals | Player's outline flickers with faint teal static; screen edges desaturate; a hollow, reversed-whisper sound |

### 5.4 The Sundering

**What it is.** A one-time world event when the Ender Dragon dies for the **first** time in a world. It reveals the second half of the End.

**Sequence (about 20 seconds, after the vanilla death animation and egg placement):**
1. A low rumble; the exit portal's bedrock shudders (particles only, no block changes to the portal).
2. Cracks of magenta light race outward across the main island along a predefined pattern.
3. At a fixed spot about 40 blocks from the exit portal, chosen at world creation in a location free of player blocks, a 7×7 shaft — **the Descent** — opens with falling-block animation: the end stone drops away into the dark.
4. A spiral of end stone and purpur steps lines the shaft down to the **Undercroft** (§11.4) beneath the island.
5. A new advancement: "Nothing Holds".

**Hard rules.**
- The Sundering MUST NOT remove any block a player placed (the mod checks a "player-placed" tag in the chunk's data before touching a block; if the planned shaft location contains any player blocks, it moves to the next free candidate).
- It MUST be skippable via config (`sundering.enabled = false`), in which case the Descent is replaced by a Fracture Rift entrance on a small new island 300 blocks from the center.
- In worlds where the dragon was already killed before the mod was installed, the Sundering triggers the first time a player enters the End after installation, with a chat-free visual sequence.

### 5.5 Hoarding behavior (ambient world system)

Vanilla Endermen holding a block have a small chance (every few minutes) to path toward the nearest Hoard within 96 blocks and drop the block there. Over a long-lived world, Hoards slowly grow. This is capped (a Hoard stops accepting blocks at 300 blocks of "pile") and can be disabled.

### 5.6 Starfall (optional, post-1.0)

Rarely, a streak of light crosses the End sky and a small **Starfall Shard** cluster lands on a random outer island within 200 blocks of a player, glowing for 5 minutes. It contains Lumen Crystal and a chance of an Echo Tablet. This is a reason to look up. It is scheduled after v1.0 to keep scope in check.

---

## 6. Blocks and materials

### 6.1 Design rules for blocks

- **Palette discipline.** Each new block family belongs to exactly one biome color story (§18.2). No block mixes two biome palettes.
- **Vanilla behaviors first.** Each block reuses a vanilla behavior where possible (moss spreading, amethyst-like breaking, dripstone-like hanging, glass translucency), so players can predict it.
- **Building value.** Every natural block that generates in large quantity MUST have at least a polished, brick or slab/stair/wall variant, because players will build with it.
- **No ores.** The End has no mining tier. Resources come from creatures, structures, plants, crystals and the void.

### 6.2 Natural blocks

| Block | Where | Behavior | Look (in words) | Light |
|---|---|---|---|---|
| **Pale Loam** | Chorus Reach, Midlands patches | Spreads slowly onto adjacent end stone when a Grazer walks on it or a Chorus Pod rots on it; chorus can be planted on it; turns back to end stone if covered | End stone with a soft, short lilac moss on top; sides show moss draping 2–3 pixels down | 0 |
| **Lilac Tuft** | Chorus Reach | Replaceable ground plant; drops nothing unless sheared | Short, curly lilac grass in two shades | 0 |
| **Chorus Pod** (block form, on plant) | Chorus groves | Grows in 3 stages on chorus plant sides; ripe pods drop when touched or after time | A rounded, segmented pod, pale violet turning glowing lavender when ripe | 0 → 4 (ripe) |
| **Stillstone** | Stillwater Basins | Forms when Stillwater flows onto end stone; hard (like deepslate) | Very dark violet-black stone with fine silver flecks like distant stars | 0 |
| **Mirror Shard** | Basin lips | Small decoration; breaks like amethyst bud | A thin, upright sliver of black glass that reflects a bright highlight on one edge | 2 |
| **Rimglass** | Basin lips | Natural Stillglass; smooth; translucent | Smooth, very dark glass with a faint blue sheen | 0 |
| **Weathered Purpur** | Fallen Spires | Building block | Purpur with faded, uneven color and pale water-stain streaks | 0 |
| **Cracked Purpur Pillar** | Fallen Spires | Building block | Purpur pillar with a diagonal crack and missing chips | 0 |
| **Purpur Rubble** | Fallen Spires | Half-height; when placed over air it falls after 1 s if an entity stands on it | A loose heap of purpur chunks | 0 |
| **Spire Lantern** | Fallen Spires | Light source; can be placed on posts and hung | A tall, slim lantern of purpur and pale glass with a magenta-white flame-like core | 13 |
| **Undershale** | Underside | Stone; can be polished/bricked | Dark slate layers with thin violet bands, layered horizontally | 0 |
| **Ceiling Moss** | Underside shelves and ceilings | Grows on the **bottom** face of blocks; spreads slowly | Dangling, dark teal moss strands | 0 |
| **Lumen Crystal** | Underside | Hanging growth like dripstone (base, middle, tip); grows longer slowly in Light Zones | Faceted teal crystal, bright white-cyan tip | 12 (cluster), 8 (column) |
| **Voidroot** | Underside | Climbable (like vines, any direction); hanging | Dark violet twisted roots with amber glowing tips | 0 (tip 6) |
| **Glowbulb** | Underside | Harvestable fruit-like light; regrows over ~10 min | A round, translucent magenta bulb with a brighter core, hanging by a short stem | 10 |
| **Driftstone** | The Drift | Building block; generates in debris fields | Weathered grey-violet stone with wind-scoured streaks | 0 |
| **Null Obsidian** | The Fracture | Very hard; blast resistant | Crimson-black obsidian with faint red veins | 0 |
| **Fracture Glass** | The Fracture | Translucent | Dark red glass with cracks of light | 3 |
| **Unmade Shard** | The Fracture | Decorative; drops nothing | A jagged black spike with glowing magenta edges | 5 |

### 6.3 Crafted and functional blocks

| Block | Function | Look |
|---|---|---|
| **Stillglass** | Glass made from Stillwater + sand. Blocks sound passing through it (muffles noises by 50%). One-way look: from the dark side it is transparent; from the bright side it reflects (approximation through two texture faces) | Smooth, near-black glass with a faint star speckle |
| **Lumen Lamp** | Toggleable light (redstone) | A Undershale frame holding a teal crystal |
| **Tide Gauge** | Redstone component: outputs signal strength 0–15 proportional to current tide height (0 at Low, 15 at High). Also displays the phase on its face | A purpur-and-glass column with a teal liquid level that rises and falls |
| **Hoard Cache** | Barter container (§16.4); only generates naturally; players can craft a decorative version that does not barter | A lidded chest-like box made of end stone with Overworld blocks crudely embedded in its sides |
| **Keystone Socket** | Accepts a Spire Key to trigger Reassembly; only generates naturally | A purpur pedestal with a key-shaped hollow glowing magenta |
| **Egg Cradle** | Required for the Dragon Egg hatching ritual (§10) | A ring-shaped altar of end stone bricks and obsidian with four Lumen Crystal posts |
| **Gravity Anchor** | Post-boss: creates a Stable Field (§9.8) | A heavy obsidian core suspended in a purpur frame, slowly rotating, with a teal halo |
| **Anchor Frame** | Scaffolding-like block that can be placed in midair inside a Stable Field | A thin purpur lattice glowing faintly teal |
| **Fracture Rift Frame** | Portal frame in the Undercroft; activated with the Rift Key | A ring of Null Obsidian with magenta cracks; when active, the center is a swirling crimson-black surface |

### 6.4 Building sets

Each SHOULD have block, slab, stairs and wall unless stated:

- Pale Loam: no set (it's a surface block).
- Stillstone: block, polished, bricks, chiseled (star pattern), slab/stairs/wall for all three.
- Weathered Purpur: block, slab, stairs; plus pillar, cracked pillar.
- Undershale: block, polished, bricks, tiles, slab/stairs/wall.
- Driftstone: block, polished, bricks, slab/stairs/wall.
- Null Obsidian: block only (not obtainable in survival before the boss is defeated; after that it is mined normally in the Fracture).
- Stillglass: block and pane.
- Lumen Crystal: block (decorative, crafted from 4 shards), lamp.

---

## 7. Fluids: Stillwater

Stillwater is the mod's signature material and its only fluid.

### 7.1 Physical properties

| Property | Value | Vanilla comparison |
|---|---|---|
| Flow distance | 3 blocks | Water 7, lava (Overworld) 3 |
| Flow speed | Every 30 ticks | Lava (Overworld) speed |
| Infinite sources | No | Unlike water |
| Swimming | Possible but slow: movement ×0.5, no sprint-swimming | Heavier than water |
| Buoyancy | Items float on the surface indefinitely and **do not despawn while floating** (configurable cap of 64 floating stacks per chunk) | Unique |
| Light | Emits level 3 (a faint glow so basins aren't pitch-black) | — |
| Breathing | Players drown in it like water | Same as water |
| Fall damage | Landing in it cancels fall damage | Same as water |
| Fire | Extinguishes | Same as water |

### 7.2 Special effects on entities

- **Experience drain.** While a player is submerged (head inside), they lose 1 XP point every second. Experience orbs touching Stillwater are absorbed and disappear. *Purpose:* a gentle, legible cost — the void takes.
- **Sound dampening.** Sounds originating within 4 blocks of a submerged player are muffled (volume ×0.4, low-pass). Mobs inside Stillwater make muffled sounds.
- **Endermen** treat Stillwater like water: they take damage and teleport away.

### 7.3 Interactions

| Meets | Result |
|---|---|
| Water (Stillwater flowing onto water) | Water source becomes **Stillstone**; flowing water becomes Stillstone |
| Lava | Becomes **Stillstone** (Null Obsidian stays Fracture-only to protect progression) |
| End stone (flowing over it) | End stone it stops on becomes Stillstone over time (slow, 1 block per ~5 minutes, max 16 per basin) — this is how basin floors form |
| Sand (crafting) | 1 Stillwater bucket + 4 sand in a furnace → 4 Stillglass |
| Glass bottle | **Bottle of Stillwater** — used in brewing (§9.7) and crafting |

### 7.4 How Stillwater looks (visual requirement)

- **Surface:** an almost-black, very slightly violet base with **moving star specks** that drift slowly (an animated texture, 32 frames, very slow). Where implementations allow, the surface SHOULD reflect the sky. The 26.3 order-independent transparency work makes layered translucent surfaces (Stillwater over Stillglass, crystals behind fluid) render more correctly, which this fluid relies on.
- **Edge:** a thin, pale blue rim highlight where Stillwater meets a block.
- **Falling:** dark, slow curtains with faint vertical streaks, fading in alpha the further they fall.
- **Underwater view:** a very dark navy fog with sparse slow stars; sounds muffled; vignette.
- **Floating items:** bob extremely slowly (one cycle per 6 seconds) with a faint pale ring beneath them.

### 7.5 Bucket and placement rules

- Stillwater can be bucketed and placed in any dimension. In the Nether, it evaporates like water.
- A cauldron can hold Stillwater; dipping a glass bottle produces a Bottle of Stillwater.
- Dispensers can place and pick up Stillwater.

---

## 8. Creatures

### 8.1 Creature design rules

- **Silhouette first.** Every mob MUST be identifiable from its silhouette alone at 32 blocks, against both the dark sky and the pale end stone.
- **One job each.** Every creature has one clear role (food, transport, threat, guardian, mimic, ambience).
- **Telegraphs.** Every attack has a wind-up of at least 10 ticks (0.5 s) with a distinct sound and animation pose.
- **Vanilla stat scale.** Health and damage sit within vanilla ranges for their role (e.g., an elite guardian around Iron Golem/Ravager range, not Warden).
- **Respect the End's rarity.** Mob caps for new End creatures are low (see per-mob limits) so islands do not feel crowded.

### 8.2 Void Skate

**Role:** transport and the second mobility step.

**Look.** A broad, manta-like glider with a wingspan about 5 blocks across, deep indigo on top with a lighter violet underside. Two small curled cephalic fins at the front. A long, thin whip tail with a glowing tip. Rows of teal bioluminescent spots along the wing edges and down the center; a glowing teal line traces the leading edge of each wing. Small, pale, calm eyes. It moves with slow, deep wingbeats and long glides, wingtips rippling. When it banks, the glowing spots stream into short trails.

| Stat | Value |
|---|---|
| Health | 30 HP |
| Armor | 2 |
| Hitbox | 4.0 wide × 0.8 high |
| Wild speed | Cruise 7 b/s |
| Spawning | The Drift near islands, Y 30–100; groups of 2–3; max 6 per player within 128 blocks; also at Skate Roosts |
| Behavior (wild) | Neutral and shy. Circles islands in slow loops; perches on Skate Roosts and island edges to rest; flees when hit. Never attacks. |
| Drops | Skate Membrane (1–2), Glow Gland (0–1) |
| Sounds | Deep, gentle whale-like calls; soft wing "whumps"; a bright chime when taming progresses |

**Taming.**
1. Hold **Chorus Pods** within 8 blocks of a perched or slow-circling Skate. It will approach and hover near the island edge.
2. Feed it pods (right-click). Each pod has a 25% chance to add one "trust" point; 5 trust points are needed. Trust drops by 1 every 30 seconds without feeding, and resets fully if the Skate is damaged.
3. On full trust, it emits a chime and a ring of teal particles, and it waits at the edge.
4. Use a **Skate Harness** on it to make it rideable.

*Design note:* taming rewards patience and positioning on an island edge — a skill the player will need anyway — rather than combat.

**Riding.**

| Control | Effect |
|---|---|
| Forward | Accelerate to cruise (9 b/s) |
| Mouse | Steer (turn rate limited; Skates bank in wide arcs) |
| Jump (hold) | Climb at 4 b/s, uses Stamina |
| Sneak (hold) | Dive, gaining speed up to 16 b/s while descending |
| Sneak at an island edge | Land and perch; rider can dismount safely |
| No input | Glide slowly, losing about 1 block of height per 3 blocks travelled |

- **Stamina:** 100 points; climbing costs 8/s; regenerates 5/s while gliding level or perched. Shown as a slim teal arc on the right side of the crosshair (like the horse jump bar's slot).
- **Safety:** a Skate refuses to descend below the tide plane +4, and pulls up automatically.
- **Dismount over the void:** allowed, but the game warns once per session with a subtitle ("Nothing below").
- **Carry:** one rider. No chest. (A Satchel solves storage.)
- **Dimensions:** a tamed Skate follows its rider through the Fracture Rift when ridden (§13.2). It cannot enter the Overworld or the Nether (it refuses portals there and stays in the End).

**Breeding.** Two tamed Skates fed **Glowbulbs** produce a **Skateling** (baby, cannot be ridden, grows in 40 minutes).

**Variants.** Two visual variants: **Indigo** (common) and **Dusk** (rare, warmer violet with amber spots). Purely cosmetic.

### 8.3 Chorus Grazer

**Role:** food and the living part of the Chorus Reach loop.

**Look.** A soft-bodied, six-legged browser about the size of a cow, pale lilac with a lighter belly. A long, curved neck ends in a small head with dark, round eyes and a short, soft snout. Along its back runs a frill of crystal-like plates that glow faintly lavender at the tips. Thin, dark-violet legs. It moves with a gentle rocking walk; when grazing, it lowers its neck in a long smooth curve.

| Stat | Value |
|---|---|
| Health | 14 HP |
| Spawning | Chorus Reach (herds of 3–6), End Midlands (1–2, rare); max 12 per player within 128 blocks |
| Behavior | Passive. Wanders, eats fallen Chorus Pods, follows players holding pods. Leaves Pale Loam behind it (1 block per minute while walking on end stone in Chorus Reach). Panics when hurt. |
| Breeding | Chorus Pods; baby grows in 20 minutes |
| Shearing | Shears remove its frill plates: drops 1–2 **Grazer Frill**; regrows in 5 minutes |
| Drops | Grazer Flank (1–2, cooked when killed by fire), Grazer Frill (0–1) |
| Sounds | Hollow, low hums; soft chime when eating; a short, airy bleat when hurt |

### 8.4 Underside Lurker

**Role:** the Underside's ambush predator — the reason players carry light.

**Look.** A dark, spindly creature that hangs from a single thread from the ceiling. Its body is a rounded, armored teardrop of near-black chitin with a pointed crown at the top where the thread attaches. A ring of four small magenta eyes glows around its middle. Seven long, thin, jointed legs splay downward like an open hand. When hidden, it is nearly invisible: its body shows only as a faint heat-shimmer distortion and its eyes are closed.

| Stat | Value |
|---|---|
| Health | 28 HP |
| Armor | 4 |
| Spawning | Underside only, light level ≤ 7; max 3 per player within 64 blocks |
| Behavior | Hangs motionless and hidden. When a player comes within 6 blocks (or 3 blocks if the player is sneaking), it opens its eyes (sound cue), drops on its thread and lunges. |
| Attack 1 — Snare | Fires a sticky thread (projectile, 12 blocks). On hit: pulls the target 3 blocks toward the Lurker and applies Slowness II for 3 s. Wind-up: 0.75 s, eyes flare. |
| Attack 2 — Bite | Melee 6 HP. |
| Retreat | After attacking, climbs back up its thread and re-hides after 8 s if not killed. |
| Weakness | **Light**: a Lurker within 3 blocks of a light level ≥ 12 source cannot hide (it is always visible). Glowbulbs and Lumen Lamps are therefore safety tools. |
| Drops | Lurker Lens (0–1, used for the Void Lens spyglass), Voidroot fiber (1–2) |
| Sounds | Soft clicking while hidden (subtitle: "Something clicks above"), a dry rattle when its eyes open, a whip-crack for the snare |

### 8.5 Ender castes

Three new mobs related to the vanilla Enderman. They are separate entity types (vanilla Endermen stay unchanged and keep spawning). They share Enderman traits: tall, black, long-limbed, take damage from water and Stillwater, and teleport.

#### 8.5.1 Hoarder

**Role:** guardian of the Hoard and the barter partner.

**Look.** An Enderman-like figure, slightly shorter and stooped, with a ragged cloak of patched Overworld materials (grass, bark, wool colors) draped over its shoulders and a pouch at its side. Its eyes are amber instead of purple.

| Stat | Value |
|---|---|
| Health | 40 HP |
| Damage | 6 HP |
| Spawning | Only at Hoards (2–4); does not spawn naturally elsewhere |
| Behavior | Neutral. Patrols the Hoard. Accepts barter (§16.4). If a player breaks Hoard blocks or opens the Cache without having bartered in the last 5 minutes, all Hoarders at that Hoard become hostile for 2 minutes. Looking at a Hoarder does not anger it (unlike vanilla Endermen). |
| Drops | Ender pearl (0–1), a random Overworld block from its pouch (1) |

#### 8.5.2 Spire Warden

**Role:** the elite guardian of Fallen Spires and the only source of Spire Keys.

**Look.** A tall, heavy Enderman figure (about 3.5 blocks tall) wearing plates of cracked purpur on its shoulders, chest and forearms, held together by faint magenta light. Its head is partly enclosed by a purpur crest. Its eyes burn magenta. It moves slowly and deliberately; plates grind when it turns.

| Stat | Value |
|---|---|
| Health | 80 HP |
| Armor | 8 |
| Knockback resistance | 0.8 |
| Spawning | Generated with Fallen Spires next to Keystone Sockets (1–2 per complex); does not despawn; respawns after 7 Minecraft days of world time if its socket has not been used |
| Behavior | Stands guard. Hostile within 16 blocks. |
| Attack 1 — Slam | Melee 10 HP with heavy knockback. Wind-up 0.6 s (raises both arms). |
| Attack 2 — Teleport Slam | Teleports above a target within 20 blocks and falls, dealing 12 HP in a 3-block radius on landing. Wind-up: a ring of magenta light appears on the ground under the target 1.0 s before impact (clear dodge window). Cooldown 10 s. |
| Attack 3 — Plate Shield | Below 50% health, raises its forearm plates for 3 s, reflecting projectiles. Visual: plates glow bright. |
| Weakness | Stillwater and water as with Endermen; its plates crack visibly as it loses health. |
| Drops | **Spire Key** (1, guaranteed), Weathered Purpur (2–4), ender pearl (1–2) |
| Sounds | Grinding stone, deep breathing, a rising tone before the Teleport Slam |

#### 8.5.3 Echo

**Role:** the mimic — keeps players alert in ruins.

**Look.** In its true form, a thin, faint, semi-transparent Enderman shape with a static-like shimmer along its edges and no visible eyes until it attacks (then two white points). In disguise, it looks **exactly like a block** — specifically the last block type the nearest player placed in the End (fallback: Purpur Rubble).

| Stat | Value |
|---|---|
| Health | 24 HP |
| Spawning | Fallen Spires and Root Hollows, rarely the Undercroft; max 2 per player within 64 blocks |
| Behavior | Sits disguised as a block. When a player comes within 3 blocks, or right-clicks / mines it, it unfolds and attacks for 8 s, then teleports away and disguises again elsewhere within 24 blocks. |
| Tell | Its "block" never casts ambient occlusion correctly and very faintly hums (subtitle: "A block hums"). With a Void Lens (§9.6) it is visible as an outline. |
| Attack | Melee 5 HP, plus "Scramble": swaps 2 random hotbar slots of the player (not armor, not offhand). Wind-up: shimmer intensifies 0.5 s. Scramble can be disabled in config. |
| Drops | Echo Dust (1–2, used in the Gateway Compass and Void Lens) |

### 8.6 Wild Shulkers

**Role:** a calm, renewable shell source that respects vanilla balance.

**Look.** Vanilla shulker model with a **weathered, lichen-dusted** shell texture variant in muted purpur and pale end-stone tones, so players can tell wild ones from city ones.

| Stat | Value |
|---|---|
| Spawning | Small colonies (2–4) on exposed ridges of End Highlands and Far Rim islands |
| Behavior | Like vanilla shulkers but **less aggressive**: only attack if a player comes within 6 blocks (vanilla is 16). |
| Molting | Every 40 minutes of loaded time, each Wild Shulker sheds its shell: it drops 1 Shulker Shell and stays exposed (shell visually thin and pale) for 5 minutes, during which it does not attack. |
| Luring | Feeding a Wild Shulker a Chorus Pod makes it teleport to a block within 8 blocks of the player. This lets players gather them into a "shell garden" over time. |
| Limit | Max 8 Wild Shulkers molting within a 32-block radius (prevents mega-farms beating vanilla). |

*Design note:* the yield (roughly 1.5 shells per shulker per hour) is deliberately **lower** than a good vanilla duplication farm, but it is calm, safe and available earlier. It complements rather than replaces vanilla methods.

### 8.7 Tidewisp

**Role:** ambient tide warning and atmosphere.

**Look.** A small, soft, teal-white light (about the size of a firefly cluster) with three faint trailing streamers; no body.

| Stat | Value |
|---|---|
| Health | 2 HP (not worth hunting) |
| Spawning | The Drift and island edges, more often in the 2 minutes before Rising tide; despawn after High Tide begins |
| Behavior | Passive, non-colliding, drifts in loose swarms; gathers and pulses brighter as the tide approaches |
| Drops | Nothing (a Bottle can capture one: **Bottled Tidewisp**, a portable light source level 9 that also pulses before tide changes) |

### 8.8 Shardling

**Role:** The Unmade's minions (Fracture only).

**Look.** A floating, jagged black crystal shard about 1 block tall with a magenta glowing core and two orbiting smaller splinters.

| Stat | Value |
|---|---|
| Health | 10 HP |
| Spawning | Summoned by The Unmade in Phase 2 and 3 (§13) |
| Behavior | Hovers and dives at a target |
| Attack | Dive 5 HP; on death bursts into 3 fast splinters (2 HP each) that fly in a straight line — dodgeable |
| Drops | Nothing (keeps the boss reward focused) |

### 8.9 Mob summary table

| Mob | Health | Hostility | Where | Key drop |
|---|---|---|---|---|
| Void Skate | 30 | Passive/shy | Drift | Skate Membrane |
| Chorus Grazer | 14 | Passive | Chorus Reach | Grazer Flank, Frill |
| Underside Lurker | 28 | Hostile (ambush) | Underside | Lurker Lens |
| Hoarder | 40 | Neutral | Hoards | Barter |
| Spire Warden | 80 | Hostile (guard) | Fallen Spires | Spire Key |
| Echo | 24 | Hostile (mimic) | Spires, Root Hollows | Echo Dust |
| Wild Shulker | 30 | Mildly hostile | Highlands/Far Rim | Shulker Shell (molting) |
| Tidewisp | 2 | Passive | Drift | (Bottled Tidewisp) |
| Shardling | 10 | Hostile | Fracture | — |
| The Unmade | see §13 | Boss | Fracture | Heart of Stillness |
| Wyrm (companion) | see §10 | Tamed | Player | — |

---

## 9. Items, equipment and food

### 9.1 Item design rules

- **Every reward answers "where can I go now that I couldn't before?"** (Pillar 2).
- No item may increase raw melee damage or armor protection above Netherite.
- Durability-based tools repair with End materials, so the End stays relevant.
- Every item has a one-line description of what it does (shown in the tooltip on Shift) and at most one line of flavor text.

### 9.2 Void Anchor

**What it does.** Carried in the inventory, a Void Anchor saves your items when you die in the void or below the tide plane. Instead of being destroyed, your dropped items, armor and XP (50% of it) are sealed in a **Void Capsule** that floats at the tide plane +2 blocks (or Y 2 if no tide applies) directly above where you died.

| Property | Value |
|---|---|
| Uses | 1 (consumed on trigger) |
| Stack size | 1 |
| Capsule lifetime | 60 minutes of loaded time (configurable; infinite in config) |
| Capsule access | Owner and their team only; anyone in config "public" mode |
| Capsule visual | A glass-and-metal capsule glowing teal, swirling inside, with a thin vertical teal beam visible from 256 blocks |
| Capsule behavior | Moves up if the tide rises (always stays above the plane) |
| **Cracked Void Anchor** | A cheaper version (early game). Saves a random 50% of item stacks. |

**Look.** A small cylindrical lantern of grey metal caps and teal glass. Inside, a slowly coiling teal strand swirls like smoke in a jar. A short loop of chain on top.

**Recipe.** See §15. Uses Stillglass, Glow Gland, an ender eye and iron.

### 9.3 Rift Tether

**What it does.** A pearl-powered grappling hook. Use to fire a hook (range 24 blocks). It latches onto solid blocks. Hold use to reel in; release to swing on the line; sneak to detach.

| Property | Value |
|---|---|
| Durability | 96 uses; repaired with ender pearls in an anvil |
| Range | 24 blocks |
| Reel speed | 12 b/s |
| Swing | Pendulum physics around the anchor point |
| Fall damage | None while attached; normal after detaching |
| Can't latch on | Stillwater, Stillglass, Fracture Glass, entities, Null Obsidian (by design, so the arena can't be cheesed) |
| Cooldown | 0.5 s after detaching |

**Look.** A coil of dark Voidroot cord ending in a small sea-green pearl set in a pale metal claw with three curved prongs. When fired, a dashed teal line connects player and hook; the pearl glows while attached.

### 9.4 Gateway Compass

**What it does.**
- **Unbound:** points to the nearest End Gateway you have not yet used.
- **Use on a Gateway:** binds to it. The compass now points to that Gateway from anywhere in the End and shows the distance in its tooltip.
- **While held:** displays the Void Tide phase and time to next change as a small line under the hotbar.

**Look.** A round, dark dial with a violet rim, a glowing pink star-shaped needle, and a tiny dark diamond (a miniature gateway) at the top that glows when bound.

### 9.5 Shulker Satchel

**What it does.** A portable 9-slot inventory carried as an item (like a bundle you open into a grid). Upgrade with 2 more Shulker Shells in a smithing table to 18 slots. Cannot contain Shulker Boxes or other Satchels. Keeps its contents when broken (it drops as an item with contents).

**Look.** A soft leather bag in muted violet with a flap made of overlapping shell plates, a brass clasp with a small amber glow, and a dark strap.

*Design note:* this is the "carry more" reward. It does not use the chest slot, so it never competes with an elytra.

### 9.6 Void Lens

**What it does.** An upgraded spyglass. While looking through it: Echoes show their true outline, hidden Lurkers show a faint shimmer outline, Driftwrecks within 128 blocks are outlined, and the tide plane's height is shown as a line with a number.

**Look.** A spyglass with a dark Undershale barrel and a teal crystal lens that glows faintly.

### 9.7 Elytra upgrades

Applied at a smithing table using an **upgrade template** (found in Fallen Spire vaults), the elytra and a material. An elytra can hold **one** upgrade at a time (stored as a data component); applying another replaces it (the old template is not returned). This forces a choice of flying style.

| Upgrade | Template source | Material | Effect | Cost |
|---|---|---|---|---|
| **Gale** (boost) | Spire vaults | Skate Membrane | Double-tap jump while flying: a short forward boost (about one-third of a firework rocket) | 8 s cooldown, 3 durability per boost |
| **Still** (air brake) | Spire vaults | Mirror Shard | Hold sneak while flying: decelerate sharply to a slow glide without diving | 1 durability per second of braking |
| **Hover** | Rare: Deep Rim spire vaults only | Glow Gland | Hold sneak while nearly stopped: hover in place up to 3 s; recharges while gliding or on ground | 2 durability per hover |

**Visual requirement.** Each upgrade changes the elytra's look subtly:
- Gale: teal glowing veins along the membrane; a burst of teal wind particles on boost.
- Still: a dark mirror-like edge along the wing tips; a ripple ring when braking.
- Hover: two small glowing teal nodes near the shoulders; a soft downward glow and floating motes while hovering.

### 9.8 Gravity Anchor and Anchor Frames (post-boss)

**What it does.** The final reward of the mod: control of space.

- **Gravity Anchor** (block): creates a **Stable Field** — a sphere of radius 12 (configurable 8–24).
  - Inside, Void Erosion does not apply.
  - Players and mobs that fall out of the bottom of the field slow-fall for 10 seconds (time to recover).
  - **Anchor Frames** can be placed in midair inside the field (they need no support). They act like scaffolding you can stand on and build from.
- Requires the **Heart of Stillness** (boss drop) for the first one; further anchors use **Stillness Fragments** (also from the boss and from post-boss Fracture runs).
- A field visibly shimmers at its edge: a faint teal sphere visible only when a player is within 4 blocks of the boundary (so it doesn't clutter views).

**Why it matters.** It lets players build *in the Drift itself* — bridges, floating bases, skate stations — which no other End mod offers as an endgame.

**Look.** A heavy, dark obsidian sphere held in a cage of four purpur arcs, rotating slowly. A thin teal halo spins around it at a slight tilt.

### 9.9 Skate Harness

**What it does.** Makes a tamed Void Skate rideable (like a saddle).

**Look.** A brown leather saddle-pad with two long straps ending in brass rings, edged with pale stitched membrane.

**Recipe:** Leather ×3, Skate Membrane ×2, Grazer Frill ×1, iron nugget ×2.

### 9.10 Key and progression items

| Item | Source | Use | Look |
|---|---|---|---|
| **Spire Key** | Spire Warden (guaranteed) | Consumed in a Keystone Socket to trigger Reassembly | A long purpur key with a magenta crystal in the bow and teeth shaped like little towers |
| **Echo Tablet** | Vaults, Driftwrecks, Undercroft | Lore (12 unique) | A palm-sized slate of pale stone with faint glowing glyphs |
| **Ascendant Sigil — Spire** | A Deep Rim Spire's Grand Vault | One of three Rift keys | A purpur disc with a tower glyph |
| **Ascendant Sigil — Tide** | A Driftwreck's Tide Shrine | One of three Rift keys | A teal glass disc with a wave glyph |
| **Ascendant Sigil — Under** | A Deep Root Hollow's Heartroot chamber | One of three Rift keys | An Undershale disc with an inverted-tree glyph |
| **Heart of Stillness** | The Unmade (first kill per player) | First Gravity Anchor; can be placed in the Undercroft's Still Throne (§11.4) | A smooth, pale sphere with a calm white core, faintly ringing |
| **Stillness Fragment** | The Unmade (repeat kills), Fracture debris | Further Gravity Anchors | A chip of the same pale material |

### 9.11 Armor trims

Armor trims are data-driven in vanilla, so the mod adds new patterns and materials without new armor:

| Type | Name | Source | Look |
|---|---|---|---|
| Pattern | **Spire** | Fallen Spire vaults | Vertical tower lines rising from the boots to the helmet |
| Pattern | **Drift** | Driftwrecks | Horizontal flowing wave lines |
| Pattern | **Undersong** | Root Hollows | Branching lines that hang downward from the shoulders |
| Material | **Lumen** (Lumen Crystal Shard) | Underside | Teal with a bright highlight |
| Material | **Still** (Mirror Shard) | Stillwater Basins | Near-black with a silver edge |
| Material | **Frill** (Grazer Frill) | Grazers | Soft lavender |

### 9.12 Food

| Food | Hunger | Saturation | Effect | Source |
|---|---|---|---|---|
| **Chorus Pod** | 3 | 1.8 | None (unlike chorus fruit, **does not teleport**) | Chorus groves |
| **Roasted Chorus Pod** | 6 | 7.2 | None | Smelt a pod |
| **Grazer Flank** (raw) | 3 | 1.8 | None | Grazers |
| **Cooked Grazer Flank** | 8 | 12.8 | None (steak-equivalent) | Smelt raw flank |
| **Glowbulb** | 2 | 1.2 | Slow Falling 8 s | Underside |
| **Drift Stew** | 10 | 12.0 | None | Bowl + Roasted Pod + Cooked Flank + Glowbulb |
| **Stillwater Tea** (bottle) | 1 | 0.6 | Clears Levitation and Void Erosion | Bottle of Stillwater + Lilac Tuft, brewed |

*Design note:* the End now feeds you, which means players can stay for long expeditions — a requirement for a "frontier" that is meant to be lived in for a while.

### 9.13 Brewing

| Potion | Base | Ingredient | Effect | Duration |
|---|---|---|---|---|
| **Potion of Steadiness** | Awkward | Mirror Shard | Immune to Levitation and knockback from explosions | 1:30 (extended 4:00) |
| **Potion of Tidesight** | Awkward | Lumen Crystal Shard | See clearly in Stillwater, no XP drain in Stillwater, and see the tide plane as a bright line through any fog | 3:00 (8:00) |
| **Stillwater Tea** | Bottle of Stillwater | Lilac Tuft | Instant: clears Levitation and Void Erosion | instant |

### 9.14 Crafting materials

| Material | Source | Main uses |
|---|---|---|
| Skate Membrane | Void Skate | Harness, Gale upgrade |
| Glow Gland | Void Skate (rare) | Void Anchor, Hover upgrade, egg ritual |
| Grazer Frill | Grazer (shearing, drops) | Harness, Frill trim |
| Voidroot Fiber | Underside, Lurker | Rift Tether cord, string substitute |
| Lumen Crystal Shard | Underside crystals | Lamps, trims, Tidesight potions |
| Mirror Shard | Stillwater Basins | Still upgrade, Steadiness potion, trims |
| Echo Dust | Echo | Gateway Compass, Void Lens |
| Lurker Lens | Lurker | Void Lens |
| Stillglass | Crafted | Anchors, decor |

---

## 10. The Dragon Egg and the Wyrm companion

The vanilla Dragon Egg is a trophy with no use. This mod lets the player hatch it into a long-term companion — the most emotionally important feature in the mod. It is a reward of **time and care**, not combat.

### 10.1 Egg availability

- **Default:** one egg per world (vanilla behavior).
- **Multiplayer option (on by default on servers):** each later dragon kill drops one **Dragon Egg Fragment**; 4 fragments combine into a new egg. This lets every player on a server raise a Wyrm eventually without devaluing the first egg.

### 10.2 The hatching ritual

1. **Build an Egg Cradle** (§6.3) anywhere — in the End it works twice as fast.
2. **Attune:** place the egg on the cradle. The cradle's four Lumen posts light up one by one over 30 seconds. The egg begins to pulse faintly.
3. **Nourish:** feed the egg 8 **Glow Glands** by right-clicking it, no more than one every 5 minutes. Each feeding makes a crack of light appear on its shell.
4. **Vigil:** a player must stay within 32 blocks of the cradle for a total of 30 minutes of loaded time (in the End: 15 minutes). The egg hums and its cracks grow brighter. Leaving pauses the timer; it does not reset.
5. **Hatch:** when the vigil completes, the egg shakes, light pours from the cracks in rays, and it breaks open. A **Hatchling** emerges and bonds to the nearest player who fed it the most Glow Glands.

**Visual requirement for the ritual.** Each step must have a clearly different look: posts lighting (step 2), pink cracks appearing one by one (step 3), cracks glowing brighter with light rays escaping (step 4), and a final burst of light, shell fragments and a soft chime chord (step 5).

### 10.3 Growth stages

| Stage | Time with owner (loaded time near owner) | Size | Abilities |
|---|---|---|---|
| **Hatchling** | 0–2 h | Cat-sized | Follows the owner; can sit on the owner's shoulder like a parrot; emits light level 7; chirps when a Void Tide is about to rise |
| **Fledgling** | 2–6 h | Wolf-sized | All of the above, plus **Tide Sense** (points its head toward the nearest Driftwreck within 64 blocks when the owner sneaks) and **Fetch** (dives to retrieve one item stack that fell off an island within the last 5 seconds) |
| **Drake** | 6 h+, and fed 16 Roasted Chorus Pods after reaching Fledgling | Horse-sized | All of the above, plus **rideable short flight** and **Void Ward** |

**Drake flight.** Rideable with a Skate Harness. 30 seconds of flight stamina, cruise 8 b/s, recharge on ground. It cannot fly higher than 40 blocks above the highest block below it (so it is not a better elytra; it is a companion you can hop islands with).

**Void Ward.** While the Drake is within 4 blocks, the owner does not suffer Void Erosion. Combined with a Void Anchor, this makes Driftwreck diving much safer — a late-game comfort, not a skip.

**Combat.** The Wyrm does not fight (Pillar 2). It flees from danger and hides behind its owner.

### 10.4 Death and recovery

If a Wyrm's health reaches zero (Hatchling 10 HP, Fledgling 20 HP, Drake 40 HP), it does not die. It curls up and becomes a **Slumbering Egg** item at that spot (it floats above the void if over the void). Placing it on an Egg Cradle and waiting 10 minutes restores it at its current stage. It cannot be lost to the void.

### 10.5 The Wyrm's look (visual requirement)

- **Hatchling:** a small, round-bodied serpentine creature with oversized eyes glowing soft pink, two stubby horns, tiny membranous wings that flutter fast, and slate-blue scales with a pale teal belly. It wobbles when it walks.
- **Fledgling:** longer and leaner, wings now larger than its body (indigo membranes with pale veins), horns curving back, a short crest along its spine that glows faintly lavender.
- **Drake:** a sleek, long-necked flying creature about the size of a horse, slate-blue to deep indigo, with wide violet wings showing pale veins, two swept-back pale horns, glowing pink eyes, and a row of small lavender-glowing spines down its back. It glides with slow, wide wingbeats and curls its tail around its owner when idle.
- It MUST NOT resemble the vanilla Ender Dragon's design (no black-and-purple bony look, no identical head shape). It is a different, gentler lineage — related, not a copy.

---

## 11. Structures

### 11.1 Fallen Spire complexes

**Variants.**

| Variant | Description | Frequency |
|---|---|---|
| **Leaning Spire** | One tower 25–40 blocks tall leaning 10–25 degrees, top broken, base sunk 3–6 blocks into the island | 50% |
| **Shattered Spire** | Tower lying horizontally across the island, rooms opened along its length; a stub of the base still standing | 35% |
| **Drowned Spire** | A tower half-sunk into a Stillwater Basin; lower rooms flooded with Stillwater | 15% |

**Rooms** (assembled from templates; a complex has 4–8 rooms):

| Room | Contents |
|---|---|
| **Lantern Hall** | Main entry; Spire Lanterns; murals |
| **Library** | Lecterns, broken bookshelves of purpur; 1 Echo Tablet chance 40% |
| **Quarters** | Purpur-slab beds, shell bowls, an empty cradle (lore) |
| **Workshop** | Smithing table, broken tools; elytra upgrade template chance 25% (Gale or Still) |
| **Vault (sealed)** | Collapsed; restored by Reassembly. Contains a **Spire Vault** block that gives per-player loot (each player can claim once, like vanilla trial vaults) |
| **Grand Vault** (Deep Rim only) | Requires two Keystone Sockets used; contains the **Spire Sigil** (per-player) and the Hover template chance 30% |

**Reassembly sequence (visual requirement).**
1. The Spire Key is inserted; the socket's key-shaped hollow fills with magenta light.
2. A low chord sounds; rubble blocks within 16 blocks begin to tremble.
3. Over ~10 seconds, rubble lifts into the air (rendered as block display entities, not real blocks while moving), rotates slowly and flies along curved paths into the missing wall, floor and ceiling positions of the vault room, each landing with a soft stone "click."
4. The final block lands; the room's lanterns ignite one by one; the vault door opens.
5. If any player-placed block occupies a needed position, the sequence skips that position (it never overwrites player blocks).

**Loot table: Spire Vault (per player).**

| Item | Chance | Amount |
|---|---|---|
| Ender pearls | 80% | 2–6 |
| Echo Tablet | 35% | 1 |
| Gale or Still upgrade template | 25% | 1 |
| Spire armor trim template | 20% | 1 |
| Shulker Shell | 30% | 1–2 |
| Stillglass | 50% | 4–12 |
| Cracked Void Anchor | 30% | 1 |
| Enchanted book (Feather Falling IV, Unbreaking III, Mending) | 25% | 1 |
| Elytra (Deep Rim only) | 8% | 1 |

### 11.2 Hoards

See §4.4. Structure: a small island template set (12 variants), a Hoard Cache, 2–4 Hoarders, a pile generator that stacks blocks from a weighted Overworld block list. Grand Hoards (1 in 15) are larger and contain a crude replica (a well, a small hut, a pumpkin patch).

### 11.3 Driftwrecks

**What they are.** Broken chunks of the Ascendancy's lower world, hanging deep in the Drift at Y 8–28. Submerged at High Tide; reachable at Low Tide by Skate, elytra, Rift Tether or (later) Anchor Frames.

**Types.**

| Type | Description | Key loot |
|---|---|---|
| **Ladder Wreck** | A broken segment of a spire, hanging vertically | Spire trim template, pearls |
| **Pod Store** | A sealed granary room on a rock | Chorus Pods ×16–32, Drift Stew ingredients, Drift trim template |
| **Tide Shrine** | A small round shrine with a wave mural and a pedestal | **Tide Sigil** (per player), Echo Tablet |
| **Debris Hulk** | A big tangle of debris with several small chests | Mixed loot, Mirror Shards, Glow Glands |

**Rules.**
- Loot containers in Driftwrecks are per-player (like vanilla trial vaults) so multiplayer groups don't race each other.
- Driftwrecks are visible from above as dark shapes beneath the tide surface at High Tide and become sharp and clear at Low Tide.
- Frequency: about one per 300×300 area in the Far Rim; Tide Shrines are 1 in 6 Driftwrecks.

### 11.4 The Undercroft (beneath the main island)

Generated only by the Sundering (§5.4).

**Layout.**
1. **The Descent:** a 7×7 shaft with a spiral stair of end stone and purpur from the main island top down to Y ~25.
2. **Hall of Murals:** a long, low hall with six mural panels telling the story of the tides, the towers, the Heart and the fall (the only place the whole story is shown at once — but without words).
3. **The Rift Chamber:** a round chamber with the **Fracture Rift Frame** at its center and three sigil sockets around it.
4. **The Still Throne:** an empty socket in the north wall. After defeating The Unmade, placing the Heart of Stillness here **calms the tides**: High Tide drops from Y 36 to Y 20 for the whole world, and the tide plane's color shifts from teal to a calm silver-blue. Removing the Heart restores normal tides. (Players choose whether they want the challenge to remain.)
5. **Side Root Hollows:** two short side passages into the main island's Underside, with Lurkers.

**Look.** Dark Undershale walls with purpur trim, hanging Lumen crystals lighting the space teal, magenta cracks in the floor near the Rift Frame. The Descent shaft is lit by a single beam of pale light from above.

### 11.5 Root Hollows and Deep Root Hollows

- **Root Hollows:** small caves cut into the Underside ceiling (5–10 blocks across), reachable by climbing Voidroots. One chest (Undersong trim template 15%, Glow Glands, Lumen Shards, Voidroot fiber).
- **Deep Root Hollows** (Deep Rim only, under the largest islands): a larger cave network ending in the **Heartroot Chamber** — a hollow around a giant glowing root knot that holds the **Under Sigil** (per player). Guarded by 4–6 Lurkers and 1–2 Echoes.

### 11.6 Skate Roosts

Tall, thin spikes of Driftstone (8–20 blocks) jutting up from tiny islets or hanging below islands, with ledges where Skates perch. They are landmarks visible from far away, and the most reliable place to tame Skates. Some appear in the Gap ring, so players can find Skates before the outer islands.

### 11.7 Echo Shrines

A single Echo Tablet on a purpur plinth on a small island, lit by one Spire Lantern. Rare. Pure lore.

### 11.8 The Fracture arena

A fixed structure in `worldsfall:the_fracture`: nine island fragments in a ring (radius ~40), each with a **Tether Pylon** (§13.3), outer debris clouds, and **Updraft Columns** between some fragments (visible as columns of rising ash) that lift gliding players. A small, safe **Arrival Platform** sits 60 blocks outside the ring, connected to the ring by a debris path; the exit Rift reopens on this platform after the fight.

---

## 12. Progression, gating and advancements

### 12.1 The arc

| Step | Gate | Opens | Typical time |
|---|---|---|---|
| **0. Vanilla End** | Kill the dragon (vanilla) | The Sundering, the Descent, the Undercroft | (vanilla) |
| **1. The Underside** | None (walk down the Descent) | Lumen, Glowbulbs, first Lurkers, Root Hollows | 30–60 min |
| **2. The Gap** | Find a Skate Roost; tame a Void Skate with Chorus Pods (pods come from Hoards' barter or Chorus Reach islands reachable by bridging/pearls) | Outer islands without elytra | 30–90 min |
| **3. The Near Rim** | — | Chorus Reach (food), Hoards (barter), Stillwater (glass, anchors) | 1–3 h |
| **4. The Far Rim** | — | Fallen Spires, Spire Wardens, Keys, Reassembly, elytra (from vanilla End Cities or Deep Rim vaults), upgrade templates | 2–5 h |
| **5. The Drift** | Void Anchor + Skate/elytra recommended | Driftwrecks at Low Tide, Tide Shrines | 1–3 h |
| **6. The Three Sigils** | Spire Sigil (Grand Vault), Tide Sigil (Tide Shrine), Under Sigil (Heartroot) | The Fracture Rift | 3–6 h |
| **7. The Fracture** | Insert three sigils into the Rift Frame | The Unmade | 30–60 min per attempt |
| **8. Stability** | Defeat The Unmade | Gravity Anchors, Still Throne, building in the Drift | open-ended |
| **Parallel: The Wyrm** | Dragon egg + ritual | Companion over time | 6+ h of play |

**Why the Gap step matters.** In vanilla, crossing the 1,000-block gap without an elytra means a pearl from a gateway. Here, Skate Roosts in the Gap and Skate taming give an earned, alternative path. Gateways still work as in vanilla — nothing is removed.

### 12.2 Gating rules

- **Nothing is behind a single random drop.** Every required item is guaranteed from a specific, findable source (Spire Keys from Wardens, Sigils from per-player containers).
- **Sigils are per player** so every player on a server can open the Rift for themselves, but one player opening it lets the whole party enter (the Rift stays open for 10 minutes after activation).
- **Each gate has a visual landmark** the player can see before they can pass it (the Descent glow, Skate Roost spikes, leaning towers on the horizon, Driftwreck shadows under the tide).

### 12.3 Advancements

A new tab, **"Where Worlds Go to Fall"**, with its icon a Lumen Crystal.

| Advancement | Requirement | Type |
|---|---|---|
| Nothing Holds | Witness the Sundering | Task |
| Underneath | Enter the Underside | Task |
| Something Clicks | Kill an Underside Lurker | Task |
| Patience of the Void | Tame a Void Skate | Goal |
| Across the Gap | Ride a Void Skate from the main island ring to the outer islands | Challenge |
| Pale Pasture | Breed two Chorus Grazers | Task |
| Fair Trade | Barter with a Hoarder | Task |
| Light Fingers | Anger an entire Hoard | Task (hidden) |
| A Hole Full of Stars | Swim in Stillwater | Task |
| The Tide Remembers | Loot a Driftwreck at Low Tide | Goal |
| Ladders to Nowhere | Complete a Reassembly | Goal |
| Wings, Revised | Apply an elytra upgrade | Task |
| Carry the World | Fill a Shulker Satchel | Task |
| Anchored | Survive a void death with a Void Anchor | Task |
| Echoes of Them | Collect 6 Echo Tablets | Goal |
| The Whole Story | Collect all 12 Echo Tablets | Challenge |
| Three Keys | Hold all three Ascendant Sigils | Goal |
| Below the Bottom | Enter the Fracture | Goal |
| Unmade | Defeat The Unmade | Challenge |
| Stillness | Place a Gravity Anchor | Task |
| Calm Waters | Place the Heart of Stillness in the Still Throne | Goal |
| Warm Hands | Hatch the Dragon Egg | Goal |
| Fully Grown | Raise a Drake | Challenge |

---

## 13. Final boss: The Unmade

### 13.1 Concept

The Unmade is what remains of the Heart of Stillness: a core of blinding white-pink light wrapped in rings of black crystal shards, with void tendrils that reach out and pull the world apart. It does not want to kill the player so much as **unmake their footing**. The fight is a test of everything the mod taught: reading the void, moving with Skate, elytra and Tether, and keeping your nerve when the ground leaves.

### 13.2 Entering

- Insert the three Ascendant Sigils into the Rift Frame; the center becomes a swirling crimson-black surface for 10 minutes.
- Players arrive on the **Arrival Platform**. A tamed Void Skate being ridden passes through with its rider.
- The fight begins when a player steps onto any ring fragment (a boss bar appears: "The Unmade").
- If all players leave the Fracture or die, the boss resets to full health after 30 seconds and the arena rebuilds.

### 13.3 Arena elements

| Element | Description |
|---|---|
| **Ring fragments (9)** | Islands 10–16 blocks across at radius ~40 around the center |
| **Tether Pylons (one per fragment)** | Null Obsidian pillars with a magenta crystal on top, each linked to the boss by a visible beam. They are the damage mechanic of Phase 1. |
| **Updraft Columns** | Columns of rising ash between some fragments; gliding through one gives lift (elytra and Skate) |
| **Anchorstones** | Small floating Driftstone blocks the Rift Tether can latch onto (Null Obsidian can't be latched) |
| **The Core** | The boss itself, hovering at the ring's center about 12 blocks above fragment height |

### 13.4 Health and scaling

| Players | Total health | Phase thresholds |
|---|---|---|
| 1 | 720 | 480 / 240 |
| 2 | 1,080 | 720 / 360 |
| 3 | 1,368 | 912 / 456 |
| 4+ | 1,584 (cap) | 1,056 / 528 |

The Core takes damage from melee only when exposed and from projectiles at any time it is exposed. Armor: 10.

### 13.5 Phase 1 — The Shell (100% → 66%)

The Core is shielded by three orbiting shard rings. It is **immune** while the rings are complete.

**Damage mechanic.** Each ring is anchored by three Tether Pylons. Destroying a pylon's crystal (20 HP, any damage) breaks its beam. When all three pylons of one ring are broken, that ring shatters and the Core is exposed for 12 seconds. Then the pylons regrow after 20 seconds. Three ring cycles in total; the third exposure transitions to Phase 2 at 66%.

**Attacks.**

| Attack | Telegraph | Effect | Counter |
|---|---|---|---|
| **Shard Volley** | Core flares, 6 shards line up and aim (1.0 s) | 6 shards fly at the target, 4 HP each | Shield blocks; strafing dodges |
| **Tendril Sweep** | A dark line of particles appears across a fragment (1.25 s) | A tendril sweeps along the line, 8 HP and heavy knockback | Step off the line; jump to another fragment |
| **Gravity Lens** | A ring of distorted light on the ground (1.0 s) | Reverses gravity inside the ring for 4 s, lifting players up | Get out of the ring; Potion of Steadiness does not help (this is gravity, not Levitation) |

### 13.6 Phase 2 — The Unbinding (66% → 33%)

The arena begins to come apart. The ring slowly expands: every 20 seconds, one random fragment drifts 6 blocks outward. Fragments that drift more than 18 blocks away crumble and fall (players get a 3-second rumble and crumbling particles first).

**New attacks.**

| Attack | Telegraph | Effect | Counter |
|---|---|---|---|
| **Shardling Swarm** | The Core cracks open (1.0 s) | 4 Shardlings spawn (6 with 3+ players) | Kill them; splinters fly straight — dodge sideways |
| **Void Pulse** | The Core inhales, the arena darkens, and a ring of teal light expands from the bottom (2.0 s) | An arena-wide wave rising from below; anyone touching a fragment's surface takes 10 HP and Void Erosion for 3 s | Be **airborne** when it passes: jump, glide, ride a Skate, hang from a Tether |
| **Tendril Grab** | Tendril tip glows and tracks a player (1.5 s) | Grabs and pulls the player toward the Core, 6 HP | Break line of sight behind a pylon; Tether away |

**Damage mechanic.** The Core stays exposed in Phase 2 but regenerates 2 HP/s whenever no player has damaged it in the last 5 seconds. Players must keep pressure while fragments drift.

### 13.7 Phase 3 — Unmade (33% → 0%)

Most fragments are gone. The arena is now debris: dozens of small chunks drifting slowly in a loose orbit around the Core. The Core descends to fragment height and starts pulling.

**Mechanics.**
- **Inward Pull:** constant gentle pull toward the Core (like being near a black hole), stronger the closer you are. Players must keep moving outward between attacks.
- **Collapse Timer:** a soft enrage. After 4 minutes in Phase 3, the pull doubles. After 6 minutes, debris begins disappearing.
- All earlier attacks continue at higher frequency, except Gravity Lens.

**Final blow.** When the Core reaches 0, everything stops. The shards freeze mid-air. The Core's light turns from pink to calm white. The debris slowly rearranges itself into a single, flat, stable island under the players (a Reassembly on a grand scale, ~15 seconds). The Heart of Stillness drops in the center. The exit Rift opens on the new island.

### 13.8 Rewards

| Reward | Condition |
|---|---|
| **Heart of Stillness** | First kill per player (per-player drop, like vanilla vault logic) |
| **Stillness Fragments** ×2–4 | Every kill |
| Null Obsidian, Fracture Glass become mineable in the Fracture | After first kill in the world |
| Advancement "Unmade" | Every participant within 64 blocks |
| Music disc "Unmade" | First kill per player |

### 13.9 Rematches

The Fracture can be re-entered with a **Rift Key** crafted from Stillness Fragment ×2 + one of each Sigil type (Sigils can be re-obtained per player once per real-time day from their containers on server config; single-player: unlimited). Repeat fights drop Stillness Fragments for more Gravity Anchors.

### 13.10 Visual and audio requirements

- **The Core:** a sphere of white light fading to hot pink and magenta at its edges, with a soft bloom. It throbs slowly (one pulse every 2 seconds) and quickens as health drops.
- **Shard rings:** black diamond-shaped crystals with thin magenta edges, each slowly spinning on its own axis while the ring orbits.
- **Tendrils:** dark, smooth, tapering ribbons edged in soft magenta glow, moving like seaweed in slow water.
- **Phase changes:** each phase change plays a 2-second freeze (the world holds still, sound drops to a single ringing tone) before the new phase begins.
- **Damage feedback:** hitting the Core flashes a white ring around it and plays a glassy crack; hitting pylons cracks them visibly.
- **Accessibility:** every telegraph has a unique sound and a shape on the ground or in the air, so it never relies on color alone. Flash intensity respects the "Reduced flashing" option (§17.4).
- **Music:** "Unmade" — three layers that add as phases advance (§19.4).

### 13.11 Anti-cheese rules

- The Core cannot be damaged from outside a 64-block radius.
- The Rift Tether cannot latch onto Null Obsidian or the Core.
- Placed blocks in the Fracture during the fight decay after 10 seconds ("the Fracture unmakes them"), so players can't build towers. This rule switches off after the first kill (so the arena can be built in later).
- Ender pearls work, but land with a 1-second Slowness I (disorientation), to limit pearl-spam escapes.

---

## 14. Integration with vanilla content

### 14.1 What stays exactly as in vanilla

- The Ender Dragon fight, its health, its phases, its crystals.
- End Cities, their Shulkers, their loot, their elytra, End Ships.
- Endermen, Endermites, Shulkers (city ones), End Gateways and how they teleport.
- Chorus plants and chorus fruit (including fruit teleportation).
- The End's fixed time of day and the vanilla kill plane.

### 14.2 What the mod extends

| Vanilla thing | Extension |
|---|---|
| Ender Dragon's first death | Triggers the Sundering |
| Dragon Egg | Hatchable (§10) |
| Elytra | Upgradable (§9.7); otherwise unchanged |
| End Gateways | Gateway Compass can bind to them |
| Chorus plants | Grow Chorus Pods in Chorus Reach; Pale Loam supports them |
| Endermen | Ambient hoarding behavior (§5.5); new caste relatives |
| Shulkers | Wild variant with molting (§8.6) |
| Ender pearls | Repair material for the Rift Tether |
| Phantom membrane | Not required anywhere in the mod (Skate Membrane replaces it in End recipes) |
| Spyglass | Upgradable to Void Lens |
| Armor trims | New patterns and materials (data-driven) |
| Smithing table | Elytra upgrades and Satchel upgrade |
| Brewing | Three new brews |
| Bundle | Satchel is a bundle-like item with a grid view |

### 14.3 Use of 26.x vanilla features

- **Data-driven worldgen** for features, placed features and structures (§20.4).
- The 26.3 changes that expose more of the old hard-coded features as configurable feature types (for example `stepped_column_cluster`, the renamed basalt columns, and the configurable glowstone-blob-style placement) SHOULD be used for Skate Roost spikes and crystal clusters instead of custom Java features, where they fit **[verify exact fields]**.
- The 26.3 **`block_transformer`** item component (items that transform one block into another when used) SHOULD be used for simple interactions such as shearing Pale Loam back to end stone or scraping Weathered Purpur into purpur **[verify]**.
- The 26.3 **order-independent transparency** improves the layered translucency the mod relies on (Stillwater, Stillglass, Lumen crystals, the tide plane). Art MUST still look correct with it turned off.
- **Attributes** for gravity and fall damage (§5.2).
- **Per-player vault logic** (from vanilla trial chamber vaults) as the model for Spire Vaults, Driftwreck containers and Sigil containers.

### 14.4 Watch list (future vanilla drops)

- **The Sift (2027):** a new vanilla dimension. If it reuses words or materials (souls, meadows), avoid naming collisions. Keep the End's palette distinct.
- **An official End update:** if Mojang announces one, the mod will need a compatibility review; design choices that touch vanilla behavior (Sundering, egg) are already behind config switches for this reason.
- **New status effects in upcoming drops** (for example, the Freezing effect seen in 26.4 snapshots): consider small integrations only after release, never before.

---

## 15. Crafting and recipe reference

Recipes are written as ingredients and layout notes. Shaped recipes use a 3×3 grid read top-left to bottom-right; `·` is empty.

| Result | Station | Recipe |
|---|---|---|
| Skate Harness | Crafting | `L L L / M F M / N · N` — L leather, M Skate Membrane, F Grazer Frill, N iron nugget |
| Cracked Void Anchor | Crafting | `· C · / G E G / · I ·` — C chain, G Stillglass, E ender eye, I iron ingot |
| Void Anchor | Smithing | Cracked Void Anchor + Glow Gland + Dragon's Breath |
| Rift Tether | Crafting | `· P · / V I V / V · ·` — P ender pearl, V Voidroot Fiber, I iron ingot |
| Gateway Compass | Crafting | `· D · / D C D / · D ·` — D Echo Dust, C compass |
| Shulker Satchel (9) | Crafting | `· S · / L C L / L L L` — S Shulker Shell, L leather, C chest |
| Shulker Satchel (18) | Smithing | Satchel (9) + Shulker Shell ×2 (as material, via template-free upgrade) |
| Void Lens | Smithing | Spyglass + Lurker Lens + Echo Dust |
| Stillglass ×4 | Furnace | Stillwater bucket + 4 sand (bucket returned) |
| Lumen Lamp | Crafting | `U U U / U L U / U R U` — U Undershale, L Lumen Crystal Shard, R redstone |
| Tide Gauge | Crafting | `P G P / P W P / P R P` — P purpur, G Stillglass, W Bottle of Stillwater, R comparator |
| Egg Cradle | Crafting | `L B L / B O B / L B L` — L Lumen Lamp (the four posts), B end stone bricks, O obsidian |
| Gravity Anchor | Crafting | `P O P / O H O / P O P` — P purpur pillar, O Null Obsidian, H Heart of Stillness or Stillness Fragment ×2 |
| Anchor Frame ×8 | Crafting | `P · P / · S · / P · P` — P purpur slab, S Stillness Fragment |
| Drift Stew | Crafting (shapeless) | Bowl + Roasted Chorus Pod + Cooked Grazer Flank + Glowbulb |
| Rift Key | Crafting | Stillness Fragment ×2 + Spire Sigil + Tide Sigil + Under Sigil |
| Dragon Egg | Crafting | Dragon Egg Fragment ×4 (multiplayer option) |
| Building blocks | Stonecutter | All Stillstone, Undershale, Driftstone and Weathered Purpur variants |

---

## 16. Balance and economy

### 16.1 Balance principles

- **Comparisons are to vanilla, always.** Every new item is checked against the closest vanilla item: the Satchel against a Shulker Box, the Tether against pearls, the Skate against a horse and an elytra, the Void Anchor against a Totem of Undying.
- **Time-to-reward must feel generous at first and stable later.** Early End rewards (Skate, food) come within the first hour after the dragon. Late rewards (Sigils, boss) take an evening.
- **No infinite loops** without a cost or a cap (molting caps, floating item caps, Hoard caps).

### 16.2 Mobility ladder (target speeds)

| Mode | Speed | Range limits | When available |
|---|---|---|---|
| Walking/sprinting | 4.3 / 5.6 b/s | Ground only | Always |
| Ender pearl | ~burst | 1 pearl per throw, fall risk | Always |
| Rift Tether | 12 b/s reel | 24-block reach, needs a latch point | Underside onward |
| Void Skate | 9 cruise / 16 dive | Climb limited by stamina, cannot go below tide +4 | Gap step |
| Drake | 8 b/s | 30 s flight, 40-block ceiling over ground | Long-term |
| Elytra | vanilla (fastest with rockets) | Needs height or rockets | Far Rim |
| Elytra + Gale | vanilla + boost | 8 s cooldown | Far Rim |
| Anchor Frames | building | Inside Stable Fields | Post-boss |

The elytra stays the fastest long-distance option. The Skate is better for **careful** travel (stopping, perching, controlled climbs) and is available earlier. The Tether is best for short, precise movement and the Underside.

### 16.3 Comparisons with vanilla items

| New item | Closest vanilla | Why it's balanced |
|---|---|---|
| Shulker Satchel (9/18) | Shulker Box (27) | Smaller, but carried without placing; cannot nest boxes |
| Void Anchor | Totem of Undying | Does not prevent death; only saves items, and only for void deaths |
| Rift Tether | Ender pearl | No damage, reusable, but short range and needs a latch point |
| Gravity Anchor | Beacon | Endgame-only, area utility, no stat buffs |
| Elytra upgrades | Fireworks | Small, situational; one per elytra; costs durability |
| Cooked Grazer Flank | Steak | Identical values |

### 16.4 Hoard barter table

Each barter: the player inserts **one stack-group** of accepted Overworld blocks into a Hoard Cache. The Hoarders take it and leave one result. Values are in "hoard value": grass/dirt/sand 1 each; flowers/saplings 2; logs 2; stone/cobblestone 1; coal/copper ore 4; iron ore 6; gold/lapis ore 10. A barter consumes 32 value.

| Result | Weight | Amount |
|---|---|---|
| Chorus Pods | 25 | 4–8 |
| Ender pearls | 15 | 1–3 |
| Stillglass | 12 | 4–8 |
| Mirror Shards | 10 | 2–4 |
| Lumen Crystal Shards | 10 | 2–4 |
| Skate Membrane | 8 | 1 |
| Glow Gland | 5 | 1 |
| Echo Tablet | 4 | 1 |
| Gateway Compass | 3 | 1 |
| Spire Key | 1 | 1 (rare shortcut, max once per Hoard) |
| Nothing (Hoarder keeps it, shrugs) | 7 | — |

Hoarders do not accept diamonds, emeralds, netherite, or any End block (to prevent loops).

### 16.5 Spawn and density budgets

| Thing | Budget |
|---|---|
| New mobs near a player (all types) | ≤ 20 within 128 blocks |
| Floating items on Stillwater | ≤ 64 stacks per chunk |
| Molting Wild Shulkers | ≤ 8 in a 32-block radius |
| Hoard pile size | ≤ 300 blocks |
| Reassembly block displays at once | ≤ 120 |
| Gravity Anchors | No hard cap; fields don't stack effects |

---

## 17. Playability, onboarding, accessibility and multiplayer

### 17.1 Onboarding: how the player learns without a tutorial

| What to learn | How the world teaches it |
|---|---|
| "There is more below" | The Sundering cracks the island and opens a lit shaft right next to where the player is standing |
| Low gravity | The Descent's lower half is a Light Zone; the player notices they're falling slowly, sees motes drifting up |
| Lurkers fear light | The first Lurker in the Undercroft side passage hangs just outside a lantern's light; if the player stands in the light, it stays hidden but visible |
| Skates can be tamed | Skate Roosts in the Gap have a Chorus Pod pile nearby (placed by Hoarders); Skates come close when the player holds pods |
| Tides are dangerous | The first time a tide rises while a player is in the End, Tidewisps swarm near the player and the hum starts 2 minutes early |
| Stillwater drains XP | The XP bar visibly flickers and a soft draining sound plays the first second the player enters |
| Echoes mimic blocks | The first Echo encountered always disguises as a block that is obviously out of place (a lone grass block in a purpur room) |
| Reassembly | Keystone Sockets glow and hum when a player holds a Spire Key nearby |

Optional **hint toasts** (on by default, can be turned off): one-line hints that appear once per world for each key discovery ("Skates like Chorus Pods.").

### 17.2 Difficulty and fairness

- Every damaging attack has a telegraph ≥ 0.5 s (boss telegraphs ≥ 1.0 s).
- Void deaths are the biggest risk; the Void Anchor exists so they are survivable losses, not rage-quits.
- **Peaceful:** Lurkers, Echoes, Spire Wardens and The Unmade do not spawn. Spire Keys instead appear in Keystone-adjacent chests; the Rift opens to a peaceful Fracture where the Heart of Stillness rests on a pedestal (so peaceful players still get Gravity Anchors).
- **Hardcore:** Void Anchors still work (they save items, not lives).

### 17.3 Quality of life

- All new blocks are pick-block-able and have sensible tool requirements (pickaxe for stone, axe for Voidroot, any tool for Glowbulbs).
- Stack sizes: Chorus Pods 64, Glowbulbs 64, Sigils 1, Keys 16.
- Tooltips: Shift shows mechanics; no hidden mechanics that matter for survival.
- Riding a Skate shows the stamina arc only while climbing or recovering.
- The tide indicator is optional and appears automatically when holding a relevant item.

### 17.4 Accessibility

| Area | Requirement |
|---|---|
| **Subtitles** | Every new sound has a subtitle. Important threats have descriptive subtitles ("Something clicks above", "Void tide stirs", "Plates grind"). |
| **Color independence** | Every telegraph uses shape + sound + motion, not color alone. Tide state is also shown by height and sound. |
| **Reduced flashing** | An option caps bright flashes (Reassembly, boss phase changes, egg hatching) to a soft fade. Default off; on automatically if vanilla's "Hide Lightning Flashes" is on **[verify option interaction]**. |
| **Motion comfort** | "Reduced floatiness" option for Gravity Zones; no camera roll or flip anywhere; screen effects (vignette, desaturation) can be turned off. |
| **Readability** | Text on Echo Tablets uses the standard font with a glyph-reveal animation that can be disabled. |
| **Audio dampening** | Stillwater's sound muffling can be turned off. |

### 17.5 Multiplayer

- **Per-player loot** in all vaults, Driftwrecks and Sigil containers.
- **Shared world events:** the tide and the Sundering are global.
- **Boss scaling** by player count (§13.4).
- **Hoard anger** is per Hoard, not per player (a thief angers it for everyone).
- **Skates and Wyrms** are owned; others cannot ride without permission (a "Share Mount" interaction allows a friend once).
- **Egg Fragments** so each player can raise a Wyrm.
- **Server performance:** see §20.8 budgets.

---

## 18. Aesthetics: the art, lighting, sky, particle, animation and UI bible

### 18.1 The look in one paragraph

**Bone-pale islands in a black, cold, star-filled void, lit by small, precious lights.** The End is mostly dark and mostly empty. Color appears in small, saturated pools — teal crystals, lilac groves, magenta lanterns, a riot of stolen Overworld green — and each pool means something: life, danger, history or the void itself. The further down you go, the darker and more saturated it gets, ending in the crimson-black and blinding white of the Fracture. Nothing is cute except the things the player will love (Grazers, Skates, the Wyrm), and even those are soft rather than cartoonish. It should always look like vanilla Minecraft at heart: 16×16 pixel textures, blocky models, honest shading — just with a much stronger sense of place.

### 18.2 Color script by layer

Color hex values are targets for the texture artist; textures use 4–8 colors per block within these families.

| Layer / biome | Dominant | Secondary | Accent (light) | Mood words |
|---|---|---|---|---|
| **Sky (all of the End)** | Near-black violet `#05030C` → `#160A28` | Nebula violet `#4B2A7A`, navy `#21446B` | Star white `#FFFFFF`, pale blue `#CDEFFD` | Vast, cold, still |
| **Rim — end stone** | Bone `#E6DFA8` | Sand-olive body `#BDB37C` → `#635C40` | — | Weathered, old |
| **Chorus Reach** | Lilac moss `#9B7BB0`, `#B493C4` | Chorus stalk `#8A5C8E` | Lavender glow `#D8B6FF`, pollen `#F1D6FF` | Gentle, cared for |
| **Stillwater Basins** | Ink `#0C0B1C` | Stillstone `#2A2140`, `#3A2D58` | Silver-blue `#8FB0FF`, star specks `#DFE8FF` | Silent, wrong, deep |
| **Fallen Spires** | Purpur `#A87BB6`, `#8D649D` | Dark windows `#2A1631` | Lantern magenta-white `#FFB6F0` | Fallen grandeur |
| **The Hoard** | Overworld green `#6FBF4A`, dirt `#8A5F3C` | Stone grey `#8D8D8D`, sand `#E3D59A` | Flower red `#FF5A7A`, yellow `#FFD84A` | Absurd, displaced |
| **The Underside** | Undershale `#2E2A33`, `#3A3540` | Voidroot violet `#3A2A4A` | Lumen teal `#2FC9BB` → `#C8FFF8`, bulb magenta `#FF7AD9`, root amber `#FFD27A` | Upside-down, watchful |
| **The Drift** | Deep navy `#03030A` → `#08121C` | Driftstone grey-violet `#4B3D66` | Tide teal `#3AE0D0`, crest `#B8FFF7` | Depth, motion |
| **The Fracture** | Crimson-black `#1A0510` | Ash-grey islands `#3A2F3A` | Core white `#FFFFFF` → pink `#FFB3EC` → magenta `#FF3FB4` | Ending, held breath |
| **Calmed tide (post-Still Throne)** | — | — | Silver-blue `#B9D3FF` | Peace |

**Rules of color.**
1. **Light is earned.** Large areas are dark or neutral; saturated color appears only as light sources, life or loot.
2. **One accent per biome.** Each biome owns one glow color. Teal belongs to the Underside and tides; magenta to Spires and the Fracture; lavender to Chorus Reach. Do not mix them inside a biome except at borders.
3. **Down is darker.** Value drops as the player descends: Rim (light), Underside (dark with bright points), Drift (darker), Fracture (darkest, with the single brightest object in the mod).
4. **The Hoard is the exception.** Its Overworld colors are deliberately "wrong" for the End. Keep it small so it stays a joke, not a theme.

### 18.3 Texture guidelines

- **Resolution:** 16×16 for blocks and items, matching vanilla. No HD textures.
- **Palette size:** 4–8 colors per block texture; 3–6 for items.
- **Shading:** light from the top-left, as vanilla. Ambient occlusion handled by the game.
- **Noise:** use clustered, chunky noise like vanilla end stone, never single-pixel static.
- **Emissive textures:** glowing parts (crystal tips, lantern cores, pod highlights, mob eyes, Skate spots) use a separate emissive layer so they glow in darkness (full brightness) without lighting the whole block face.
- **Animated textures:**
  - Stillwater: 32 frames, slow (2 ticks/frame × interpolation), drifting star specks.
  - Lumen Crystal: 8 frames, very subtle shimmer on the tip.
  - Tide Gauge face: driven by block state (16 levels), not animation.
  - Fracture Rift surface: 16 frames, swirling.
  - Spire Lantern core: 8 frames, gentle flicker.
- **Consistency checks:** every new stone must sit visually between vanilla end stone and deepslate in contrast, so it reads as part of the same world.

**Block texture descriptions (for the artist):**

| Block | Description |
|---|---|
| Pale Loam | Top: short lilac moss in two tones with tiny pale flecks; sides: end stone with moss drip edges 2–3 px deep, irregular |
| Lilac Tuft | Cross model; thin curly blades in two lilacs with a pale tip |
| Chorus Pod | Rounded, segmented pod; unripe pale violet, ripe lavender with a bright emissive center line |
| Stillstone | Near-black violet with sparse 1–2 px silver flecks; polished version gets subtle concentric rings; chiseled shows a 4-point star |
| Mirror Shard | Thin vertical sliver, black with one bright white-blue edge highlight |
| Weathered Purpur | Vanilla purpur desaturated by ~20%, with uneven pale vertical streaks and a few chipped corners |
| Spire Lantern | Purpur cap and base, pale glass sides, a vertical magenta-white emissive core |
| Undershale | Horizontal layered bands of slate grey and thin violet lines; polished version shows smooth bands; tiles are 2×2 squares |
| Ceiling Moss | Dangling dark-teal strands, transparent background, longer strands at the center |
| Lumen Crystal | Like pointed dripstone but faceted, teal body darkening toward the base, emissive white-cyan tip |
| Voidroot | Twisted dark-violet roots with amber emissive tips, cross-model, climbable |
| Glowbulb | Round translucent magenta bulb with a brighter emissive core and a short dark stem |
| Driftstone | Grey-violet stone with diagonal wind-scour streaks |
| Null Obsidian | Crimson-black with thin dark-red veins, slight emissive on the veins (very faint) |
| Fracture Glass | Dark red translucent glass with a few bright crack lines |
| Gravity Anchor | Obsidian core with purpur arc frame; teal emissive ring segment that rotates via block entity rendering |

### 18.4 Model guidelines (entities)

- **Tool:** Blockbench, Java Edition entity format, cuboids only, vanilla-like pixel density (1 texel = 1/16 block).
- **Silhouette test:** each model must be recognizable as a flat black shape at 32 px tall.
- **Bone counts:** keep animations readable — Skate (body, 2 wing segments per side, tail 3 segments, fins), Grazer (neck 3 segments, 6 legs, frill), Lurker (body, crown, 7 legs × 2 segments, thread), Castes (vanilla Enderman skeleton + attachments), Wyrm (per stage: body, neck, head, wings 2 segments each, tail 3–4 segments).
- **Scale reference:** Grazer ≈ cow; Skate wingspan ≈ 5 blocks; Lurker body ≈ 1 block, legs reach 2; Spire Warden ≈ 3.5 blocks tall; Drake ≈ horse; The Unmade core ≈ 4 blocks across with shard rings to ~14 blocks.

**Per-creature visual keys** (complements §8):

| Creature | Must-have visual keys |
|---|---|
| Void Skate | Wide flat wings; teal spot rows; glowing leading-edge line; whip tail with glowing tip; slow deep wingbeats; trail of fading teal dots at speed |
| Chorus Grazer | Long curved neck; six thin legs; crystal frill with glowing tips; rocking gait |
| Underside Lurker | Teardrop body on a thread; four magenta eye dots in a ring; seven long legs splayed; hidden state = shimmer only |
| Hoarder | Patchwork cloak of Overworld colors; amber eyes; side pouch |
| Spire Warden | Cracked purpur plates bound by magenta light; crest; plates fall off visually as health drops (3 damage states) |
| Echo | Static-edged translucent form; perfect block disguise |
| Wild Shulker | Weathered, lichen-dusted shell; thin pale shell when molted |
| Tidewisp | Soft teal-white light with three trailing streamers |
| Shardling | Black crystal with magenta core and two orbiting splinters |
| The Unmade | White-pink core with bloom; three orbiting rings of black shards edged magenta; tendrils like seaweed |
| Wyrm | See §10.5; gentle, never menacing; must not resemble the Ender Dragon |

### 18.5 Animation principles

- **Weight tells the story.** Skates and Drakes move slowly with long glides; Grazers rock; Wardens are heavy and deliberate; Lurkers are still, then sudden; Echoes snap.
- **Low gravity is slower, not stranger.** In Gravity Zones, mob animations play at ~80% speed and jumps hang at the top.
- **Idle life.** Every creature has at least two idle animations (Skate: wing ripple, tail flick; Grazer: graze, look around; Lurker: leg twitch; Warden: plate adjust).
- **Telegraph poses** are held still for their full wind-up time — stillness is the cue.
- **Block animations:** Reassembly blocks follow curved arcs with ease-in-out and a slight spin; ripening pods swell over 1 second; Glowbulbs pulse slowly (one pulse per 4 seconds); Lumen crystals do not move.

### 18.6 Lighting

| Source | Level | Color intent |
|---|---|---|
| Spire Lantern | 13 | Magenta-white |
| Lumen Crystal (cluster) | 12 | Teal |
| Glowbulb | 10 | Magenta |
| Lumen Lamp | 15 (on) | Teal-white |
| Ripe Chorus Pod | 4 | Lavender |
| Voidroot tip | 6 | Amber |
| Stillwater | 3 | Cold blue |
| Mirror Shard | 2 | Silver |
| Fracture Glass | 3 | Red |
| Unmade Shard | 5 | Magenta |
| Bottled Tidewisp | 9 | Teal |
| Wyrm (Hatchling+) | 7 (dynamic light only if a dynamic-light mod is present; otherwise an emissive glow) | Pink |

Vanilla light is uncolored; the color intent is carried by emissive textures, particles and fog. Shader packs MAY tint light; the art MUST work without them.

### 18.7 Sky, fog and atmosphere

The vanilla End sky is a flat tiled texture. The mod replaces it with a **layered End sky** (custom dimension special effects / sky renderer **[verify 26.3 rendering API]**):

1. **Base gradient:** near-black violet at the zenith to a slightly lighter violet at the horizon.
2. **Star field:** three layers of stars at different densities and sizes; the nearest layer moves very slightly with the camera (parallax) to create depth.
3. **Nebula layer:** large, soft, slowly drifting clouds of color. Tint depends on the biome the player stands in (blended over 64 blocks): warm violet (Chorus Reach), cold navy (Stillwater), wine (Spires), neutral (Hoard), teal haze from below (Underside/Drift).
4. **Distant debris silhouettes:** a ring of dark, blocky island silhouettes near the horizon, very faint, to make the void feel populated with distant places.
5. **Tide glow:** when looking down, the tide plane adds a teal glow to the bottom of the sky.
6. **Fracture sky:** replaced entirely by crimson-black with the Core's glow as the light source.

**Fog by biome (distance fog color and density):**

| Biome | Fog color | Density |
|---|---|---|
| Vanilla End biomes | Vanilla | Vanilla |
| Chorus Reach | Lilac `#3A2448` | Low; extra low-lying haze near ground |
| Stillwater Basins | Navy `#0B1024` | Very low (clarity for reflections) |
| Fallen Spires | Wine `#2A0F25` | Low-medium |
| The Hoard | Faint green-grey `#1A2420` | Low |
| The Underside | Teal-black `#0A1C1E` | Medium-high |
| The Drift | Navy `#08121C` fading to teal near the tide | Medium |
| The Fracture | Crimson-black `#1A0510` | High at distance |

### 18.8 Particles

| Particle | Where | Look and motion |
|---|---|---|
| Pollen mote | Chorus Reach | Tiny pale lilac dots rising slowly, swaying |
| Pod pop | Ripe pods | Small burst of lavender sparks |
| Still ring | Floating items on Stillwater | A faint expanding pale ring every few seconds |
| Stillfall mist | Basin spill points | Dark grey mist at the bottom of the fall |
| Dust fall | Spire overhangs | Grey-violet specks falling slowly |
| Socket spark | Keystone Sockets | Purple sparks spiraling upward |
| Up-mote | Underside, Gravity Wells | Teal-white dots rising; the main low-gravity cue |
| Up-drip | Lumen tips | Small droplets falling *upward* from crystal tips |
| Skate wake | Riding a Skate fast | Trail of fading teal dots |
| Tide crest | Tide plane | Bright cyan sparkles along the crest line |
| Erosion static | Entities below tide | Teal static flickering on the outline |
| Ash inflow | Fracture | Grey ash drifting toward the center |
| Shard spark | Fracture, boss hits | Magenta sparks |

**Particle budget:** no single effect may exceed 200 particles on screen at "All" particle settings; all effects scale down with the vanilla particle setting.

### 18.9 UI and HUD

- **Skate stamina arc:** a slim teal arc to the right of the crosshair, fades out when full.
- **Tide indicator (optional):** a tiny wave icon with a fill level and a phase label under the hotbar, shown while holding a Gateway Compass, Tide Gauge item or Void Lens.
- **Boss bar:** "The Unmade", magenta, with three notches at phase thresholds.
- **Advancement tab:** background texture of dark Undershale with faint teal crystal glints.
- **Echo Tablet reading:** a centered single line that fades in from glyphs to letters over 1 second, then fades out after 6 seconds.
- **Tooltips:** item names in vanilla style; mechanics on Shift in grey; flavor in italic dark purple.
- **Creative tab:** one tab, icon Lumen Crystal, ordered by biome then function.

### 18.10 "The picture" acceptance tests

Before a biome, structure or creature is considered done, a reviewer compares in-game screenshots with the written "picture" in its section (§4, §8, §10, §13). The asset passes if:
1. The silhouette and composition match the description.
2. The color story matches §18.2 (dominant, secondary, accent).
3. It reads clearly with shaders off, at default brightness, at 12 chunks render distance.
4. It reads clearly with the Reduced flashing and Reduced floatiness options on.
5. A tester who has not read the document can say in one sentence what the place or creature *is for*.

### 18.11 Concept art note

The Whiteboard (Appendix A) contains concept illustrations for the layers, biomes, creatures, rewards and boss. They are **inspirational only**: they set mood, composition and color, but the in-game look is defined by this section's written requirements and by vanilla's art style.

---

## 19. Audio bible: sound design and music

### 19.1 Audio philosophy

**Silence is the default.** The vanilla End is quiet, and that quiet is part of its identity. New sounds are sparse, low and spatial. Each biome has one signature sound that tells the player where they are with their eyes closed.

### 19.2 Ambience by biome

| Biome | Signature | Loop | Additional (random) sounds |
|---|---|---|---|
| Chorus Reach | Glassy chimes | Soft wind with a warm low pad | Pod ripening chime, Grazer hums, pollen "shimmer" |
| Stillwater Basins | Absence | A felt, very low hum; reduced reverb | A single distant drop; a breath-like swell once a minute |
| Fallen Spires | Whistling stone | Wind through gaps, creaks | Stone settling, distant lantern chime, Warden footsteps |
| The Hoard | Warped Overworld | Slowed, pitched-down birdsong and cattle sounds blended into wind | A leaf rustle that sounds slightly wrong |
| The Underside | Reverse swells | Low resonant drone; crystal tinks | Drips falling upward (reversed drip), Lurker clicks |
| The Drift | Distant whales | Wind that rises with speed | Skate calls, tide hum (louder near the plane) |
| The Fracture | Breath held | Sub-bass drone, a choir that only inhales | Shards grinding, ash hiss |

### 19.3 Sound effects list (for implementation)

| Group | Sounds |
|---|---|
| Blocks | Pale Loam (soft moss), Stillstone (dense stone), Mirror Shard (glass chime), Lumen Crystal (amethyst-like but lower, colder), Voidroot (creaking wood-fibre), Glowbulb (soft squelch + glow hum), Driftstone (dry stone), Null Obsidian (deep thud), Fracture Glass (dark glass), Spire Lantern (soft ring) |
| Fluids | Stillwater swim (slow, heavy), bucket fill/empty (thick, hollow), XP drain (a faint descending sparkle) |
| Mobs | Skate: call, wingbeat, taming chime, hurt, death; Grazer: hum, eat, bleat, death; Lurker: click, eyes open, snare crack, bite, retreat; Hoarder: barter accept/refuse, anger; Warden: grind, slam, teleport rise, plate shield; Echo: hum, unfold, scramble; Wild Shulker: molt; Tidewisp: pulse; Shardling: hum, dive, burst |
| Systems | Tide stir (2 min warning), tide rise swell, tide fall exhale, erosion static, Sundering rumble and crack, Reassembly (chord, block flights, block clicks, lanterns igniting), egg pulse, egg crack, egg hatch chord, Gravity Anchor hum, Rift open/close |
| Items | Tether fire, latch, reel, detach; Void Anchor trigger; Capsule beacon hum; Compass bind; Satchel open/close; Echo Tablet read (soft glyph shimmer); elytra upgrade sounds (Gale burst, Still brake ripple, Hover hum) |
| Boss | Core pulse, shard volley, tendril sweep, lens hum, void pulse inhale/exhale, Shardling swarm, phase freeze tone, final silence, island reforming chord |

### 19.4 Music

Instrumental, sparse, in the spirit of vanilla's ambient piano and synth work — but colder. Instruments: felt piano, glass harmonica, low synth pads, bowed metal, a distant wordless choir (Fracture only).

| Track | Plays | Mood and instrumentation |
|---|---|---|
| **Pale Pasture** | Chorus Reach | Gentle felt piano over a warm pad; glass chimes; the only "warm" track |
| **Hold Still** | Stillwater (rare) | Near-silence; one sustained glass tone that slowly changes pitch |
| **Ladders to Nowhere** | Fallen Spires | Slow, descending piano phrases; bowed metal; a feeling of something unfinished |
| **Undersong** | The Underside | Reversed piano notes that swell into themselves; teal-colored synth pads; soft pulses |
| **Below the Bottom** | The Drift (rare) | Wide, low pads; whale-like synth calls; slow and vast |
| **Nothing Holds** | After the Sundering (once) | A low crack, then a slow rising chord |
| **Unmade** | Boss fight | Layer 1 (Phase 1): pulsing sub-bass and ticking glass; Layer 2 (Phase 2): adds a breathing choir and percussion like stone falling; Layer 3 (Phase 3): adds a distorted, high, sustained tone that resolves into silence on the final blow |
| **Stillness** | After the boss (once), and as a music disc | The same melody as Ladders to Nowhere, now rising and resolved |

### 19.5 Mixing rules

- New ambience never exceeds vanilla ambient loudness.
- Threat sounds (Lurker eyes, Warden teleport rise, tide stir) are mixed to cut through and always carry a subtitle.
- Music respects the vanilla music volume slider and plays with vanilla's random delays.

---

## 20. Technical architecture and integration

> This section sets direction for the implementation sessions. Mojang changes internals every quarterly drop, so every API name here is a starting point to check against the 26.3 code, not a guarantee. Items marked **[verify]** MUST be confirmed first.

### 20.1 Platform facts that shape the design

- Since **26.1**, Minecraft: Java Edition ships **unobfuscated**, with Mojang's own names for classes, methods and parameters. There are no mapping layers to manage; code and documentation use the official names.
- 26.1 and later require **Java 25**.
- **NeoForge** versions use four numbers that start with the Minecraft version (for example `26.1.0.x`), and NeoForge has signalled that the 26.x line is the next long-term modding target after 1.21.1.
- **Fabric** uses Loom with the unobfuscated toolchain; each drop needs a matching Loom and Fabric API.
- Mojang releases a **game drop roughly every quarter** (26.1 Tiny Takeover, 26.2 Chaos Cubed, 26.3 Wilderness Bound, 26.4 in snapshots). The mod must be cheap to port, which drives the "data first" rule (§20.3).

### 20.2 Loader strategy

| Option | Pros | Cons | Decision |
|---|---|---|---|
| NeoForge only | Strong data-attachment, event and registry APIs; long-term target | Excludes Fabric players | **Default for v0.x** |
| Fabric only | Fast ports, performance-mod players | More mixins for some systems | Not chosen |
| Multiloader (common + NeoForge + Fabric) | Reaches everyone | ~30% more build and test work | **Target for v1.0** if the user wants Fabric (open question) |

Recommended layout if multiloader:

```
worldsfall/
  common/        # all game logic using vanilla APIs + platform interfaces
  neoforge/      # NeoForge entry point, registries, events, data attachments
  fabric/        # Fabric entry point, registries, events, mixins as needed
  data/          # generated datapack content (worldgen, loot, recipes, tags)
  assets/        # textures, models, sounds, lang
```

### 20.3 "Data first" rule

As much content as possible is **data**, not Java:

| Content | Format |
|---|---|
| Biomes, configured and placed features, structures, template pools, structure sets, processors | Worldgen JSON (generated by a data generator) |
| Loot tables (vaults, Driftwrecks, Hoard barter, mob drops) | Loot table JSON |
| Recipes (crafting, smithing, smelting, stonecutting) | Recipe JSON |
| Armor trim patterns and materials | Trim JSON |
| Tags (accepted Hoard blocks, Tether-unlatchable blocks, gravity-immune entities, Stillwater-converting blocks) | Tag JSON |
| Advancements | Advancement JSON |
| Sounds, subtitles, language | `sounds.json`, lang JSON |
| Damage types (Void Erosion, Lurker snare, Warden slam, Unmade attacks) | Damage type JSON |

**Java is reserved for:** the tide system, gravity zones, Stillwater fluid, entity AI and models, the Sundering and Reassembly sequences, the Wyrm lifecycle, the boss, the sky/tide renderers, and the End biome injection.

All data SHOULD be produced by **data generators** in code, so a vanilla rename is fixed in one place.

### 20.4 World generation

**The End's biome source.** Vanilla assigns End biomes with a dedicated End biome source driven by the island density noise, not with the multi-noise system the Overworld uses. Adding biomes requires either:
- **(a)** a small mixin/wrapper around the End biome source that, in the outer region (distance > 1,000), selects this mod's biomes using an additional noise, falling back to the vanilla choice; or
- **(b)** a worldgen library that already provides End biome injection (check what libraries Enderscape and other End mods use on 26.x).

**Decision:** prefer (b) if a maintained library exists for 26.3 on the chosen loader; otherwise (a), kept to the smallest possible mixin. **[verify]**

**3D biomes for the Underside.** Since 1.18, biomes are three-dimensional (assigned per 4×4×4 cell). The Underside SHOULD be a true 3D biome assigned to cells **below** the body of large islands (Y 15–55), so fog, particles, music and mob spawns switch automatically when the player goes under. The biome source can decide this by sampling the End island density function a few cells above. **[verify feasibility and cost]** Fallback: the Underside is created by features (crystals, roots, bulbs) placed by a "ceiling scan", and Gravity Zones are detected at runtime by checking for a solid ceiling within 24 blocks above plus being below the island body.

**The Drift as a biome.** The open air of the outer End becomes the Drift biome wherever no island density exists across a whole column region, replacing vanilla's assignment there. Vanilla biomes keep their islands.

**Features.** Use vanilla feature types where they fit (the 26.3 drop exposed more parameters on several old features — e.g. the renamed basalt-column feature and the glowstone-blob-style placement — which can build Skate Roost spikes and hanging crystal clusters **[verify fields]**). Custom features: Pale Loam spreader, Chorus Pod groves, Stillwater basin carver, debris field scatterer, hanging crystal/root/bulb clusters.

**Structures.** Jigsaw structures with template pools:
- Fallen Spire (3 start pools: leaning, shattered, drowned), rooms as pool elements.
- Hoard (island templates + pile processor).
- Driftwrecks (4 types; Y range 8–28; placed in the Drift).
- Skate Roosts, Echo Shrines, Root Hollows, Deep Root Hollows.
- The Undercroft is **not** worldgen: it is placed by the Sundering from a template.

**Compatibility mode with terrain mods.** If another mod changes the End's terrain (Nullscape) or biome source (BetterEnd, Enderscape), the mod switches to **additive mode**: no biome injection, but structures, mobs and systems still apply, placed with tag-based checks (`#minecraft:is_end`, plus a mod tag for "large island surface"). This keeps the mod stackable.

### 20.5 Core systems — implementation notes

| System | Approach |
|---|---|
| **Void Tides** | Server-side saved data storing cycle configuration; height computed from world game time; one network packet per phase change plus a periodic resync; per-entity check every 10 ticks; client renderer draws the plane as a large translucent quad mesh following the camera's horizontal position |
| **Gravity Zones** | Attribute modifiers (gravity, safe fall distance, fall damage multiplier) applied/removed on zone entry/exit; zone membership evaluated every 5 ticks per entity from the 3D biome (Underside), Gravity Well spheres (stored as positions in chunk data at worldgen) and Stable Fields (block entity positions) **[verify attribute ids]** |
| **Void Erosion** | A mob effect with a damage type that bypasses armor; applied by the tide check |
| **Stillwater** | Custom fluid type and fluid blocks; buoyancy and XP drain on entity tick; floating-item despawn suppression via item entity data; sound muffling via a client-side sound filter |
| **Sundering** | Hook on the dragon's death; checks player-placed block tracking in the main island region; places the Descent and Undercroft templates over several ticks to avoid lag spikes |
| **Player-placed tracking** | A per-chunk data attachment that records player-placed block positions **only within 250 blocks of the End origin** (bounded cost) |
| **Reassembly** | Vanilla block display entities animate flying blocks; final placement from a structure template; skips occupied positions |
| **Per-player containers** | Reuse the vanilla vault concept (per-player reward tracking) or a custom block entity storing claimed player UUIDs |
| **Hoard barter** | Block entity with an insert slot; evaluates "hoard value" by tag weights; rolls a loot table; Hoarders animate the exchange |
| **Elytra upgrades** | A data component on the elytra stack; smithing recipes write it; client and server handle input (double-tap jump, sneak) during fall-flying |
| **Satchel** | A data component storing contents, with a bundle-like tooltip and a container screen |
| **Wyrm** | Tamable entity with a stage data value; growth timer counted only while the owner is within 32 blocks; "Slumbering Egg" item stores the full entity data |
| **Void Capsule** | Entity storing an item list and owner; moves with the tide; persistent |
| **Fracture** | Custom dimension type (void, fixed time, no sky light, custom effects) and a single arena template placed on first entry |
| **Boss** | Multi-part entity: core plus shard rings as rendered parts; pylons as block entities; phase state machine; boss event bar |

### 20.6 Rendering

| Element | Approach | Fallback |
|---|---|---|
| Layered End sky | Custom dimension special effects / sky renderer for the End and the Fracture **[verify 26.3 API; rendering changes every drop]** | Vanilla sky with tinted fog only |
| Tide plane | Translucent mesh, animated texture, crest line, ghost line | Simple translucent quad |
| Stillwater | Fluid renderer with animated star texture; optional planar reflection | Animated texture only |
| Emissive parts | Emissive texture layers on models; block model element light emission where supported **[verify]** | Full-bright render type for small parts |
| Gravity field edge | Translucent sphere shown only near the boundary | Particles at the boundary |
| Boss core | Bloom-like layered translucent spheres | Flat bright sphere |

Rendering MUST be tested with: vanilla renderer (including the new order-independent transparency on and off), Sodium (on Fabric) or its NeoForge equivalent, and a popular shader loader. Where a shader pack replaces the sky, the mod's sky yields gracefully.

### 20.7 Networking

Packets (server → client unless noted): tide phase and timestamp; tide resync; Sundering sequence start; Reassembly sequence start; boss phase change; Wyrm stage change; Skate stamina (client-predicted, server-validated); elytra upgrade activation (client → server); Satchel open (client → server); Echo Tablet text display.

### 20.8 Performance budgets

| Area | Budget |
|---|---|
| End chunk generation time | ≤ +15% vs. vanilla End on the same machine |
| Server tick cost of tide + gravity checks | ≤ 0.3 ms per player per tick on average |
| New entities ticking near a player | ≤ 20 (see §16.5) |
| Sundering placement | Spread over ≥ 40 ticks; no single tick > 10 ms |
| Reassembly | ≤ 120 display entities, ≤ 15 s total |
| Client: tide plane | One draw call |
| Client: particles | ≤ 200 per effect at "All" |

Profile with a sampling profiler (such as spark) on a test server with 4 players in the Far Rim during High Tide.

### 20.9 Save compatibility

- **Adding the mod to an existing world:** new biomes and structures appear only in **new** chunks. Already-generated outer islands stay as they are, but systems (tides, gravity in Underside biome cells where present, mobs that spawn on any End island, Skates in the Drift) still work. The Sundering triggers on first End entry if the dragon is already dead (§5.4).
- **Removing the mod:** mod blocks become air (standard behavior); the Undercroft becomes a hollow; vanilla content is untouched. Document this clearly for players.
- **Updating between mod versions:** registry IDs are permanent once released; renamed things use data fixers or remapping.

### 20.10 Tags and data hooks for pack makers

| Tag | Purpose |
|---|---|
| `worldsfall:hoard_accepts` (+ value tags `worldsfall:hoard_value_1/2/4/6/10`) | What Hoarders take and how much it's worth |
| `worldsfall:tether_unlatchable` | Blocks the Rift Tether can't grab |
| `worldsfall:gravity_immune` | Entities unaffected by Gravity Zones |
| `worldsfall:erosion_immune` | Entities unaffected by Void Erosion |
| `worldsfall:stillwater_converts` | Blocks that become Stillstone under Stillwater |
| `worldsfall:skate_food`, `worldsfall:grazer_food`, `worldsfall:wyrm_food` | Feeding items |
| `worldsfall:large_island_surface` | Where large-island structures may place (for compatibility with terrain mods) |

### 20.11 Commands and debugging

| Command | Use |
|---|---|
| `/worldsfall tide get` / `set <phase>` / `pause` / `resume` | Inspect and control tides |
| `/worldsfall sundering status` / `trigger` | Check or force the Sundering (respects player-block safety) |
| `/worldsfall gravity debug` | Show zone membership and current attribute modifiers |
| `/worldsfall wyrm stage <player> <stage>` | Test Wyrm stages |
| `/worldsfall boss reset` | Reset the Fracture arena |
| `/worldsfall locate <structure>` | Wrapper around vanilla locate for mod structures |

All commands require operator level 2.

### 20.12 Testing

- **GameTests** for: Stillwater interactions, Hoard barter value calculation, per-player vault claims, Reassembly skipping occupied blocks, Sundering safety check, elytra upgrade data component, Satchel restrictions, Void Anchor capsule creation.
- **Fixed test seeds** for worldgen review (one per biome showcase).
- **Visual review** against the "picture" tests (§18.10).
- **Compatibility matrix** (per release): vanilla only; with Nullscape; with Enderscape; with a shader pack; with a performance renderer; dedicated server with 4 players.

### 20.13 Porting policy for quarterly drops

1. Track snapshots for the next drop; open a porting branch at the first pre-release.
2. Update the loader toolchain; regenerate data with data generators.
3. Fix compile errors in Java systems (expected mainly in rendering and worldgen hooks).
4. Run GameTests and the compatibility matrix.
5. Release within 2–4 weeks of the loader's stable release for the drop.
6. Support only the latest drop plus the previous one.

---

## 21. Configuration reference

| Key | Default | Range / options | Effect |
|---|---|---|---|
| `sundering.enabled` | true | bool | If false, a Rift island replaces the Descent (§5.4) |
| `sundering.retroactive` | true | bool | Trigger on first entry if dragon already dead |
| `tides.enabled` | true | bool | Turn Void Tides off entirely (plane stays at Low) |
| `tides.lowY` / `tides.highY` | 0 / 36 | -16–64 | Tide heights |
| `tides.lowMinutes` / `highMinutes` / `transitionMinutes` | 10 / 10 / 3 | 1–120 | Cycle timing |
| `tides.screenEffects` | true | bool | Vignette on rise |
| `tides.hudIndicator` | "auto" | off / auto / always | Tide HUD |
| `erosion.maxDamagePerSecond` | 4 | 1–20 | Erosion cap |
| `gravity.underside` | 0.45 | 0.1–1.0 | Gravity multiplier in the Underside |
| `gravity.reducedFloatiness` | false | bool | Accessibility |
| `stillwater.xpDrain` | 1 | 0–5 per second | XP drain |
| `stillwater.soundMuffle` | true | bool | Accessibility |
| `stillwater.maxFloatingStacksPerChunk` | 64 | 0–512 | Performance |
| `voidAnchor.capsuleMinutes` | 60 | 5–∞ | Capsule lifetime |
| `voidAnchor.publicCapsules` | false | bool | Anyone can open |
| `skate.maxPerPlayer` | 6 | 0–20 | Spawn cap |
| `echo.scramble` | true | bool | Echoes swap hotbar slots |
| `wildShulker.moltMinutes` | 40 | 5–240 | Molting period |
| `hoard.ambientHoarding` | true | bool | Endermen bring blocks to Hoards |
| `egg.fragments` | "servers" | off / servers / always | Egg Fragments from repeat kills |
| `wyrm.growthHours` | 2 / 6 | — | Stage thresholds |
| `boss.maxHealth` | 720 | 200–5,000 | Base health |
| `boss.playerScaling` | true | bool | Health scaling |
| `boss.blockDecay` | true | bool | Fracture block decay during fight |
| `anchor.radius` | 12 | 8–24 | Stable Field radius |
| `visual.reducedFlashing` | false | bool | Accessibility |
| `visual.customSky` | true | bool | Use layered sky |
| `visual.reflections` | false | bool | Planar reflections on Stillwater (expensive) |
| `compat.additiveMode` | "auto" | auto / on / off | Additive worldgen with other End mods |
| `compat.tallEnd` | false | bool | Expand End height if no other mod does |
| `vanillaBiomeAdditions` | true | bool | Light-touch additions to vanilla biomes |
| `hints.toasts` | true | bool | Onboarding hints |

---

## 22. Roadmap and milestones

Each milestone is playable on its own and ends with a release.

| Version | Theme | Scope | Exit criteria |
|---|---|---|---|
| **v0.1 "Reach"** | Prove the core idea | Chorus Reach (Pale Loam, Chorus Pods, Lilac Tufts), Chorus Grazer, Void Skate (spawning, taming, riding, Harness), Void Anchor + Capsule, Egg Cradle and hatching to Hatchling stage, basic layered sky | A tester tames a Skate and crosses between islands without elytra; an item-loss death in the void is recovered |
| **v0.2 "Under"** | Down | The Sundering, Descent, Undercroft (without Rift activation), Underside biome, Lumen/Voidroot/Glowbulb, Gravity Zones, Underside Lurker, Root Hollows, Rift Tether, Fledgling stage | Players descend after the dragon and understand low gravity without reading anything |
| **v0.3 "Ruins"** | History | Fallen Spires (3 variants), Spire Warden, Spire Keys, Reassembly, per-player vaults, Echo, Echo Tablets, elytra upgrades (Gale, Still), Spire trim | A Reassembly runs smoothly on a 4-player server |
| **v0.4 "Hoard"** | Economy | The Hoard, Hoarder, barter, Hoard Cache, Shulker Satchel, Wild Shulkers, Gateway Compass, Void Lens | Barter loop feels fair; Satchel and shells balanced vs vanilla |
| **v0.5 "Tides"** | Void weather | Void Tides, tide renderer, Void Erosion, Tidewisps, Stillwater fluid and Basins, Stillglass, Tide Gauge, Driftwrecks, Drift biome, Skate Roosts, Drake stage, brewing | Players plan Driftwreck dives around the tide |
| **v0.6 "Unmade"** | Endgame | Three Sigils (Grand Vault, Tide Shrine, Heartroot), Fracture dimension, The Unmade, Shardlings, Gravity Anchor, Anchor Frames, Still Throne, Hover upgrade | Solo and 4-player boss fights finish in 10–20 minutes for prepared players |
| **v0.7 "Polish"** | Feel | Full audio and music, particles, all "picture" tests, accessibility options, hint toasts, advancement tab | Every biome passes §18.10 |
| **v1.0** | Release | Balance pass, compatibility matrix, performance budgets met, documentation | All budgets in §20.8 met; no known world-corrupting bugs |
| **Post-1.0** | Extras | Starfall, Skate variants, optional dragon-fight rework, tall-End mode, more Wyrm interactions | — |

---

## 23. Risks, open questions and decisions log

### 23.1 Risks

| Risk | Impact | Mitigation |
|---|---|---|
| End biome injection breaks with each drop | High | Keep the hook tiny; prefer a maintained library; additive mode as fallback |
| Custom sky and fluid rendering break with rendering refactors (every drop changes rendering) | High | Isolate rendering in one module; keep fallbacks; test with OIT on and off |
| Gravity attributes behave differently than expected | Medium | Prototype in v0.2 first; fall back to custom motion handling |
| Scope is very large for a solo developer | High | Milestones are independently shippable; v0.1 alone is a complete small mod |
| Conflicts with other End mods | Medium | Additive mode, tags, compatibility matrix |
| Mojang ships an official End update | Medium | Everything touching vanilla behavior is behind config; review on announcement |
| The Sift arrives in 2027 with overlapping themes | Low | Distinct palette and tone (§1.4) |
| Players find the tide punishing | Medium | Long low tide, clear telegraphs, Void Anchor, configurable cycle |

### 23.2 Open questions (from the Whiteboard)

1. **Mood:** desolate and eerie, or some lushness? *(This document assumes desolate with small lush exceptions — Chorus Reach and The Hoard.)*
2. **Team:** solo or team? How comfortable with Java? *(Affects milestone sizes.)*
3. **Loader:** Fabric, NeoForge, or both? *(This document assumes NeoForge first, multiloader by v1.0.)*
4. **Dragon fight:** rework it, or build after it? *(This document assumes: keep vanilla, build after it, with an optional rework post-1.0.)*
5. **License:** open source or all rights reserved?
6. **Name:** keep "Where Worlds Go to Fall" / `worldsfall`?

### 23.3 Decisions log

| # | Decision | Reason |
|---|---|---|
| D1 | No new ore or armor tier | Pillar 2; avoids duplicating Enderite mods |
| D2 | Keep vanilla End height; Fracture is a separate dimension | Compatibility with terrain mods |
| D3 | Tide is global and deterministic | Multiplayer fairness; survives restarts |
| D4 | Gravity via attributes, no camera flipping | Compatibility and motion comfort |
| D5 | Sundering never touches player blocks | Trust; anti-goal §1.5 |
| D6 | Per-player loot for all key containers | Multiplayer fairness |
| D7 | Wyrm cannot die permanently | Emotional investment; anti-frustration |
| D8 | Elytra holds one upgrade at a time | Meaningful choice; keeps elytra balanced |
| D9 | NeoForge first | Long-term target, strong APIs |
| D10 | Data-first content | Cheap quarterly ports |

---

## Appendix A — The design Whiteboard

The Whiteboard is the visual companion to this document: **[Where Worlds Go to Fall — End Mod Design](https://claude.ai/artifact/8x7mbvm5S5DKcKSbDBp4xG)**. A full text transcription is also provided as a separate file, `worldsfall-whiteboard-companion.md`.

### A.1 Board structure

| # | Section on the board | Matches this document |
|---|---|---|
| — | Title: "Where Worlds Go to Fall" / "The Nether gives power. The End gives reach." | §1 |
| 1 | The setting: four layers of void (layer picture, layer cards, Void Tides picture, existing-mods table, gaps, vanilla context, lore) | §1.4, §2, §3, §5.1 |
| 2 | Biomes: Chorus Reach, Stillwater Basins, Fallen Spires, The Hoard, The Underside | §4 |
| 3 | Creatures: Void Skate, Chorus Grazer, Underside Lurker, Ender castes, Wild Shulkers | §8 |
| 4 | Progression arc: dragon → Skate → Spires → Fracture | §12 |
| 5 | Rewards: Void Anchor, Rift Tether, Gateway Compass, Shulker Satchel, Elytra upgrades, Dragon Egg hatching, "No Enderite" | §9, §10 |
| 6 | Final boss: The Unmade | §13 |
| 7 | Building it: platform, data first, play nice, v0.1 scope, open questions | §20, §22, §23 |

### A.2 Concept illustrations on the board (described, not included)

All illustrations are inspirational. They set mood and composition, not final in-game visuals.

| File | Shows |
|---|---|
| `01_layers.jpg` | Cross-section of the End: a pale island with lilac chorus and a small ruined tower on top, teal crystals hanging beneath it, two glowing manta-like Skates gliding over a teal tide line, and a magenta crack of the Fracture glowing at the bottom |
| `02_chorus_reach.jpg` | A pale island patched with lilac moss, tall glowing chorus groves at the back, three pale long-necked frilled Grazers in the foreground, under a warm violet nebula |
| `03_stillwater.jpg` | A heart-shaped ring of pale stone around a black, star-filled lake with colored blocks floating on it and a dark curtain of fluid pouring off the edge |
| `04_fallen_spires.jpg` | A purple tower leaning hard with a broken top, a second tower lying on its side, magenta lantern posts, and loose purple blocks drifting into the sky |
| `05_the_hoard.jpg` | A small pale island crowded with Overworld blocks — grass, dirt, stone, sand, logs, flowers — and a single leafy tree |
| `06_the_underside.jpg` | Looking up at the dark underside of a huge island hung with teal crystals, amber-tipped roots and magenta bulbs, motes drifting upward, and a Lurker with pink eyes dangling on a thread |
| `07_void_tides.jpg` | Islands above a glowing teal tide surface with a dashed line where it used to be, and drowned, muted islands visible beneath |
| `08_void_skate.jpg` | A large indigo manta-like Skate with rows of teal glowing spots and a saddle on its back, with smaller Skates and islands in the distance |
| `09_dragon_egg.jpg` | A dark egg with glowing pink cracks on a stepped altar with four glowing posts and light rays, beside a young slate-blue Wyrm with violet wings on a second altar |
| `10_the_unmade.jpg` | A blinding white-pink core inside rings of black shards, dark tendrils reaching down to a ring of broken ash-grey island fragments in crimson darkness |
| `11_items.jpg` | Five items on small pedestals: the teal Void Anchor lantern, the Rift Tether with its sea-green pearl hook, the violet Gateway Compass with a pink needle, the shell-flapped Shulker Satchel, and a pair of membrane wings with teal thrusters |

---

## Appendix B — Glossary

| Term | Meaning |
|---|---|
| **Ascendancy** | The lost civilization that built the towers and the Heart of Stillness |
| **Anchor Frame** | Midair scaffolding usable inside a Stable Field |
| **Driftwreck** | Low structure in the Drift, reachable at Low Tide |
| **Gravity Zone** | Area with modified gravity (Light Zone, Gravity Well, Stable Field, Lens) |
| **Heart of Stillness** | The boss's first-kill drop; powers the first Gravity Anchor or calms the tides |
| **Reassembly** | The sequence that rebuilds a Spire vault room |
| **Sigil** | One of three keys (Spire, Tide, Under) that open the Fracture Rift |
| **Stable Field** | The area around a Gravity Anchor |
| **Sundering** | The world event when the dragon first dies |
| **Tide plane** | The moving surface of the Void Tide |
| **Undercroft** | The hub beneath the main island |
| **Void Erosion** | Damage-over-time below the tide plane |
| **Wyrm** | The companion hatched from the Dragon Egg (Hatchling → Fledgling → Drake) |

---

## Appendix C — ID and naming conventions

- **Namespace:** `worldsfall`.
- **IDs:** lowercase snake_case, noun first for families: `stillstone`, `polished_stillstone`, `stillstone_bricks`, `stillstone_brick_slab`.
- **Entities:** `void_skate`, `chorus_grazer`, `underside_lurker`, `hoarder`, `spire_warden`, `echo`, `wild_shulker`, `tidewisp`, `shardling`, `the_unmade`, `wyrm`, `void_capsule`.
- **Biomes:** `chorus_reach`, `stillwater_basins`, `fallen_spires`, `the_hoard`, `the_underside`, `the_drift`, `fracture_ring`.
- **Dimension:** `the_fracture`.
- **Structures:** `fallen_spire_leaning`, `fallen_spire_shattered`, `fallen_spire_drowned`, `hoard`, `grand_hoard`, `driftwreck_ladder`, `driftwreck_pod_store`, `driftwreck_tide_shrine`, `driftwreck_hulk`, `skate_roost`, `echo_shrine`, `root_hollow`, `deep_root_hollow`, `fracture_arena`, `undercroft` (template, not worldgen).
- **Items (selection):** `void_anchor`, `cracked_void_anchor`, `rift_tether`, `gateway_compass`, `shulker_satchel`, `void_lens`, `skate_harness`, `spire_key`, `echo_tablet`, `spire_sigil`, `tide_sigil`, `under_sigil`, `rift_key`, `heart_of_stillness`, `stillness_fragment`, `gale_upgrade_template`, `still_upgrade_template`, `hover_upgrade_template`, `dragon_egg_fragment`, `slumbering_egg`.
- **Effects:** `void_erosion`, `steadiness`, `tidesight`.
- **Damage types:** `void_erosion`, `lurker_snare`, `warden_slam`, `unmade_shard`, `unmade_tendril`, `void_pulse`.

---

## Appendix D — Asset checklist (approximate counts)

| Asset type | Count |
|---|---|
| Block textures (including variants, slabs/stairs reuse) | ~70 |
| Animated textures | 6 |
| Item textures | ~45 |
| Entity models | 12 (Wyrm has 3 stage models; Warden has 3 damage states) |
| Entity textures (incl. variants) | ~20 |
| Structure templates | ~60 (rooms and pieces) |
| Sound events | ~110 |
| Music tracks | 8 |
| Advancements | 23 |
| Language keys | ~450 |
| Loot tables | ~35 |
| Recipes | ~90 |

---

*End of document. Every number here is a starting point for playtesting; every [verify] is a task for the first implementation session.*
