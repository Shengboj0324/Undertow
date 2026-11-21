package dev.worldsfall.client.expansion;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.worldsfall.Worldsfall;
import dev.worldsfall.client.entity.EmissiveLayer;
import dev.worldsfall.client.entity.Tex;
import dev.worldsfall.client.model.WFModel;
import dev.worldsfall.undertow.DrownedAscendant;
import dev.worldsfall.undertow.Glimmer;
import dev.worldsfall.undertow.TideEel;
import dev.worldsfall.undertow.VoidBell;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/** Renderers for the Undertow (release 2.0): the Void Bell and the creatures of the haze. */
public final class UndertowRenderers {
    public static final ModelLayerLocation BELL = layer("void_bell"), EEL = layer("tide_eel"), ASCENDANT = layer("drowned_ascendant"),
        GLIMMER = layer("glimmer");

    private UndertowRenderers() {}

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(Worldsfall.MODID, name), "main");
    }

    // ------------------------------------------------------------------ Void Bell

    public static final class BellState extends LivingEntityRenderState {
        public boolean lamp;
        public float charge;
    }

    public static final class BellModel extends WFModel<BellState> {
        private final ModelPart core, lamp, hatch;

        public BellModel(ModelPart root) {
            super(root, "void_bell");
            core = p("core");
            lamp = p("lamp");
            hatch = p("hatch");
        }

        @Override
        public void setupAnim(BellState s) {
            super.setupAnim(s);
            core.yRot += s.ageInTicks * 0.05f * (0.3f + s.charge);
            lamp.y += Mth.sin(s.ageInTicks * 0.1f) * 0.3f;
            hatch.xRot += s.charge > 0 ? 0 : 0.6f;
            root().y += Mth.sin(s.ageInTicks * 0.05f) * 0.6f;
        }
    }

    public static final class BellRenderer extends MobRenderer<VoidBell, BellState, BellModel> {
        private static final Identifier TEX = Tex.of("void_bell/void_bell"), GLOW = Tex.of("void_bell/void_bell_emissive");

        public BellRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new BellModel(ctx.bakeLayer(BELL)), 0.9f);
            addLayer(new EmissiveLayer<>(this, s -> GLOW, s -> s.lamp && s.charge > 0));
        }

        @Override
        public Identifier getTextureLocation(BellState s) {
            return TEX;
        }

        @Override
        public BellState createRenderState() {
            return new BellState();
        }

        /** The pilot sits inside the hull: in first person the bell is not drawn for its own riders, so they can see out. */
        @Override
        public boolean shouldRender(VoidBell bell, net.minecraft.client.renderer.culling.Frustum culler, double camX, double camY, double camZ, float partial) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() != null && bell.hasPassenger(mc.getCameraEntity())) {
                return false;
            }
            return super.shouldRender(bell, culler, camX, camY, camZ, partial);
        }

        @Override
        public void extractRenderState(VoidBell e, BellState s, float partial) {
            super.extractRenderState(e, s, partial);
            s.lamp = e.lampOn();
            s.charge = Math.min(1f, e.charge() / (float) VoidBell.CHARGE_PER_FRAGMENT);
        }

        @Override
        protected void scale(BellState s, PoseStack pose) {
            pose.translate(0.0, -1.2, 0.0);
        }
    }

    // ------------------------------------------------------------------ Tide Eel

    public static final class EelState extends LivingEntityRenderState {
        public boolean coiled;
    }

    public static final class EelModel extends WFModel<EelState> {
        private final ModelPart[] body = new ModelPart[8];
        private final ModelPart jaw, fin;

        public EelModel(ModelPart root) {
            super(root, "tide_eel");
            for (int i = 0; i < 8; i++) {
                body[i] = p("body_" + (i + 1));
            }
            jaw = p("jaw");
            fin = p("tail_fin");
        }

        @Override
        public void setupAnim(EelState s) {
            super.setupAnim(s);
            float t = s.ageInTicks * (s.coiled ? 0.35f : 0.2f);
            for (int i = 0; i < 8; i++) {
                body[i].yRot += Mth.sin(t - i * 0.7f) * (s.coiled ? 0.32f : 0.18f);
                body[i].xRot += Mth.cos(t * 0.7f - i * 0.5f) * 0.05f;
            }
            fin.yRot += Mth.sin(t - 6.0f) * 0.3f;
            jaw.xRot += s.coiled ? 0.4f + Mth.sin(s.ageInTicks * 0.6f) * 0.15f : 0.08f;
        }
    }

    public static final class EelRenderer extends MobRenderer<TideEel, EelState, EelModel> {
        private static final Identifier TEX = Tex.of("tide_eel/tide_eel"), GLOW = Tex.of("tide_eel/tide_eel_emissive");

        public EelRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new EelModel(ctx.bakeLayer(EEL)), 0.0f);
            addLayer(new EmissiveLayer<>(this, s -> GLOW, s -> true));
        }

        @Override
        public Identifier getTextureLocation(EelState s) {
            return TEX;
        }

        @Override
        public EelState createRenderState() {
            return new EelState();
        }

        @Override
        public void extractRenderState(TideEel e, EelState s, float partial) {
            super.extractRenderState(e, s, partial);
            s.coiled = e.isCoiled();
        }
    }

    // ------------------------------------------------------------------ Drowned Ascendant (translucent)

    public static final class AscendantState extends LivingEntityRenderState {
        public int action;
    }

    public static final class AscendantModel extends WFModel<AscendantState> {
        private final ModelPart right, left, head, tablet;

        public AscendantModel(ModelPart root) {
            super(root, "drowned_ascendant", RenderTypes::entityTranslucent);
            right = p("right_arm");
            left = p("left_arm");
            head = p("head");
            tablet = p("tablet");
        }

        @Override
        public void setupAnim(AscendantState s) {
            super.setupAnim(s);
            float t = s.ageInTicks;
            head.yRot += s.yRot * Mth.DEG_TO_RAD;
            head.xRot += s.xRot * Mth.DEG_TO_RAD;
            switch (s.action) {
                case DrownedAscendant.READING -> {
                    right.xRot -= 0.9f;
                    left.xRot -= 0.9f;
                    head.xRot += 0.35f;
                    tablet.visible = true;
                }
                case DrownedAscendant.WRITING -> {
                    right.xRot -= 0.8f + Mth.sin(t * 0.3f) * 0.12f;
                    right.zRot += Mth.cos(t * 0.3f) * 0.08f;
                    left.xRot -= 0.9f;
                    tablet.visible = true;
                }
                default -> {
                    right.xRot -= 1.5f + s.xRot * Mth.DEG_TO_RAD;
                    tablet.visible = false;
                }
            }
            root().y += Mth.sin(t * 0.04f) * 0.8f;
        }
    }

    public static final class AscendantRenderer extends MobRenderer<DrownedAscendant, AscendantState, AscendantModel> {
        private static final Identifier TEX = Tex.of("drowned_ascendant/drowned_ascendant"), GLOW = Tex.of("drowned_ascendant/drowned_ascendant_emissive");

        public AscendantRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new AscendantModel(ctx.bakeLayer(ASCENDANT)), 0.0f);
            addLayer(new EmissiveLayer<>(this, s -> GLOW, s -> true));
        }

        @Override
        public Identifier getTextureLocation(AscendantState s) {
            return TEX;
        }

        @Override
        public AscendantState createRenderState() {
            return new AscendantState();
        }

        @Override
        public void extractRenderState(DrownedAscendant e, AscendantState s, float partial) {
            super.extractRenderState(e, s, partial);
            s.action = e.action();
        }

        @Override
        protected int getModelTint(AscendantState s) {
            return ARGB.color((int) (150 + 30 * Mth.sin(s.ageInTicks * 0.05f)), 255, 255, 255);
        }
    }

    // ------------------------------------------------------------------ Glimmer

    public static final class GlimmerModel extends WFModel<LivingEntityRenderState> {
        private final ModelPart tail;

        public GlimmerModel(ModelPart root) {
            super(root, "glimmer");
            tail = p("tail");
        }

        @Override
        public void setupAnim(LivingEntityRenderState s) {
            super.setupAnim(s);
            tail.yRot += Mth.sin(s.ageInTicks * 0.9f) * 0.5f;
        }
    }

    public static final class GlimmerRenderer extends MobRenderer<Glimmer, LivingEntityRenderState, GlimmerModel> {
        private static final Identifier TEX = Tex.of("glimmer/glimmer"), GLOW = Tex.of("glimmer/glimmer_emissive");

        public GlimmerRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new GlimmerModel(ctx.bakeLayer(GLIMMER)), 0.0f);
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
