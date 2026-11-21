package dev.worldsfall.zenith;

import dev.worldsfall.entity.VoidSkate;
import dev.worldsfall.network.ElytraActionPayload;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Gale Harrier (proposal "A fifth layer above"): the Zenith's predator, a fast hawk that circles high and dives at gliding
 * players to knock them out of Sky Currents: no damage, only a strong shove. A raised shield or the Still upgrade's air
 * brake makes it miss. It also hunts young Skates. Drops Harrier Plumes.
 */
public class GaleHarrier extends Mob implements Enemy {
    public static final int CIRCLE = 0, DIVE = 1, CLIMB = 2;
    public static final double SHOVE = 2.4, DIVE_SPEED = 1.6, CIRCLE_SPEED = 0.55;
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(GaleHarrier.class, EntityDataSerializers.INT);
    private @Nullable LivingEntity prey;
    private Vec3 anchor = Vec3.ZERO;
    private int modeTicks;

    public GaleHarrier(EntityType<? extends GaleHarrier> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 16.0).add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, CIRCLE);
    }

    public int mode() {
        return entityData.get(MODE);
    }

    private void setMode(int m) {
        entityData.set(MODE, m);
        modeTicks = 0;
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level)) {
            if (mode() == DIVE && random.nextInt(2) == 0) {
                level().addParticle(WFParticles.SKY_CURRENT.get(), getX(), getY(), getZ(), 0, 0, 0);
            }
            return;
        }
        if (anchor == Vec3.ZERO) {
            anchor = position();
        }
        modeTicks++;
        Vec3 v = getDeltaMovement();
        switch (mode()) {
            case CIRCLE -> {
                double a = (tickCount + getId() * 13) * 0.035;
                Vec3 goal = anchor.add(Math.cos(a) * 26, Math.sin(a * 0.5) * 4, Math.sin(a) * 26);
                v = steer(v, goal, CIRCLE_SPEED, 0.08);
                if (modeTicks > 40 && tickCount % 10 == 0) {
                    prey = findPrey(level);
                    if (prey != null) {
                        setMode(DIVE);
                        playSound(WFSounds.ENTITY_GALE_HARRIER_SCREECH.get(), 4.0f, 1.0f);
                        playSound(WFSounds.ENTITY_GALE_HARRIER_DIVE.get(), 3.0f, 1.0f);
                    }
                }
            }
            case DIVE -> {
                if (prey == null || !prey.isAlive() || prey.distanceToSqr(this) > 64 * 64 || modeTicks > 120) {
                    setMode(CLIMB);
                    break;
                }
                Vec3 aim = prey.position().add(prey.getDeltaMovement().scale(6)).add(0, prey.getBbHeight() * 0.5, 0);
                v = steer(v, aim, DIVE_SPEED, 0.25);
                if (distanceTo(prey) < 2.2) {
                    strike(level, prey);
                    setMode(CLIMB);
                }
            }
            default -> {
                v = steer(v, anchor.add(0, 12, 0), 0.8, 0.1);
                if (modeTicks > 100) {
                    setMode(CIRCLE);
                }
            }
        }
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        if (v.horizontalDistanceSqr() > 1.0e-4) {
            float yaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
            setYRot(Mth.approachDegrees(getYRot(), yaw, 12));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
            setXRot((float) Mth.clamp(-Math.toDegrees(Math.atan2(v.y, v.horizontalDistance())), -60, 60));
        }
    }

    /** Turns the current velocity a fraction {@code turn} toward flying at {@code speed} straight at {@code goal}. */
    private Vec3 steer(Vec3 v, Vec3 goal, double speed, double turn) {
        Vec3 want = goal.subtract(position()).normalize().scale(speed);
        return v.add(want.subtract(v).scale(turn));
    }

    /** Gliding players and young Skates within 40 blocks. */
    private @Nullable LivingEntity findPrey(ServerLevel level) {
        AABB area = getBoundingBox().inflate(40);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, area, p -> !p.isCreative() && !p.isSpectator()
            && (p.isFallFlying() || p.getVehicle() instanceof VoidSkate) && hasLineOfSight(p))) {
            return p;
        }
        for (VoidSkate s : level.getEntitiesOfClass(VoidSkate.class, area, s -> s.isBaby() && hasLineOfSight(s))) {
            return s;
        }
        return null;
    }

    private void strike(ServerLevel level, LivingEntity target) {
        if (target instanceof ServerPlayer p) {
            if (p.isBlocking()) {
                level.playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
                setDeltaMovement(getDeltaMovement().scale(-0.6));
                return;
            }
            if (ElytraActionPayload.braking(p, 30)) {
                level.sendParticles(WFParticles.STILL_RING.get(), p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
                return;
            }
            Vec3 push = getDeltaMovement().multiply(1, 0, 1).normalize().scale(SHOVE).add(0, -0.5, 0);
            p.push(push.x, push.y, push.z);
            p.needsSync = true;
            level.sendParticles(WFParticles.DOWNDRAFT_DUST.get(), p.getX(), p.getY() + 1, p.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
            playSound(WFSounds.ENTITY_GALE_HARRIER_SCREECH.get(), 3.0f, 1.3f);
            dev.worldsfall.world.Progress.award(p, "knocked_out_of_the_sky");
        } else {
            target.hurtServer(level, damageSources().mobAttack(this), 4.0f);
        }
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return WFSounds.ENTITY_GALE_HARRIER_SCREECH.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return WFSounds.ENTITY_GALE_HARRIER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return WFSounds.ENTITY_GALE_HARRIER_DEATH.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 400;
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }
}
