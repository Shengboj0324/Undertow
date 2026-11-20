package dev.worldsfall.client.entity;

import dev.worldsfall.Worldsfall;
import net.minecraft.resources.Identifier;

final class Tex {
    private Tex() {}

    static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath(Worldsfall.MODID, "textures/entity/" + path + ".png");
    }
}
