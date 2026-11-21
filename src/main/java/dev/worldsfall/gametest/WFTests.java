package dev.worldsfall.gametest;

import dev.worldsfall.block.ChorusPodBlock;
import dev.worldsfall.block.entity.EggCradleBlockEntity;
import dev.worldsfall.block.entity.HoardCacheBlockEntity;
import dev.worldsfall.block.entity.PerPlayerLootBlockEntity;
import dev.worldsfall.effect.VoidErosionEffect;
import dev.worldsfall.entity.Echo;
import dev.worldsfall.entity.TheUnmade;
import dev.worldsfall.entity.VoidCapsule;
import dev.worldsfall.item.ElytraUpgrade;
import dev.worldsfall.item.EchoTabletItem;
import dev.worldsfall.menu.SatchelMenu;
import dev.worldsfall.network.ElytraActionPayload;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFComponents;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFFluids;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.world.BlockQueue;
import dev.worldsfall.world.PlayerPlaced;
import dev.worldsfall.world.VoidAnchorHandler;
import dev.worldsfall.world.gravity.GravityZones;
import dev.worldsfall.world.reassembly.Reassembly;
import dev.worldsfall.world.reassembly.ReassemblyPlan;
import dev.worldsfall.world.tide.TideCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** The GameTest suite (§20.12): every mechanic the design names as test-worthy, plus data integrity. */
public final class WFTests {
    private WFTests() {}

    /** 24 from 1.0 plus the expansion's (1.2 → 2.0). */
    static final int EXPECTED_ADVANCEMENTS = 44;

    static void collect(Map<String, Consumer<GameTestHelper>> out) {
        out.put("tide_cycle_math", WFTests::tideCycleMath);
        out.put("stillwater_turns_water_to_stillstone", WFTests::stillwaterWater);
        out.put("hoard_barter_value", WFTests::hoardBarter);
        out.put("hoard_refuses_diamonds", WFTests::hoardRefuses);
        out.put("per_player_vault_claims", WFTests::vaultClaims);
        out.put("reassembly_skips_occupied", WFTests::reassemblySkips);
        out.put("sundering_never_touches_player_blocks", WFTests::sunderingSafety);
        out.put("elytra_upgrade_component", WFTests::elytraUpgrade);
        out.put("satchel_restrictions", WFTests::satchelRestrictions);
        out.put("void_anchor_capsule", WFTests::voidAnchor);
        out.put("pale_loam_reverts_when_covered", WFTests::paleLoam);
        out.put("chorus_pod_harvest_regrows", WFTests::chorusPod);
        out.put("void_erosion_damage_curve", WFTests::erosion);
        out.put("egg_cradle_ritual_stages", WFTests::eggCradle);
        out.put("gravity_lens_reverses_gravity", WFTests::gravityLens);
        out.put("boss_health_scaling", WFTests::bossScaling);
        out.put("echo_scramble_swaps_two_slots", WFTests::echoScramble);
        out.put("echo_tablets_are_numbered", WFTests::tablets);
        out.put("data_integrity", WFTests::dataIntegrity);
        out.put("stillwater_cauldron_bottles", WFTests::stillwaterCauldron);
        out.put("wyrm_shoulder_sync", WFTests::wyrmShoulder);
        out.put("basin_shape_and_trims", WFTests::basinAndTrims);
        // expansion 1.2: the Warden Cycle
        out.put("warden_pact_weight_and_rewards", WFTests::pactWeights);
        out.put("warden_crystal_kinds", WFTests::crystalKinds);
        out.put("warden_release_needs_the_heart", WFTests::releaseGate);
        out.put("elytra_styles_stack", WFTests::elytraStyles);
        out.put("wyrm_barding_and_temperament", WFTests::wyrmBarding);
        // expansion 1.3: the Gap
        out.put("causeway_plan_shape", WFTests::causewayPlan);
        out.put("drift_buoy_needs_open_air", WFTests::buoyOpenAir);
        out.put("pillar_glyphs_ring_in_order", WFTests::pillarGlyphs);
        // expansion 1.4: cities and navigation
        out.put("lattice_nodes_and_knowledge", WFTests::latticeNodes);
        out.put("archivist_offers_and_peace", WFTests::archivistOffers);
        out.put("sinking_pulls_down", WFTests::sinkingPull);
        out.put("void_chart_exact_centre_and_palette", WFTests::voidChart);
        out.put("end_city_loot_points_onward", WFTests::cityLoot);
        // expansion 1.5: living systems
        out.put("king_tide_one_cycle_in_eight", WFTests::kingTide);
        out.put("world_events_are_scheduled_deterministically", WFTests::eventSchedule);
        out.put("hoard_standing_and_couriers", WFTests::couriers);
        out.put("driftsilt_seeds_pale_loam", WFTests::driftsilt);
        out.put("gravity_plate_and_tide_bell", WFTests::plateAndBell);
        out.put("lost_things_and_fishing_tables", WFTests::lostThings);
        out.put("dusk_skates_only_while_migrating", WFTests::duskSkates);
        out.put("eclipse_memories_are_harmless", WFTests::eclipseMemory);
    }

    static int maxTicks(String name) {
        return switch (name) {
            case "stillwater_turns_water_to_stillstone" -> 200;
            case "egg_cradle_ritual_stages" -> 400;
            default -> 100;
        };
    }

    static boolean endDimension(String name) {
        return name.equals("sundering_never_touches_player_blocks") || name.equals("void_anchor_capsule");
    }

    // ------------------------------------------------------------------
    private static void tideCycleMath(GameTestHelper h) {
        TideCycle c = TideCycle.DEFAULT;
        h.assertTrue(c.period() == 26 * 1200, "full cycle must be 26 minutes");
        h.assertTrue(c.phase(0) == TideCycle.Phase.LOW && c.height(0) == 0, "starts at Low Tide Y0");
        h.assertTrue(c.phase(10 * 1200) == TideCycle.Phase.RISING, "rising after 10 min");
        h.assertTrue(Math.abs(c.height(13 * 1200) - 36) < 1e-6, "high tide at Y36");
        h.assertTrue(c.isStirring(8 * 1200 + 1) && !c.isStirring(7 * 1200), "stirs 2 minutes before rising");
        h.assertTrue(c.redstoneSignal(14 * 1200) == 15 && c.redstoneSignal(0) == 0, "gauge 0..15");
        double mid = c.height(10 * 1200 + 1800);
        h.assertTrue(mid > 17 && mid < 19, "eased midpoint near 18");
        h.assertTrue(c.calmed().highY() == 20, "the Still Throne calms high tide to Y20");
        h.succeed();
    }

