package dev.worldsfall.client.particle;

import dev.worldsfall.registry.WFParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * The mod's particles (§18.8), each tuned by a small "behaviour" record: motion, lifetime, size, glow.
 * All scale with the vanilla particle setting automatically (spawn counts are already low).
 */
public final class WFParticle extends SimpleAnimatedParticle {
    record Behaviour(float gravity, float rise, float drift, int life, int lifeJitter, float size, boolean glow, float sway, boolean inward) {}

    private final Behaviour b;
    private final float phase;

    WFParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, Behaviour b) {
        super(level, x, y, z, sprites, b.gravity);
        this.b = b;
        this.xd = dx + (random.nextDouble() - 0.5) * b.drift;
        this.yd = dy + b.rise;
        this.zd = dz + (random.nextDouble() - 0.5) * b.drift;
        this.lifetime = b.life + random.nextInt(Math.max(1, b.lifeJitter));
        this.quadSize *= b.size;
        this.friction = 0.96f;
        this.phase = random.nextFloat() * Mth.TWO_PI;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (b.sway > 0) {
            xd += Mth.sin(age * 0.15f + phase) * b.sway;
            zd += Mth.cos(age * 0.13f + phase) * b.sway;
        }
        if (b.inward) {
            // Fracture ash drifts toward the arena centre (0, 64, 0)
            xd += -x * 0.00008;
            zd += -z * 0.00008;
        }
        float life = age / (float) lifetime;
        setAlpha(life < 0.15f ? life / 0.15f : 1f - Math.max(0, life - 0.6f) / 0.4f);
    }

    @Override
    public int getLightCoords(float a) {
        return b.glow ? 0xF000F0 : super.getLightCoords(a);
    }

    record Provider(SpriteSet sprites, Behaviour b) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                                       RandomSource random) {
            return new WFParticle(level, x, y, z, dx, dy, dz, sprites, b);
        }
    }

    public static void register(RegisterParticleProvidersEvent e) {
        reg(e, WFParticles.POLLEN_MOTE.get(), new Behaviour(-0.002f, 0.012f, 0.02f, 80, 60, 0.6f, true, 0.0015f, false));
        reg(e, WFParticles.POD_POP.get(), new Behaviour(0.02f, 0.05f, 0.18f, 14, 8, 0.7f, true, 0, false));
        reg(e, WFParticles.STILL_RING.get(), new Behaviour(0f, 0f, 0f, 40, 10, 2.4f, true, 0, false));
        reg(e, WFParticles.STILLFALL_MIST.get(), new Behaviour(0.004f, 0.0f, 0.05f, 50, 30, 2.0f, false, 0.001f, false));
        reg(e, WFParticles.DUST_FALL.get(), new Behaviour(0.004f, -0.01f, 0.01f, 90, 40, 0.5f, false, 0.0008f, false));
        reg(e, WFParticles.SOCKET_SPARK.get(), new Behaviour(-0.002f, 0.02f, 0.02f, 30, 15, 0.6f, true, 0.004f, false));
        reg(e, WFParticles.UP_MOTE.get(), new Behaviour(-0.003f, 0.015f, 0.01f, 110, 60, 0.55f, true, 0.001f, false));
        reg(e, WFParticles.UP_DRIP.get(), new Behaviour(-0.02f, 0.04f, 0.0f, 30, 10, 0.5f, true, 0, false));
        reg(e, WFParticles.SKATE_WAKE.get(), new Behaviour(0f, 0f, 0.01f, 26, 10, 0.75f, true, 0, false));
        reg(e, WFParticles.TIDE_CREST.get(), new Behaviour(-0.001f, 0.004f, 0.02f, 24, 12, 0.6f, true, 0, false));
        reg(e, WFParticles.EROSION_STATIC.get(), new Behaviour(0f, 0f, 0.05f, 6, 6, 0.6f, true, 0, false));
        reg(e, WFParticles.ASH_INFLOW.get(), new Behaviour(-0.0005f, 0.004f, 0.02f, 140, 60, 0.32f, false, 0.0008f, true));
        reg(e, WFParticles.SHARD_SPARK.get(), new Behaviour(0.01f, 0.02f, 0.06f, 18, 10, 0.6f, true, 0, false));
        reg(e, WFParticles.TIDEWISP_TRAIL.get(), new Behaviour(0f, 0.002f, 0.004f, 20, 10, 0.5f, true, 0, false));
        reg(e, WFParticles.SHELL_FRAGMENT.get(), new Behaviour(0.04f, 0.08f, 0.2f, 40, 20, 0.9f, false, 0, false));
    }

    private static void reg(RegisterParticleProvidersEvent e, SimpleParticleType type, Behaviour b) {
        e.registerSpriteSet(type, sprites -> new Provider(sprites, b));
    }
}
