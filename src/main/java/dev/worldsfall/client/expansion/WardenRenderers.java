package dev.worldsfall.client.expansion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.worldsfall.Worldsfall;
import dev.worldsfall.client.entity.Tex;
import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.warden.LumenHeart;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

/** Client renderers for the Warden Cycle. */
public final class WardenRenderers {
    public static final ModelLayerLocation LUMEN_HEART = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "lumen_heart"), "main");
    static final int FULL_BRIGHT = 0xF000F0;

    private WardenRenderers() {}

    /**
     * The vanilla dragon renderer plus the Warden Cycle's poses: upside down on the Warden's Gallery ceiling (Descending
     * Warden), and the calm Release / Elder Warden (a pale aura). Everything else is drawn exactly as vanilla.
     */
    public static final class DragonRenderer extends EnderDragonRenderer {
        static final ContextKey<Integer> POSE = new ContextKey<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "dragon_pose"));

        public DragonRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public void extractRenderState(EnderDragon dragon, EnderDragonRenderState state, float partial) {
            super.extractRenderState(dragon, state, partial);
            state.setRenderData(POSE, dragon.getData(WFAttachments.DRAGON_POSE));
        }

        @Override
        public void submit(EnderDragonRenderState state, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            int flags = state.getRenderDataOrDefault(POSE, 0);
            if ((flags & 1) != 0) {
                // perched upside down on the Gallery ceiling
                pose.pushPose();
                pose.translate(0, 3.2, 0);
                pose.rotate(Axis.ZP.rotationDegrees(180));
                super.submit(state, pose, c, camera);
                pose.popPose();
                return;
            }
            super.submit(state, pose, c, camera);
        }
    }

    public static final class HeartState extends EntityRenderState {
        float pulse;
    }

    /** A Lumen Heart: a hanging teal crystal cluster that slowly turns and pulses. */
    public static final class LumenHeartRenderer extends EntityRenderer<LumenHeart, HeartState> {
        private static final Identifier TEX = Tex.of("lumen_heart/lumen_heart"), GLOW = Tex.of("lumen_heart/lumen_heart_emissive");
        private final ModelPart model;

        public LumenHeartRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            model = ctx.bakeLayer(LUMEN_HEART);
        }

        @Override
        public HeartState createRenderState() {
            return new HeartState();
        }

        @Override
        public boolean shouldRender(LumenHeart e, Frustum f, double x, double y, double z, float partial) {
            return true;
        }

        @Override
        public void extractRenderState(LumenHeart e, HeartState s, float partial) {
            super.extractRenderState(e, s, partial);
            s.pulse = 0.5f + 0.5f * Mth.sin((e.time + partial) * 0.12f);
        }

        @Override
        public void submit(HeartState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            pose.pushPose();
            float scale = 1.0f + s.pulse * 0.05f;
            pose.translate(0, 1.0, 0);
            pose.rotate(Axis.YP.rotation(s.ageInTicks * 0.01f));
            pose.rotate(Axis.XP.rotationDegrees(180));
            pose.scale(scale, scale, scale);
            pose.translate(0, -1.5, 0);
            c.submitModelPart(model, pose, RenderTypes.entityCutout(TEX), s.lightCoords, OverlayTexture.NO_OVERLAY, null);
            c.order(1).submitModelPart(model, pose, RenderTypes.eyes(GLOW), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
            pose.popPose();
            super.submit(s, pose, c, camera);
        }
    }
}
