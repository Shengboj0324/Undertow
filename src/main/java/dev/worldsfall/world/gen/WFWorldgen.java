package dev.worldsfall.world.gen;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.Worldsfall;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Worldgen registrations: the End biome source, features, structures, placement modifiers (§20.4). */
public final class WFWorldgen {
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, Worldsfall.MODID);
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<WFEndBiomeSource>> END_SOURCE = BIOME_SOURCES.register("end",
        () -> WFEndBiomeSource.CODEC);

    private WFWorldgen() {}

    public static void register(IEventBus bus) {
        BIOME_SOURCES.register(bus);
        WFFeatures.register(bus);
        WFStructures.register(bus);
    }
}
