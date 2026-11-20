package dev.worldsfall.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.worldsfall.client.model.CreatureModels;
import dev.worldsfall.client.model.WFStates;
import dev.worldsfall.entity.ChorusGrazer;
import dev.worldsfall.entity.Echo;
import dev.worldsfall.entity.Hoarder;
import dev.worldsfall.entity.Shardling;
import dev.worldsfall.entity.SpireWarden;
import dev.worldsfall.entity.UndersideLurker;
import dev.worldsfall.entity.VoidSkate;
import dev.worldsfall.entity.WildShulker;
import dev.worldsfall.entity.Wyrm;
import dev.worldsfall.world.gravity.GravityZones;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.ShulkerRenderer;
import net.minecraft.client.renderer.entity.state.ShulkerRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Shulker;

/** Renderers for the mod's living creatures. */
public final class CreatureRenderers {
    private CreatureRenderers() {}

    /** Low-gravity zones slow animations to ~80% (§18.5). */
    static float slowedAge(LivingEntity e, float age) {
        return GravityZones.hasModifier(e) ? age * 0.8f : age;
    }

    // ------------------------------------------------------------------ Void Skate
    public static final class SkateRenderer extends MobRenderer<VoidSkate, WFStates.Skate, CreatureModels.VoidSkate> {
        private static final Identifier INDIGO = Tex.of("void_skate/void_skate"), DUSK = Tex.of("void_skate/void_skate_dusk");
        private static final Identifier INDIGO_E = Tex.of("void_skate/void_skate_emissive"), DUSK_E = Tex.of("void_skate/void_skate_dusk_emissive");

