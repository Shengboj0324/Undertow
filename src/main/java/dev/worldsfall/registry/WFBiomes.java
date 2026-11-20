package dev.worldsfall.registry;

import dev.worldsfall.Worldsfall;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/** Biome keys (Appendix C). Biomes themselves are data (worldgen JSON). */
public final class WFBiomes {
    public static final ResourceKey<Biome> CHORUS_REACH = key("chorus_reach");
    public static final ResourceKey<Biome> STILLWATER_BASINS = key("stillwater_basins");
    public static final ResourceKey<Biome> FALLEN_SPIRES = key("fallen_spires");
    public static final ResourceKey<Biome> THE_HOARD = key("the_hoard");
    public static final ResourceKey<Biome> THE_UNDERSIDE = key("the_underside");
    public static final ResourceKey<Biome> THE_DRIFT = key("the_drift");
    public static final ResourceKey<Biome> FRACTURE_RING = key("fracture_ring");

    private WFBiomes() {}

    private static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(Worldsfall.MODID, name));
    }
}
