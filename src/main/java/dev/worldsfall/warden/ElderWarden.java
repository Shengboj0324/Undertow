package dev.worldsfall.warden;

import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.Progress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Elder Warden: the released dragon, a unique and peaceful world creature. It hovers over the tallest Fallen Spire;
 * once per Minecraft day it accepts an offering of Roasted Chorus Pods and carries the player to the End Gateway their
 * Gateway Compass is bound to (Warden's Passage). It is its own entity type, so the vanilla dragon fight never mistakes it
 * for the boss.
 */
public class ElderWarden extends EnderDragon {
    public static final int OFFERING = 16;
    private BlockPos perch = BlockPos.ZERO;
    private @Nullable Vec3 destination;
    private long lastOfferingDay = -1;
    private int idleCall;

    public ElderWarden(EntityType<? extends EnderDragon> type, Level level) {
        super(type, level);
        setData(WFAttachments.DRAGON_POSE, 2);
    }

    /** Spawns the Elder over its perch the first time a player comes near after the Release. */
    static void ensure(ServerLevel end, WardenData data) {
        BlockPos p = data.elderPerch();
        if (p == null || data.elderSpawned() || end.getGameTime() % 40 != 0) {
            return;
        }
        if (end.getNearestPlayer(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 160, false) == null || !end.isLoaded(p)) {
            return;
        }
        ElderWarden elder = WardenRelease.type().create(end, WardenRelease.reason());
        if (elder == null) {
            return;
        }
        int y = Math.max(p.getY(), WardenRelease.perchSurface(end, p) + 6);
        elder.perch = new BlockPos(p.getX(), y, p.getZ());
        elder.snapTo(p.getX() + 0.5, y + 4, p.getZ() + 0.5, end.getRandom().nextFloat() * 360, 0);
        elder.setCustomName(Component.translatable("entity.worldsfall.elder_warden"));
        elder.setPersistenceRequired();
        end.addFreshEntity(elder);
        data.setElderSpawned(true);
    }

    /** Where the Elder rests; it hovers six blocks above. */
    public void setPerch(BlockPos pos) {
        perch = pos.immutable();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (destination != null && getFirstPassenger() instanceof ServerPlayer rider) {
            carry(level, rider);
            return;
        }
        destination = null;
        Vec3 home = Vec3.atCenterOf(perch).add(0, 6, 0);
        if (position().distanceToSqr(home) > 12 * 12) {
            WardenFight.steer(this, home);
        } else if (getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOVERING) {
            getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
        }
        if (tickCount % 6 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.5, getZ(), 2, 3.5, 1.5, 3.5, 0.01);
        }
        if (--idleCall < 0) {
            idleCall = 1200 + getRandom().nextInt(1200);
            level.playSound(null, getX(), getY(), getZ(), WFSounds.ENTITY_ELDER_WARDEN_CALL.get(), getSoundSource(), 8.0f, 0.9f);
        }
    }

    private void carry(ServerLevel level, ServerPlayer rider) {
        rider.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false, false));
        Vec3 flat = new Vec3(destination.x - getX(), 0, destination.z - getZ());
        double cruise = Math.max(destination.y + 24, 130);
        if (flat.lengthSqr() > 16 * 16) {
            Vec3 lead = position().add(flat.normalize().scale(40));
            WardenFight.steer(this, new Vec3(lead.x, cruise, lead.z));
        } else {
            WardenFight.steer(this, destination.add(0, 6, 0));
            if (position().distanceToSqr(destination.add(0, 6, 0)) < 9 * 9) {
                rider.stopRiding();
                rider.teleportTo(destination.x, destination.y + 2, destination.z);
                level.playSound(null, getX(), getY(), getZ(), WFSounds.ENTITY_ELDER_WARDEN_CALL.get(), getSoundSource(), 6.0f, 1.1f);
                Progress.award(rider, "elder_passage");
                destination = null;
            }
        }
    }

    /** Right-clicking the Elder (or one of its parts) with an offering of Roasted Chorus Pods. */
    public boolean offer(ServerPlayer player, InteractionHand hand) {
        if (!(level() instanceof ServerLevel level)) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(WFItems.ROASTED_CHORUS_POD.get())) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.elder.wants"));
            return false;
        }
        long day = level.getOverworldClockTime() / 24000L;
        if (day == lastOfferingDay) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.elder.rested"));
            return false;
        }
        if (held.getCount() < OFFERING) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.elder.more", OFFERING));
            return false;
        }
        Vec3 dest = boundGateway(player);
        if (dest == null) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.elder.no_gateway"));
            return false;
        }
        held.shrink(OFFERING);
        lastOfferingDay = day;
        destination = dest;
        player.startRiding(this, true, true);
        level.playSound(null, getX(), getY(), getZ(), WFSounds.EVENT_WARDEN_BOW.get(), getSoundSource(), 6.0f, 1.0f);
        player.sendOverlayMessage(Component.translatable("message.worldsfall.elder.passage"));
        return true;
    }

    private static @Nullable Vec3 boundGateway(ServerPlayer player) {
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(h);
            LodestoneTracker tracker = s.get(DataComponents.LODESTONE_TRACKER);
            if (s.is(WFItems.GATEWAY_COMPASS.get()) && tracker != null && tracker.target().isPresent()) {
                GlobalPos g = tracker.target().get();
                if (g.dimension() == Level.END) {
                    return Vec3.atCenterOf(g.pos());
                }
            }
        }
        return null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().isEmpty();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return true;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putLong("perch", perch.asLong());
        output.putLong("last_offering_day", lastOfferingDay);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        perch = BlockPos.of(input.getLongOr("perch", 0L));
        lastOfferingDay = input.getLongOr("last_offering_day", -1L);
    }
}
