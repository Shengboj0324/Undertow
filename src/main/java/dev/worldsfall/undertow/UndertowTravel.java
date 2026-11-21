package dev.worldsfall.undertow;

import dev.worldsfall.registry.WFDimensions;
import dev.worldsfall.world.Progress;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Moving between the End and the Undertow. A Tide Maw swallows you into the Undertow at the same place; rising above its
 * ceiling (the tide plane seen from below) or sinking to its floor returns you to the End's tide plane, with a few seconds
 * of Slow Falling. A Void Bell travels with everyone inside it.
 */
public final class UndertowTravel {
    public static final int ARRIVAL_Y = 200, CEILING_Y = 248, FLOOR_Y = 6;

    private UndertowTravel() {}

    public static void enter(ServerLevel end, Entity e, Vec3 maw) {
        ServerLevel undertow = end.getServer().getLevel(WFDimensions.THE_UNDERTOW);
        if (undertow == null || e.isOnPortalCooldown()) {
            return;
        }
        SunkenArchive.ensure(undertow, maw);
        Vec3 at = new Vec3(maw.x + e.getRandom().nextGaussian() * 2, ARRIVAL_Y, maw.z + e.getRandom().nextGaussian() * 2);
        Entity moved = move(e, undertow, at);
        if (moved instanceof ServerPlayer sp) {
            Progress.award(sp, "into_the_undertow");
        }
        for (Entity p : moved == null ? List.<Entity>of() : moved.getPassengers()) {
            if (p instanceof ServerPlayer sp) {
                Progress.award(sp, "into_the_undertow");
            }
        }
    }

    public static void exit(ServerLevel undertow, Entity e) {
        ServerLevel end = undertow.getServer().getLevel(Level.END);
        if (end == null || e.isOnPortalCooldown()) {
            return;
        }
        double y = Tides.appliesTo(end) ? Tides.height(end) + 3 : 40;
        Entity moved = move(e, end, new Vec3(e.getX(), y, e.getZ()));
        if (moved instanceof LivingEntity l && !(moved instanceof VoidBell)) {
            l.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
        }
        if (moved != null) {
            for (Entity p : moved.getPassengers()) {
                if (p instanceof LivingEntity l) {
                    l.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
                }
            }
        }
    }

    /** Teleports an entity and everything riding it, then seats the riders again. */
    static @Nullable Entity move(Entity e, ServerLevel target, Vec3 at) {
        List<Entity> riders = new ArrayList<>(e.getPassengers());
        for (Entity r : riders) {
            r.stopRiding();
        }
        Entity moved = e.teleport(new TeleportTransition(target, at, Vec3.ZERO, e.getYRot(), e.getXRot(), TeleportTransition.PLAY_PORTAL_SOUND));
        if (moved == null) {
            return null;
        }
        moved.setPortalCooldown(100);
        for (Entity r : riders) {
            Entity mr = r.teleport(new TeleportTransition(target, at.add(0, 0.5, 0), Vec3.ZERO, r.getYRot(), r.getXRot(), TeleportTransition.DO_NOTHING));
            if (mr != null) {
                mr.setPortalCooldown(100);
                mr.startRiding(moved, true, true);
            }
        }
        return moved;
    }
}
