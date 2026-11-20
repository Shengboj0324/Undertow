package dev.worldsfall.data;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.Worldsfall;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Data codec registrations referenced from JSON: loot functions and biome modifiers. */
public final class WFDataTypes {
    public static final DeferredRegister<MapCodec<? extends LootItemFunction>> LOOT_FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Worldsfall.MODID);
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIERS = DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, Worldsfall.MODID);

    static {
        LOOT_FUNCTIONS.register("random_tablet", () -> RandomTabletFunction.CODEC);
        BIOME_MODIFIERS.register("end_sky", () -> EndSkyBiomeModifier.CODEC);
    }

    private WFDataTypes() {}

    public static void register(IEventBus bus) {
        LOOT_FUNCTIONS.register(bus);
        BIOME_MODIFIERS.register(bus);
    }
}
