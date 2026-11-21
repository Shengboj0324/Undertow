package dev.worldsfall.entity;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.registry.WFComponents;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.registry.WFTags;
import dev.worldsfall.world.Progress;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LandOnOwnersShoulderGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.animal.parrot.ShoulderRidingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Wyrm companion (§10): Hatchling (0–2 h, shoulder, chirps before tides) → Fledgling (2–6 h, Tide Sense,
 * Fetch) → Drake (6 h + 16 Roasted Chorus Pods: rideable short flight, Void Ward). Never fights. At zero
 * health it curls into a Slumbering Egg instead of dying (D7).
 */
public class Wyrm extends ShoulderRidingEntity {
    public enum Stage { HATCHLING, FLEDGLING, DRAKE }

    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(Wyrm.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(Wyrm.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> STAMINA = SynchedEntityData.defineId(Wyrm.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<BlockPos> SENSE = SynchedEntityData.defineId(Wyrm.class, EntityDataSerializers.BLOCK_POS);
    /** Bit 0 Wardenborn (slate and gold), bit 1 barded, bit 2 saddlebags. */
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(Wyrm.class, EntityDataSerializers.INT);
    public static final int LOOK_WARDENBORN = 1, LOOK_BARDED = 2, LOOK_BAGS = 4;
    /** Temperament set at hatching by the tide (expansion "The Wyrm grows a personality"). */
    public enum Temperament { NONE, CALM, BOLD, DREAMING }
    public static final net.minecraft.resources.Identifier BARDING_HEALTH =
        net.minecraft.resources.Identifier.fromNamespaceAndPath(dev.worldsfall.Worldsfall.MODID, "wyrm_barding");
    private Temperament temperament = Temperament.NONE;
    private final net.minecraft.world.SimpleContainer bags = new net.minecraft.world.SimpleContainer(9);
    private BlockPos roost = BlockPos.ZERO;
    public static final int PODS_FOR_DRAKE = 16;
    public static final float MAX_STAMINA = 30 * 20;
    public static final double CRUISE = 8.0 / 20.0;
    public static final int CEILING = 40;
    public static final double WARD_RADIUS = 4.0;

    private long growthTicks;
    private int podsFed;
    private @Nullable ItemEntity fetching;

    public Wyrm(EntityType<? extends Wyrm> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TamableAnimalAttributes.base();
    }

    static final class TamableAnimalAttributes {
        static AttributeSupplier.Builder base() {
            return net.minecraft.world.entity.animal.Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FLYING_SPEED, 0.4).add(Attributes.FOLLOW_RANGE, 32.0);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STAGE, 0);
        builder.define(SADDLED, false);
        builder.define(STAMINA, MAX_STAMINA);
        builder.define(SENSE, BlockPos.ZERO);
        builder.define(LOOK, 0);
    }

    // ------------------------------------------------------------ personality, barding, saddlebags (expansion)
    public int look() {
        return entityData.get(LOOK);
    }

    public boolean hasLook(int bit) {
        return (look() & bit) != 0;
    }

    public void setLook(int bit, boolean on) {
        entityData.set(LOOK, on ? look() | bit : look() & ~bit);
    }

    public Temperament temperament() {
        return temperament;
    }

    /** Sets the temperament from the tide at the moment of hatching: Low → Calm, High → Bold, an Eclipse → Dreaming. */
    public void setTemperamentFromWorld(ServerLevel level) {
        if (dev.worldsfall.life.WorldEvents.current(level.getServer()) == dev.worldsfall.life.WorldEvents.ECLIPSE) {
            temperament = Temperament.DREAMING;
        } else if (Tides.appliesTo(level)) {
            var phase = Tides.phase(level);
            temperament = phase == dev.worldsfall.world.tide.TideCycle.Phase.HIGH || phase == dev.worldsfall.world.tide.TideCycle.Phase.RISING
                ? Temperament.BOLD : Temperament.CALM;
        } else {
            temperament = Temperament.CALM;
        }
    }

    public float maxStamina() {
        return temperament == Temperament.BOLD ? MAX_STAMINA + 10 * 20 : MAX_STAMINA;
    }

    public BlockPos roost() {
        return roost;
    }

    public void setRoost(BlockPos pos) {
        roost = pos.immutable();
    }

    private void applyBarding() {
        var attr = getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) {
            return;
        }
        boolean on = hasLook(LOOK_BARDED) && stage() == Stage.DRAKE;
        if (on && attr.getModifier(BARDING_HEALTH) == null) {
            attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(BARDING_HEALTH, 1.0,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        } else if (!on && attr.getModifier(BARDING_HEALTH) != null) {
            attr.removeModifier(BARDING_HEALTH);
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Monster.class, 8.0f, 1.2, 1.5));
        goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.1, 6.0f, 2.0f));
        goalSelector.addGoal(4, new LandOnOwnersShoulderGoal(this) {
            @Override
            public boolean canUse() {
                return stage() == Stage.HATCHLING && super.canUse();
            }
        });
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------ stage
    public Stage stage() {
        return Stage.values()[Mth.clamp(entityData.get(STAGE), 0, 2)];
    }

