package dev.worldsfall.undertow;

import dev.worldsfall.entity.RiderInput;
import dev.worldsfall.entity.VoidSkate;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.gravity.StableFields;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The Void Bell (proposal "Getting in"): a diving bell grown from a Gravity Anchor core. It carries up to two players and
 * one tamed Skate inside a 5-block Stable Field (no Void Erosion), moves at 3 blocks a second where its pilot looks
 * (jump to rise), and holds ten minutes of charge per Stillness Fragment. Sneak-use toggles its lamp, which Tide Eels
 * are drawn to. A Bell Winch recalls it.
 */
public class VoidBell extends Mob {
    public static final int FIELD_RADIUS = 5, CHARGE_PER_FRAGMENT = 12000, MAX_CHARGE = CHARGE_PER_FRAGMENT * 4;
    public static final double SPEED = 0.15;
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(VoidBell.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> LAMP = SynchedEntityData.defineId(VoidBell.class, EntityDataSerializers.BOOLEAN);
    private @Nullable UUID owner;
    private @Nullable BlockPos field;

    public VoidBell(EntityType<? extends VoidBell> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 60.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGE, 0);
        builder.define(LAMP, true);
    }

    public int charge() { return entityData.get(CHARGE); }
    public boolean lampOn() { return entityData.get(LAMP); }
    public void setLamp(boolean on) { entityData.set(LAMP, on); }
    public @Nullable UUID owner() { return owner; }
    public void setOwner(@Nullable UUID owner) { this.owner = owner; }

    /** Drains charge (Tide Eels, flight). */
    public void drain(int ticks) {
        entityData.set(CHARGE, Math.max(0, charge() - ticks));
    }

    public void addCharge(int ticks) {
        entityData.set(CHARGE, Math.min(MAX_CHARGE, charge() + ticks));
    }

    // ------------------------------------------------------------------ the moving Stable Field

