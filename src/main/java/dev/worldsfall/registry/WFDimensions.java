package dev.worldsfall.registry;

import dev.worldsfall.Worldsfall;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class WFDimensions {
    public static final ResourceKey<Level> THE_FRACTURE = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(Worldsfall.MODID, "the_fracture"));

    private WFDimensions() {}
}
