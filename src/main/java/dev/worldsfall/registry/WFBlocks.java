package dev.worldsfall.registry;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.block.*;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Every block from design doc §6 (natural, functional, building sets) and §7 (Stillwater). */
public final class WFBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Worldsfall.MODID);
    /** Blocks that get a plain BlockItem, in creative-tab order (§18.9: by biome then function). */
    private static final List<DeferredBlock<? extends Block>> ITEM_BLOCKS = new ArrayList<>();
    /** Building-set families: base id -> [slab, stairs, wall?]. Used by the generators and tests. */
    public static final Map<String, List<DeferredBlock<? extends Block>>> FAMILIES = new LinkedHashMap<>();

    // ---- sound types (§19.3)
    public static final SoundType PALE_LOAM_SOUND = sound("pale_loam", 1.0f, 1.0f);
    public static final SoundType STILLSTONE_SOUND = sound("stillstone", 1.0f, 1.0f);
    public static final SoundType MIRROR_SHARD_SOUND = sound("mirror_shard", 1.0f, 1.0f);
    public static final SoundType LUMEN_SOUND = sound("lumen_crystal", 1.0f, 1.0f);
    public static final SoundType VOIDROOT_SOUND = sound("voidroot", 1.0f, 1.0f);
    public static final SoundType GLOWBULB_SOUND = sound("glowbulb", 1.0f, 1.0f);
    public static final SoundType DRIFTSTONE_SOUND = sound("driftstone", 1.0f, 1.0f);
    public static final SoundType NULL_OBSIDIAN_SOUND = sound("null_obsidian", 1.0f, 1.0f);
    public static final SoundType FRACTURE_GLASS_SOUND = sound("fracture_glass", 1.0f, 1.0f);
    public static final SoundType SPIRE_LANTERN_SOUND = sound("spire_lantern", 1.0f, 1.0f);

    // ================= Chorus Reach
    public static final DeferredBlock<PaleLoamBlock> PALE_LOAM = item(BLOCKS.registerBlock("pale_loam", PaleLoamBlock::new,
        p -> p.mapColor(MapColor.COLOR_PURPLE).strength(3.0f, 9.0f).requiresCorrectToolForDrops().sound(PALE_LOAM_SOUND).randomTicks()
            .instrument(NoteBlockInstrument.BASEDRUM)));
    public static final DeferredBlock<EndPlantBlock> LILAC_TUFT = item(BLOCKS.registerBlock("lilac_tuft", EndPlantBlock::new,
        p -> p.mapColor(MapColor.COLOR_PURPLE).noCollision().instabreak().sound(SoundType.GRASS).replaceable()
            .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<ChorusPodBlock> CHORUS_POD = BLOCKS.registerBlock("chorus_pod", ChorusPodBlock::new,
        p -> p.mapColor(MapColor.COLOR_PURPLE).strength(0.4f).sound(SoundType.CHERRY_LEAVES).noOcclusion().randomTicks()
            .pushReaction(PushReaction.POPPED).lightLevel(s -> s.getValue(ChorusPodBlock.AGE) == ChorusPodBlock.RIPE ? 4 : 0));

    // ================= Stillwater Basins
    public static final DeferredBlock<Block> STILLSTONE = item(BLOCKS.registerSimpleBlock("stillstone",
        p -> p.mapColor(MapColor.COLOR_BLACK).strength(3.5f, 6.0f).requiresCorrectToolForDrops().sound(STILLSTONE_SOUND)
            .instrument(NoteBlockInstrument.BASEDRUM)));
    public static final DeferredBlock<Block> POLISHED_STILLSTONE = item(BLOCKS.registerSimpleBlock("polished_stillstone",
        p -> p.mapColor(MapColor.COLOR_BLACK).strength(3.5f, 6.0f).requiresCorrectToolForDrops().sound(STILLSTONE_SOUND)));
    public static final DeferredBlock<Block> STILLSTONE_BRICKS = item(BLOCKS.registerSimpleBlock("stillstone_bricks",
        p -> p.mapColor(MapColor.COLOR_BLACK).strength(3.5f, 6.0f).requiresCorrectToolForDrops().sound(STILLSTONE_SOUND)));
    public static final DeferredBlock<Block> CHISELED_STILLSTONE = item(BLOCKS.registerSimpleBlock("chiseled_stillstone",
        p -> p.mapColor(MapColor.COLOR_BLACK).strength(3.5f, 6.0f).requiresCorrectToolForDrops().sound(STILLSTONE_SOUND)));
    public static final DeferredBlock<AmethystClusterBlock> MIRROR_SHARD = item(BLOCKS.registerBlock("mirror_shard",
        p -> new AmethystClusterBlock(7, 3, p),
        p -> p.mapColor(MapColor.COLOR_BLACK).noOcclusion().strength(1.5f).sound(MIRROR_SHARD_SOUND).lightLevel(s -> 2)
            .pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<TransparentBlock> RIMGLASS = item(BLOCKS.registerBlock("rimglass", TransparentBlock::new,
        p -> p.mapColor(MapColor.COLOR_BLUE).strength(0.6f).sound(SoundType.GLASS).noOcclusion()
            .isValidSpawn((st, g, ps, t) -> false).isRedstoneConductor((st, g, ps) -> false).isSuffocating((st, g, ps) -> false).isViewBlocking((st, g, ps, a) -> false)));
    public static final DeferredBlock<TransparentBlock> STILLGLASS = item(BLOCKS.registerBlock("stillglass", TransparentBlock::new,
        p -> p.mapColor(MapColor.COLOR_BLACK).strength(0.6f).sound(SoundType.GLASS).noOcclusion()
            .isValidSpawn((st, g, ps, t) -> false).isRedstoneConductor((st, g, ps) -> false).isSuffocating((st, g, ps) -> false).isViewBlocking((st, g, ps, a) -> false)));
    public static final DeferredBlock<IronBarsBlock> STILLGLASS_PANE = item(BLOCKS.registerBlock("stillglass_pane", IronBarsBlock::new,
        p -> p.mapColor(MapColor.COLOR_BLACK).strength(0.3f).sound(SoundType.GLASS).noOcclusion()));

    // ================= Fallen Spires
    public static final DeferredBlock<Block> WEATHERED_PURPUR = item(BLOCKS.registerSimpleBlock("weathered_purpur",
        p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(1.5f, 6.0f).requiresCorrectToolForDrops()));
    public static final DeferredBlock<RotatedPillarBlock> WEATHERED_PURPUR_PILLAR = item(BLOCKS.registerBlock("weathered_purpur_pillar",
        RotatedPillarBlock::new, p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(1.5f, 6.0f).requiresCorrectToolForDrops()));
    public static final DeferredBlock<RotatedPillarBlock> CRACKED_PURPUR_PILLAR = item(BLOCKS.registerBlock("cracked_purpur_pillar",
        RotatedPillarBlock::new, p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(1.5f, 6.0f).requiresCorrectToolForDrops()));
    public static final DeferredBlock<PurpurRubbleBlock> PURPUR_RUBBLE = item(BLOCKS.registerBlock("purpur_rubble", PurpurRubbleBlock::new,
        p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(0.8f).sound(SoundType.GRAVEL)));
    public static final DeferredBlock<LanternBlock> SPIRE_LANTERN = item(BLOCKS.registerBlock("spire_lantern", LanternBlock::new,
        p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(3.5f).sound(SPIRE_LANTERN_SOUND).lightLevel(s -> 13).noOcclusion()
            .pushReaction(PushReaction.POPPED)));

    // ================= The Underside
    public static final DeferredBlock<Block> UNDERSHALE = item(BLOCKS.registerSimpleBlock("undershale",
        p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE)));
    public static final DeferredBlock<Block> POLISHED_UNDERSHALE = item(BLOCKS.registerSimpleBlock("polished_undershale",
        p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.POLISHED_DEEPSLATE)));
    public static final DeferredBlock<Block> UNDERSHALE_BRICKS = item(BLOCKS.registerSimpleBlock("undershale_bricks",
        p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS)));
    public static final DeferredBlock<Block> UNDERSHALE_TILES = item(BLOCKS.registerSimpleBlock("undershale_tiles",
        p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_TILES)));
    public static final DeferredBlock<CeilingMossBlock> CEILING_MOSS = item(BLOCKS.registerBlock("ceiling_moss", CeilingMossBlock::new,
        p -> p.mapColor(MapColor.COLOR_CYAN).noCollision().instabreak().sound(SoundType.MOSS).randomTicks().replaceable()
            .pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<HangingCrystalBlock> LUMEN_CRYSTAL = item(BLOCKS.registerBlock("lumen_crystal",
        p -> new HangingCrystalBlock(p, true),
        p -> p.mapColor(MapColor.COLOR_CYAN).strength(1.5f).sound(LUMEN_SOUND).noOcclusion().randomTicks()
            .lightLevel(s -> s.getValue(HangingCrystalBlock.PART) == HangingCrystalBlock.Part.TIP ? 12 : 8)
            .pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<AmethystClusterBlock> LUMEN_CLUSTER = item(BLOCKS.registerBlock("lumen_cluster",
        p -> new AmethystClusterBlock(7, 3, p),
        p -> p.mapColor(MapColor.COLOR_CYAN).strength(1.5f).sound(LUMEN_SOUND).noOcclusion().lightLevel(s -> 12)
            .pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<Block> LUMEN_CRYSTAL_BLOCK = item(BLOCKS.registerSimpleBlock("lumen_crystal_block",
        p -> p.mapColor(MapColor.COLOR_CYAN).strength(1.5f).sound(LUMEN_SOUND).lightLevel(s -> 10).requiresCorrectToolForDrops()));
    public static final DeferredBlock<HangingCrystalBlock> VOIDROOT = item(BLOCKS.registerBlock("voidroot",
        p -> new HangingCrystalBlock(p, false),
        p -> p.mapColor(MapColor.COLOR_PURPLE).strength(1.0f).sound(VOIDROOT_SOUND).noOcclusion().noCollision()
            .lightLevel(s -> s.getValue(HangingCrystalBlock.PART) == HangingCrystalBlock.Part.TIP ? 6 : 0)
            .pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<GlowbulbBlock> GLOWBULB_BLOCK = BLOCKS.registerBlock("glowbulb", GlowbulbBlock::new,
        p -> p.mapColor(MapColor.COLOR_MAGENTA).instabreak().sound(GLOWBULB_SOUND).noOcclusion().randomTicks()
            .lightLevel(GlowbulbBlock::light).pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<RedstoneLampBlock> LUMEN_LAMP = item(BLOCKS.registerBlock("lumen_lamp", RedstoneLampBlock::new,
        p -> p.mapColor(MapColor.COLOR_CYAN).strength(0.3f).sound(LUMEN_SOUND)
            .lightLevel(s -> s.getValue(RedstoneLampBlock.LIT) ? 15 : 0).isValidSpawn((st, g, ps, t) -> true)));

    // ================= The Drift
    public static final DeferredBlock<Block> DRIFTSTONE = item(BLOCKS.registerSimpleBlock("driftstone",
        p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(DRIFTSTONE_SOUND)));
    public static final DeferredBlock<Block> POLISHED_DRIFTSTONE = item(BLOCKS.registerSimpleBlock("polished_driftstone",
        p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(DRIFTSTONE_SOUND)));
    public static final DeferredBlock<Block> DRIFTSTONE_BRICKS = item(BLOCKS.registerSimpleBlock("driftstone_bricks",
        p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(DRIFTSTONE_SOUND)));
    public static final DeferredBlock<TideGaugeBlock> TIDE_GAUGE = item(BLOCKS.registerBlock("tide_gauge", TideGaugeBlock::new,
        p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(1.5f).sound(SoundType.GLASS).noOcclusion().lightLevel(s -> 3)));

    // ================= The Fracture
    public static final DeferredBlock<Block> NULL_OBSIDIAN = item(BLOCKS.registerSimpleBlock("null_obsidian",
        p -> p.mapColor(MapColor.COLOR_BLACK).strength(60.0f, 1800.0f).requiresCorrectToolForDrops().sound(NULL_OBSIDIAN_SOUND)));
    public static final DeferredBlock<TransparentBlock> FRACTURE_GLASS = item(BLOCKS.registerBlock("fracture_glass", TransparentBlock::new,
        p -> p.mapColor(MapColor.COLOR_RED).strength(0.6f).sound(FRACTURE_GLASS_SOUND).noOcclusion().lightLevel(s -> 3)
            .isValidSpawn((st, g, ps, t) -> false).isRedstoneConductor((st, g, ps) -> false).isSuffocating((st, g, ps) -> false).isViewBlocking((st, g, ps, a) -> false)));
    public static final DeferredBlock<AmethystClusterBlock> UNMADE_SHARD = item(BLOCKS.registerBlock("unmade_shard",
        p -> new AmethystClusterBlock(12, 3, p),
        p -> p.mapColor(MapColor.COLOR_BLACK).strength(2.0f).sound(FRACTURE_GLASS_SOUND).noOcclusion().lightLevel(s -> 5)
            .noLootTable().pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<Block> ASH_STONE = item(BLOCKS.registerSimpleBlock("ash_stone",
        p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.TUFF)));

    // ================= Functional (§6.3)
    public static final DeferredBlock<HoardCacheBlock> HOARD_CACHE = item(BLOCKS.registerBlock("hoard_cache", p -> new HoardCacheBlock(p, true),
        p -> p.mapColor(MapColor.SAND).strength(-1.0f, 3600000.0f).noLootTable().sound(SoundType.STONE)));
    public static final DeferredBlock<HoardCacheBlock> DECORATIVE_HOARD_CACHE = item(BLOCKS.registerBlock("decorative_hoard_cache",
        p -> new HoardCacheBlock(p, false), p -> p.mapColor(MapColor.SAND).strength(2.5f).sound(SoundType.STONE)));
    public static final DeferredBlock<KeystoneSocketBlock> KEYSTONE_SOCKET = item(BLOCKS.registerBlock("keystone_socket", KeystoneSocketBlock::new,
        p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(-1.0f, 3600000.0f).noLootTable().lightLevel(s -> s.getValue(KeystoneSocketBlock.USED) ? 9 : 4)));
    public static final DeferredBlock<SpireVaultBlock> SPIRE_VAULT = item(BLOCKS.registerBlock("spire_vault", SpireVaultBlock::new,
        p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(-1.0f, 3600000.0f).noLootTable().noOcclusion().lightLevel(s -> 6)));
    public static final DeferredBlock<SpireVaultBlock> DRIFT_CACHE = item(BLOCKS.registerBlock("drift_cache", SpireVaultBlock::new,
        p -> p.mapColor(MapColor.COLOR_CYAN).strength(-1.0f, 3600000.0f).noLootTable().noOcclusion().lightLevel(s -> 6)));
    public static final DeferredBlock<SpireVaultBlock> SIGIL_PEDESTAL = item(BLOCKS.registerBlock("sigil_pedestal", SpireVaultBlock::new,
        p -> p.mapColor(MapColor.COLOR_PURPLE).strength(-1.0f, 3600000.0f).noLootTable().noOcclusion().lightLevel(s -> 9)));
    public static final DeferredBlock<EggCradleBlock> EGG_CRADLE = item(BLOCKS.registerBlock("egg_cradle", EggCradleBlock::new,
        p -> p.mapColor(MapColor.COLOR_BLACK).strength(5.0f, 1200.0f).requiresCorrectToolForDrops().noOcclusion()
            .lightLevel(EggCradleBlock::light)));
    public static final DeferredBlock<GravityAnchorBlock> GRAVITY_ANCHOR = item(BLOCKS.registerBlock("gravity_anchor", GravityAnchorBlock::new,
        p -> p.mapColor(MapColor.COLOR_BLACK).strength(10.0f, 1200.0f).requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> 7)));
    public static final DeferredBlock<AnchorFrameBlock> ANCHOR_FRAME = item(BLOCKS.registerBlock("anchor_frame", AnchorFrameBlock::new,
        p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(0.5f).sound(SoundType.SCAFFOLDING).noOcclusion().lightLevel(s -> 3)));
    public static final DeferredBlock<RiftFrameBlock> FRACTURE_RIFT_FRAME = item(BLOCKS.registerBlock("fracture_rift_frame", RiftFrameBlock::new,
        p -> p.mapColor(MapColor.COLOR_RED).strength(-1.0f, 3600000.0f).noLootTable().lightLevel(s -> s.getValue(RiftFrameBlock.SIGILS) * 3)));
    public static final DeferredBlock<FractureRiftBlock> FRACTURE_RIFT = BLOCKS.registerBlock("fracture_rift", FractureRiftBlock::new,
        p -> p.mapColor(MapColor.COLOR_RED).noCollision().strength(-1.0f, 3600000.0f).noLootTable().lightLevel(s -> 11)
            .pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredBlock<StillThroneBlock> STILL_THRONE = item(BLOCKS.registerBlock("still_throne", StillThroneBlock::new,
        p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0f, 3600000.0f).noLootTable().noOcclusion()
            .lightLevel(s -> s.getValue(StillThroneBlock.FILLED) ? 15 : 2)));

    public static final DeferredBlock<dev.worldsfall.block.TidewispJarBlock> TIDEWISP_JAR = BLOCKS.registerBlock("tidewisp_jar",
        dev.worldsfall.block.TidewispJarBlock::new, p -> p.mapColor(MapColor.COLOR_CYAN).strength(0.3f).sound(SoundType.GLASS).noOcclusion()
            .lightLevel(s -> 9).pushReaction(PushReaction.POPPED));

    // ================= Fluid
    // ================= Expansion 1.2: the Warden Cycle
    public static final DeferredBlock<dev.worldsfall.warden.WardensLanternBlock> WARDENS_LANTERN = item(BLOCKS.registerBlock("wardens_lantern",
        dev.worldsfall.warden.WardensLanternBlock::new, p -> p.mapColor(MapColor.COLOR_BLACK).strength(3.5f).sound(SPIRE_LANTERN_SOUND)
            .lightLevel(s -> 14).noOcclusion().pushReaction(PushReaction.POPPED)));

    // ================= Expansion 1.3: the Gap
    public static final DeferredBlock<dev.worldsfall.gap.CausewaySocketBlock> CAUSEWAY_SOCKET = item(BLOCKS.registerBlock("causeway_socket",
        dev.worldsfall.gap.CausewaySocketBlock::new, p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(-1.0f, 3600000.0f).noLootTable()
            .noOcclusion().lightLevel(s -> s.getValue(dev.worldsfall.gap.CausewaySocketBlock.USED) ? 4 : 8)));
    public static final DeferredBlock<dev.worldsfall.gap.CausewayLanternBlock> CAUSEWAY_LANTERN = item(BLOCKS.registerBlock("causeway_lantern",
        dev.worldsfall.gap.CausewayLanternBlock::new, p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(3.5f).sound(SPIRE_LANTERN_SOUND)
            .lightLevel(s -> 13).noOcclusion().pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<dev.worldsfall.gap.PlaqueBlock> CAUSEWAY_PLAQUE = item(BLOCKS.registerBlock("causeway_plaque",
        dev.worldsfall.gap.PlaqueBlock::new, p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(1.5f, 6.0f).noOcclusion()));
    public static final DeferredBlock<dev.worldsfall.gap.DriftBuoyBlock> DRIFT_BUOY = BLOCKS.registerBlock("drift_buoy",
        dev.worldsfall.gap.DriftBuoyBlock::new, p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.5f).sound(SoundType.LANTERN)
            .lightLevel(s -> 10).noOcclusion().pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<dev.worldsfall.gap.RoostPostBlock> ROOST_POST = item(BLOCKS.registerBlock("roost_post",
        dev.worldsfall.gap.RoostPostBlock::new, p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(1.5f, 6.0f).noOcclusion()));

    // ================= Expansion 1.4: cities and navigation
    public static final DeferredBlock<dev.worldsfall.nav.DriftHelmBlock> DRIFT_HELM = item(BLOCKS.registerBlock("drift_helm",
        dev.worldsfall.nav.DriftHelmBlock::new, p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0f, 6.0f).sound(SoundType.WOOD)
            .noOcclusion().lightLevel(s -> s.getValue(dev.worldsfall.nav.DriftHelmBlock.MOORED) ? 7 : 0)));
    public static final DeferredBlock<dev.worldsfall.nav.ShipsBellBlock> SHIPS_BELL = item(BLOCKS.registerBlock("ships_bell",
        dev.worldsfall.nav.ShipsBellBlock::new, p -> p.mapColor(MapColor.GOLD).strength(3.0f, 6.0f).sound(SoundType.ANVIL).noOcclusion()));

    // ================= Expansion 1.5: living systems
    public static final DeferredBlock<dev.worldsfall.life.LanternMothJarBlock> LANTERN_MOTH_JAR = BLOCKS.registerBlock("lantern_moth_jar",
        dev.worldsfall.life.LanternMothJarBlock::new, p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(0.3f).sound(SoundType.GLASS).noOcclusion()
            .lightLevel(s -> 12).pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<dev.worldsfall.life.TideBellBlock> TIDE_BELL = item(BLOCKS.registerBlock("tide_bell",
        dev.worldsfall.life.TideBellBlock::new, p -> p.mapColor(MapColor.COLOR_CYAN).strength(2.0f, 6.0f).sound(SoundType.ANVIL).noOcclusion()));
    public static final DeferredBlock<dev.worldsfall.life.WyrmRoostBlock> WYRM_ROOST = item(BLOCKS.registerBlock("wyrm_roost",
        dev.worldsfall.life.WyrmRoostBlock::new, p -> p.mapColor(MapColor.COLOR_PURPLE).strength(0.8f).sound(SoundType.AZALEA).noOcclusion()));
    public static final DeferredBlock<dev.worldsfall.life.GravityPlateBlock> GRAVITY_PLATE = item(BLOCKS.registerBlock("gravity_plate",
        dev.worldsfall.life.GravityPlateBlock::new, p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.5f, 6.0f).sound(SoundType.STONE).noOcclusion()
            .lightLevel(s -> s.getValue(dev.worldsfall.life.GravityPlateBlock.POWERED) ? 9 : 3).pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<dev.worldsfall.life.CourierCacheBlock> COURIER_CACHE = item(BLOCKS.registerBlock("courier_cache",
        dev.worldsfall.life.CourierCacheBlock::new, p -> p.mapColor(MapColor.SAND).strength(2.5f, 6.0f).sound(SoundType.STONE).lightLevel(s -> 5)));
    public static final DeferredBlock<dev.worldsfall.life.StarfallShardBlock> STARFALL_SHARD = item(BLOCKS.registerBlock("starfall_shard",
        dev.worldsfall.life.StarfallShardBlock::new, p -> p.mapColor(MapColor.GOLD).strength(1.5f).sound(SoundType.AMETHYST_CLUSTER).noOcclusion()
            .lightLevel(s -> 12).pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<dev.worldsfall.life.TidewrackBlock> TIDEWRACK = item(BLOCKS.registerBlock("tidewrack",
        dev.worldsfall.life.TidewrackBlock::new, p -> p.mapColor(MapColor.COLOR_CYAN).strength(0.6f).sound(SoundType.GRAVEL).noOcclusion()));
    public static final DeferredBlock<Block> ANCHOR_FRAME_LAMP = item(BLOCKS.registerSimpleBlock("anchor_frame_lamp",
        p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(1.5f, 6.0f).sound(SoundType.METAL).lightLevel(s -> 13)));
    public static final DeferredBlock<SlabBlock> ANCHOR_FRAME_SLAB = item(BLOCKS.registerBlock("anchor_frame_slab", SlabBlock::new,
        p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(1.5f, 6.0f).sound(SoundType.METAL)));
    public static final DeferredBlock<StairBlock> ANCHOR_FRAME_STAIRS = item(BLOCKS.registerBlock("anchor_frame_stairs",
        p -> new StairBlock(ANCHOR_FRAME_LAMP.get().defaultBlockState(), p), p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(1.5f, 6.0f).sound(SoundType.METAL)));

    // ================= Expansion 1.6: the Zenith
    public static final DeferredBlock<Block> SKYSTONE = item(BLOCKS.registerSimpleBlock("skystone",
        p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.CALCITE)));
    public static final DeferredBlock<Block> POLISHED_SKYSTONE = item(BLOCKS.registerSimpleBlock("polished_skystone",
        p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.CALCITE)));
    public static final DeferredBlock<Block> SKYSTONE_BRICKS = item(BLOCKS.registerSimpleBlock("skystone_bricks",
        p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.CALCITE)));
    public static final DeferredBlock<Block> SKYSTONE_TILES = item(BLOCKS.registerSimpleBlock("skystone_tiles",
        p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.CALCITE)));
    public static final DeferredBlock<Block> CHISELED_SKYSTONE = item(BLOCKS.registerSimpleBlock("chiseled_skystone",
        p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.CALCITE)));
    public static final DeferredBlock<dev.worldsfall.zenith.HaloGlassBlock> HALO_GLASS = item(BLOCKS.registerBlock("halo_glass",
        dev.worldsfall.zenith.HaloGlassBlock::new, p -> p.mapColor(MapColor.SNOW).strength(0.5f).sound(SoundType.GLASS).noOcclusion()
            .lightLevel(dev.worldsfall.zenith.HaloGlassBlock::lightFor).isValidSpawn((s, l, pos, t) -> false).isRedstoneConductor((s, l, pos) -> false)
            .isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos, a) -> false)));
    public static final DeferredBlock<Block> ORRERY_RING = item(BLOCKS.registerSimpleBlock("orrery_ring",
        p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(-1.0f, 3600000.0f).noLootTable().lightLevel(s -> 4)));
    public static final DeferredBlock<dev.worldsfall.zenith.OrreryLensBlock> ORRERY_LENS = item(BLOCKS.registerBlock("orrery_lens",
        dev.worldsfall.zenith.OrreryLensBlock::new, p -> p.mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000.0f).noLootTable()
            .lightLevel(s -> s.getValue(dev.worldsfall.zenith.OrreryLensBlock.FOCUSED) ? 15 : 3)));
    public static final DeferredBlock<dev.worldsfall.zenith.OrrerySocketBlock> ORRERY_SOCKET = item(BLOCKS.registerBlock("orrery_socket",
        dev.worldsfall.zenith.OrrerySocketBlock::new, p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0f, 3600000.0f).noLootTable()
            .lightLevel(s -> s.getValue(dev.worldsfall.zenith.OrrerySocketBlock.FREED) ? 12 : 6)));

    // ================= Expansion 2.0: the Undertow and the Restoration
    public static final DeferredBlock<Block> ARCHIVE_TILES = item(BLOCKS.registerSimpleBlock("archive_tiles",
        p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_TILES)));
    public static final DeferredBlock<dev.worldsfall.undertow.ArchiveShelfBlock> ARCHIVE_SHELF = item(BLOCKS.registerBlock("archive_shelf",
        dev.worldsfall.undertow.ArchiveShelfBlock::new, p -> p.mapColor(MapColor.COLOR_CYAN).strength(1.5f).sound(SoundType.CHISELED_BOOKSHELF).lightLevel(s -> 4)));
    public static final DeferredBlock<Block> NULL_CORAL_BLOCK = item(BLOCKS.registerSimpleBlock("null_coral_block",
        p -> p.mapColor(MapColor.COLOR_BLACK).strength(1.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.CORAL_BLOCK)));
    public static final DeferredBlock<Block> NULL_CORAL_BRICKS = item(BLOCKS.registerSimpleBlock("null_coral_bricks",
        p -> p.mapColor(MapColor.COLOR_BLACK).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS)));
    public static final DeferredBlock<Block> POLISHED_NULL_CORAL = item(BLOCKS.registerSimpleBlock("polished_null_coral",
        p -> p.mapColor(MapColor.COLOR_BLACK).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.POLISHED_DEEPSLATE)));
    public static final DeferredBlock<dev.worldsfall.undertow.NullCoralBlock> NULL_CORAL = item(BLOCKS.registerBlock("null_coral",
        dev.worldsfall.undertow.NullCoralBlock::new, p -> p.mapColor(MapColor.COLOR_BLACK).instabreak().noCollision().sound(SoundType.CORAL_BLOCK)
            .noOcclusion().pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<dev.worldsfall.undertow.NullCoralBlock> NULL_CORAL_FAN = item(BLOCKS.registerBlock("null_coral_fan",
        dev.worldsfall.undertow.NullCoralBlock::new, p -> p.mapColor(MapColor.COLOR_BLACK).instabreak().noCollision().sound(SoundType.CORAL_BLOCK)
            .noOcclusion().pushReaction(PushReaction.POPPED)));
    public static final DeferredBlock<dev.worldsfall.undertow.MuralPanelBlock> MURAL_PANEL = item(BLOCKS.registerBlock("mural_panel",
        dev.worldsfall.undertow.MuralPanelBlock::new, p -> p.mapColor(MapColor.COLOR_PURPLE).strength(-1.0f, 3600000.0f).noLootTable()
            .lightLevel(s -> s.getValue(dev.worldsfall.undertow.MuralPanelBlock.LIT) ? 6 : 0)));
    public static final DeferredBlock<dev.worldsfall.undertow.BellWinchBlock> BELL_WINCH = item(BLOCKS.registerBlock("bell_winch",
        dev.worldsfall.undertow.BellWinchBlock::new, p -> p.mapColor(MapColor.COLOR_GRAY).strength(3.0f, 6.0f).sound(SoundType.CHAIN)));

    public static final DeferredBlock<LiquidBlock> STILLWATER = BLOCKS.registerBlock("stillwater",
        p -> new dev.worldsfall.fluid.StillwaterBlock(WFFluids.STILLWATER.get(), p),
        p -> p.mapColor(MapColor.COLOR_BLACK).replaceable().noCollision().strength(100.0f).pushReaction(PushReaction.POPPED)
            .noLootTable().liquid().sound(SoundType.EMPTY).lightLevel(s -> 3));
    /** Cauldron holding Stillwater (§7.5); drops a plain cauldron, has no item of its own. */
    public static final DeferredBlock<net.minecraft.world.level.block.LayeredCauldronBlock> STILLWATER_CAULDRON = BLOCKS.registerBlock("stillwater_cauldron",
        p -> new net.minecraft.world.level.block.LayeredCauldronBlock(net.minecraft.world.level.biome.Biome.Precipitation.NONE,
            dev.worldsfall.fluid.StillwaterCauldron.INTERACTIONS, p),
        p -> p.mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(2.0f).noOcclusion().lightLevel(s -> 3));

    // ================= building sets (§6.4)
    static {
        family("stillstone", STILLSTONE, true);
        family("polished_stillstone", POLISHED_STILLSTONE, true);
        family("stillstone_brick", STILLSTONE_BRICKS, true);
        family("weathered_purpur", WEATHERED_PURPUR, false);
        family("undershale", UNDERSHALE, true);
        family("polished_undershale", POLISHED_UNDERSHALE, true);
        family("undershale_brick", UNDERSHALE_BRICKS, true);
        family("undershale_tile", UNDERSHALE_TILES, true);
        family("driftstone", DRIFTSTONE, true);
        family("polished_driftstone", POLISHED_DRIFTSTONE, true);
        family("driftstone_brick", DRIFTSTONE_BRICKS, true);
        family("skystone", SKYSTONE, true);
        family("polished_skystone", POLISHED_SKYSTONE, true);
        family("skystone_brick", SKYSTONE_BRICKS, true);
        family("skystone_tile", SKYSTONE_TILES, true);
    }

    private WFBlocks() {}

    private static void family(String base, DeferredBlock<? extends Block> parent, boolean wall) {
        List<DeferredBlock<? extends Block>> list = new ArrayList<>();
        UnaryOperator<BlockBehaviour.Properties> copy = p -> BlockBehaviour.Properties.ofFullCopy(parent.get());
        list.add(item(BLOCKS.registerBlock(base + "_slab", SlabBlock::new, copy)));
        list.add(item(BLOCKS.registerBlock(base + "_stairs", p -> new StairBlock(parent.get().defaultBlockState(), p), copy)));
        if (wall) {
            list.add(item(BLOCKS.registerBlock(base + "_wall", WallBlock::new, p -> BlockBehaviour.Properties.ofFullCopy(parent.get()).forceSolidOn())));
        }
        FAMILIES.put(base, Collections.unmodifiableList(list));
    }

    private static <B extends Block> DeferredBlock<B> item(DeferredBlock<B> block) {
        ITEM_BLOCKS.add(block);
        return block;
    }

    public static List<DeferredBlock<? extends Block>> itemBlocks() {
        return Collections.unmodifiableList(ITEM_BLOCKS);
    }

    private static boolean never(net.minecraft.world.level.block.state.BlockState s, net.minecraft.world.level.BlockGetter g, net.minecraft.core.BlockPos p) {
        return false;
    }

    private static SoundType sound(String name, float volume, float pitch) {
        Function<String, Supplier<net.minecraft.sounds.SoundEvent>> ev = suffix -> () -> net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT
            .getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(Worldsfall.MODID, "block." + name + "." + suffix));
        return new DeferredSoundType(volume, pitch, ev.apply("break"), ev.apply("step"), ev.apply("place"), ev.apply("hit"), ev.apply("fall"));
    }
}
