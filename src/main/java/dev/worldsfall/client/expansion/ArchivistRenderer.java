package dev.worldsfall.client.expansion;

import dev.worldsfall.client.entity.Tex;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ShulkerRenderer;
import net.minecraft.client.renderer.entity.state.ShulkerRenderState;
import net.minecraft.resources.Identifier;

/** The Shulker Archivist: the vanilla shulker model with a pale, dusty, glyph-marked shell. */
public class ArchivistRenderer extends ShulkerRenderer {
    private static final Identifier TEX = Tex.of("shulker_archivist");

    public ArchivistRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getTextureLocation(ShulkerRenderState state) {
        return TEX;
    }
}
