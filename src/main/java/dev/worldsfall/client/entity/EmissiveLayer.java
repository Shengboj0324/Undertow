package dev.worldsfall.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.util.function.Function;
import java.util.function.Predicate;

/** Fullbright glow layer (eyes, spots, edges) — the emissive texture glows in darkness (§18.3). */
public class EmissiveLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
    private final Function<S, Identifier> texture;
    private final Predicate<S> visible;

    public EmissiveLayer(RenderLayerParent<S, M> parent, Function<S, Identifier> texture, Predicate<S> visible) {
        super(parent);
        this.texture = texture;
        this.visible = visible;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
        if (state.isInvisible || !visible.test(state)) {
            return;
        }
        collector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(texture.apply(state)), lightCoords,
            OverlayTexture.NO_OVERLAY, state.outlineColor);
    }
}
