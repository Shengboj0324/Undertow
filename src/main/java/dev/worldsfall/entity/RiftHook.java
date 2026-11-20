package dev.worldsfall.entity;

import dev.worldsfall.item.RiftTetherItem;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.registry.WFTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Rift Tether's hook (§9.3). Flies up to 24 blocks; latches onto solid blocks except
 * {@code #worldsfall:tether_unlatchable}. While latched, the owner is held on a rope (pendulum swing) and
 * can reel in at 12 b/s. Player movement is client-authoritative, so rope physics run on the owner's client.
 */
public class RiftHook extends Projectile {
    private static final EntityDataAccessor<Boolean> LATCHED = SynchedEntityData.defineId(RiftHook.class, EntityDataSerializers.BOOLEAN);
    private static final Map<Boolean, Map<UUID, RiftHook>> ACTIVE = new ConcurrentHashMap<>();
    private static final double SPEED = 2.2;

    private double ropeLength = -1;
    private int life;

    public RiftHook(EntityType<? extends RiftHook> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static RiftHook fire(ServerLevel level, Player owner) {
        RiftHook hook = new RiftHook(WFEntities.RIFT_HOOK.get(), level);
        hook.setOwner(owner);
        Vec3 eye = owner.getEyePosition();
        hook.setPos(eye.x, eye.y - 0.1, eye.z);
        Vec3 look = owner.getLookAngle();
        hook.setDeltaMovement(look.scale(SPEED));
        return hook;
    }

    public static @Nullable RiftHook activeFor(Player player) {
        RiftHook hook = ACTIVE.computeIfAbsent(player.level().isClientSide(), k -> new ConcurrentHashMap<>()).get(player.getUUID());
        if (hook != null && (hook.isRemoved() || hook.level() != player.level())) {
            ACTIVE.get(player.level().isClientSide()).remove(player.getUUID(), hook);
            return null;
        }
        return hook;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LATCHED, false);
    }

    public boolean isLatched() {
        return entityData.get(LATCHED);
    }

    public double ropeLength() {
        return ropeLength;
    }

    private @Nullable Player ownerPlayer() {
        return getOwner() instanceof Player p ? p : null;
    }

    @Override
    public void tick() {
        super.tick();
        Player owner = ownerPlayer();
        if (owner != null) {
            ACTIVE.computeIfAbsent(level().isClientSide(), k -> new ConcurrentHashMap<>()).put(owner.getUUID(), this);
        }
        if (level().isClientSide()) {
            if (isLatched() && owner != null && ropeLength < 0) {
                ropeLength = owner.position().add(0, owner.getBbHeight() * 0.5, 0).distanceTo(position());
            }
            if (isLatched() && owner != null && owner.isLocalPlayer()) {
                constrain(owner);
            }
            return;
        }
        life++;
        if (owner == null || !owner.isAlive() || owner.level() != level() || owner.isSpectator()
            || !(owner.getMainHandItem().is(WFItems.RIFT_TETHER.get()) || owner.getOffhandItem().is(WFItems.RIFT_TETHER.get()))) {
            discard();
            return;
        }
        if (isLatched()) {
            owner.resetFallDistance();
            if (owner.isShiftKeyDown() || owner.distanceTo(this) > RiftTetherItem.RANGE * 1.6) {
                detach(owner);
            }
            return;
        }
        Vec3 start = position();
        Vec3 end = start.add(getDeltaMovement());
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, e -> false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
            BlockPos pos = bhr.getBlockPos();
            if (level().getBlockState(pos).is(WFTags.Blocks.TETHER_UNLATCHABLE)) {
                level().playSound(null, pos, WFSounds.ITEM_RIFT_TETHER_DETACH.get(), SoundSource.PLAYERS, 0.7f, 1.6f);
                discard();
                return;
            }
            Vec3 at = bhr.getLocation();
            setPos(at.x, at.y, at.z);
            setDeltaMovement(Vec3.ZERO);
            entityData.set(LATCHED, true);
            ropeLength = owner.position().add(0, owner.getBbHeight() * 0.5, 0).distanceTo(at);
            level().playSound(null, pos, WFSounds.ITEM_RIFT_TETHER_LATCH.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            return;
        }
        setPos(end.x, end.y, end.z);
        if (owner.distanceTo(this) > RiftTetherItem.RANGE || life > 40) {
            discard();
        }
    }

    /** Reel one tick: shorten the rope and pull the owner toward the anchor (client side physics). */
    public void reel(Player owner) {
        if (!isLatched()) {
            return;
        }
        if (!level().isClientSide()) {
            owner.resetFallDistance();
            return;
        }
        if (ropeLength < 0) {
            ropeLength = owner.position().distanceTo(position());
        }
        ropeLength = Math.max(1.5, ropeLength - RiftTetherItem.REEL_SPEED);
        Vec3 toAnchor = position().subtract(owner.position().add(0, owner.getBbHeight() * 0.5, 0));
        if (toAnchor.lengthSqr() > 1e-6) {
            Vec3 dir = toAnchor.normalize();
            Vec3 v = owner.getDeltaMovement();
            double along = v.dot(dir);
            if (along < RiftTetherItem.REEL_SPEED) {
                owner.setDeltaMovement(v.add(dir.scale(RiftTetherItem.REEL_SPEED - along)));
            }
        }
    }

    /** Rope constraint: never further than ropeLength; outward velocity is removed, producing a pendulum. */
    private void constrain(Player owner) {
        if (ropeLength < 0) {
            return;
        }
        Vec3 center = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
        Vec3 fromAnchor = center.subtract(position());
        double d = fromAnchor.length();
        if (d > ropeLength && d > 1e-4) {
            Vec3 n = fromAnchor.scale(1 / d);
            Vec3 v = owner.getDeltaMovement();
            double outward = v.dot(n);
            if (outward > 0) {
                v = v.subtract(n.scale(outward));
            }
            Vec3 correction = n.scale(-(d - ropeLength));
            owner.setDeltaMovement(v.add(correction.scale(0.5)));
        }
    }

    private void detach(Player owner) {
        level().playSound(null, owner.blockPosition(), WFSounds.ITEM_RIFT_TETHER_DETACH.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        ItemCooldownHelper.apply(owner);
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        Player owner = ownerPlayer();
        if (owner != null) {
            Map<UUID, RiftHook> map = ACTIVE.get(level().isClientSide());
            if (map != null) {
                map.remove(owner.getUUID(), this);
            }
        }
        super.remove(reason);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    static final class ItemCooldownHelper {
        static void apply(Player owner) {
            net.minecraft.world.item.ItemStack stack = owner.getMainHandItem().is(WFItems.RIFT_TETHER.get()) ? owner.getMainHandItem() : owner.getOffhandItem();
            owner.getCooldowns().addCooldown(stack, RiftTetherItem.DETACH_COOLDOWN);
        }
    }
}
