package dev.worldsfall.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.worldsfall.Worldsfall;
import dev.worldsfall.client.model.CreatureModels;
import dev.worldsfall.client.model.WFStates;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;

/**
 * A Hatchling Wyrm sitting on its owner's shoulder like a parrot (§10.3). The server keeps the Wyrm in the player's
 * shoulder slot; which shoulders hold one is synced through {@code WFAttachments.WYRM_SHOULDERS} and copied into the
 * player's render state under {@link #KEY}.
 */
public final class WyrmShoulderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Bit 0: left shoulder, bit 1: right shoulder. */
    public static final ContextKey<Integer> KEY = new ContextKey<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "wyrm_shoulders"));
    private static final Identifier TEX = Tex.of("wyrm/wyrm_hatchling"), GLOW = Tex.of("wyrm/wyrm_hatchling_emissive");
    private static final float SCALE = 0.8f;
    private final CreatureModels.Wyrm model;

    public WyrmShoulderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
        super(parent);
        model = new CreatureModels.Wyrm(models.bakeLayer(WFLayers.WYRM_HATCHLING), "wyrm_hatchling");
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        int bits = state.getRenderDataOrDefault(KEY, 0);
        if (bits == 0 || state.isInvisible) {
            return;
        }
        if ((bits & 1) != 0) {
            submitOne(pose, collector, light, state, yRot, xRot, true);
        }
        if ((bits & 2) != 0) {
            submitOne(pose, collector, light, state, yRot, xRot, false);
        }
    }

    private void submitOne(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState player, float yRot, float xRot, boolean left) {
        WFStates.Wyrm s = new WFStates.Wyrm();
        s.stage = 0;
        s.sitting = true;
        s.flying = false;
        // a little offset in time per shoulder so two hatchlings never breathe in step
        s.ageInTicks = player.ageInTicks + (left ? 0 : 37);
        s.walkAnimationPos = 0;
        s.walkAnimationSpeed = 0;
        s.yRot = Mth.clamp(yRot, -50, 50) * 0.6f + (left ? 12 : -12);
        s.xRot = xRot * 0.4f;
        pose.pushPose();
        pose.translate(left ? 0.38f : -0.38f, player.isCrouching ? 0.2f : 0.0f, 0.02f);
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(0.0f, -1.5f, 0.0f);
        collector.submitModel(model, s, pose, RenderTypes.entityCutout(TEX), light, OverlayTexture.NO_OVERLAY, player.outlineColor);
        collector.order(1).submitModel(model, s, pose, RenderTypes.eyes(GLOW), light, OverlayTexture.NO_OVERLAY, player.outlineColor);
        pose.popPose();
    }
}
