package dev.worldsfall.client.expansion;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.client.entity.EmissiveLayer;
import dev.worldsfall.client.entity.Tex;
import dev.worldsfall.client.model.WFModel;
import dev.worldsfall.life.LanternMoth;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Renderers for the living-systems creatures (release 1.5). */
public final class LifeRenderers {
    public static final ModelLayerLocation LANTERN_MOTH = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "lantern_moth"), "main");

    private LifeRenderers() {}

    public static final class MothModel extends WFModel<LivingEntityRenderState> {
        private final ModelPart left, right, antennae, body;

        public MothModel(ModelPart root) {
            super(root, "lantern_moth");
            body = p("body");
            left = p("left_wing");
            right = p("right_wing");
            antennae = p("antennae");
        }

        @Override
        public void setupAnim(LivingEntityRenderState s) {
            super.setupAnim(s);
            float t = s.ageInTicks;
            float flap = Mth.sin(t * 1.9f) * 0.9f + 0.25f;
            left.zRot += flap;
            right.zRot -= flap;
            body.y += Mth.sin(t * 0.3f) * 0.4f;
            antennae.xRot += Mth.sin(t * 0.25f) * 0.12f;
        }
    }

    public static final class MothRenderer extends MobRenderer<LanternMoth, LivingEntityRenderState, MothModel> {
        private static final Identifier TEX = Tex.of("lantern_moth/lantern_moth"), GLOW = Tex.of("lantern_moth/lantern_moth_emissive");

        public MothRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new MothModel(ctx.bakeLayer(LANTERN_MOTH)), 0.0f);
            addLayer(new EmissiveLayer<>(this, s -> GLOW, s -> true));
        }

        @Override
        public Identifier getTextureLocation(LivingEntityRenderState s) {
            return TEX;
        }

        @Override
        public LivingEntityRenderState createRenderState() {
            return new LivingEntityRenderState();
        }
    }
}
