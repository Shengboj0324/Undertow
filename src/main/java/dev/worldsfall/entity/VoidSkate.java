package dev.worldsfall.entity;

import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.registry.WFTags;
import dev.worldsfall.world.Progress;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Void Skate (§8.2): a manta-like glider of the Drift and the second mobility step. Shy in the wild; tamed
 * by patience (Chorus Pods → trust), made rideable with a Skate Harness. Riding: forward = cruise 9 b/s,
 * jump = climb 4 b/s (stamina), sneak = dive up to 16 b/s or perch near ground, idle = glide 1:3.
 * Refuses to go below the tide plane + 4.
 */
public class VoidSkate extends TamableAnimal {
    private static final EntityDataAccessor<Integer> TRUST = SynchedEntityData.defineId(VoidSkate.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(VoidSkate.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> PERCHED = SynchedEntityData.defineId(VoidSkate.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> STAMINA = SynchedEntityData.defineId(VoidSkate.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(VoidSkate.class, EntityDataSerializers.INT);

    public static final int TRUST_NEEDED = 5;
    public static final float MAX_STAMINA = 100;
    public static final double CRUISE = 9.0 / 20.0;
    public static final double DIVE_MAX = 16.0 / 20.0;
    public static final double CLIMB = 4.0 / 20.0;
    public static final double GLIDE = 6.0 / 20.0;
    public static final float STAMINA_COST = 8.0f / 20.0f;
    public static final float STAMINA_REGEN = 5.0f / 20.0f;
    public static final double TIDE_MARGIN = 4.0;

    private int trustDecay;
    private double rideSpeed;
    /** Client-side visual banking (radians), driven by turn rate. */
    public float bank;
    public float bankO;
    private float lastYaw;
    public long dismountWarnedSession;

    public VoidSkate(EntityType<? extends VoidSkate> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 6, true);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 30.0)
            .add(Attributes.ARMOR, 2.0)
            .add(Attributes.FLYING_SPEED, 0.35)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.FOLLOW_RANGE, 32.0)
            .add(Attributes.TEMPT_RANGE, 8.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TRUST, 0);
        builder.define(SADDLED, false);
        builder.define(PERCHED, false);
        builder.define(STAMINA, MAX_STAMINA);
        builder.define(VARIANT, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SkateGoals.FleeWhenHurt(this));
        goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        goalSelector.addGoal(3, new TemptGoal(this, 1.1, s -> s.is(WFTags.Items.SKATE_FOOD), false, 2.5));
        goalSelector.addGoal(4, new SkateGoals.WaitWhenTrusted(this));
        goalSelector.addGoal(5, new SkateGoals.Perch(this));
        goalSelector.addGoal(6, new SkateGoals.CircleIsland(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0f));
    }

    // ------------------------------------------------------------ state
    public int trust() { return entityData.get(TRUST); }
    public boolean isSaddled() { return entityData.get(SADDLED); }
    public boolean isPerched() { return entityData.get(PERCHED); }
    public float stamina() { return entityData.get(STAMINA); }
    public int variant() { return entityData.get(VARIANT); }
    public void setPerched(boolean perched) { entityData.set(PERCHED, perched); }
    public void setVariant(int v) { entityData.set(VARIANT, v); }
    public void setSaddled(boolean s) { entityData.set(SADDLED, s); }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(WFTags.Items.SKATE_BREEDING);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        VoidSkate baby = WFEntities.VOID_SKATE.get().create(level, EntitySpawnReason.BREEDING);
        if (baby != null) {
            baby.setVariant(random.nextInt(10) == 0 ? 1 : variant());
            if (getOwnerReference() != null) {
                baby.setOwnerReference(getOwnerReference());
                baby.setTame(true, true);
            }
        }
        return baby;
    }

    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal other) {
        return other instanceof VoidSkate skate && isTame() && skate.isTame() && super.canMate(other);
    }

    // ------------------------------------------------------------ interaction (§8.2 taming)
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isBaby()) {
            return super.mobInteract(player, hand);
        }
        if (!isTame() && stack.is(WFTags.Items.SKATE_FOOD)) {
            if (level() instanceof ServerLevel server) {
                stack.consume(1, player);
                trustDecay = 0;
                if (random.nextFloat() < 0.25f) {
                    int trust = trust() + 1;
                    entityData.set(TRUST, trust);
                    playSound(WFSounds.ENTITY_VOID_SKATE_TAME_PROGRESS.get(), 1.0f, 0.8f + trust * 0.1f);
                    server.sendParticles(WFParticles.SKATE_WAKE.get(), getX(), getY() + 0.5, getZ(), 6 + trust * 3, 1.2, 0.2, 1.2, 0.01);
                    if (trust >= TRUST_NEEDED) {
                        tame(player);
                        setPerched(false);
                        playSound(WFSounds.ENTITY_VOID_SKATE_TAME_PROGRESS.get(), 1.5f, 1.4f);
                        ringOfParticles(server);
                        if (player instanceof ServerPlayer sp) {
                            Progress.award(sp, "patience_of_the_void");
                        }
                    }
                } else {
                    server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.5, getZ(), 3, 0.4, 0.1, 0.4, 0.01);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (isTame() && isOwnedBy(player)) {
            if (!isSaddled() && stack.is(WFItems.SKATE_HARNESS.get())) {
                if (!level().isClientSide()) {
                    stack.consume(1, player);
                    setSaddled(true);
                    playSound(net.minecraft.sounds.SoundEvents.HORSE_SADDLE.value(), 1.0f, 0.9f);
                }
                return InteractionResult.SUCCESS;
            }
            if (isFood(stack) && canFallInLove()) {
                return super.mobInteract(player, hand);
            }
            if (isSaddled() && !player.isSecondaryUseActive() && stack.isEmpty()) {
                if (!level().isClientSide()) {
                    player.startRiding(this);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.mobInteract(player, hand);
    }

    private void ringOfParticles(ServerLevel server) {
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI * 2 / 32;
            server.sendParticles(WFParticles.SKATE_WAKE.get(), getX() + Math.cos(a) * 2.5, getY() + 0.4, getZ() + Math.sin(a) * 2.5, 1, 0, 0, 0, 0);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && !isTame()) {
            entityData.set(TRUST, 0);
        }
        return hurt;
    }

    // ------------------------------------------------------------ ticking
    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide() && !isTame() && trust() > 0 && ++trustDecay >= 600) {
            trustDecay = 0;
            entityData.set(TRUST, trust() - 1);
        }
        // Tide safety: never below tide + 4 (§8.2, §5.1)
        if (Tides.appliesTo(level())) {
            double floor = Tides.height(level()) + TIDE_MARGIN;
            if (getY() < floor) {
                Vec3 v = getDeltaMovement();
                setDeltaMovement(v.x, Math.max(v.y, 0.12), v.z);
            }
        }
        if (level().isClientSide()) {
            bankO = bank;
            float yawDelta = Mth.wrapDegrees(getYRot() - lastYaw);
            lastYaw = getYRot();
            float target = Mth.clamp(-yawDelta * 0.12f, -0.6f, 0.6f);
            bank += (target - bank) * 0.15f;
            if (getDeltaMovement().horizontalDistanceSqr() > 0.12 && random.nextInt(2) == 0) {
                spawnWake();
            }
        }
        if (!isVehicle() && !level().isClientSide() && stamina() < MAX_STAMINA) {
            // a Roost Post doubles the recovery of a perched Skate
            boolean roost = isPerched() && level().getBlockState(blockPosition().below()).getBlock() instanceof dev.worldsfall.gap.RoostPostBlock;
            entityData.set(STAMINA, Math.min(MAX_STAMINA, stamina() + STAMINA_REGEN * (roost ? 2 : 1)));
        }
    }

    private void spawnWake() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double side = 2.2;
        for (int s = -1; s <= 1; s += 2) {
            double ox = Mth.cos(yaw) * side * s, oz = Mth.sin(yaw) * side * s;
            level().addParticle(WFParticles.SKATE_WAKE.get(), getX() + ox, getY() + 0.3, getZ() + oz, 0, 0, 0);
        }
    }

    @Override
    public void travel(Vec3 input) {
        if (isLocalInstanceAuthoritative() && getControllingPassenger() instanceof Player rider) {
            travelRidden(rider);
            return;
        }
        if (isPerched() && !isVehicle()) {
            setDeltaMovement(getDeltaMovement().multiply(0.5, 0.0, 0.5));
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        travelFlying(input, (float) getAttributeValue(Attributes.FLYING_SPEED) * 0.3f);
    }

    /** Ridden flight model (§8.2). Runs on the controlling side (rider's client). */
    private void travelRidden(Player rider) {
        Input in = RiderInput.of(rider);
        float stamina = stamina();
        // steering: limited turn rate, wide arcs
        float diff = Mth.wrapDegrees(rider.getYRot() - getYRot());
        float turn = Mth.clamp(diff, -3.5f, 3.5f);
        setYRot(getYRot() + turn);
        yBodyRot = yHeadRot = getYRot();
        setXRot(rider.getXRot() * 0.3f);

        double vy;
        if (isPerched()) {
            if (in.forward() || in.jump()) {
                setPerched(false);
                rideSpeed = GLIDE;
                vy = CLIMB;
            } else {
                setDeltaMovement(Vec3.ZERO);
                move(MoverType.SELF, new Vec3(0, -0.05, 0));
                regen(rider, true);
                return;
            }
        } else {
            if (in.shift()) {
                if (isNearGround()) {
                    setPerched(true);
                    setDeltaMovement(Vec3.ZERO);
                    return;
                }
                rideSpeed = Math.min(DIVE_MAX, rideSpeed + 0.025);
                vy = -rideSpeed * 0.6;
            } else if (in.jump() && stamina > 0) {
                rideSpeed += (CRUISE - rideSpeed) * 0.05;
                vy = CLIMB;
                drain(rider);
            } else if (in.forward()) {
                rideSpeed += (CRUISE - rideSpeed) * 0.04;
                vy = -rideSpeed / 3.0 * 0.35;
                regen(rider, false);
            } else {
                rideSpeed += (GLIDE - rideSpeed) * 0.02;
                vy = -rideSpeed / 3.0;
                regen(rider, false);
            }
        }
        if (Tides.appliesTo(level()) && getY() < Tides.height(level()) + TIDE_MARGIN) {
            vy = Math.max(vy, 0.15);
        }
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 motion = new Vec3(-Mth.sin(yaw) * rideSpeed, vy, Mth.cos(yaw) * rideSpeed);
        Vec3 smoothed = getDeltaMovement().lerp(motion, 0.25);
        setDeltaMovement(smoothed);
        move(MoverType.SELF, smoothed);
        if (horizontalCollision) {
            rideSpeed *= 0.5;
        }
        resetFallDistance();
        rider.resetFallDistance();
    }

    private boolean isNearGround() {
        if (onGround()) {
            return true;
        }
        BlockPos below = blockPosition().below();
        return !level().getBlockState(below).isAir() || !level().getBlockState(below.below()).isAir();
    }

    private void drain(Player rider) {
        entityData.set(STAMINA, Math.max(0, stamina() - STAMINA_COST));
    }

    private void regen(Player rider, boolean perched) {
        if (perched || Math.abs(getDeltaMovement().y) < 0.1) {
            entityData.set(STAMINA, Math.min(MAX_STAMINA, stamina() + STAMINA_REGEN));
        }
    }

    @Override
    protected void tickRidden(Player controller, Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        if (!level().isClientSide()) {
            Input in = RiderInput.of(controller);
            if (in.jump() && !isPerched() && stamina() > 0) {
                entityData.set(STAMINA, Math.max(0, stamina() - STAMINA_COST));
            } else if (isPerched() || (!in.jump() && !in.shift())) {
                entityData.set(STAMINA, Math.min(MAX_STAMINA, stamina() + STAMINA_REGEN));
            }
            if (tickCount % 30 == 0) {
                playSound(WFSounds.ENTITY_VOID_SKATE_FLAP.get(), 0.6f, 0.9f + random.nextFloat() * 0.2f);
            }
            if (controller instanceof ServerPlayer sp && !isPerched() && distanceToSqr(0, getY(), 0) > 1000.0 * 1000.0) {
                SkateJourneys.onRiddenFar(sp, this);
            }
        }
    }

    @Override
    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        return Vec3.ZERO;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return isSaddled() && getFirstPassenger() instanceof Player p && isOwnedBy(p) ? p : super.getControllingPassenger();
    }

    @Override
    protected boolean canAddPassenger(net.minecraft.world.entity.Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(net.minecraft.world.entity.Entity passenger, net.minecraft.world.entity.EntityDimensions dims, float scale) {
        return new Vec3(0, dims.height() * 0.85, -0.2);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !isVehicle();
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return !isTame() && !hasCustomName() && distSqr > 128 * 128;
    }

    // ------------------------------------------------------------ sounds
    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return WFSounds.ENTITY_VOID_SKATE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return WFSounds.ENTITY_VOID_SKATE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return WFSounds.ENTITY_VOID_SKATE_DEATH.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 400;
    }

    @Override
    protected float getSoundVolume() {
        return 1.6f;
    }

    // ------------------------------------------------------------ save
    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("trust", trust());
        out.putBoolean("saddled", isSaddled());
        out.putBoolean("perched", isPerched());
        out.putFloat("stamina", stamina());
        out.putInt("variant", variant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(TRUST, in.getIntOr("trust", 0));
        setSaddled(in.getBooleanOr("saddled", false));
        setPerched(in.getBooleanOr("perched", false));
        entityData.set(STAMINA, in.getFloatOr("stamina", MAX_STAMINA));
        setVariant(in.getIntOr("variant", 0));
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
                                                                  net.minecraft.world.DifficultyInstance difficulty, EntitySpawnReason reason,
                                                                  net.minecraft.world.entity.@Nullable SpawnGroupData data) {
        setVariant(random.nextInt(12) == 0 ? 1 : 0);
        return super.finalizeSpawn(level, difficulty, reason, data);
    }
}