        public SkateRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new CreatureModels.VoidSkate(ctx.bakeLayer(WFLayers.VOID_SKATE)), 1.6f);
            addLayer(new EmissiveLayer<>(this, s -> s.dusk ? DUSK_E : INDIGO_E, s -> true));
        }

        @Override
        public Identifier getTextureLocation(WFStates.Skate s) {
            return s.dusk ? DUSK : INDIGO;
        }

        @Override
        public WFStates.Skate createRenderState() {
            return new WFStates.Skate();
        }

        @Override
        public void extractRenderState(VoidSkate e, WFStates.Skate s, float partial) {
            super.extractRenderState(e, s, partial);
            s.ageInTicks = slowedAge(e, s.ageInTicks);
            s.saddled = e.isSaddled();
            s.perched = e.isPerched();
            s.dusk = e.variant() == 1;
            s.ridden = e.isVehicle();
            s.bank = Mth.lerp(partial, e.bankO, e.bank);
            s.speed = (float) e.getDeltaMovement().horizontalDistance() * 4f;
            s.climb = (float) e.getDeltaMovement().y * 4f;
        }
    }

    // ------------------------------------------------------------------ Chorus Grazer
    public static final class GrazerRenderer extends MobRenderer<ChorusGrazer, WFStates.Grazer, CreatureModels.ChorusGrazer> {
        private static final Identifier TEX = Tex.of("chorus_grazer/chorus_grazer"), SHEARED = Tex.of("chorus_grazer/chorus_grazer_sheared");
        private static final Identifier GLOW = Tex.of("chorus_grazer/chorus_grazer_emissive");

        public GrazerRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new CreatureModels.ChorusGrazer(ctx.bakeLayer(WFLayers.CHORUS_GRAZER)), 0.7f);
            addLayer(new EmissiveLayer<>(this, s -> GLOW, s -> !s.sheared));
        }

        @Override
        public Identifier getTextureLocation(WFStates.Grazer s) {
            return s.sheared ? SHEARED : TEX;
        }

        @Override
        public WFStates.Grazer createRenderState() {
            return new WFStates.Grazer();
        }

        @Override
        public void extractRenderState(ChorusGrazer e, WFStates.Grazer s, float partial) {
            super.extractRenderState(e, s, partial);
            s.sheared = e.isSheared();
            s.graze = e.grazeTicks() > 0 ? Math.min(1f, e.grazeTicks() / 10f) : 0f;
        }

        @Override
        protected void scale(WFStates.Grazer s, PoseStack pose) {
            if (s.isBaby) {
                pose.scale(0.55f, 0.55f, 0.55f);
            }
        }
    }

    // ------------------------------------------------------------------ Underside Lurker
    public static final class LurkerRenderer extends MobRenderer<UndersideLurker, WFStates.Lurker, CreatureModels.Lurker> {
        private static final Identifier TEX = Tex.of("underside_lurker/underside_lurker"), EYES = Tex.of("underside_lurker/underside_lurker_emissive");

        public LurkerRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new CreatureModels.Lurker(ctx.bakeLayer(WFLayers.UNDERSIDE_LURKER)), 0.0f);
            addLayer(new EmissiveLayer<>(this, s -> EYES, s -> !s.hidden));
        }

        @Override
        public Identifier getTextureLocation(WFStates.Lurker s) {
            return TEX;
        }

        @Override
        public WFStates.Lurker createRenderState() {
            return new WFStates.Lurker();
        }

        @Override
        public void extractRenderState(UndersideLurker e, WFStates.Lurker s, float partial) {
            super.extractRenderState(e, s, partial);
            s.state = e.state().ordinal();
            s.hidden = e.isHidden();
            s.lit = e.isLit();
            s.stateTicks = e.stateTicks() + partial;
            BlockPos a = e.anchor();
            s.threadLength = a.equals(BlockPos.ZERO) ? 0 : (float) (a.getY() - (s.y + s.boundingBoxHeight));
            if (s.hidden) {
                s.outlineColor = dev.worldsfall.client.hud.VoidLensOverlay.outlineFor(e, s.outlineColor); // Void Lens reveals the shimmer (§9.6)
            }
        }

        /** Hidden Lurkers show only as a faint shimmer (§8.4). */
        @Override
        protected int getModelTint(WFStates.Lurker s) {
            if (!s.hidden) {
                return -1;
            }
            int alpha = (int) (28 + 18 * Mth.sin(s.ageInTicks * 0.25f));
            return ARGB.color(alpha, 255, 255, 255);
        }

        @Override
        public void submit(WFStates.Lurker s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            super.submit(s, pose, collector, camera);
            if (s.threadLength > 0.1f) {
                // the single thread to the ceiling
                float top = s.boundingBoxHeight + s.threadLength;
                float h = s.boundingBoxHeight;
                int alpha = s.hidden ? 60 : 170;
                collector.submitCustomGeometry(pose, RenderTypes.lines(), (p, buf) -> {
                    buf.addVertex(p, 0, h, 0).setColor(ARGB.color(alpha, 220, 230, 235)).setNormal(p, 0, 1, 0).setLineWidth(1.5f);
                    buf.addVertex(p, 0, top, 0).setColor(ARGB.color(alpha / 2, 220, 230, 235)).setNormal(p, 0, 1, 0).setLineWidth(1.5f);
                });
            }
        }
    }

    // ------------------------------------------------------------------ Hoarder
    public static final class HoarderRenderer extends MobRenderer<Hoarder, WFStates.Hoarder, CreatureModels.Hoarder> {
        private static final Identifier TEX = Tex.of("hoarder/hoarder"), EYES = Tex.of("hoarder/hoarder_emissive");

        public HoarderRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new CreatureModels.Hoarder(ctx.bakeLayer(WFLayers.HOARDER)), 0.5f);
            addLayer(new EmissiveLayer<>(this, s -> EYES, s -> true));
        }

        @Override
        public Identifier getTextureLocation(WFStates.Hoarder s) {
            return TEX;
        }

        @Override
        public WFStates.Hoarder createRenderState() {
            return new WFStates.Hoarder();
        }

        @Override
        public void extractRenderState(Hoarder e, WFStates.Hoarder s, float partial) {
            super.extractRenderState(e, s, partial);
            s.angry = e.isAngry();
            s.barter = e.barterAnim();
        }
    }

    // ------------------------------------------------------------------ Spire Warden
    public static final class WardenRenderer extends MobRenderer<SpireWarden, WFStates.Warden, CreatureModels.SpireWarden> {
        private static final Identifier[] TEX = {Tex.of("spire_warden/spire_warden"), Tex.of("spire_warden/spire_warden_cracked"),
            Tex.of("spire_warden/spire_warden_shattered")};
        private static final Identifier GLOW = Tex.of("spire_warden/spire_warden_emissive");

        public WardenRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new CreatureModels.SpireWarden(ctx.bakeLayer(WFLayers.SPIRE_WARDEN)), 0.9f);
            addLayer(new EmissiveLayer<>(this, s -> GLOW, s -> true));
        }

        @Override
        public Identifier getTextureLocation(WFStates.Warden s) {
            return TEX[Mth.clamp(s.damageState, 0, 2)];
        }

        @Override
        public WFStates.Warden createRenderState() {
            return new WFStates.Warden();
        }

        @Override
        public void extractRenderState(SpireWarden e, WFStates.Warden s, float partial) {
            super.extractRenderState(e, s, partial);
            s.attack = e.attack().ordinal();
            s.attackTicks = e.attackTicks() + partial;
            s.damageState = e.damageState();
        }
    }

    // ------------------------------------------------------------------ Echo
    public static final class EchoRenderer extends MobRenderer<Echo, WFStates.Echo, CreatureModels.Echo> {
        private static final Identifier TEX = Tex.of("echo/echo"), EYES = Tex.of("echo/echo_emissive");
        private final MovingBlockRenderState block = new MovingBlockRenderState();

        public EchoRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new CreatureModels.Echo(ctx.bakeLayer(WFLayers.ECHO)), 0.3f);
            addLayer(new EmissiveLayer<>(this, s -> EYES, s -> !s.disguised && s.shimmer > 0));
        }

        @Override
        public Identifier getTextureLocation(WFStates.Echo s) {
            return TEX;
        }

        @Override
        public WFStates.Echo createRenderState() {
            return new WFStates.Echo();
        }

        @Override
        public void extractRenderState(Echo e, WFStates.Echo s, float partial) {
            super.extractRenderState(e, s, partial);
            s.disguised = e.isDisguised();
            s.disguise = e.disguise();
            s.shimmer = e.shimmer();
            s.outlineColor = dev.worldsfall.client.hud.VoidLensOverlay.outlineFor(e, s.outlineColor); // true outline through a Void Lens (§9.6)
            if (s.disguised && e.level() instanceof ClientLevel level) {
                block.blockState = s.disguise;
                block.blockPos = e.blockPosition();
                block.randomSeedPos = e.blockPosition();
                block.biome = level.getBiome(e.blockPosition());
                block.cardinalLighting = level.cardinalLighting();
                block.lightEngine = level.getLightEngine();
            }
        }

        @Override
        protected int getModelTint(WFStates.Echo s) {
            int alpha = s.shimmer > 0 ? 230 : (int) (150 + 40 * Mth.sin(s.ageInTicks * 0.9f));
            return ARGB.color(alpha, 255, 255, 255);
        }

        @Override
        public void submit(WFStates.Echo s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            if (s.disguised) {
                // a perfect block disguise — except it never casts ambient occlusion correctly (§8.5.3 tell)
                pose.pushPose();
                pose.translate(-0.5, 0.0, -0.5);
                collector.submitMovingBlock(pose, block, s.outlineColor);
                pose.popPose();
                return;
            }
            super.submit(s, pose, collector, camera);
        }
    }

    // ------------------------------------------------------------------ Wild Shulker
    public static final class WildShulkerRenderer extends ShulkerRenderer {
        private static final Identifier TEX = Tex.of("wild_shulker/wild_shulker"), MOLTED = Tex.of("wild_shulker/wild_shulker_molted");

        public static final class State extends ShulkerRenderState {
            boolean molted;
        }

        public WildShulkerRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public ShulkerRenderState createRenderState() {
            return new State();
        }

        @Override
        public void extractRenderState(Shulker e, ShulkerRenderState s, float partial) {
            super.extractRenderState(e, s, partial);
            if (s instanceof State ws && e instanceof WildShulker w) {
                ws.molted = w.isMolted();
            }
        }

        @Override
        public Identifier getTextureLocation(ShulkerRenderState s) {
            return s instanceof State ws && ws.molted ? MOLTED : TEX;
        }
    }

    // ------------------------------------------------------------------ Wyrm
    public static final class WyrmRenderer extends MobRenderer<Wyrm, WFStates.Wyrm, CreatureModels.Wyrm> {
        private static final Identifier[] TEX = {Tex.of("wyrm/wyrm_hatchling"), Tex.of("wyrm/wyrm_fledgling"), Tex.of("wyrm/wyrm_drake")};
        private static final Identifier[] GLOW = {Tex.of("wyrm/wyrm_hatchling_emissive"), Tex.of("wyrm/wyrm_fledgling_emissive"),
            Tex.of("wyrm/wyrm_drake_emissive")};
        private final CreatureModels.Wyrm[] models;

        public WyrmRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new CreatureModels.Wyrm(ctx.bakeLayer(WFLayers.WYRM_HATCHLING), "wyrm_hatchling"), 0.4f);
            models = new CreatureModels.Wyrm[] {model, new CreatureModels.Wyrm(ctx.bakeLayer(WFLayers.WYRM_FLEDGLING), "wyrm_fledgling"),
                new CreatureModels.Wyrm(ctx.bakeLayer(WFLayers.WYRM_DRAKE), "wyrm_drake")};
            addLayer(new EmissiveLayer<>(this, s -> GLOW[Mth.clamp(s.stage, 0, 2)], s -> true));
        }

        @Override
        public Identifier getTextureLocation(WFStates.Wyrm s) {
            return TEX[Mth.clamp(s.stage, 0, 2)];
        }

        @Override
        public WFStates.Wyrm createRenderState() {
            return new WFStates.Wyrm();
        }

        @Override
        public void extractRenderState(Wyrm e, WFStates.Wyrm s, float partial) {
            super.extractRenderState(e, s, partial);
            s.stage = e.stage().ordinal();
            s.flying = !e.onGround();
            s.sitting = e.isInSittingPose();
            model = models[Mth.clamp(s.stage, 0, 2)];
            shadowRadius = switch (s.stage) {
                case 0 -> 0.35f;
                case 1 -> 0.5f;
                default -> 0.9f;
            };
        }
    }

    // ------------------------------------------------------------------ Shardling
    public static final class ShardlingRenderer extends MobRenderer<Shardling, WFStates.Shardling, CreatureModels.Shardling> {
        private static final Identifier TEX = Tex.of("shardling/shardling"), GLOW = Tex.of("shardling/shardling_emissive");

        public ShardlingRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new CreatureModels.Shardling(ctx.bakeLayer(WFLayers.SHARDLING)), 0.0f);
            addLayer(new EmissiveLayer<>(this, s -> GLOW, s -> true));
        }

        @Override
        public Identifier getTextureLocation(WFStates.Shardling s) {
            return TEX;
        }

        @Override
        public WFStates.Shardling createRenderState() {
            return new WFStates.Shardling();
        }

        @Override
        public void extractRenderState(Shardling e, WFStates.Shardling s, float partial) {
            super.extractRenderState(e, s, partial);
            s.diving = e.isDiving();
        }
    }
}
