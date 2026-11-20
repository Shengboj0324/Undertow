package dev.worldsfall.client.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.worldsfall.Worldsfall;
import dev.worldsfall.block.entity.EggCradleBlockEntity;
import dev.worldsfall.block.entity.GravityAnchorBlockEntity;
import dev.worldsfall.config.WFConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Block entity renderers: the Egg Cradle ritual (§10.2) and the Gravity Anchor core, halo and field (§9.8). */
public final class WFBlockEntityRenderers {
    private WFBlockEntityRenderers() {}

    static final int FULL = 0xF000F0;

    static void glowQuad(PoseStack.Pose p, VertexConsumer b, float x0, float y0, float x1, float y1, float z, int c) {
        b.addVertex(p, x0, y0, z).setColor(c);
        b.addVertex(p, x1, y0, z).setColor(c);
        b.addVertex(p, x1, y1, z).setColor(c);
        b.addVertex(p, x0, y1, z).setColor(c);
    }

    // ------------------------------------------------------------------ Egg Cradle
    public static final class CradleState extends BlockEntityRenderState {
        EggCradleBlockEntity.Stage stage = EggCradleBlockEntity.Stage.EMPTY;
        int glands;
        float progress, time;
        final MovingBlockRenderState egg = new MovingBlockRenderState();
    }

    public static final class CradleRenderer implements BlockEntityRenderer<EggCradleBlockEntity, CradleState> {
        public CradleRenderer(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public CradleState createRenderState() {
            return new CradleState();
        }

        @Override
        public void extractRenderState(EggCradleBlockEntity be, CradleState s, float partial, Vec3 cam, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
            BlockEntityRenderer.super.extractRenderState(be, s, partial, cam, crumble);
            s.stage = be.stage();
            s.glands = be.glands();
            float total = switch (be.stage()) {
                case ATTUNING -> EggCradleBlockEntity.ATTUNE_TICKS;
                case VIGIL -> be.vigilRequired();
                case HATCHING -> EggCradleBlockEntity.HATCH_ANIM_TICKS;
                case SLUMBER -> EggCradleBlockEntity.RESTORE_TICKS;
                default -> 1;
            };
            s.progress = Mth.clamp(be.progress() / total, 0, 1);
            s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 24000L) + partial;
            s.egg.blockState = Blocks.DRAGON_EGG.defaultBlockState();
            s.egg.blockPos = be.getBlockPos().above();
            s.egg.randomSeedPos = be.getBlockPos();
            if (be.getLevel() instanceof ClientLevel level) {
                s.egg.biome = level.getBiome(be.getBlockPos());
                s.egg.cardinalLighting = level.cardinalLighting();
                s.egg.lightEngine = level.getLightEngine();
            }
        }

