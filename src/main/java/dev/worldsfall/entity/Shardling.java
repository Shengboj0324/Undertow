package dev.worldsfall.entity;

import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shardling (§8.8): floating black crystal with a magenta core. Hovers, then dives (5 HP). On death it bursts
 * into 3 fast splinters (2 HP each) that fly in straight lines — dodge sideways.
 */
public class Shardling extends Monster {
    private int diveCooldown = 40;
    private int diving;
    private Vec3 diveDir = Vec3.ZERO;

    public Shardling(EntityType<? extends Shardling> type, Level level) {
        super(type, level);
        setNoGravity(true);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.ATTACK_DAMAGE, 5.0)
            .add(Attributes.FLYING_SPEED, 0.6).add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public boolean isDiving() {
        return diving > 0;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        if (target == null) {
            setDeltaMovement(getDeltaMovement().scale(0.9));
            return;
        }
        if (diving > 0) {
            diving--;
            setDeltaMovement(diveDir.scale(0.9));
            if (distanceToSqr(target) < 2.0) {
                doHurtTarget(level, target);
                diving = 0;
                diveCooldown = 50;
            }
        } else if (--diveCooldown <= 0) {
            diveDir = target.getEyePosition().subtract(position()).normalize();
            diving = 20;
            playSound(WFSounds.ENTITY_SHARDLING_DIVE.get(), 1.0f, 1.0f);
        } else {
            Vec3 hover = target.position().add(Math.cos(tickCount * 0.05 + getId()) * 5, 4, Math.sin(tickCount * 0.05 + getId()) * 5);
            setDeltaMovement(hover.subtract(position()).scale(0.04));
        }
        move(MoverType.SELF, getDeltaMovement());
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level) {
            for (int i = 0; i < 3; i++) {
                double a = i * Math.PI * 2 / 3 + random.nextDouble();
                UnmadeShard splinter = new UnmadeShard(WFEntities.UNMADE_SHARD.get(), level).withDamage(2.0f);
                splinter.setOwner(this);
                splinter.launch(position().add(0, 0.5, 0), new Vec3(Math.cos(a), (random.nextDouble() - 0.5) * 0.3, Math.sin(a)).scale(0.9));
                level.addFreshEntity(splinter);
            }
            playSound(WFSounds.ENTITY_SHARDLING_BURST.get(), 1.0f, 1.0f);
        }
        super.die(source);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return WFSounds.ENTITY_SHARDLING_HUM.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return WFSounds.ENTITY_SHARDLING_BURST.get();
    }
}
