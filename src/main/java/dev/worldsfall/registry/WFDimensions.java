package dev.worldsfall.registry;

import dev.worldsfall.Worldsfall;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class WFDimensions {
    public static final ResourceKey<Level> THE_FRACTURE = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(Worldsfall.MODID, "the_fracture"));
    /** The Undertow (expansion 2.0): the tide seen from the inside, a pocket dimension reached through Tide Maws. */
    public static final ResourceKey<Level> THE_UNDERTOW = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(Worldsfall.MODID, "the_undertow"));

    private WFDimensions() {}
}
