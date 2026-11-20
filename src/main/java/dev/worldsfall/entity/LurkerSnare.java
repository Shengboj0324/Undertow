package dev.worldsfall.entity;

import dev.worldsfall.registry.WFDamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Lurker Snare (§8.4): sticky thread, 12-block range. Pulls the target 3 blocks toward the Lurker + Slowness II 3 s. */
public class LurkerSnare extends StraightProjectile {
    public LurkerSnare(EntityType<? extends LurkerSnare> type, Level level) {
        super(type, level);
        maxLife = 14;
    }

    @Override
    protected void onHitTarget(ServerLevel level, Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return;
        }
        Entity owner = getOwner();
        living.hurtServer(level, WFDamageTypes.source(level, WFDamageTypes.LURKER_SNARE, this, owner), 1.0f);
        living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), owner);
        if (owner != null) {
            Vec3 pull = owner.position().subtract(living.position()).normalize().scale(0.9);
            living.push(pull.x, Math.max(0.15, pull.y * 0.5), pull.z);
            dev.worldsfall.util.WFUtil.syncMotion(living);
        }
    }
}
