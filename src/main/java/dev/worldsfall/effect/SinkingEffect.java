package dev.worldsfall.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Sinking (proposal "Cities change with the tide"): the High Tide counterpart of Levitation. While airborne the target
 * is pulled down at {@link #PULL} blocks per tick; a Sinking Bullet lasts {@link #BULLET_TICKS}, about four blocks.
 * Players are moved by the client (they own their motion), everything else here.
 */
public class SinkingEffect extends MobEffect {
    public static final double PULL = 0.35;
    public static final int BULLET_TICKS = 12;

    public SinkingEffect() {
        super(MobEffectCategory.HARMFUL, 0x1E7C86);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remainingDuration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (!(entity instanceof Player)) {
            pull(entity);
            entity.needsSync = true;
        }
        return true;
    }

    /** Applies one tick of the pull; shared with the client for the local player. */
    public static void pull(LivingEntity entity) {
        if (entity.onGround() || entity.isPassenger()) {
            return;
        }
        Vec3 v = entity.getDeltaMovement();
        if (v.y > -PULL) {
            entity.setDeltaMovement(v.x, -PULL, v.z);
        }
        entity.resetFallDistance();
    }
}
