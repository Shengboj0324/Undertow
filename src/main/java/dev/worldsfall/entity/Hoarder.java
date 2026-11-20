package dev.worldsfall.entity;

import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Hoarder (§8.5.1): neutral Enderman caste that patrols a Hoard and barters. Looking at it does not anger it;
 * looting without trading angers the whole Hoard for 2 minutes.
 */
public class Hoarder extends EnderCaste {
    private static final EntityDataAccessor<Integer> BARTER_ANIM = SynchedEntityData.defineId(Hoarder.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ANGRY = SynchedEntityData.defineId(Hoarder.class, EntityDataSerializers.BOOLEAN);
    private BlockPos home = BlockPos.ZERO;
    private int angerTicks;

    public Hoarder(EntityType<? extends Hoarder> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.ATTACK_DAMAGE, 6.0)
            .add(Attributes.MOVEMENT_SPEED, 0.26).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BARTER_ANIM, 0);
        builder.define(ANGRY, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7, 0.0f) {
            @Override
            public boolean canUse() {
                return super.canUse() && (home.equals(BlockPos.ZERO) || blockPosition().closerThan(home, 10));
            }
        });
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    public void setHome(BlockPos home) {
        this.home = home.immutable();
        setHomeTo(home, 10);
    }

    public BlockPos home() {
        return home;
    }

    public boolean isAngry() {
        return entityData.get(ANGRY);
    }

    public int barterAnim() {
        return entityData.get(BARTER_ANIM);
    }

    public void angerAt(LivingEntity culprit, int ticks) {
        angerTicks = ticks;
        entityData.set(ANGRY, true);
        setTarget(culprit);
    }

    public void onBarter(BlockPos cache, ServerPlayer player, boolean accepted) {
        entityData.set(BARTER_ANIM, 30);
        getLookControl().setLookAt(player);
        if (accepted && distanceToSqr(cache.getX() + 0.5, cache.getY(), cache.getZ() + 0.5) > 25 && level() instanceof ServerLevel server) {
            teleportNear(server, net.minecraft.world.phys.Vec3.atCenterOf(cache).add(random.nextGaussian() * 2, 1, random.nextGaussian() * 2));
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (entityData.get(BARTER_ANIM) > 0) {
            entityData.set(BARTER_ANIM, entityData.get(BARTER_ANIM) - 1);
        }
        if (angerTicks > 0 && --angerTicks == 0) {
            entityData.set(ANGRY, false);
            setTarget(null);
        }
        if (home.equals(BlockPos.ZERO)) {
            BlockPos found = findCache(level);
            if (found != null) {
                setHome(found);
            }
        }
        // "fetching": occasionally blink away and back with portal motes (§4.4)
        if (!isAngry() && random.nextInt(2400) == 0 && !home.equals(BlockPos.ZERO)) {
            level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.5, getZ(), 30, 0.4, 1, 0.4, 0.4);
            teleportNear(level, net.minecraft.world.phys.Vec3.atCenterOf(home).add(random.nextGaussian() * 4, 2, random.nextGaussian() * 4));
        }
    }

    private @Nullable BlockPos findCache(ServerLevel level) {
        for (BlockPos p : BlockPos.betweenClosed(blockPosition().offset(-12, -6, -12), blockPosition().offset(12, 6, 12))) {
            if (level.getBlockState(p).is(WFBlocks.HOARD_CACHE.get())) {
                return p.immutable();
            }
        }
        return null;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return isAngry() || target == getLastHurtByMob() ? super.canAttack(target) : false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return isAngry() ? WFSounds.ENTITY_HOARDER_ANGER.get() : WFSounds.ENTITY_HOARDER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return WFSounds.ENTITY_HOARDER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return WFSounds.ENTITY_HOARDER_DEATH.get();
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.store("home", BlockPos.CODEC, home);
        out.putInt("anger", angerTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.read("home", BlockPos.CODEC).ifPresent(this::setHome);
        angerTicks = in.getIntOr("anger", 0);
        entityData.set(ANGRY, angerTicks > 0);
    }
}
