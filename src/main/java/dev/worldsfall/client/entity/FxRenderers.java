package dev.worldsfall.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.worldsfall.client.model.WFStates;
import dev.worldsfall.config.WFConfig;
import dev.worldsfall.entity.FlyingBlock;
import dev.worldsfall.entity.LurkerSnare;
import dev.worldsfall.entity.RiftHook;
import dev.worldsfall.entity.TetherPylon;
import dev.worldsfall.entity.TheUnmade;
import dev.worldsfall.entity.Tidewisp;
import dev.worldsfall.entity.UnmadeShard;
import dev.worldsfall.entity.VoidCapsule;
import dev.worldsfall.world.fracture.FractureArena;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Renderers for effects-heavy entities: The Unmade, pylons, capsules, the tether, flying blocks, wisps, projectiles. */
public final class FxRenderers {
    private FxRenderers() {}

    static final int FULL_BRIGHT = 0xF000F0;

    /** Camera-facing quad of given radius with an emissive texture. */
    static void billboard(PoseStack pose, SubmitNodeCollector c, Quaternionf cam, Identifier tex, float radius, int color) {
        pose.pushPose();
        pose.rotate(cam);
        c.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(tex), (p, b) -> {
            quad(p, b, radius, color);
        });
        pose.popPose();
    }

    static void quad(PoseStack.Pose p, VertexConsumer b, float r, int color) {
        b.addVertex(p, -r, -r, 0).setColor(color).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 0, 1);
        b.addVertex(p, r, -r, 0).setColor(color).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 0, 1);
        b.addVertex(p, r, r, 0).setColor(color).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 0, 1);
        b.addVertex(p, -r, r, 0).setColor(color).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 0, 1);
    }

    /** Additive glowing ribbon between two points (POSITION_COLOR lightning type), width w, facing the camera roughly. */
    static void ribbon(PoseStack.Pose p, VertexConsumer b, Vector3f a, Vector3f z, float w0, float w1, int c0, int c1, Vector3f side) {
        b.addVertex(p, a.x - side.x * w0, a.y - side.y * w0, a.z - side.z * w0).setColor(c0);
        b.addVertex(p, a.x + side.x * w0, a.y + side.y * w0, a.z + side.z * w0).setColor(c0);
        b.addVertex(p, z.x + side.x * w1, z.y + side.y * w1, z.z + side.z * w1).setColor(c1);
        b.addVertex(p, z.x - side.x * w1, z.y - side.y * w1, z.z - side.z * w1).setColor(c1);
    }

    // ------------------------------------------------------------------ The Unmade (§13.10)
    public static final class UnmadeRenderer extends LivingEntityRenderer<TheUnmade, WFStates.Unmade, EntityModel<WFStates.Unmade>> {
        private static final Identifier CORE = Tex.of("the_unmade/core"), TENDRIL = Tex.of("the_unmade/tendril");
        private static final Identifier SHARD = Tex.of("the_unmade/shard"), SHARD_GLOW = Tex.of("the_unmade/shard_emissive");
        private final ModelPart shard;

        public UnmadeRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new EntityModel<>(ctx.bakeLayer(WFLayers.UNMADE_SHARD)) {}, 0.0f);
            this.shard = ctx.bakeLayer(WFLayers.UNMADE_SHARD);
        }

        @Override
        public Identifier getTextureLocation(WFStates.Unmade s) {
            return SHARD;
        }

        @Override
        public WFStates.Unmade createRenderState() {
            return new WFStates.Unmade();
        }

        @Override
        public boolean shouldRender(TheUnmade e, Frustum f, double x, double y, double z, float partial) {
            return true;
        }

        @Override
        public void extractRenderState(TheUnmade e, WFStates.Unmade s, float partial) {
            super.extractRenderState(e, s, partial);
            s.phase = e.phase();
            s.rings = e.rings();
            s.exposed = e.isExposed();
            s.attack = e.attack().ordinal();
            s.attackTicks = e.attackTicks() + partial;
            s.freeze = e.freezeTicks();
            s.hitFlash = e.hitFlash();
            s.active = e.isActive();
            s.pulsePeriod = e.pulsePeriod();
            s.teleA = new Vector3f(e.telegraphA()).sub((float) s.x, (float) s.y, (float) s.z);
            s.teleB = new Vector3f(e.telegraphB()).sub((float) s.x, (float) s.y, (float) s.z);
        }

        @Override
        public void submit(WFStates.Unmade s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            boolean reduced = WFConfig.get(WFConfig.CLIENT.reducedFlashing, false);
            float t = s.freeze > 0 ? s.ageInTicks - s.freeze * 0.98f : s.ageInTicks; // the world holds still
            boolean finale = s.phase == TheUnmade.PHASE_FINALE;
            float pulse = 0.5f + 0.5f * Mth.sin(t * Mth.TWO_PI / Math.max(8f, s.pulsePeriod));
            pose.pushPose();
            pose.translate(0, 2.0, 0);
            // core: white -> hot pink -> magenta, layered for bloom; calm white after the final blow
            int coreTint = finale ? ARGB.color(255, 255, 255, 255) : -1;
            billboard(pose, c, camera.orientation, CORE, 2.1f + pulse * 0.15f, coreTint);
            billboard(pose, c, camera.orientation, CORE, 4.4f + pulse * 0.4f, ARGB.color(reduced ? 70 : 120, 255, finale ? 255 : 120, finale ? 255 : 210));
            billboard(pose, c, camera.orientation, CORE, 8.5f, ARGB.color(reduced ? 25 : 45, 255, finale ? 240 : 63, finale ? 255 : 180));
            if (s.hitFlash > 0) {
                float k = s.hitFlash / 8f;
                billboard(pose, c, camera.orientation, CORE, 3.0f + (1 - k) * 3f, ARGB.color((int) ((reduced ? 60 : 200) * k), 255, 255, 255));
            }
            // three orbiting rings of black shards edged magenta, each shard spinning on its own axis
            float[] radii = {4.6f, 5.9f, 7.0f};
            float[] tilt = {0.35f, -0.55f, 0.95f};
            for (int r = 0; r < 3; r++) {
                boolean intact = (s.rings & (1 << r)) != 0 && s.phase == TheUnmade.PHASE_SHELL;
                int count = 9 + r * 2;
                for (int k = 0; k < count; k++) {
                    float a = k * Mth.TWO_PI / count + t * (0.012f + r * 0.006f) * (r % 2 == 0 ? 1 : -1);
                    float rad = intact ? radii[r] : radii[r] + 5f + (k % 3) * 2f;
                    if (!intact && s.phase >= TheUnmade.PHASE_UNMADE && k % 2 == 1) {
                        continue;
                    }
                    pose.pushPose();
                    pose.rotate(Axis.XP.rotation(tilt[r]));
                    pose.translate(Mth.cos(a) * rad, intact ? 0 : Mth.sin(a * 3 + t * 0.01f) * 3f, Mth.sin(a) * rad);
                    pose.rotate(Axis.YP.rotation(t * 0.05f + k));
                    pose.rotate(Axis.ZP.rotation(t * 0.03f + k * 0.7f));
                    pose.scale(0.8f, 0.8f, 0.8f);
                    c.submitModelPart(shard, pose, RenderTypes.entityCutout(SHARD), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
                    c.order(1).submitModelPart(shard, pose, RenderTypes.eyes(SHARD_GLOW), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
                    pose.popPose();
                }
            }
            pose.popPose();
            submitTendrils(s, pose, c, t);
            submitTelegraphs(s, pose, c, t, reduced);
        }

        /** Dark, smooth, tapering ribbons edged in magenta, swaying like seaweed in slow water, reaching for the islands. */
        private void submitTendrils(WFStates.Unmade s, PoseStack pose, SubmitNodeCollector c, float t) {
            if (s.phase == TheUnmade.PHASE_FINALE) {
                return;
            }
            Vector3f origin = new Vector3f(0, 2, 0);
            c.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(TENDRIL), (p, b) -> {
                for (int i = 0; i < 9; i++) {
                    double a = i * Math.PI * 2 / 9;
                    Vector3f target = new Vector3f((float) (FractureArena.CENTER.getX() + 0.5 + Math.cos(a) * FractureArena.RING_RADIUS - s.x),
                        (float) (FractureArena.CENTER.getY() + 1 - s.y), (float) (FractureArena.CENTER.getZ() + 0.5 + Math.sin(a) * FractureArena.RING_RADIUS - s.z));
                    target.lerp(origin, 0.35f);
                    tendril(p, b, origin, target, t + i * 13, 0.55f);
                }
                if (s.attack == TheUnmade.Attack.GRAB.ordinal() || (s.attack == TheUnmade.Attack.SWEEP.ordinal() && s.attackTicks > 25)) {
                    Vector3f target = s.attack == TheUnmade.Attack.GRAB.ordinal() ? s.teleB : new Vector3f(s.teleA).lerp(s.teleB, Mth.clamp((s.attackTicks - 25) / 10f, 0, 1));
                    tendril(p, b, origin, target, t * 2, 0.9f);
                }
            });
        }

        private static void tendril(PoseStack.Pose p, VertexConsumer b, Vector3f from, Vector3f to, float t, float width) {
            int segs = 16;
            Vector3f prev = new Vector3f(from);
            for (int i = 1; i <= segs; i++) {
                float u = i / (float) segs;
                Vector3f pt = new Vector3f(from).lerp(to, u);
                float sway = Mth.sin(t * 0.05f + u * 5f) * 1.4f * Mth.sin(u * Mth.PI);
                pt.add(sway, Mth.sin(t * 0.04f + u * 3f) * 0.6f * u, Mth.cos(t * 0.045f + u * 4f) * 1.1f * Mth.sin(u * Mth.PI));
                Vector3f dir = new Vector3f(pt).sub(prev).normalize();
                Vector3f side = new Vector3f(dir).cross(0, 1, 0);
                if (side.lengthSquared() < 1e-4f) {
                    side.set(1, 0, 0);
                }
                side.normalize();
                float w0 = width * (1 - (u - 1f / segs) * 0.85f), w1 = width * (1 - u * 0.85f);
                int col = ARGB.color(235, 255, 255, 255);
                b.addVertex(p, prev.x - side.x * w0, prev.y, prev.z - side.z * w0).setColor(col).setUv(0, u - 1f / segs).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 1, 0);
                b.addVertex(p, prev.x + side.x * w0, prev.y, prev.z + side.z * w0).setColor(col).setUv(1, u - 1f / segs).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 1, 0);
                b.addVertex(p, pt.x + side.x * w1, pt.y, pt.z + side.z * w1).setColor(col).setUv(1, u).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 1, 0);
                b.addVertex(p, pt.x - side.x * w1, pt.y, pt.z - side.z * w1).setColor(col).setUv(0, u).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 1, 0);
                prev = pt;
            }
        }

        /** Shapes on the ground/air for every telegraph so nothing relies on colour alone (§13.10 accessibility). */
        private void submitTelegraphs(WFStates.Unmade s, PoseStack pose, SubmitNodeCollector c, float t, boolean reduced) {
            TheUnmade.Attack attack = TheUnmade.Attack.values()[Mth.clamp(s.attack, 0, TheUnmade.Attack.values().length - 1)];
            if (attack == TheUnmade.Attack.NONE) {
                return;
            }
            c.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
                switch (attack) {
                    case SWEEP -> { // a dark line across the fragment, with bright edges
                        Vector3f a = new Vector3f(s.teleA).add(0, 0.15f, 0), z = new Vector3f(s.teleB).add(0, 0.15f, 0);
                        Vector3f side = new Vector3f(z).sub(a).cross(0, 1, 0).normalize();
                        int col = ARGB.color(150, 255, 63, 180);
                        ribbon(p, b, a, z, 0.6f, 0.6f, col, col, side);
                    }
                    case LENS -> ring(p, b, new Vector3f(s.teleA).add(0, 0.12f, 0), 4.0f + Mth.sin(t * 0.5f) * 0.15f, 0.45f, ARGB.color(170, 184, 255, 247));
                    case PULSE -> { // teal ring expanding up from below
                        float k = Mth.clamp(s.attackTicks / 40f, 0, 1);
                        ring(p, b, new Vector3f(0, -40 + 40 * k, 0), 6 + 50 * k, 1.6f, ARGB.color(reduced ? 90 : 170, 58, 224, 208));
                    }
                    case GRAB -> ring(p, b, new Vector3f(s.teleB).add(0, -0.9f, 0), 1.2f, 0.3f, ARGB.color(200, 255, 120, 220));
                    case VOLLEY, SWARM -> ring(p, b, new Vector3f(0, 2, 0), 3.2f + (s.attackTicks / 20f) * 2f, 0.4f, ARGB.color(160, 255, 200, 240));
                    default -> {
                    }
                }
            });
        }

        static void ring(PoseStack.Pose p, VertexConsumer b, Vector3f c, float r, float w, int col) {
            int n = 48;
            for (int i = 0; i < n; i++) {
                float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
                b.addVertex(p, c.x + Mth.cos(a0) * r, c.y, c.z + Mth.sin(a0) * r).setColor(col);
                b.addVertex(p, c.x + Mth.cos(a0) * (r + w), c.y, c.z + Mth.sin(a0) * (r + w)).setColor(ARGB.color(0, 0, 0, 0));
                b.addVertex(p, c.x + Mth.cos(a1) * (r + w), c.y, c.z + Mth.sin(a1) * (r + w)).setColor(ARGB.color(0, 0, 0, 0));
                b.addVertex(p, c.x + Mth.cos(a1) * r, c.y, c.z + Mth.sin(a1) * r).setColor(col);
                b.addVertex(p, c.x + Mth.cos(a0) * (r - w), c.y, c.z + Mth.sin(a0) * (r - w)).setColor(ARGB.color(0, 0, 0, 0));
                b.addVertex(p, c.x + Mth.cos(a0) * r, c.y, c.z + Mth.sin(a0) * r).setColor(col);
                b.addVertex(p, c.x + Mth.cos(a1) * r, c.y, c.z + Mth.sin(a1) * r).setColor(col);
                b.addVertex(p, c.x + Mth.cos(a1) * (r - w), c.y, c.z + Mth.sin(a1) * (r - w)).setColor(ARGB.color(0, 0, 0, 0));
            }
        }
    }

    // ------------------------------------------------------------------ Tether Pylon
    public static final class PylonState extends EntityRenderState {
        float flash;
        Vector3f toCore = new Vector3f();
        boolean hasCore;
    }

    public static final class PylonRenderer extends EntityRenderer<TetherPylon, PylonState> {
        private static final Identifier TEX = Tex.of("tether_pylon/tether_pylon"), GLOW = Tex.of("tether_pylon/tether_pylon_emissive");
        private final ModelPart crystal;

        public PylonRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            crystal = ctx.bakeLayer(WFLayers.TETHER_PYLON);
        }

        @Override
        public PylonState createRenderState() {
            return new PylonState();
        }

        @Override
        public boolean shouldRender(TetherPylon e, Frustum f, double x, double y, double z, float partial) {
            return true;
        }

        @Override
        public void extractRenderState(TetherPylon e, PylonState s, float partial) {
            super.extractRenderState(e, s, partial);
            s.flash = e.flash();
            TheUnmade boss = e.level().getEntitiesOfClass(TheUnmade.class, e.getBoundingBox().inflate(96)).stream().findFirst().orElse(null);
            s.hasCore = boss != null && boss.phase() == TheUnmade.PHASE_SHELL;
            if (boss != null) {
                Vec3 d = boss.getPosition(partial).add(0, 2, 0).subtract(e.getPosition(partial));
                s.toCore = new Vector3f((float) d.x, (float) d.y, (float) d.z);
            }
        }

        @Override
        public void submit(PylonState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            pose.pushPose();
            pose.translate(0, 0.9 + Mth.sin(s.ageInTicks * 0.08f) * 0.1f, 0);
            pose.rotate(Axis.YP.rotation(s.ageInTicks * 0.03f));
            pose.rotate(Axis.XP.rotationDegrees(180));
            int light = s.flash > 0 ? FULL_BRIGHT : s.lightCoords;
            c.submitModelPart(crystal, pose, RenderTypes.entityCutout(TEX), light, OverlayTexture.NO_OVERLAY, null);
            c.order(1).submitModelPart(crystal, pose, RenderTypes.eyes(GLOW), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
            pose.popPose();
            if (s.hasCore) {
                // visible beam to the Core
                c.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
                    Vector3f a = new Vector3f(0, 1.2f, 0), z = s.toCore;
                    Vector3f side = new Vector3f(z).sub(a).cross(0, 1, 0).normalize();
                    float wob = 0.12f + 0.04f * Mth.sin(s.ageInTicks * 0.3f);
                    ribbon(p, b, a, z, wob, wob * 0.5f, ARGB.color(200, 255, 80, 200), ARGB.color(60, 255, 180, 240), side);
                    Vector3f up = new Vector3f(z).sub(a).cross(side).normalize();
                    ribbon(p, b, a, z, wob, wob * 0.5f, ARGB.color(200, 255, 80, 200), ARGB.color(60, 255, 180, 240), up);
                });
            }
            super.submit(s, pose, c, camera);
        }
    }

    // ------------------------------------------------------------------ Void Capsule (§9.2): capsule + teal beam visible from 256 blocks
    public static final class CapsuleState extends EntityRenderState {
    }

    public static final class CapsuleRenderer extends EntityRenderer<VoidCapsule, CapsuleState> {
        private static final Identifier TEX = Tex.of("void_capsule/void_capsule"), GLOW = Tex.of("void_capsule/void_capsule_emissive");
        private static final Identifier BEAM = Identifier.withDefaultNamespace("textures/entity/beacon/beacon_beam.png");
        private final ModelPart model;

        public CapsuleRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            model = ctx.bakeLayer(WFLayers.VOID_CAPSULE);
        }

        @Override
        public CapsuleState createRenderState() {
            return new CapsuleState();
        }

        @Override
        public boolean shouldRender(VoidCapsule e, Frustum f, double x, double y, double z, float partial) {
            return true;
        }

        @Override
        public void submit(CapsuleState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            pose.pushPose();
            pose.translate(0, 1.5, 0);
            pose.rotate(Axis.XP.rotationDegrees(180));
            pose.rotate(Axis.YP.rotation(s.ageInTicks * 0.02f));
            c.submitModelPart(model, pose, RenderTypes.entityTranslucent(TEX), s.lightCoords, OverlayTexture.NO_OVERLAY, null);
            c.order(1).submitModelPart(model, pose, RenderTypes.eyes(GLOW), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
            pose.popPose();
            pose.pushPose();
            pose.translate(-0.5, 0, -0.5);
            BeaconRenderer.submitBeaconBeam(pose, c, BEAM, 1.0f, s.ageInTicks, 1, 256, ARGB.color(255, 58, 224, 208), 0.08f, 0.16f);
            pose.popPose();
            super.submit(s, pose, c, camera);
        }
    }

    // ------------------------------------------------------------------ Rift Tether hook + dashed teal line (§9.3)
    public static final class HookState extends EntityRenderState {
        boolean latched;
        Vector3f toHand = new Vector3f();
        boolean hasOwner;
    }

    public static final class HookRenderer extends EntityRenderer<RiftHook, HookState> {
        private static final Identifier TEX = Tex.of("rift_hook/rift_hook"), GLOW = Tex.of("rift_hook/rift_hook_emissive");
        private final ModelPart model;

        public HookRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            model = ctx.bakeLayer(WFLayers.RIFT_HOOK);
        }

        @Override
        public HookState createRenderState() {
            return new HookState();
        }

        @Override
        public boolean shouldRender(RiftHook e, Frustum f, double x, double y, double z, float partial) {
            return true;
        }

        @Override
        public void extractRenderState(RiftHook e, HookState s, float partial) {
            super.extractRenderState(e, s, partial);
            s.latched = e.isLatched();
            if (e.getOwner() instanceof Player owner) {
                s.hasOwner = true;
                Vec3 hand = owner.getPosition(partial).add(0, owner.getBbHeight() * 0.62, 0)
                    .add(owner.getViewVector(partial).cross(new Vec3(0, 1, 0)).normalize().scale(-0.35));
                Vec3 d = hand.subtract(e.getPosition(partial));
                s.toHand = new Vector3f((float) d.x, (float) d.y, (float) d.z);
            } else {
                s.hasOwner = false;
            }
        }

        @Override
        public void submit(HookState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            pose.pushPose();
            pose.rotate(Axis.XP.rotationDegrees(180));
            c.submitModelPart(model, pose, RenderTypes.entityCutout(TEX), s.lightCoords, OverlayTexture.NO_OVERLAY, null);
            if (s.latched) {
                c.order(1).submitModelPart(model, pose, RenderTypes.eyes(GLOW), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
            }
            pose.popPose();
            if (s.hasOwner) {
                c.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
                    int dashes = Math.max(4, (int) (s.toHand.length() * 2));
                    for (int i = 0; i < dashes; i += 2) {
                        Vector3f a = new Vector3f(s.toHand).mul(i / (float) dashes);
                        Vector3f z = new Vector3f(s.toHand).mul((i + 1) / (float) dashes);
                        a.y -= Mth.sin(i / (float) dashes * Mth.PI) * (s.latched ? 0.0f : 0.4f);
                        z.y -= Mth.sin((i + 1) / (float) dashes * Mth.PI) * (s.latched ? 0.0f : 0.4f);
                        Vector3f side = new Vector3f(z).sub(a).cross(0, 1, 0);
                        if (side.lengthSquared() < 1e-5f) {
                            side.set(1, 0, 0);
                        }
                        side.normalize();
                        int col = ARGB.color(220, 58, 224, 208);
                        ribbon(p, b, a, z, 0.035f, 0.035f, col, col, side);
                        Vector3f up = new Vector3f(z).sub(a).cross(side).normalize();
                        ribbon(p, b, a, z, 0.035f, 0.035f, col, col, up);
                    }
                });
            }
            super.submit(s, pose, c, camera);
        }
    }

    // ------------------------------------------------------------------ Flying blocks (Reassembly, Sundering, reforming)
    public static final class FlyingState extends EntityRenderState {
        final MovingBlockRenderState block = new MovingBlockRenderState();
        float spin;
        boolean glow;
    }

    public static final class FlyingBlockRenderer extends EntityRenderer<FlyingBlock, FlyingState> {
        public FlyingBlockRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            shadowRadius = 0.4f;
        }

        @Override
        public FlyingState createRenderState() {
            return new FlyingState();
        }

        @Override
        public void extractRenderState(FlyingBlock e, FlyingState s, float partial) {
            super.extractRenderState(e, s, partial);
            BlockState st = e.blockState();
            BlockPos pos = BlockPos.containing(e.getX(), e.getBoundingBox().maxY, e.getZ());
            s.block.blockState = st;
            s.block.blockPos = pos;
            s.block.randomSeedPos = BlockPos.ZERO;
            if (e.level() instanceof ClientLevel level) {
                s.block.biome = level.getBiome(pos);
                s.block.cardinalLighting = level.cardinalLighting();
                s.block.lightEngine = level.getLightEngine();
            }
            s.spin = e.spinAt(e.flightAge() + partial);
            s.glow = e.mode() == FlyingBlock.MODE_PLACE;
        }

        @Override
        public void submit(FlyingState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            if (s.block.blockState == null || s.block.blockState.is(Blocks.AIR)) {
                return;
            }
            pose.pushPose();
            pose.translate(0, 0.5, 0);
            pose.rotate(Axis.YP.rotationDegrees(s.spin));
            pose.rotate(Axis.XP.rotationDegrees(s.spin * 0.35f));
            pose.translate(-0.5, -0.5, -0.5);
            c.submitMovingBlock(pose, s.block, s.outlineColor);
            pose.popPose();
            super.submit(s, pose, c, camera);
        }
    }

    // ------------------------------------------------------------------ Tidewisp: soft teal-white light with three streamers
    public static final class WispRenderer extends LivingEntityRenderer<Tidewisp, WFStates.Tidewisp, EntityModel<WFStates.Tidewisp>> {
        private static final Identifier TEX = Tex.of("tidewisp/tidewisp");

        public WispRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new EntityModel<>(new ModelPart(java.util.List.of(), java.util.Map.of())) {}, 0.0f);
        }

        @Override
        public Identifier getTextureLocation(WFStates.Tidewisp s) {
            return TEX;
        }

        @Override
        public WFStates.Tidewisp createRenderState() {
            return new WFStates.Tidewisp();
        }

        @Override
        public void extractRenderState(Tidewisp e, WFStates.Tidewisp s, float partial) {
            super.extractRenderState(e, s, partial);
            s.pulse = e.pulse(partial);
        }

        @Override
        public void submit(WFStates.Tidewisp s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            pose.pushPose();
            pose.translate(0, 0.2, 0);
            int a = (int) (110 + 145 * s.pulse);
            billboard(pose, c, camera.orientation, TEX, 0.28f + s.pulse * 0.08f, ARGB.color(a, 255, 255, 255));
            billboard(pose, c, camera.orientation, TEX, 0.7f, ARGB.color((int) (40 * s.pulse), 120, 255, 240));
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------ projectiles
    public static final class ShardState extends EntityRenderState {
        float yRot, xRot;
    }

    public static final class ShardRenderer extends EntityRenderer<UnmadeShard, ShardState> {
        private static final Identifier TEX = Tex.of("the_unmade/shard"), GLOW = Tex.of("the_unmade/shard_emissive");
        private final ModelPart shard;

        public ShardRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            shard = ctx.bakeLayer(WFLayers.UNMADE_SHARD);
        }

        @Override
        public ShardState createRenderState() {
            return new ShardState();
        }

        @Override
        public void extractRenderState(UnmadeShard e, ShardState s, float partial) {
            super.extractRenderState(e, s, partial);
            s.yRot = e.getYRot(partial);
            s.xRot = e.getXRot(partial);
        }

        @Override
        public void submit(ShardState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            pose.pushPose();
            pose.rotate(Axis.YP.rotationDegrees(s.yRot));
            pose.rotate(Axis.XP.rotationDegrees(90 - s.xRot));
            pose.scale(0.45f, 0.45f, 0.45f);
            c.submitModelPart(shard, pose, RenderTypes.entityCutout(TEX), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
            c.order(1).submitModelPart(shard, pose, RenderTypes.eyes(GLOW), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
            pose.popPose();
        }
    }

    public static final class SnareRenderer extends EntityRenderer<LurkerSnare, EntityRenderState> {
        public SnareRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public EntityRenderState createRenderState() {
            return new EntityRenderState();
        }

        @Override
        public void submit(EntityRenderState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            c.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
                int col = ARGB.color(220, 235, 240, 245);
                for (int i = 0; i < 3; i++) {
                    float a = i * Mth.TWO_PI / 3 + s.ageInTicks;
                    Vector3f side = new Vector3f(Mth.cos(a), 0.3f, Mth.sin(a)).normalize();
                    ribbon(p, b, new Vector3f(0, 0.15f, 0), new Vector3f(0, -0.15f, 0), 0.25f, 0.25f, col, col, side);
                }
            });
        }
    }
}