        @Override
        public void submit(CradleState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            if (s.stage == EggCradleBlockEntity.Stage.EMPTY) {
                return;
            }
            boolean reduced = WFConfig.get(WFConfig.CLIENT.reducedFlashing, false);
            float pulse = 0.5f + 0.5f * Mth.sin(s.time * (s.stage == EggCradleBlockEntity.Stage.VIGIL ? 0.25f : 0.12f));
            pose.pushPose();
            pose.translate(0.5, 0.38, 0.5);
            if (s.stage == EggCradleBlockEntity.Stage.HATCHING) {
                float shake = (1 - s.progress) * 0.0f + s.progress * 0.08f;
                pose.translate(Mth.sin(s.time * 3.1f) * shake, 0, Mth.cos(s.time * 2.7f) * shake);
            }
            float scale = 0.92f + pulse * 0.02f;
            pose.scale(scale, scale, scale);
            pose.translate(-0.5, 0, -0.5);
            c.submitMovingBlock(pose, s.egg, 0);
            pose.popPose();
            // pink cracks, one per Glow Gland, brighter during the vigil (§10.2 step 3–4)
            int cracks = s.stage == EggCradleBlockEntity.Stage.NOURISHING ? s.glands : s.stage == EggCradleBlockEntity.Stage.ATTUNING ? 0 : 8;
            float bright = s.stage == EggCradleBlockEntity.Stage.VIGIL ? 0.6f + 0.4f * s.progress : 0.55f;
            pose.pushPose();
            pose.translate(0.5, 0.38, 0.5);
            int alpha = (int) (255 * Math.min(1f, bright * (0.75f + 0.25f * pulse)));
            c.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
                for (int i = 0; i < cracks; i++) {
                    float a = i * 2.39996f;
                    float y0 = 0.15f + (i % 4) * 0.17f;
                    float dx = Mth.cos(a), dz = Mth.sin(a);
                    float r = 0.505f;
                    // a zig-zag crack on the egg's surface
                    for (int k = 0; k < 4; k++) {
                        float ya = y0 + k * 0.08f, yb = ya + 0.08f;
                        float off = (k % 2 == 0 ? 1 : -1) * 0.04f;
                        float x0 = dx * r + dz * off, z0 = dz * r - dx * off;
                        float x1 = dx * r - dz * off, z1 = dz * r + dx * off;
                        int col = ARGB.color(alpha, 255, 122, 217);
                        b.addVertex(p, x0, ya, z0).setColor(col);
                        b.addVertex(p, x0 + dz * 0.03f, ya, z0 - dx * 0.03f).setColor(col);
                        b.addVertex(p, x1 + dz * 0.03f, yb, z1 - dx * 0.03f).setColor(col);
                        b.addVertex(p, x1, yb, z1).setColor(col);
                    }
                }
            });
            // light rays escaping during the vigil and the hatch burst
            if (s.stage == EggCradleBlockEntity.Stage.VIGIL || s.stage == EggCradleBlockEntity.Stage.HATCHING) {
                float rayLen = s.stage == EggCradleBlockEntity.Stage.HATCHING ? 1.2f + s.progress * 3.5f : 0.6f + s.progress * 1.6f;
                int rayAlpha = reduced ? 50 : (s.stage == EggCradleBlockEntity.Stage.HATCHING ? 200 : 110);
                pose.translate(0, 0.45, 0);
                pose.rotate(Axis.YP.rotation(s.time * 0.01f));
                c.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
                    for (int i = 0; i < 12; i++) {
                        float a = i * Mth.TWO_PI / 12;
                        float el = Mth.sin(i * 1.7f) * 0.6f;
                        float x = Mth.cos(a) * Mth.cos(el), y = Mth.sin(el), z = Mth.sin(a) * Mth.cos(el);
                        float px = -z * 0.06f, pz = x * 0.06f;
                        b.addVertex(p, 0, 0, 0).setColor(ARGB.color(rayAlpha, 255, 220, 245));
                        b.addVertex(p, x * rayLen + px, y * rayLen, z * rayLen + pz).setColor(ARGB.color(0, 255, 122, 217));
                        b.addVertex(p, x * rayLen - px, y * rayLen, z * rayLen - pz).setColor(ARGB.color(0, 255, 122, 217));
                        b.addVertex(p, 0, 0, 0).setColor(ARGB.color(rayAlpha, 255, 220, 245));
                    }
                });
            }
            pose.popPose();
        }

        @Override
        public int getViewDistance() {
            return 96;
        }
    }

    // ------------------------------------------------------------------ Gravity Anchor
    public static final class AnchorState extends BlockEntityRenderState {
        float time;
        int radius;
        float shell;
    }

    public static final class AnchorRenderer implements BlockEntityRenderer<GravityAnchorBlockEntity, AnchorState> {
        private static final Identifier CORE = Identifier.fromNamespaceAndPath(Worldsfall.MODID, "textures/block/gravity_anchor_core.png");

        public AnchorRenderer(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public AnchorState createRenderState() {
            return new AnchorState();
        }

        @Override
        public void extractRenderState(GravityAnchorBlockEntity be, AnchorState s, float partial, Vec3 cam, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
            BlockEntityRenderer.super.extractRenderState(be, s, partial, cam, crumble);
            s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 24000L) + partial;
            s.radius = be.radius();
            double d = cam.distanceTo(Vec3.atCenterOf(be.getBlockPos()));
            s.shell = (float) Mth.clamp(1.0 - Math.abs(d - s.radius) / 4.0, 0, 1); // visible only near the boundary (§9.8)
        }

        @Override
        public void submit(AnchorState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.rotate(Axis.YP.rotation(s.time * 0.02f));
            pose.rotate(Axis.XP.rotation(0.3f));
            // heavy dark core (slowly rotating)
            float h = 0.27f;
            c.submitCustomGeometry(pose, RenderTypes.entityCutout(CORE), (p, b) -> cube(p, b, h, s.lightCoords));
            pose.popPose();
            // thin teal halo spinning at a slight tilt
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.rotate(Axis.ZP.rotation(0.35f));
            pose.rotate(Axis.YP.rotation(-s.time * 0.05f));
            c.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
                int n = 40;
                for (int i = 0; i < n; i++) {
                    float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
                    float r0 = 0.52f, r1 = 0.58f;
                    int col = ARGB.color(i % 5 == 0 ? 255 : 170, 58, 224, 208);
                    b.addVertex(p, Mth.cos(a0) * r0, 0, Mth.sin(a0) * r0).setColor(col);
                    b.addVertex(p, Mth.cos(a0) * r1, 0, Mth.sin(a0) * r1).setColor(col);
                    b.addVertex(p, Mth.cos(a1) * r1, 0, Mth.sin(a1) * r1).setColor(col);
                    b.addVertex(p, Mth.cos(a1) * r0, 0, Mth.sin(a1) * r0).setColor(col);
                }
            });
            pose.popPose();
            if (s.shell > 0.01f) {
                // the field edge: a faint teal sphere, only visible within 4 blocks of the boundary
                pose.pushPose();
                pose.translate(0.5, 0.5, 0.5);
                int alpha = (int) (40 * s.shell);
                float r = s.radius;
                c.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
                    int lat = 18, lon = 36;
                    for (int i = 0; i < lat; i++) {
                        float t0 = Mth.PI * i / lat - Mth.HALF_PI, t1 = Mth.PI * (i + 1) / lat - Mth.HALF_PI;
                        for (int j = 0; j < lon; j++) {
                            float p0 = Mth.TWO_PI * j / lon, p1 = Mth.TWO_PI * (j + 1) / lon;
                            int col = ARGB.color((j + i) % 3 == 0 ? alpha : alpha / 2, 120, 255, 240);
                            b.addVertex(p, r * Mth.cos(t0) * Mth.cos(p0), r * Mth.sin(t0), r * Mth.cos(t0) * Mth.sin(p0)).setColor(col);
                            b.addVertex(p, r * Mth.cos(t0) * Mth.cos(p1), r * Mth.sin(t0), r * Mth.cos(t0) * Mth.sin(p1)).setColor(col);
                            b.addVertex(p, r * Mth.cos(t1) * Mth.cos(p1), r * Mth.sin(t1), r * Mth.cos(t1) * Mth.sin(p1)).setColor(col);
                            b.addVertex(p, r * Mth.cos(t1) * Mth.cos(p0), r * Mth.sin(t1), r * Mth.cos(t1) * Mth.sin(p0)).setColor(col);
                        }
                    }
                });
                pose.popPose();
            }
        }

        static void cube(PoseStack.Pose p, VertexConsumer b, float h, int light) {
            float[][] faces = {
                {-h, -h, h, h, -h, h, h, h, h, -h, h, h, 0, 0, 1}, {h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h, 0, 0, -1},
                {h, -h, h, h, -h, -h, h, h, -h, h, h, h, 1, 0, 0}, {-h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h, -1, 0, 0},
                {-h, h, h, h, h, h, h, h, -h, -h, h, -h, 0, 1, 0}, {-h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h, 0, -1, 0}};
            float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
            for (float[] f : faces) {
                for (int v = 0; v < 4; v++) {
                    b.addVertex(p, f[v * 3], f[v * 3 + 1], f[v * 3 + 2]).setColor(-1).setUv(uv[v][0], uv[v][1]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light).setNormal(p, f[12], f[13], f[14]);
                }
            }
        }

        @Override
        public boolean shouldRenderOffScreen() {
            return true;
        }

        @Override
        public int getViewDistance() {
            return 128;
        }
    }
}
