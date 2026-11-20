package dev.worldsfall.registry;

import dev.worldsfall.Worldsfall;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Particles from the art bible §18.8. */
public final class WFParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Worldsfall.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> POLLEN_MOTE = simple("pollen_mote");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> POD_POP = simple("pod_pop");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STILL_RING = simple("still_ring");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STILLFALL_MIST = simple("stillfall_mist");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUST_FALL = simple("dust_fall");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SOCKET_SPARK = simple("socket_spark");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> UP_MOTE = simple("up_mote");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> UP_DRIP = simple("up_drip");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SKATE_WAKE = simple("skate_wake");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TIDE_CREST = simple("tide_crest");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EROSION_STATIC = simple("erosion_static");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ASH_INFLOW = simple("ash_inflow");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHARD_SPARK = simple("shard_spark");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TIDEWISP_TRAIL = simple("tidewisp_trail");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHELL_FRAGMENT = simple("shell_fragment");

    private WFParticles() {}

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> simple(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }
}
