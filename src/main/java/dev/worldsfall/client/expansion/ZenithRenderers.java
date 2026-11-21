package dev.worldsfall.client.expansion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.worldsfall.Worldsfall;
import dev.worldsfall.client.entity.EmissiveLayer;
import dev.worldsfall.client.entity.Tex;
import dev.worldsfall.client.model.WFModel;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.zenith.GaleHarrier;
import dev.worldsfall.zenith.LeviathanDrifter;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Renderers for the Zenith's creatures (release 1.6). */
public final class ZenithRenderers {
    public static final ModelLayerLocation LEVIATHAN = layer("leviathan_drifter");
    public static final ModelLayerLocation HARRIER = layer("gale_harrier");
    public static final float LEVIATHAN_SCALE = 4.0f;

    private ZenithRenderers() {}

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(Worldsfall.MODID, name), "main");
    }

    // ------------------------------------------------------------------ Leviathan Drifter

    public static final class LeviathanState extends LivingEntityRenderState {
        public int barnacles;
        public float bodyYaw;
    }

    public static final class LeviathanModel extends WFModel<LeviathanState> {
        private final ModelPart leftFin, rightFin, leftTip, rightTip, tail1, tail2, fluke, jaw;

        public LeviathanModel(ModelPart root) {
            super(root, "leviathan_drifter");
            leftFin = p("left_fin");
            rightFin = p("right_fin");
            leftTip = p("left_fin_tip");
            rightTip = p("right_fin_tip");
            tail1 = p("tail_1");
            tail2 = p("tail_2");
            fluke = p("tail_fluke");
            jaw = p("jaw");
        }

        @Override
        public void setupAnim(LeviathanState s) {
            super.setupAnim(s);
            float t = s.ageInTicks * 0.045f;
            float beat = Mth.sin(t);
            leftFin.zRot += beat * 0.28f;
            rightFin.zRot -= beat * 0.28f;
            leftTip.zRot += Mth.sin(t - 0.7f) * 0.22f;
            rightTip.zRot -= Mth.sin(t - 0.7f) * 0.22f;
            tail1.yRot += Mth.sin(t * 0.8f) * 0.08f;
            tail2.yRot += Mth.sin(t * 0.8f - 0.6f) * 0.12f;
            fluke.xRot += Mth.sin(t * 0.8f - 1.2f) * 0.14f;
            jaw.xRot += 0.05f + Mth.sin(t * 0.3f) * 0.05f;
        }
    }

    public static final class LeviathanRenderer extends MobRenderer<LeviathanDrifter, LeviathanState, LeviathanModel> {
        private static final Identifier TEX = Tex.of("leviathan_drifter/leviathan_drifter"), GLOW = Tex.of("leviathan_drifter/leviathan_drifter_emissive");
        /** The garden on its back: a bed of Pale Loam, chorus, a glowbulb or two. */
        private static final int[][] GARDEN = {{-1, 0, 0}, {0, 0, 0}, {1, 0, 0}, {-1, 0, 1}, {0, 0, 1}, {1, 0, 1}, {0, 0, 2}, {0, 0, -1}};
        private final MovingBlockRenderState block = new MovingBlockRenderState();

        public LeviathanRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new LeviathanModel(ctx.bakeLayer(LEVIATHAN)), 0.0f);
            addLayer(new EmissiveLayer<>(this, s -> GLOW, s -> true));
        }

        @Override
        public Identifier getTextureLocation(LeviathanState s) {
            return TEX;
        }

        @Override
        public LeviathanState createRenderState() {
            return new LeviathanState();
        }

        @Override
        public void extractRenderState(LeviathanDrifter e, LeviathanState s, float partial) {
            super.extractRenderState(e, s, partial);
            s.barnacles = e.barnacles();
            s.bodyYaw = e.yBodyRot;
            if (e.level() instanceof ClientLevel level) {
                block.blockPos = e.blockPosition().above(6);
                block.randomSeedPos = e.blockPosition();
                block.biome = level.getBiome(e.blockPosition());
                block.cardinalLighting = level.cardinalLighting();
                block.lightEngine = level.getLightEngine();
            }
        }

        @Override
        protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(LeviathanDrifter e, float partial) {
            return super.getBoundingBoxForCulling(e, partial).inflate(26, 8, 26);
        }

        @Override
        protected void scale(LeviathanState s, PoseStack pose) {
            // the model's back sits at the top of the (solid) hitbox
            pose.translate(0.0, -2.6, 0.0);
            pose.scale(LEVIATHAN_SCALE, LEVIATHAN_SCALE, LEVIATHAN_SCALE);
        }

        @Override
        public void submit(LeviathanState s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            super.submit(s, pose, collector, camera);
            if (block.lightEngine == null) {
                return;
            }
            pose.pushPose();
            pose.rotate(Axis.YP.rotationDegrees(180.0f - s.bodyYaw));
            pose.translate(0.0, s.boundingBoxHeight, 2.0);
            for (int i = 0; i < GARDEN.length; i++) {
                int[] g = GARDEN[i];
                place(pose, collector, s, g[0], g[1], g[2], WFBlocks.PALE_LOAM.get().defaultBlockState());
            }
            place(pose, collector, s, -1, 1, 0, Blocks.CHORUS_PLANT.defaultBlockState());
            place(pose, collector, s, -1, 2, 0, Blocks.CHORUS_FLOWER.defaultBlockState());
            place(pose, collector, s, 1, 1, 1, WFBlocks.LILAC_TUFT.get().defaultBlockState());
            place(pose, collector, s, 0, 1, 2, WFBlocks.GLOWBULB_BLOCK.get().defaultBlockState());
            pose.popPose();
        }

        private void place(PoseStack pose, SubmitNodeCollector collector, LeviathanState s, int x, int y, int z, BlockState state) {
            pose.pushPose();
            pose.translate(x - 0.5, y - 1.0, z - 0.5);
            block.blockState = state;
            collector.submitMovingBlock(pose, block, s.outlineColor);
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------ Gale Harrier

    public static final class HarrierState extends LivingEntityRenderState {
        public boolean diving;
    }

    public static final class HarrierModel extends WFModel<HarrierState> {
        private final ModelPart leftWing, rightWing, leftTip, rightTip, tail, body;

        public HarrierModel(ModelPart root) {
            super(root, "gale_harrier");
            body = p("body");
            leftWing = p("left_wing");
            rightWing = p("right_wing");
            leftTip = p("left_wing_tip");
            rightTip = p("right_wing_tip");
            tail = p("tail");
        }

        @Override
        public void setupAnim(HarrierState s) {
            super.setupAnim(s);
            body.xRot += s.xRot * Mth.DEG_TO_RAD;
            if (s.diving) {
                // wings swept back for the stoop
                leftWing.zRot += 0.15f;
                rightWing.zRot -= 0.15f;
                leftWing.yRot += 0.9f;
                rightWing.yRot -= 0.9f;
                leftTip.yRot += 0.5f;
                rightTip.yRot -= 0.5f;
            } else {
                float flap = Mth.sin(s.ageInTicks * 0.55f) * 0.55f;
                leftWing.zRot += flap;
                rightWing.zRot -= flap;
                leftTip.zRot += flap * 0.6f;
                rightTip.zRot -= flap * 0.6f;
            }
            tail.xRot += Mth.sin(s.ageInTicks * 0.2f) * 0.08f;
        }
    }

    public static final class HarrierRenderer extends MobRenderer<GaleHarrier, HarrierState, HarrierModel> {
        private static final Identifier TEX = Tex.of("gale_harrier/gale_harrier"), GLOW = Tex.of("gale_harrier/gale_harrier_emissive");

        public HarrierRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new HarrierModel(ctx.bakeLayer(HARRIER)), 0.3f);
            addLayer(new EmissiveLayer<>(this, s -> GLOW, s -> true));
        }

        @Override
        public Identifier getTextureLocation(HarrierState s) {
            return TEX;
        }

        @Override
        public HarrierState createRenderState() {
            return new HarrierState();
        }

        @Override
        public void extractRenderState(GaleHarrier e, HarrierState s, float partial) {
            super.extractRenderState(e, s, partial);
            s.diving = e.mode() == GaleHarrier.DIVE;
        }

        @Override
        protected void scale(HarrierState s, PoseStack pose) {
            pose.scale(1.6f, 1.6f, 1.6f);
        }
    }
}
