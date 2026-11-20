package dev.worldsfall.entity;

import dev.worldsfall.registry.WFDamageTypes;
import dev.worldsfall.registry.WFParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** A black, magenta-edged shard from Shard Volley (4 HP) or a Shardling splinter (2 HP) (§13.5, §8.8). */
public class UnmadeShard extends StraightProjectile {
    private float damage = 4.0f;

    public UnmadeShard(EntityType<? extends UnmadeShard> type, Level level) {
        super(type, level);
        maxLife = 100;
    }

    public UnmadeShard withDamage(float damage) {
        this.damage = damage;
        return this;
    }

    @Override
    protected void onHitTarget(ServerLevel level, Entity target) {
        target.hurtServer(level, WFDamageTypes.source(level, WFDamageTypes.UNMADE_SHARD, this, getOwner()), damage);
    }

    @Override
    protected void trail() {
        if (level().isClientSide() && random.nextInt(2) == 0) {
            level().addParticle(WFParticles.SHARD_SPARK.get(), getX(), getY(), getZ(), 0, 0, 0);
        }
    }
}