    public void setStage(Stage stage) {
        entityData.set(STAGE, stage.ordinal());
        double hp = switch (stage) {
            case HATCHLING -> 10;
            case FLEDGLING -> 20;
            case DRAKE -> 40;
        };
        var attr = getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(hp);
        }
        applyBarding();
        setHealth(getMaxHealth());
        refreshDimensions();
    }

    public long growthTicks() {
        return growthTicks;
    }

    public boolean isSaddled() {
        return entityData.get(SADDLED);
    }

    public float stamina() {
        return entityData.get(STAMINA);
    }

    public BlockPos senseTarget() {
        return entityData.get(SENSE);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STAGE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return switch (stage()) {
            case HATCHLING -> EntityDimensions.scalable(0.6f, 0.6f);
            case FLEDGLING -> EntityDimensions.scalable(0.9f, 0.9f);
            case DRAKE -> EntityDimensions.scalable(1.4f, 1.6f);
        };
    }

    @Override
    public boolean canSitOnShoulder() {
        return stage() == Stage.HATCHLING && super.canSitOnShoulder();
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(WFTags.Items.WYRM_FOOD);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal other) {
        return false;
    }

    // ------------------------------------------------------------ interaction
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isTame() || !isOwnedBy(player)) {
            return InteractionResult.PASS;
        }
        if (stack.is(WFItems.ROASTED_CHORUS_POD.get()) && stage() == Stage.FLEDGLING) {
            if (!level().isClientSide()) {
                stack.consume(1, player);
                podsFed++;
                playSound(WFSounds.ENTITY_WYRM_CHIRP.get(), 1.0f, 1.2f);
                tryGrow();
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(WFItems.SKATE_HARNESS.get()) && stage() == Stage.DRAKE && !isSaddled()) {
            if (!level().isClientSide()) {
                stack.consume(1, player);
                entityData.set(SADDLED, true);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(WFItems.WYRM_BARDING.get()) && stage() == Stage.DRAKE && !hasLook(LOOK_BARDED)) {
            if (!level().isClientSide()) {
                stack.consume(1, player);
                setLook(LOOK_BARDED, true);
                applyBarding();
                heal(getMaxHealth());
                playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 0.9f);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof dev.worldsfall.item.ShulkerSatchelItem && stage() == Stage.DRAKE && isSaddled() && !hasLook(LOOK_BAGS)) {
            if (!level().isClientSide()) {
                stack.consume(1, player);
                setLook(LOOK_BAGS, true);
                playSound(WFSounds.ITEM_SATCHEL_CLOSE.get(), 1.0f, 1.0f);
            }
            return InteractionResult.SUCCESS;
        }
        if (hasLook(LOOK_BAGS) && stack.isEmpty() && player.isSecondaryUseActive()) {
            if (player instanceof ServerPlayer sp) {
                sp.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new net.minecraft.world.inventory.ChestMenu(
                    net.minecraft.world.inventory.MenuType.GENERIC_9x1, id, inv, bags, 1), getDisplayName()));
                playSound(WFSounds.ITEM_SATCHEL_OPEN.get(), 1.0f, 1.0f);
            }
            return InteractionResult.SUCCESS;
        }
        if (stage() == Stage.DRAKE && isSaddled() && stack.isEmpty() && !player.isSecondaryUseActive()) {
            if (!level().isClientSide()) {
                player.startRiding(this);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.isEmpty()) {
            if (!level().isClientSide()) {
                setOrderedToSit(!isOrderedToSit());
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    // ------------------------------------------------------------ ticking
    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity owner = getOwner();
        if (owner != null && owner.level() == level && distanceToSqr(owner) < 32 * 32) {
            growthTicks++;
            if (growthTicks % 200 == 0) {
                tryGrow();
            }
            if (Tides.appliesTo(level) && Tides.isStirring(level) && tickCount % 100 == 0) {
                playSound(WFSounds.ENTITY_WYRM_WARN.get(), 1.0f, 1.1f);
            } else if (random.nextInt(600) == 0) {
                playSound(WFSounds.ENTITY_WYRM_CHIRP.get(), 0.8f, 1.0f + random.nextFloat() * 0.2f);
            }
            if (stage() != Stage.HATCHLING && owner instanceof Player p) {
                tideSense(level, p);
                fetch(level, p);
            }
        }
        if (!isVehicle() && stamina() < maxStamina() && onGround()) {
            entityData.set(STAMINA, Math.min(maxStamina(), stamina() + 2));
        }
    }

    public void tryGrow() {
        double fledgling = WFConfig.get(WFConfig.SERVER.wyrmFledglingHours, 2.0) * 72000;
        double drake = WFConfig.get(WFConfig.SERVER.wyrmDrakeHours, 6.0) * 72000;
        if (stage() == Stage.HATCHLING && growthTicks >= fledgling) {
            setStage(Stage.FLEDGLING);
            playSound(WFSounds.EVENT_EGG_HATCH.get(), 0.8f, 1.5f);
        } else if (stage() == Stage.FLEDGLING && growthTicks >= drake && podsFed >= PODS_FOR_DRAKE) {
            setStage(Stage.DRAKE);
            playSound(WFSounds.EVENT_EGG_HATCH.get(), 1.0f, 0.8f);
            if (getOwner() instanceof ServerPlayer sp) {
                Progress.award(sp, "fully_grown");
            }
        }
    }

    /** Tide Sense (§10.3): while the owner sneaks, point toward the nearest Driftwreck within 64 blocks. */
    private void tideSense(ServerLevel level, Player owner) {
        if (!owner.isShiftKeyDown()) {
            if (!senseTarget().equals(BlockPos.ZERO)) {
                entityData.set(SENSE, BlockPos.ZERO);
            }
            return;
        }
        if (tickCount % 40 == 0) {
            var target = temperament == Temperament.DREAMING ? WFTags.Structures.LORE : WFTags.Structures.DRIFTWRECKS;
            int range = temperament == Temperament.DREAMING ? 128 : 64;
            BlockPos found = level.findNearestMapStructure(target, blockPosition(), range / 16, false);
            entityData.set(SENSE, found != null && found.closerThan(blockPosition(), range) ? found : BlockPos.ZERO);
        }
        if (!senseTarget().equals(BlockPos.ZERO)) {
            getLookControl().setLookAt(Vec3.atCenterOf(senseTarget()));
        }
    }

    /** Fetch (§10.3): dive to retrieve one item stack that fell off an island within the last 5 seconds. */
    private void fetch(ServerLevel level, Player owner) {
        if (fetching == null && tickCount % 10 == 0) {
            double reach = temperament == Temperament.CALM ? 2.0 : 1.0;
            List<ItemEntity> falling = level.getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(12 * reach, 24 * reach, 12 * reach),
                e -> e.getAge() < 100 && e.getDeltaMovement().y < -0.3 && e.getY() < owner.getY() - 3);
            if (!falling.isEmpty()) {
                fetching = falling.get(0);
            }
        }
        if (fetching != null) {
            if (!fetching.isAlive()) {
                fetching = null;
                return;
            }
            Vec3 to = fetching.position().subtract(position());
            setDeltaMovement(to.normalize().scale(Math.min(1.2, to.length())));
            move(MoverType.SELF, getDeltaMovement());
            if (distanceToSqr(fetching) < 2.5) {
                dev.worldsfall.util.WFUtil.give(owner, fetching.getItem().copy());
                fetching.discard();
                fetching = null;
                playSound(WFSounds.ENTITY_WYRM_CHIRP.get(), 1.0f, 1.4f);
            }
        }
    }

    /** True if the owner is protected from Void Erosion by an adult Drake within 4 blocks (§10.3). */
    public static boolean wardsPlayer(Player player) {
        return !player.level().getEntitiesOfClass(Wyrm.class, player.getBoundingBox().inflate(WARD_RADIUS),
            w -> w.stage() == Stage.DRAKE && w.isOwnedBy(player)).isEmpty();
    }

    // ------------------------------------------------------------ Drake flight (§10.3)
    @Override
    public void travel(Vec3 input) {
        if (isLocalInstanceAuthoritative() && getControllingPassenger() instanceof Player rider && stage() == Stage.DRAKE) {
            Input in = RiderInput.of(rider);
            setYRot(getYRot() + Mth.clamp(Mth.wrapDegrees(rider.getYRot() - getYRot()), -6f, 6f));
            yBodyRot = yHeadRot = getYRot();
            double vy = 0;
            boolean flying = !onGround();
            if (in.jump() && stamina() > 0) {
                int ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
                vy = getY() < ground + CEILING ? 0.25 : 0;
                entityData.set(STAMINA, Math.max(0, stamina() - 1));
            } else if (flying) {
                vy = in.shift() ? -0.35 : -0.06;
                entityData.set(STAMINA, Math.max(0, stamina() - 1));
            }
            if (stamina() <= 0) {
                vy = Math.min(vy, -0.2);
            }
            double speed = in.forward() ? CRUISE : 0;
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            Vec3 motion = new Vec3(-Mth.sin(yaw) * speed, vy, Mth.cos(yaw) * speed);
            setDeltaMovement(getDeltaMovement().lerp(motion, 0.3));
            move(MoverType.SELF, getDeltaMovement());
            resetFallDistance();
            rider.resetFallDistance();
            return;
        }
        super.travel(input);
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return stage() == Stage.DRAKE && isSaddled() && getFirstPassenger() instanceof Player p && isOwnedBy(p) ? p : null;
    }

    @Override
    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        return Vec3.ZERO;
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    // ------------------------------------------------------------ never truly dies (§10.4)
    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel level) {
            ItemStack egg = toSlumberingEgg(level);
            ItemEntity item = new ItemEntity(level, getX(), Math.max(getY(), level.getMinY() + 2), getZ(), egg);
            item.setNoGravity(getY() < 40);
            item.setUnlimitedLifetime();
            item.setPermanentlyInvulnerable(true);
            level.addFreshEntity(item);
            playSound(WFSounds.ENTITY_WYRM_SLUMBER.get(), 1.0f, 1.0f);
            discard();
            return;
        }
        super.die(source);
    }

    public ItemStack toSlumberingEgg(ServerLevel level) {
        ItemStack egg = new ItemStack(WFItems.SLUMBERING_EGG.get());
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        saveWithoutId(out);
        CompoundTag tag = out.buildResult();
        tag.remove("Pos");
        tag.remove("Motion");
        tag.remove("UUID");
        tag.putFloat("Health", 1.0f);
        egg.set(WFComponents.WYRM_DATA.get(), CustomData.of(tag));
        return egg;
    }

    public void restoreFromSlumber(ServerLevel level, CompoundTag tag) {
        load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        setHealth(getMaxHealth());
        setOrderedToSit(false);
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return WFSounds.ENTITY_WYRM_HURT.get();
    }

    @Override
    public float getVoicePitch() {
        return switch (stage()) {
            case HATCHLING -> 1.3f;
            case FLEDGLING -> 1.0f;
            case DRAKE -> 0.75f;
        };
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("stage", stage().ordinal());
        out.putLong("growth", growthTicks);
        out.putInt("pods", podsFed);
        out.putBoolean("saddled", isSaddled());
        out.putInt("look", look());
        out.putString("temperament", temperament.name());
        out.putLong("roost", roost.asLong());
        net.minecraft.world.ContainerHelper.saveAllItems(out.child("bags"), bags.getItems());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(STAGE, Mth.clamp(in.getIntOr("stage", 0), 0, 2));
        growthTicks = in.getLongOr("growth", 0);
        podsFed = in.getIntOr("pods", 0);
        entityData.set(SADDLED, in.getBooleanOr("saddled", false));
        entityData.set(LOOK, in.getIntOr("look", 0));
        try {
            temperament = Temperament.valueOf(in.getStringOr("temperament", "NONE"));
        } catch (IllegalArgumentException e) {
            temperament = Temperament.NONE;
        }
        roost = BlockPos.of(in.getLongOr("roost", 0L));
        in.child("bags").ifPresent(c -> net.minecraft.world.ContainerHelper.loadAllItems(c, bags.getItems()));
        refreshDimensions();
        applyBarding();
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        for (ItemStack s : bags.removeAllItems()) {
            spawnAtLocation(level, s);
        }
    }

    public void debugSetStage(Stage stage) {
        double fledgling = WFConfig.get(WFConfig.SERVER.wyrmFledglingHours, 2.0) * 72000;
        double drake = WFConfig.get(WFConfig.SERVER.wyrmDrakeHours, 6.0) * 72000;
        growthTicks = switch (stage) {
            case HATCHLING -> 0;
            case FLEDGLING -> (long) fledgling;
            case DRAKE -> (long) drake;
        };
        podsFed = stage == Stage.DRAKE ? PODS_FOR_DRAKE : podsFed;
        setStage(stage);
    }
}