    private static void stillwaterWater(GameTestHelper h) {
        BlockPos water = new BlockPos(2, 2, 2);
        h.setBlock(water.below(), Blocks.STONE);
        h.setBlock(water.east().below(), Blocks.STONE);
        h.setBlock(water, Blocks.WATER);
        h.setBlock(water.east(), WFFluids.STILLWATER.get().defaultFluidState().createLegacyBlock());
        h.succeedWhen(() -> h.assertTrue(h.getBlockState(water).is(WFBlocks.STILLSTONE.get()) || h.getBlockState(water.east()).is(WFBlocks.STILLSTONE.get()),
            "water meeting Stillwater should become Stillstone"));
    }

    private static void hoardBarter(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 2, 1);
        h.setBlock(pos, WFBlocks.HOARD_CACHE.get());
        HoardCacheBlockEntity cache = h.getBlockEntity(pos, HoardCacheBlockEntity.class);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        ItemStack grass = new ItemStack(Items.GRASS_BLOCK, 32);
        HoardCacheBlockEntity.InsertResult r = cache.insert(h.getLevel(), player, grass);
        h.assertTrue(r.accepted() && r.barters() == 1, "32 grass (value 1 each) = one barter");
        h.assertTrue(cache.storedValue() == 0 && grass.isEmpty(), "stack consumed, value spent");
        ItemStack iron = new ItemStack(Items.IRON_ORE, 3);
        cache.insert(h.getLevel(), player, iron);
        h.assertTrue(cache.storedValue() == 18, "iron ore is worth 6");
        h.assertTrue(cache.recentlyBartered(h.getLevel().getGameTime()), "recent barter keeps the Hoard friendly");
        h.succeed();
    }

    private static void hoardRefuses(GameTestHelper h) {
        h.assertTrue(HoardCacheBlockEntity.valueOf(new ItemStack(Items.DIAMOND)) == 0, "no diamonds");
        h.assertTrue(HoardCacheBlockEntity.valueOf(new ItemStack(Items.EMERALD)) == 0, "no emeralds");
        h.assertTrue(HoardCacheBlockEntity.valueOf(new ItemStack(Items.END_STONE)) == 0, "no End blocks");
        h.assertTrue(HoardCacheBlockEntity.valueOf(new ItemStack(Items.GOLD_ORE)) == 10, "gold ore is worth 10");
        h.assertTrue(HoardCacheBlockEntity.valueOf(new ItemStack(Items.POPPY)) == 2, "flowers are worth 2");
        h.succeed();
    }

    private static void vaultClaims(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 2, 1);
        h.setBlock(pos, WFBlocks.SPIRE_VAULT.get());
        PerPlayerLootBlockEntity vault = h.getBlockEntity(pos, PerPlayerLootBlockEntity.class);
        ServerPlayer a = h.makeMockServerPlayerInLevel();
        vault.configure(vault.lootTable(), true);
        h.assertTrue(vault.claim(a).isEmpty() && !vault.hasClaimed(a.getUUID()), "a sealed vault gives nothing");
        vault.unseal();
        vault.claim(a);
        h.assertTrue(vault.hasClaimed(a.getUUID()), "first claim recorded");
        h.assertTrue(vault.claim(a).isEmpty(), "a second claim by the same player gives nothing");
        h.assertTrue(vault.claimCount() == 1, "exactly one claim");
        h.succeed();
    }

    private static void reassemblySkips(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos a = h.absolutePos(new BlockPos(1, 2, 1)), b = h.absolutePos(new BlockPos(2, 2, 1)), occupied = h.absolutePos(new BlockPos(3, 2, 1));
        h.setBlock(new BlockPos(3, 2, 1), Blocks.DIRT);
        BlockPos vaultPos = new BlockPos(1, 2, 3);
        h.setBlock(vaultPos, WFBlocks.SPIRE_VAULT.get());
        PerPlayerLootBlockEntity vault = h.getBlockEntity(vaultPos, PerPlayerLootBlockEntity.class);
        vault.configure(vault.lootTable(), true);
        ReassemblyPlan plan = new ReassemblyPlan(List.of(new ReassemblyPlan.Target(a, Blocks.PURPUR_BLOCK.defaultBlockState()),
            new ReassemblyPlan.Target(b, Blocks.PURPUR_BLOCK.defaultBlockState()), new ReassemblyPlan.Target(occupied, Blocks.PURPUR_BLOCK.defaultBlockState())),
            List.of(), List.of(), Optional.of(h.absolutePos(vaultPos)), List.of(a, b, occupied));
        Reassembly.start(level, h.absolutePos(new BlockPos(0, 2, 0)), plan, h.makeMockServerPlayerInLevel());
        Reassembly.finishAll();
        h.assertTrue(level.getBlockState(a).is(Blocks.PURPUR_BLOCK) && level.getBlockState(b).is(Blocks.PURPUR_BLOCK), "missing blocks restored");
        h.assertTrue(level.getBlockState(occupied).is(Blocks.DIRT), "occupied position must never be overwritten");
        h.assertTrue(!vault.sealed(), "a complete shell unseals the vault");
        h.succeed();
    }

    private static void sunderingSafety(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos rel = new BlockPos(2, 2, 2);
        BlockPos abs = h.absolutePos(rel);
        h.setBlock(rel, Blocks.OAK_PLANKS);
        PlayerPlaced.mark(level, abs, true);
        h.assertTrue(!PlayerPlaced.tracked(level, abs) || PlayerPlaced.isPlayerPlaced(level, abs), "tracking near the origin");
        BlockQueue q = new BlockQueue(level, 10);
        q.set(abs, Blocks.AIR.defaultBlockState());
        q.set(abs.east(), Blocks.PURPUR_BLOCK.defaultBlockState());
        q.runAll();
        if (PlayerPlaced.tracked(level, abs)) {
            h.assertTrue(level.getBlockState(abs).is(Blocks.OAK_PLANKS), "player block survives the Sundering");
        }
        h.assertTrue(level.getBlockState(abs.east()).is(Blocks.PURPUR_BLOCK), "other edits still apply");
        PlayerPlaced.mark(level, abs, false);
        h.succeed();
    }

    private static void elytraUpgrade(GameTestHelper h) {
        ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> key = ResourceKey.create(Registries.RECIPE,
            Identifier.fromNamespaceAndPath("worldsfall", "elytra_gale_upgrade"));
        Optional<RecipeHolder<?>> holder = h.getLevel().getServer().getRecipeManager().byKey(key);
        h.assertTrue(holder.isPresent() && holder.get().value() instanceof SmithingRecipe, "gale smithing recipe loaded");
        SmithingRecipe recipe = (SmithingRecipe) holder.get().value();
        SmithingRecipeInput input = new SmithingRecipeInput(new ItemStack(WFItems.GALE_UPGRADE_TEMPLATE.get()), new ItemStack(Items.ELYTRA),
            new ItemStack(WFItems.SKATE_MEMBRANE.get()));
        h.assertTrue(recipe.matches(input, h.getLevel()), "recipe matches its inputs");
        ItemStack out = recipe.assemble(input);
        h.assertTrue(out.is(Items.ELYTRA) && ElytraActionPayload.upgradeOf(out) == ElytraUpgrade.GALE, "result is an elytra with the Gale upgrade");
        var equippable = out.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        h.assertTrue(equippable != null && equippable.assetId().isPresent() && equippable.assetId().get().equals(ElytraUpgrade.GALE.asset()),
            "the upgrade swaps the wing look to worldsfall:elytra_gale");
        h.assertTrue(equippable.slot() == net.minecraft.world.entity.EquipmentSlot.CHEST && out.has(net.minecraft.core.component.DataComponents.GLIDER),
            "still a chest-slot glider");
        h.succeed();
    }

    private static void satchelRestrictions(GameTestHelper h) {
        h.assertTrue(!SatchelMenu.allowed(new ItemStack(Items.SHULKER_BOX)), "no shulker boxes");
        h.assertTrue(!SatchelMenu.allowed(new ItemStack(WFItems.SHULKER_SATCHEL.get())), "no nested satchels");
        h.assertTrue(SatchelMenu.allowed(new ItemStack(Items.DIRT)), "ordinary items allowed");
        ItemStack satchel = new ItemStack(WFItems.SHULKER_SATCHEL.get());
        h.assertTrue(satchel.getOrDefault(WFComponents.SATCHEL_SIZE.get(), 0) == 9, "starts with 9 slots");
        h.succeed();
    }

    private static void voidAnchor(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        BlockPos at = h.absolutePos(new BlockPos(2, 3, 2));
        player.teleportTo(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(), 0, 0, true);
        player.getInventory().setItem(0, new ItemStack(WFItems.VOID_ANCHOR.get()));
        player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 7));
        player.getInventory().setItem(2, new ItemStack(Items.ENDER_PEARL, 3));
        boolean fired = VoidAnchorHandler.onDeath(player, h.getLevel().damageSources().source(DamageTypes.FELL_OUT_OF_WORLD));
        h.assertTrue(fired, "anchor triggers on a void death");
        h.assertTrue(player.getInventory().getItem(1).isEmpty() && player.getInventory().getItem(0).isEmpty(), "items moved, anchor consumed");
        List<VoidCapsule> capsules = new java.util.ArrayList<>();
        for (var e : player.level().getAllEntities()) {
            if (e instanceof VoidCapsule vc && vc.owner().equals(player.getUUID())) {
                capsules.add(vc);
            }
        }
        h.assertTrue(capsules.size() == 1, "exactly one capsule, found " + capsules.size() + " in " + player.level().dimension().identifier());
        VoidCapsule capsule = capsules.get(0);
        h.assertTrue(capsule.items().size() == 2 && capsule.owner().equals(player.getUUID()), "capsule holds the stacks for the owner");
        capsule.discard();
        h.succeed();
    }

    private static void paleLoam(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 2, 1);
        h.setBlock(pos, WFBlocks.PALE_LOAM.get());
        h.setBlock(pos.above(), Blocks.STONE);
        h.getBlockState(pos).randomTick(h.getLevel(), h.absolutePos(pos), h.getLevel().getRandom());
        h.assertBlockState(pos, s -> s.is(Blocks.END_STONE), s -> net.minecraft.network.chat.Component.literal("expected end stone, got " + s));
        h.succeed();
    }

    private static void chorusPod(GameTestHelper h) {
        BlockPos plant = new BlockPos(1, 2, 1);
        h.setBlock(plant.below(), Blocks.END_STONE);
        h.setBlock(plant, Blocks.CHORUS_PLANT);
        BlockPos pod = plant.east();
        h.setBlock(pod, WFBlocks.CHORUS_POD.get().defaultBlockState().setValue(ChorusPodBlock.FACING, Direction.WEST).setValue(ChorusPodBlock.AGE, 2));
        ChorusPodBlock.drop(h.getLevel(), h.absolutePos(pod), h.getBlockState(pod), true);
        h.assertBlockState(pod, s -> s.is(WFBlocks.CHORUS_POD.get()) && s.getValue(ChorusPodBlock.AGE) == 0,
            s -> net.minecraft.network.chat.Component.literal("pod should regrow from stage 0"));
        h.assertTrue(!h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(pod)).inflate(2),
            e -> e.getItem().is(WFItems.CHORUS_POD.get())).isEmpty(), "a Chorus Pod item dropped");
        h.succeed();
    }

    private static void erosion(GameTestHelper h) {
        h.assertTrue(VoidErosionEffect.damagePerSecond(0, 4) == 1, "1 HP/s at first");
        h.assertTrue(VoidErosionEffect.damagePerSecond(60, 4) == 2, "+1 after 3 s");
        h.assertTrue(VoidErosionEffect.damagePerSecond(1000, 4) == 4, "capped at 4 HP/s");
        h.succeed();
    }

    private static void eggCradle(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 2, 1);
        h.setBlock(pos, WFBlocks.EGG_CRADLE.get());
        EggCradleBlockEntity cradle = h.getBlockEntity(pos, EggCradleBlockEntity.class);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        h.assertTrue(cradle.placeDragonEgg() && cradle.stage() == EggCradleBlockEntity.Stage.ATTUNING, "egg placed, attuning");
        cradle.debugSetProgress(EggCradleBlockEntity.ATTUNE_TICKS);
        h.runAfterDelay(3, () -> {
            EggCradleBlockEntity live = h.getBlockEntity(pos, EggCradleBlockEntity.class);
            h.assertTrue(live == cradle, "same block entity instance");
            h.assertTrue(cradle.stage() == EggCradleBlockEntity.Stage.NOURISHING, "posts lit, egg hungry (stage " + cradle.stage() + ", progress "
                + cradle.progress() + ")");
            for (int i = 0; i < EggCradleBlockEntity.GLANDS_REQUIRED; i++) {
                cradle.debugSkipCooldown();
                cradle.feed(player);
            }
            h.assertTrue(cradle.glands() == 8 && cradle.stage() == EggCradleBlockEntity.Stage.VIGIL, "8 glands -> vigil");
            cradle.debugSkipCooldown();
            h.assertTrue(cradle.feed(player) == EggCradleBlockEntity.FeedResult.WRONG_STAGE, "no more feeding during the vigil");
            cradle.debugSetProgress(cradle.vigilRequired());
            h.runAfterDelay(EggCradleBlockEntity.HATCH_ANIM_TICKS + 10, () -> {
                h.assertTrue(cradle.stage() == EggCradleBlockEntity.Stage.EMPTY, "hatched and reset");
                h.assertTrue(!h.getLevel().getEntitiesOfClass(dev.worldsfall.entity.Wyrm.class, new AABB(h.absolutePos(pos)).inflate(4)).isEmpty(),
                    "a Hatchling emerged");
                h.succeed();
            });
        });
    }

    private static void gravityLens(GameTestHelper h) {
        Cow cow = h.spawn(net.minecraft.world.entity.EntityTypes.COW, new BlockPos(2, 2, 2));
        GravityZones.addLens(h.getLevel(), cow.position(), 3.0, 80);
        GravityZones.update(h.getLevel(), cow);
        h.assertTrue(cow.getAttributeValue(Attributes.GRAVITY) < 0, "gravity reversed inside a Lens");
        h.succeed();
    }

    private static void bossScaling(GameTestHelper h) {
        h.assertTrue(TheUnmade.scaledHealth(1, 720) == 720 && TheUnmade.scaledHealth(2, 720) == 1080, "1-2 players");
        h.assertTrue(Math.round(TheUnmade.scaledHealth(3, 720)) == 1368 && Math.round(TheUnmade.scaledHealth(6, 720)) == 1584, "3 and 4+ (cap)");
        h.succeed();
    }

    private static void echoScramble(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, new ItemStack(Items.STICK, i + 1));
        }
        Echo.scramble(player);
        int moved = 0;
        Set<Integer> counts = new HashSet<>();
        for (int i = 0; i < 9; i++) {
            int c = player.getInventory().getItem(i).getCount();
            counts.add(c);
            if (c != i + 1) {
                moved++;
            }
        }
        h.assertTrue(moved == 2 && counts.size() == 9, "exactly two hotbar slots swapped, nothing lost");
        h.succeed();
    }

    private static void tablets(GameTestHelper h) {
        for (int i = 1; i <= EchoTabletItem.FRAGMENTS; i++) {
            h.assertTrue(EchoTabletItem.index(EchoTabletItem.of(i)) == i, "tablet " + i);
        }
        h.succeed();
    }

    private static void dataIntegrity(GameTestHelper h) {
        var server = h.getLevel().getServer();
        long recipes = server.getRecipeManager().getRecipes().stream().filter(r -> r.id().identifier().getNamespace().equals("worldsfall")).count();
        h.assertTrue(recipes >= 80, "worldsfall recipes loaded: " + recipes);
        long advancements = server.getAdvancements().getAllAdvancements().stream().filter(a -> a.id().getNamespace().equals("worldsfall")).count();
        h.assertTrue(advancements == EXPECTED_ADVANCEMENTS, EXPECTED_ADVANCEMENTS + " advancements: " + advancements);
        long loot = server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds()
            .filter(k -> k.identifier().getNamespace().equals("worldsfall")).count();
        h.assertTrue(loot >= 60, "worldsfall loot tables loaded: " + loot);
        for (var block : WFBlocks.itemBlocks()) {
            h.assertTrue(block.get().asItem() != Items.AIR, "block item for " + block.getId());
        }
        h.assertTrue(WFEntities.VOID_SKATE.get().create(h.getLevel(), EntitySpawnReason.COMMAND) != null, "entities construct");
        h.succeed();
    }

    private static void stillwaterCauldron(GameTestHelper h) {
        BlockPos rel = new BlockPos(1, 2, 1);
        h.setBlock(rel, Blocks.CAULDRON);
        BlockPos pos = h.absolutePos(rel);
        ServerLevel level = h.getLevel();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        ItemStack bucket = new ItemStack(WFItems.STILLWATER_BUCKET.get());
        player.setItemInHand(hand, bucket);
        net.minecraft.core.cauldron.CauldronInteractions.EMPTY.get(bucket).interact(level.getBlockState(pos), level, pos, player, hand, bucket);
        var state = level.getBlockState(pos);
        h.assertTrue(state.is(WFBlocks.STILLWATER_CAULDRON.get()) && state.getValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL) == 3,
            "a Stillwater bucket fills an empty cauldron");
        h.assertTrue(player.getItemInHand(hand).is(Items.BUCKET), "the bucket comes back empty");
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        player.setItemInHand(hand, bottle);
        dev.worldsfall.fluid.StillwaterCauldron.INTERACTIONS.get(bottle).interact(state, level, pos, player, hand, bottle);
        h.assertTrue(player.getItemInHand(hand).is(WFItems.BOTTLE_OF_STILLWATER.get()), "a glass bottle draws a Bottle of Stillwater");
        state = level.getBlockState(pos);
        h.assertTrue(state.getValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL) == 2, "drawing lowers the level");
        ItemStack back = player.getItemInHand(hand);
        dev.worldsfall.fluid.StillwaterCauldron.INTERACTIONS.get(back).interact(state, level, pos, player, hand, back);
        h.assertTrue(level.getBlockState(pos).getValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL) == 3
            && player.getItemInHand(hand).is(Items.GLASS_BOTTLE), "pouring a bottle back raises the level");
        h.succeed();
    }

    private static void wyrmShoulder(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("id", WFEntities.WYRM.getId().toString());
        player.setOnGround(true);
        h.assertTrue(player.setEntityOnShoulder(tag), "a Wyrm can take the left shoulder");
        dev.worldsfall.world.WFEvents.syncShoulders(player);
        h.assertTrue(player.getData(dev.worldsfall.registry.WFAttachments.WYRM_SHOULDERS) == 1, "left-shoulder bit synced");
        ServerPlayer other = h.makeMockServerPlayerInLevel();
        dev.worldsfall.world.WFEvents.syncShoulders(other);
        h.assertTrue(other.getData(dev.worldsfall.registry.WFAttachments.WYRM_SHOULDERS) == 0, "no Wyrm, no bits");
        h.succeed();
    }

    private static void basinAndTrims(GameTestHelper h) {
        double r = 12;
        h.assertTrue(dev.worldsfall.world.gen.structure.StillwaterBasinPiece.inShape(0, 0, r), "heart contains its centre");
        h.assertTrue(dev.worldsfall.world.gen.structure.StillwaterBasinPiece.inShape(r * 0.8, 0, r), "heart reaches its point toward the spill");
        h.assertTrue(!dev.worldsfall.world.gen.structure.StillwaterBasinPiece.inShape(r * 0.8, r * 0.6, r), "but narrows beside the point");
        h.assertTrue(dev.worldsfall.world.gen.structure.StillwaterBasinPiece.inShape(-r * 0.5, r * 0.4, r)
            && dev.worldsfall.world.gen.structure.StillwaterBasinPiece.inShape(-r * 0.5, -r * 0.4, r), "two lobes behind");
        h.assertTrue(!dev.worldsfall.world.gen.structure.StillwaterBasinPiece.inShape(-r * 0.55, 0, r), "with a cusp between them");
        var access = h.getLevel().registryAccess();
        for (String m : List.of("lumen", "still", "frill")) {
            h.assertTrue(access.lookupOrThrow(Registries.TRIM_MATERIAL).get(ResourceKey.create(Registries.TRIM_MATERIAL,
                Identifier.fromNamespaceAndPath("worldsfall", m))).isPresent(), "trim material " + m);
        }
        for (String p : List.of("spire", "drift", "undersong")) {
            h.assertTrue(access.lookupOrThrow(Registries.TRIM_PATTERN).get(ResourceKey.create(Registries.TRIM_PATTERN,
                Identifier.fromNamespaceAndPath("worldsfall", p))).isPresent(), "trim pattern " + p);
        }
        h.assertTrue(dev.worldsfall.world.VoidLensScan.wrecksNear(h.getLevel(), 0, 0).isEmpty(), "no Driftwrecks in the test world");
        h.succeed();
    }

    // ------------------------------------------------------------------ expansion 1.2: the Warden Cycle
    private static void pactWeights(GameTestHelper h) {
        var all = java.util.EnumSet.allOf(dev.worldsfall.warden.Pact.class);
        h.assertTrue(dev.worldsfall.warden.Pact.weight(all) == 12, "weight is capped at 12");
        h.assertTrue(dev.worldsfall.warden.Pact.weight(java.util.EnumSet.of(dev.worldsfall.warden.Pact.TIDE, dev.worldsfall.warden.Pact.ECHOES)) == 3,
            "tide 2 + echoes 1");
        h.assertTrue(dev.worldsfall.warden.Pact.tier(0) == 0 && dev.worldsfall.warden.Pact.tier(3) == 1 && dev.worldsfall.warden.Pact.tier(4) == 2
            && dev.worldsfall.warden.Pact.tier(11) == 3 && dev.worldsfall.warden.Pact.tier(12) == 4, "reward tiers 1-3 / 4-7 / 8-11 / 12");
        var r = net.minecraft.util.RandomSource.create(7);
        for (int k = 0; k < 50; k++) {
            int t1 = dev.worldsfall.warden.WardenRewards.scales(1, r), t2 = dev.worldsfall.warden.WardenRewards.scales(3, r);
            h.assertTrue(t1 >= 2 && t1 <= 4 && t2 >= 4 && t2 <= 8, "scale counts per tier");
        }
        h.succeed();
    }

    private static void crystalKinds(GameTestHelper h) {
        var crystal = new net.minecraft.world.entity.boss.enderdragon.EndCrystal(h.getLevel(), 0, 0, 0);
        h.assertTrue(dev.worldsfall.warden.WardenFight.pactOf(crystal) == null && !dev.worldsfall.warden.WardenFight.isStillness(crystal),
            "a vanilla crystal carries nothing");
        crystal.setData(dev.worldsfall.registry.WFAttachments.CRYSTAL_KIND, WFItems.PACT_CRYSTAL_DEPTHS.get().kind());
        h.assertTrue(dev.worldsfall.warden.WardenFight.pactOf(crystal) == dev.worldsfall.warden.Pact.DEPTHS, "a Pact Crystal names its pact");
        crystal.setData(dev.worldsfall.registry.WFAttachments.CRYSTAL_KIND, WFItems.STILLNESS_CRYSTAL.get().kind());
        h.assertTrue(dev.worldsfall.warden.WardenFight.isStillness(crystal), "a Stillness Crystal is recognised");
        h.succeed();
    }

    private static void releaseGate(GameTestHelper h) {
        ServerLevel end = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.END);
        h.assertTrue(end != null, "the End exists");
        h.assertTrue(!dev.worldsfall.warden.WardenRelease.ready(end), "no Release before The Unmade falls and the Heart sits in the throne");
        h.succeed();
    }

    private static void elytraStyles(GameTestHelper h) {
        var server = h.getLevel().getServer();
        SmithingRecipe scaled = (SmithingRecipe) server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
            Identifier.fromNamespaceAndPath("worldsfall", "elytra_scaled"))).orElseThrow().value();
        SmithingRecipe gale = (SmithingRecipe) server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
            Identifier.fromNamespaceAndPath("worldsfall", "elytra_gale_upgrade"))).orElseThrow().value();
        ItemStack once = scaled.assemble(new SmithingRecipeInput(new ItemStack(WFItems.DRAGON_SCALE.get()), new ItemStack(Items.ELYTRA),
            new ItemStack(WFItems.DRAGON_SCALE.get())));
        ItemStack twice = gale.assemble(new SmithingRecipeInput(new ItemStack(WFItems.GALE_UPGRADE_TEMPLATE.get()), once,
            new ItemStack(WFItems.SKATE_MEMBRANE.get())));
        var eq = twice.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        h.assertTrue(eq != null && eq.assetId().orElseThrow().identifier().getPath().equals("elytra_gale_scaled"), "scales stack with the Gale look");
        h.assertTrue(ElytraActionPayload.upgradeOf(twice) == ElytraUpgrade.GALE && Boolean.TRUE.equals(twice.get(WFComponents.ELYTRA_SCALED.get())),
            "the upgrade and the overlay are both kept");
        h.succeed();
    }

    private static void wyrmBarding(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var wyrm = WFEntities.WYRM.get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        h.assertTrue(wyrm != null, "wyrm constructs");
        BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
        wyrm.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        h.getLevel().addFreshEntity(wyrm);
        wyrm.tame(player);
        wyrm.debugSetStage(dev.worldsfall.entity.Wyrm.Stage.DRAKE);
        float before = wyrm.getMaxHealth();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(WFItems.WYRM_BARDING.get()));
        wyrm.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(wyrm.hasLook(dev.worldsfall.entity.Wyrm.LOOK_BARDED) && Math.abs(wyrm.getMaxHealth() - before * 2) < 0.01f,
            "barding doubles a Drake's health: " + before + " -> " + wyrm.getMaxHealth());
        wyrm.setTemperamentFromWorld(h.getLevel());
        h.assertTrue(wyrm.temperament() != dev.worldsfall.entity.Wyrm.Temperament.NONE, "a temperament is set at hatching");
        wyrm.discard();
        h.succeed();
    }

    private static void causewayPlan(GameTestHelper h) {
        var a = dev.worldsfall.gap.Causeway.plan(net.minecraft.util.RandomSource.create(42));
        var b = dev.worldsfall.gap.Causeway.plan(net.minecraft.util.RandomSource.create(42));
        h.assertTrue(a.equals(b), "the plan is deterministic per seed");
        for (long seed = 0; seed < 40; seed++) {
            var plan = dev.worldsfall.gap.Causeway.plan(net.minecraft.util.RandomSource.create(seed));
            h.assertTrue(plan.size() >= 12 && plan.size() <= 30, "segment count " + plan.size());
            h.assertTrue(plan.getFirst().r0() == dev.worldsfall.gap.Causeway.START_R, "starts at the main island's rim");
            h.assertTrue(plan.getLast().r1() >= dev.worldsfall.gap.Causeway.END_R - 50, "reaches the outer islands: " + plan.getLast().r1());
            h.assertTrue(plan.stream().filter(dev.worldsfall.gap.CausewayData.Segment::waystation).count() == 3, "three waystations");
            for (int k = 0; k + 1 < plan.size(); k++) {
                int gap = plan.get(k + 1).r0() - plan.get(k).r1();
                h.assertTrue(gap >= 4 && gap <= 50, "a crossable gap between segments: " + gap);
                h.assertTrue(plan.get(k).r1() - plan.get(k).r0() >= 10, "segments are at least 10 long");
                h.assertTrue(Math.abs(plan.get(k).y() - 64) <= 5, "deck height stays near Y 64");
            }
        }
        h.succeed();
    }

    private static void buoyOpenAir(GameTestHelper h) {
        BlockPos at = h.absolutePos(new BlockPos(1, 6, 1));
        h.assertTrue(dev.worldsfall.gap.DriftBuoyBlock.openAir(h.getLevel(), at), "open air above the test floor");
        h.getLevel().setBlockAndUpdate(at.offset(2, 0, 0), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        h.assertTrue(!dev.worldsfall.gap.DriftBuoyBlock.openAir(h.getLevel(), at), "a block within three refuses the buoy");
        h.getLevel().setBlockAndUpdate(at.offset(2, 0, 0), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        h.succeed();
    }

    private static void pillarGlyphs(GameTestHelper h) {
        ServerLevel end = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.END);
        h.assertTrue(end != null, "the End exists");
        var marks = dev.worldsfall.gap.PillarGlyphs.marks(end);
        h.assertTrue(marks.size() == dev.worldsfall.gap.PillarGlyphs.WORDS, "one glyph per pillar: " + marks.size());
        double prev = -10;
        for (var m : marks) {
            double ang = Math.atan2(m.top().getZ(), m.top().getX());
            ang = ang < -1e-6 ? ang + Math.PI * 2 : ang;
            h.assertTrue(ang > prev, "glyphs run around the ring in order");
            prev = ang;
        }
        h.succeed();
    }

    private static void latticeNodes(GameTestHelper h) {
        var data = new dev.worldsfall.nav.LatticeData();
        BlockPos a = new BlockPos(100, 75, 0), b = new BlockPos(-96, 75, 12);
        java.util.UUID p = java.util.UUID.randomUUID(), q = java.util.UUID.randomUUID();
        h.assertTrue(data.add(new dev.worldsfall.nav.LatticeData.Node(a, dev.worldsfall.nav.LatticeData.GATEWAY, "A")), "a node is added");
        h.assertTrue(!data.add(new dev.worldsfall.nav.LatticeData.Node(a, dev.worldsfall.nav.LatticeData.GATEWAY, "A")), "no duplicate nodes");
        data.add(new dev.worldsfall.nav.LatticeData.Node(b, dev.worldsfall.nav.LatticeData.MOORING, "B"));
        h.assertTrue(!data.learn(p, new BlockPos(1, 2, 3)), "unknown positions cannot be learned");
        data.learn(p, a);
        data.learn(p, b);
        data.learn(q, b);
        h.assertTrue(data.knownBy(p).size() == 2 && data.knownBy(q).size() == 1, "each player only travels to the nodes they know");
        data.remove(b);
        h.assertTrue(data.size() == 1 && data.knownBy(q).isEmpty() && !data.knows(p, b), "a removed node is forgotten by everyone");
        h.assertTrue(dev.worldsfall.nav.Lattice.nameFor(a, 0).equals(dev.worldsfall.nav.Lattice.nameFor(a, 0))
            && dev.worldsfall.nav.Lattice.nameFor(b, 1).endsWith("Mooring"), "names are stable; moorings are named as such");
        h.succeed();
    }

    private static void archivistOffers(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        var archivist = WFEntities.SHULKER_ARCHIVIST.get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        h.assertTrue(archivist != null, "the archivist constructs");
        BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
        archivist.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        h.getLevel().addFreshEntity(archivist);
        h.assertTrue(archivist.targetSelector.getAvailableGoals().isEmpty(), "an Archivist never picks a target");
        h.assertTrue(dev.worldsfall.city.ShulkerArchivist.missingTablet(Set.of(1, 2, 3)) == 4
            && dev.worldsfall.city.ShulkerArchivist.missingTablet(new HashSet<>(java.util.stream.IntStream.rangeClosed(1, 12).boxed().toList())) == 0,
            "the missing tablet is the lowest unread one");
        var offers = archivist.offersFor(player);
        h.assertTrue(offers.size() == 4, "chart, shell, glyph and missing tablet: " + offers.size());
        h.assertTrue(offers.getFirst().getResult().is(WFItems.CITY_CHART.get()) && offers.getFirst().getCostA().is(WFItems.ECHO_TABLET.get()),
            "an Echo Tablet buys a City Chart");
        h.assertTrue(offers.get(2).getResult().getItem() != WFItems.PACT_GLYPH_DEPTHS.get(), "the Pact of Depths is never traded");
        h.assertTrue(WFEntities.SHULKER_ARCHIVIST.get().isAllowedInPeaceful(), "Archivists stay in Peaceful");
        archivist.discard();
        h.succeed();
    }

    private static void sinkingPull(GameTestHelper h) {
        Cow cow = h.spawn(net.minecraft.world.entity.EntityTypes.COW, new BlockPos(1, 8, 1));
        cow.setDeltaMovement(0, 0.4, 0);
        cow.setOnGround(false);
        dev.worldsfall.effect.SinkingEffect.pull(cow);
        h.assertTrue(cow.getDeltaMovement().y <= -dev.worldsfall.effect.SinkingEffect.PULL + 1e-6, "sinking pulls an airborne mob down");
        h.assertTrue(dev.worldsfall.effect.SinkingEffect.BULLET_TICKS * dev.worldsfall.effect.SinkingEffect.PULL >= 4.0,
            "a Sinking Bullet pulls about four blocks");
        cow.addEffect(new net.minecraft.world.effect.MobEffectInstance(dev.worldsfall.registry.WFEffects.STEADINESS, 100));
        cow.addEffect(new net.minecraft.world.effect.MobEffectInstance(dev.worldsfall.registry.WFEffects.SINKING, 20));
        h.assertTrue(!cow.hasEffect(dev.worldsfall.registry.WFEffects.SINKING), "Steadiness counters Sinking");
        cow.discard();
        h.succeed();
    }

    private static void voidChart(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ItemStack chart = dev.worldsfall.nav.Charts.create(level, 1237, -411, 2, false, "void");
        var data = net.minecraft.world.item.MapItem.getSavedData(chart, level);
        h.assertTrue(data != null && data.centerX == 1237 && data.centerZ == -411 && data.scale == 2, "a chart is centred exactly where it was drawn");
        h.assertTrue(chart.getItem() instanceof dev.worldsfall.nav.VoidChartItem && "void".equals(dev.worldsfall.nav.VoidChartItem.kind(chart)),
            "a filled Void Chart");
        byte v = dev.worldsfall.nav.Charts.colour(level, 3, 5, level.getMinY(), null, level.getMinY());
        h.assertTrue(v == net.minecraft.world.level.material.MapColor.COLOR_BLACK.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.LOW)
            || v == net.minecraft.world.level.material.MapColor.COLOR_PURPLE.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.HIGH)
            || v == net.minecraft.world.level.material.MapColor.COLOR_BLUE.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.LOW),
            "the void is a dark star field");
        byte contour = dev.worldsfall.nav.Charts.colour(level, 0, 0, 70, Blocks.END_STONE.defaultBlockState(), 64);
        byte flat = dev.worldsfall.nav.Charts.colour(level, 0, 0, 70, Blocks.END_STONE.defaultBlockState(), 70);
        h.assertTrue(contour != flat, "a height band change draws a contour line");
        h.succeed();
    }

    private static void cityLoot(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var table = server.reloadableRegistries().getLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.END_CITY_TREASURE);
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel())
            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.ZERO)
            .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        int ours = 0;
        for (int k = 0; k < 120; k++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("worldsfall")) {
                    ours++;
                }
            }
        }
        h.assertTrue(ours > 0, "End City chests gain Worldsfall loot through the global loot modifier");
        h.succeed();
    }

    private static void kingTide(GameTestHelper h) {
        long seed = 1234567L;
        int kings = 0;
        for (long i = 1; i <= 800; i++) {
            kings += dev.worldsfall.life.KingTide.isKing(seed, i) ? 1 : 0;
        }
        h.assertTrue(kings > 60 && kings < 140, "about one cycle in eight is a King Tide: " + kings);
        h.assertTrue(!dev.worldsfall.life.KingTide.isKing(seed, 0), "never the very first cycle");
        var base = TideCycle.DEFAULT;
        long king = -1;
        for (long i = 1; i < 200 && king < 0; i++) {
            if (dev.worldsfall.life.KingTide.isKing(h.getLevel().getServer().overworld().getSeed(), i)) {
                king = i;
            }
        }
        h.assertTrue(king > 0, "a King cycle exists");
        long t = king * base.period() + 10;
        var server = h.getLevel().getServer();
        h.assertTrue(dev.worldsfall.life.KingTide.adjust(server, base, t, false).highY() == dev.worldsfall.life.KingTide.HIGH_Y,
            "a King Tide's High reaches Y 60");
        h.assertTrue(dev.worldsfall.life.KingTide.adjust(server, base, t, true).highY() == base.highY(), "never once the tide is calmed");
        h.succeed();
    }

    private static void eventSchedule(GameTestHelper h) {
        long p = dev.worldsfall.life.WorldEvents.STARFALL_PERIOD;
        for (long i = 0; i < 50; i++) {
            long due = dev.worldsfall.life.WorldEvents.dueAt(99L, "starfall", i, p, 6200);
            h.assertTrue(due >= i * p && due + 6200 + 1200 <= (i + 1) * p, "an event falls inside its own period");
            h.assertTrue(due == dev.worldsfall.life.WorldEvents.dueAt(99L, "starfall", i, p, 6200), "deterministic");
        }
        h.assertTrue(dev.worldsfall.life.WorldEvents.current(h.getLevel().getServer()) == dev.worldsfall.life.WorldEvents.NONE, "no event at rest");
        h.succeed();
    }

    private static void couriers(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        h.assertTrue(dev.worldsfall.life.HoardStanding.get(player) == 0, "standing starts at zero");
        dev.worldsfall.life.HoardStanding.add(player, 500);
        h.assertTrue(dev.worldsfall.life.HoardStanding.get(player) == 100 && dev.worldsfall.life.HoardStanding.luck(player) == 4.0f, "standing caps at 100");
        dev.worldsfall.life.HoardStanding.add(player, -300);
        h.assertTrue(dev.worldsfall.life.HoardStanding.get(player) == -100, "and floors at -100");
        BlockPos a = h.absolutePos(new BlockPos(0, 1, 0)), b = h.absolutePos(new BlockPos(2, 1, 0));
        h.getLevel().setBlockAndUpdate(a, WFBlocks.COURIER_CACHE.get().defaultBlockState());
        h.getLevel().setBlockAndUpdate(b, WFBlocks.COURIER_CACHE.get().defaultBlockState());
        var from = (dev.worldsfall.life.CourierCacheBlockEntity) h.getLevel().getBlockEntity(a);
        var to = (dev.worldsfall.life.CourierCacheBlockEntity) h.getLevel().getBlockEntity(b);
        from.setItem(3, new ItemStack(Items.ENDER_PEARL, 7));
        from.setItem(5, new ItemStack(Items.CHORUS_FRUIT, 2));
        var c = new dev.worldsfall.life.Couriers.Contract(player.getUUID(), a, b, 0, ItemStack.EMPTY);
        var after = dev.worldsfall.life.Couriers.run(h.getLevel(), c, 100);
        h.assertTrue(after != null && after.next() == 100 + dev.worldsfall.life.Couriers.INTERVAL && after.carrying().isEmpty(), "one delivery");
        h.assertTrue(to.countItem(Items.ENDER_PEARL) == 7 && to.countItem(Items.CHORUS_FRUIT) == 0 && from.countItem(Items.CHORUS_FRUIT) == 2,
            "exactly one stack moves per visit");
        h.getLevel().removeBlock(b, false);
        h.assertTrue(dev.worldsfall.life.Couriers.run(h.getLevel(), after, 200) == null, "a contract ends when a cache is gone");
        for (var e : h.getLevel().getEntities((net.minecraft.world.entity.Entity) null, new AABB(a).inflate(8),
            e -> e.entityTags().contains(dev.worldsfall.life.Couriers.TAG))) {
            e.discard();
        }
        h.getLevel().removeBlock(a, false);
        h.succeed();
    }

    private static void driftsilt(GameTestHelper h) {
        BlockPos p = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(p, Blocks.END_STONE.defaultBlockState());
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(WFItems.DRIFTSILT.get(), 2));
        var ctx = new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(p), Direction.UP, p, false));
        WFItems.DRIFTSILT.get().useOn(ctx);
        h.assertTrue(h.getLevel().getBlockState(p).is(WFBlocks.PALE_LOAM.get()), "Driftsilt seeds Pale Loam on open end stone");
        h.assertTrue(player.getMainHandItem().getCount() == 1, "one Driftsilt is used");
        h.succeed();
    }

    private static void plateAndBell(GameTestHelper h) {
        var c = net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 3, 1)));
        h.assertTrue(!dev.worldsfall.world.gravity.GravityZones.inLight(h.getLevel(), c), "no Light Zone before the plate is used");
        dev.worldsfall.world.gravity.GravityZones.addLight(h.getLevel(), c, 3, dev.worldsfall.life.GravityPlateBlock.TICKS);
        h.assertTrue(dev.worldsfall.world.gravity.GravityZones.inLight(h.getLevel(), c.add(2, 0, 0))
            && !dev.worldsfall.world.gravity.GravityZones.inLight(h.getLevel(), c.add(4, 0, 0)), "a Gravity Plate opens a radius-3 Light Zone");
        BlockPos bell = h.absolutePos(new BlockPos(2, 1, 2));
        h.getLevel().setBlockAndUpdate(bell, WFBlocks.TIDE_BELL.get().defaultBlockState().setValue(dev.worldsfall.life.TideBellBlock.POWERED, true));
        h.assertTrue(h.getLevel().getSignal(bell.east(), Direction.EAST) == 15 || h.getLevel().getBestNeighborSignal(bell.east()) == 15,
            "a ringing Tide Bell gives a full redstone signal");
        h.succeed();
    }

    private static void lostThings(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var data = dev.worldsfall.life.LostThings.get(server);
        int before = data.size();
        data.remember(new ItemStack(Items.DIAMOND_SWORD));
        h.assertTrue(data.size() == before + 1, "an item lost to the void is remembered");
        ItemStack back = ItemStack.EMPTY;
        for (int i = 0; i < 200 && !back.is(Items.DIAMOND_SWORD); i++) {
            back = dev.worldsfall.life.LostThings.take(server);
            if (!back.is(Items.DIAMOND_SWORD) && !back.isEmpty()) {
                data.remember(back);
            }
        }
        h.assertTrue(back.is(Items.DIAMOND_SWORD), "and can come back");
        for (var key : List.of(dev.worldsfall.life.TideFishing.TIDE_LOOT, dev.worldsfall.life.TideFishing.STILL_LOOT)) {
            h.assertTrue(server.reloadableRegistries().getLootTable(key) != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "fishing table loads: " + key);
        }
        h.succeed();
    }

    private static void duskSkates(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var skate = WFEntities.VOID_SKATE.get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        h.assertTrue(skate != null, "skate constructs");
        BlockPos at = h.absolutePos(new BlockPos(1, 3, 1));
        skate.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        h.getLevel().addFreshEntity(skate);
        skate.setVariant(1);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(WFItems.CHORUS_POD.get(), 16));
        var result = skate.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(result == net.minecraft.world.InteractionResult.FAIL && player.getMainHandItem().getCount() == 16,
            "a resident Dusk Skate refuses food");
        skate.setMigrationTarget(net.minecraft.world.phys.Vec3.atCenterOf(at).add(700, 0, 0));
        h.assertTrue(skate.isMigrating() && skate.trustNeeded() == 3, "a migrating Skate can be won over, with less patience");
        skate.discard();
        h.succeed();
    }

    private static void eclipseMemory(GameTestHelper h) {
        var echo = WFEntities.ECHO.get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        h.assertTrue(echo != null, "echo constructs");
        echo.makeMemory();
        h.assertTrue(echo.isMemory() && !echo.isDisguised() && echo.hasGlowingTag(), "a memory walks openly and glows");
        h.assertTrue(!echo.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 5), "and cannot be hurt");
        echo.discard();
        h.succeed();
    }
}