    @Override
    public void tick() {
        super.tick();
        // the bubble moves with the bell, on both sides, while it has charge
        BlockPos now = charge() > 0 ? blockPosition() : null;
        if (!java.util.Objects.equals(now, field)) {
            if (field != null) {
                StableFields.remove(level(), field);
            }
            if (now != null) {
                StableFields.add(level(), now, FIELD_RADIUS);
            }
            field = now;
        }
        if (level().isClientSide() && lampOn() && charge() > 0 && random.nextInt(3) == 0) {
            level().addParticle(WFParticles.TIDEWISP_TRAIL.get(), getX() + random.nextGaussian() * 0.3, getY() + 2.6, getZ() + random.nextGaussian() * 0.3, 0, 0.01, 0);
        }
        if (!level().isClientSide() && isVehicle() && charge() > 0 && tickCount % 20 == 0) {
            drain(20);
            if (charge() == 0) {
                playSound(WFSounds.ENTITY_VOID_BELL_CREAK.get(), 2.0f, 0.6f);
                if (getControllingPassenger() instanceof ServerPlayer sp) {
                    sp.sendOverlayMessage(Component.translatable("message.worldsfall.void_bell.empty"));
                }
            }
        }
        if (!level().isClientSide() && tickCount % 160 == 0 && random.nextInt(2) == 0 && level().dimension() == dev.worldsfall.registry.WFDimensions.THE_UNDERTOW) {
            playSound(WFSounds.ENTITY_VOID_BELL_CREAK.get(), 1.0f, 0.8f + random.nextFloat() * 0.3f);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (field != null) {
            StableFields.remove(level(), field);
            field = null;
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ piloting

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        return Vec3.ZERO;
    }

    @Override
    public void travel(Vec3 input) {
        if (isLocalInstanceAuthoritative() && getControllingPassenger() instanceof Player pilot && charge() > 0) {
            Input in = RiderInput.of(pilot);
            Vec3 look = pilot.getLookAngle();
            Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
            Vec3 want = Vec3.ZERO;
            if (in.forward()) want = want.add(look);
            if (in.backward()) want = want.subtract(look);
            if (in.right()) want = want.add(right);
            if (in.left()) want = want.subtract(right);
            if (in.jump()) want = want.add(0, 1, 0);
            Vec3 target = want.lengthSqr() > 1.0e-4 ? want.normalize().scale(SPEED) : Vec3.ZERO;
            setDeltaMovement(getDeltaMovement().lerp(target, 0.15));
            setYRot(Mth.approachDegrees(getYRot(), pilot.getYRot(), 4));
            yBodyRot = yHeadRot = getYRot();
        } else {
            // unpowered or empty: a slow, heavy drift down
            Vec3 v = getDeltaMovement().scale(0.9);
            setDeltaMovement(v.x, Math.max(v.y - 0.004, charge() > 0 ? 0 : -0.08), v.z);
        }
        move(MoverType.SELF, getDeltaMovement());
        if (getDeltaMovement().lengthSqr() > 0.004 && tickCount % 40 == 0) {
            playSound(WFSounds.ENTITY_VOID_BELL_MOVE.get(), 0.6f, 1.0f);
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        long players = getPassengers().stream().filter(p -> p instanceof Player).count();
        long skates = getPassengers().stream().filter(p -> p instanceof VoidSkate).count();
        return passenger instanceof Player ? players < 2 : passenger instanceof VoidSkate s && s.isTame() && skates < 1;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) {
        int i = getPassengers().indexOf(passenger);
        double side = i == 0 ? -0.45 : i == 1 ? 0.45 : 0;
        return new Vec3(side, 0.35, i == 2 ? 0.6 : 0);
    }

    // ------------------------------------------------------------------ use

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(WFItems.STILLNESS_FRAGMENT.get())) {
            if (charge() + CHARGE_PER_FRAGMENT > MAX_CHARGE) {
                return InteractionResult.FAIL;
            }
            if (!level().isClientSide()) {
                stack.consume(1, player);
                addCharge(CHARGE_PER_FRAGMENT);
                playSound(WFSounds.ENTITY_VOID_BELL_LAMP.get(), 1.0f, 1.3f);
                player.sendOverlayMessage(Component.translatable("message.worldsfall.void_bell.charge", charge() / 1200));
            }
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            if (!level().isClientSide()) {
                setLamp(!lampOn());
                playSound(WFSounds.ENTITY_VOID_BELL_LAMP.get(), 1.0f, lampOn() ? 1.2f : 0.8f);
            }
            return InteractionResult.SUCCESS;
        }
        if (!level().isClientSide() && canAddPassenger(player)) {
            player.startRiding(this);
            if (owner == null) {
                owner = player.getUUID();
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.getEntity() instanceof Player p && p.isShiftKeyDown() && (owner == null || owner.equals(p.getUUID())) && !isVehicle()) {
            // the owner can take the bell back as an item, charge and all
            ItemStack item = new ItemStack(WFItems.VOID_BELL.get());
            item.set(dev.worldsfall.registry.WFComponents.BELL_CHARGE.get(), charge());
            spawnAtLocation(level, item);
            discard();
            return true;
        }
        return source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, damage);
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
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("charge", charge());
        out.putBoolean("lamp", lampOn());
        out.storeNullable("owner", UUIDUtil.CODEC, owner);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(CHARGE, in.getIntOr("charge", 0));
        setLamp(in.getBooleanOr("lamp", true));
        owner = in.read("owner", UUIDUtil.CODEC).orElse(null);
    }

    /** True if {@code e} is inside some Void Bell's Stable Field bubble. */
    public static boolean sheltered(Entity e) {
        if (e.getVehicle() instanceof VoidBell) {
            return true;
        }
        return !e.level().getEntitiesOfClass(VoidBell.class, e.getBoundingBox().inflate(FIELD_RADIUS), b -> b.charge() > 0
            && b.distanceToSqr(e) <= FIELD_RADIUS * FIELD_RADIUS).isEmpty();
    }
}
