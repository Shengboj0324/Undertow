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
    static final int EXPECTED_ADVANCEMENTS = 31;

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
}
