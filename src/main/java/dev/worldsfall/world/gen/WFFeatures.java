package dev.worldsfall.world.gen;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.Worldsfall;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Custom worldgen feature types and placement modifiers (§20.4). Configured instances are data (worldgen/feature). */
public final class WFFeatures {
    public static final DeferredRegister<MapCodec<? extends Feature>> FEATURE_TYPES = DeferredRegister.create(Registries.FEATURE_TYPE, Worldsfall.MODID);
    public static final DeferredRegister<MapCodec<? extends PlacementModifier>> PLACEMENTS = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Worldsfall.MODID);

    static {
        FEATURE_TYPES.register("chorus_grove", () -> ChorusGroveFeature.CODEC);
        FEATURE_TYPES.register("pale_loam_patch", () -> PaleLoamPatchFeature.CODEC);
        FEATURE_TYPES.register("stillwater_basin", () -> StillwaterBasinFeature.CODEC);
        FEATURE_TYPES.register("underside_growth", () -> UndersideGrowthFeature.CODEC);
        FEATURE_TYPES.register("debris_field", () -> DebrisFieldFeature.CODEC);
        FEATURE_TYPES.register("skate_roost", () -> SkateRoostFeature.CODEC);
        FEATURE_TYPES.register("wild_shulker_colony", () -> WildShulkerColonyFeature.CODEC);
        FEATURE_TYPES.register("echo_shrine", () -> EchoShrineFeature.CODEC);
        FEATURE_TYPES.register("chorus_pod_scatter", () -> ChorusPodScatterFeature.CODEC);
        PLACEMENTS.register("distance_from_origin", () -> DistanceFilter.CODEC);
    }

    private WFFeatures() {}

    static void register(IEventBus bus) {
        FEATURE_TYPES.register(bus);
        PLACEMENTS.register(bus);
    }
}
