package dev.worldsfall.undertow;

import dev.worldsfall.registry.WFSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Tide Eel (proposal "Creatures"): a long, pale serpent of the Undertow drawn to light. It follows Glimmer shoals, coils
 * around a Void Bell whose lamp is lit and drains its charge until it is struck (then it flees), and bites anyone swimming
 * outside a bubble. Turning the lamp off makes it lose interest.
 */
public class TideEel extends Mob implements Enemy {
    public static final int DRAIN_PER_TICK = 3, FLEE_TICKS = 200;
    private int fleeing, biteCooldown;
    private @Nullable VoidBell coiled;

    public TideEel(EntityType<? extends TideEel> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 28.0).add(Attributes.ATTACK_DAMAGE, 4.0);
    }

    public boolean isCoiled() {
        return coiled != null;
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        biteCooldown = Math.max(0, biteCooldown - 1);
        Vec3 goal;
        double speed;
        if (fleeing > 0) {
            fleeing--;
            coiled = null;
            Player p = level.getNearestPlayer(this, 32);
            goal = p == null ? position().add(getLookAngle().scale(8)) : position().add(position().subtract(p.position()).normalize().scale(10));
            speed = 0.35;
        } else {
            if (coiled == null || !coiled.isAlive() || !coiled.lampOn() || coiled.charge() <= 0 || coiled.distanceToSqr(this) > 40 * 40) {
                coiled = level.getEntitiesOfClass(VoidBell.class, getBoundingBox().inflate(32), b -> b.lampOn() && b.charge() > 0).stream().findFirst()
                    .orElse(null);
            }
            if (coiled != null) {
                double a = tickCount * 0.12 + getId();
                goal = coiled.position().add(Math.cos(a) * 2.6, 1.4 + Math.sin(a * 0.5) * 0.8, Math.sin(a) * 2.6);
                speed = 0.3;
                if (distanceToSqr(coiled) < 6 * 6) {
                    coiled.drain(DRAIN_PER_TICK);
                    if (tickCount % 40 == 0) {
                        playSound(WFSounds.ENTITY_TIDE_EEL_DRAIN.get(), 1.0f, 1.0f);
                    }
                }
            } else {
                Player prey = level.getNearestPlayer(getX(), getY(), getZ(), 12, p -> !((Player) p).isCreative() && !VoidBell.sheltered(p));
                Glimmer shoal = prey != null ? null : level.getEntitiesOfClass(Glimmer.class, getBoundingBox().inflate(24)).stream().findFirst().orElse(null);
                if (prey != null) {
                    goal = prey.position().add(0, prey.getBbHeight() * 0.5, 0);
                    speed = 0.32;
                    if (distanceToSqr(prey) < 2.2 * 2.2 && biteCooldown == 0) {
                        doHurtTarget(level, prey);
                        biteCooldown = 30;
                    }
                } else if (shoal != null) {
                    goal = shoal.position();
                    speed = 0.12;
                } else {
                    goal = position().add(Math.sin(tickCount * 0.01 + getId()) * 10, Math.sin(tickCount * 0.02) * 2, Math.cos(tickCount * 0.013 + getId()) * 10);
                    speed = 0.08;
                }
            }
        }
        Vec3 want = goal.subtract(position());
        if (want.lengthSqr() > 1.0e-4) {
            want = want.normalize().scale(speed);
        }
        Vec3 v = getDeltaMovement().lerp(want, 0.08);
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        if (v.horizontalDistanceSqr() > 1.0e-4) {
            setYRot(Mth.approachDegrees(getYRot(), (float) Math.toDegrees(Math.atan2(-v.x, v.z)), 8));
            yBodyRot = yHeadRot = getYRot();
            setXRot((float) Mth.clamp(-Math.toDegrees(Math.atan2(v.y, v.horizontalDistance())), -50, 50));
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt) {
            fleeing = FLEE_TICKS;
            coiled = null;
        }
        return hurt;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return WFSounds.ENTITY_TIDE_EEL_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return WFSounds.ENTITY_TIDE_EEL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return WFSounds.ENTITY_TIDE_EEL_DEATH.get();
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }
}
