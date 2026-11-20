package dev.worldsfall.entity;

import dev.worldsfall.registry.WFDamageTypes;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Spire Warden (§8.5.2): elite guardian of Fallen Spires, the only source of Spire Keys. Slam (0.6 s arms-up
 * wind-up), Teleport Slam (1.0 s ground-ring telegraph, 12 HP in r=3, 10 s cooldown), Plate Shield below 50%
 * (3 s, reflects projectiles). Plates crack in three visible damage states.
 */
public class SpireWarden extends EnderCaste {
    public enum Attack { NONE, SLAM_WINDUP, TELEPORT_WINDUP, FALLING, SHIELD }

    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(SpireWarden.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> RING = SynchedEntityData.defineId(SpireWarden.class, EntityDataSerializers.BLOCK_POS);
    public static final int SLAM_WINDUP = 12;
    public static final int TELEPORT_WINDUP = 20;
    public static final int TELEPORT_COOLDOWN = 200;
    public static final int SHIELD_TICKS = 60;

    private BlockPos home = BlockPos.ZERO;
    private int attackTicks;
    private int teleportCooldown = 100;
    private int shieldCooldown;
    private int stepTimer;

    public SpireWarden(EntityType<? extends SpireWarden> type, Level level) {
        super(type, level);
        this.xpReward = 30;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 80.0).add(Attributes.ARMOR, 8.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.ATTACK_DAMAGE, 10.0).add(Attributes.ATTACK_KNOCKBACK, 1.5)
            .add(Attributes.MOVEMENT_SPEED, 0.22).add(Attributes.FOLLOW_RANGE, 20.0).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK, Attack.NONE.ordinal());
        builder.define(RING, BlockPos.ZERO);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
            (target, level) -> target.distanceToSqr(this) < 16 * 16));
    }

    public Attack attack() {
        return Attack.values()[entityData.get(ATTACK)];
    }

    public int attackTicks() {
        return attackTicks;
    }

    public BlockPos ringPos() {
        return entityData.get(RING);
    }

    /** 0 intact, 1 cracked, 2 shattered — plates fall away as health drops (§18.4). */
    public int damageState() {
        float f = getHealth() / getMaxHealth();
        return f > 0.66f ? 0 : f > 0.33f ? 1 : 2;
    }

    public void setHome(BlockPos home) {
        this.home = home.immutable();
    }

    private void setAttack(Attack a) {
        entityData.set(ATTACK, a.ordinal());
        attackTicks = 0;
    }

    @Override
    public void tick() {
        super.tick();
        attackTicks++;
        if (level().isClientSide() && attack() == Attack.TELEPORT_WINDUP && !ringPos().equals(BlockPos.ZERO)) {
            BlockPos r = ringPos();
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                level().addParticle(WFParticles.SOCKET_SPARK.get(), r.getX() + 0.5 + Math.cos(a) * 3, r.getY() + 0.1, r.getZ() + 0.5 + Math.sin(a) * 3,
                    0, 0.02, 0);
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        teleportCooldown = Math.max(0, teleportCooldown - 1);
        shieldCooldown = Math.max(0, shieldCooldown - 1);
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || distanceToSqr(target) > 24 * 24)) {
            setTarget(null);
            target = null;
        }
        switch (attack()) {
            case NONE -> {
                if (target == null) {
                    if (!home.equals(BlockPos.ZERO) && !blockPosition().closerThan(home, 4)) {
                        getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.8);
                    }
                    return;
                }
                getLookControl().setLookAt(target);
                if (getHealth() < getMaxHealth() * 0.5f && shieldCooldown == 0) {
                    setAttack(Attack.SHIELD);
                    playSound(WFSounds.ENTITY_SPIRE_WARDEN_PLATE_SHIELD.get(), 1.5f, 1.0f);
                } else if (teleportCooldown == 0 && distanceTo(target) > 5 && distanceTo(target) <= 20) {
                    entityData.set(RING, target.blockPosition());
                    setAttack(Attack.TELEPORT_WINDUP);
                    playSound(WFSounds.ENTITY_SPIRE_WARDEN_TELEPORT_RISE.get(), 2.0f, 1.0f);
                } else if (distanceTo(target) < 3.2) {
                    getNavigation().stop();
                    setAttack(Attack.SLAM_WINDUP);
                    playSound(WFSounds.ENTITY_SPIRE_WARDEN_GRIND.get(), 1.2f, 1.0f);
                } else {
                    getNavigation().moveTo(target, 1.0);
                    if (++stepTimer % 14 == 0) {
                        playSound(WFSounds.ENTITY_SPIRE_WARDEN_STEP.get(), 1.4f, 0.9f + random.nextFloat() * 0.2f);
                    }
                }
            }
            case SLAM_WINDUP -> {
                getNavigation().stop();
                if (target != null) {
                    getLookControl().setLookAt(target);
                }
                if (attackTicks >= SLAM_WINDUP) {
                    if (target != null && distanceTo(target) < 3.8) {
                        doHurtTarget(level, target);
                        Vec3 kb = target.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.2);
                        target.push(kb.x, 0.35, kb.z);
                        dev.worldsfall.util.WFUtil.syncMotion(target);
                    }
                    playSound(WFSounds.ENTITY_SPIRE_WARDEN_SLAM.get(), 1.6f, 1.0f);
                    setAttack(Attack.NONE);
                }
            }
            case TELEPORT_WINDUP -> {
                getNavigation().stop();
                if (attackTicks >= TELEPORT_WINDUP) {
                    BlockPos r = ringPos();
                    teleportTo(r.getX() + 0.5, r.getY() + 6, r.getZ() + 0.5);
                    setDeltaMovement(0, -0.8, 0);
                    setAttack(Attack.FALLING);
                }
            }
            case FALLING -> {
                if (onGround() || attackTicks > 40) {
                    impact(level);
                    teleportCooldown = TELEPORT_COOLDOWN;
                    entityData.set(RING, BlockPos.ZERO);
                    setAttack(Attack.NONE);
                }
            }
            case SHIELD -> {
                getNavigation().stop();
                if (attackTicks >= SHIELD_TICKS) {
                    shieldCooldown = 240;
                    setAttack(Attack.NONE);
                }
            }
        }
    }

    private void impact(ServerLevel level) {
        playSound(WFSounds.ENTITY_SPIRE_WARDEN_SLAM.get(), 2.0f, 0.8f);
        level.sendParticles(WFParticles.SOCKET_SPARK.get(), getX(), getY() + 0.2, getZ(), 40, 1.5, 0.1, 1.5, 0.1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(3), e -> e != this)) {
            if (e.distanceToSqr(this) <= 9.0) {
                e.hurtServer(level, WFDamageTypes.source(level, WFDamageTypes.WARDEN_SLAM, this, this), 12.0f);
                Vec3 push = e.position().subtract(position()).normalize();
                e.push(push.x * 0.8, 0.4, push.z * 0.8);
                dev.worldsfall.util.WFUtil.syncMotion(e);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (attack() == Attack.SHIELD && source.getDirectEntity() instanceof Projectile projectile) {
            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-0.8));
            projectile.setOwner(this);
            playSound(WFSounds.ENTITY_SPIRE_WARDEN_PLATE_SHIELD.get(), 1.0f, 1.4f);
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return WFSounds.ENTITY_SPIRE_WARDEN_BREATHE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return WFSounds.ENTITY_SPIRE_WARDEN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return WFSounds.ENTITY_SPIRE_WARDEN_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 1.5f;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.store("home", BlockPos.CODEC, home);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.read("home", BlockPos.CODEC).ifPresent(this::setHome);
    }
}
