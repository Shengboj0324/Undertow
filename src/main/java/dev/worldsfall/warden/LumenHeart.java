package dev.worldsfall.warden;

import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;

/**
 * A Lumen Heart: a crystal cluster hanging in the Warden's Gallery that heals the dragon the way End Crystals do (the
 * vanilla dragon looks for the nearest {@link EndCrystal}, which this is). Unlike an End Crystal it does not explode or
 * light fires; it shatters into teal light. Shoot it, or reach it with the Rift Tether along the Voidroot curtains.
 */
public class LumenHeart extends EndCrystal {
    public LumenHeart(EntityType<? extends EndCrystal> type, Level level) {
        super(type, level);
        setShowBottom(false);
        setNoGravity(true);
    }

    @Override
    public void tick() {
        // vanilla crystals set fire to their block in the End; a hanging heart must not
        time++;
        applyEffectsFromBlocks();
        handlePortal();
        if (level() instanceof ServerLevel level && tickCount % 40 == 0) {
            level.playSound(null, getX(), getY(), getZ(), WFSounds.ENTITY_LUMEN_HEART_PULSE.get(), SoundSource.HOSTILE, 1.0f, 0.9f + random.nextFloat() * 0.2f);
            level.sendParticles(WFParticles.UP_DRIP.get(), getX(), getY() - 0.6, getZ(), 2, 0.3, 0.1, 0.3, 0.0);
        }
    }

    /** Called instead of the vanilla crystal explosion (see {@code WardenEvents#invulnerability}). */
    public void shatter(ServerLevel level, DamageSource source) {
        if (isRemoved() || source.getEntity() instanceof EnderDragon) {
            return;
        }
        level.playSound(null, getX(), getY(), getZ(), WFSounds.ENTITY_LUMEN_HEART_BREAK.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 40, 0.6, 0.8, 0.6, 0.12);
        level.sendParticles(WFParticles.TIDE_CREST.get(), getX(), getY(), getZ(), 24, 0.5, 0.5, 0.5, 0.05);
        discard();
    }
}
