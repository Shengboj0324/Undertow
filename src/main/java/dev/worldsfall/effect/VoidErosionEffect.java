package dev.worldsfall.effect;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.registry.WFDamageTypes;
import dev.worldsfall.world.tide.TideEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Void Erosion (§5.3): 1 HP/s at first, +1 every 3 s, capped at 4 HP/s (configurable); ignores armor.
 * Escalation is tracked by {@link TideEffects#EROSION_TICKS_KEY} while the entity stays below the tide.
 */
public class VoidErosionEffect extends MobEffect {
    public VoidErosionEffect() {
        super(MobEffectCategory.HARMFUL, 0x3AE0D0);
    }

    public static float damagePerSecond(int ticksInside, int cap) {
        return Math.min(cap, 1 + ticksInside / 60);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return tickCount % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        int inside = mob.getPersistentData().getIntOr(TideEffects.EROSION_TICKS_KEY, 0);
        float dmg = damagePerSecond(inside, WFConfig.get(WFConfig.SERVER.erosionMaxDamagePerSecond, 4)) + amplification;
        mob.hurtServer(level, WFDamageTypes.source(level, WFDamageTypes.VOID_EROSION), dmg);
        return true;
    }
}
