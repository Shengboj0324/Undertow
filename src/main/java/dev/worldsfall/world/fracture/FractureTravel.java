package dev.worldsfall.world.fracture;

import dev.worldsfall.registry.WFDimensions;
import dev.worldsfall.world.Progress;
import dev.worldsfall.world.sundering.SunderingData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Moves entities through the Fracture Rift (§13.2). A ridden Skate travels with its rider. */
public final class FractureTravel {
    private FractureTravel() {}

    public static void cross(ServerLevel from, Entity entity) {
        if (entity.isOnPortalCooldown()) {
            entity.setPortalCooldown(20);
            return;
        }
        ServerLevel target;
        Vec3 pos;
        if (from.dimension() == WFDimensions.THE_FRACTURE) {
            target = from.getServer().getLevel(Level.END);
            if (target == null) {
                return;
            }
            BlockPos ret = SunderingData.get(from.getServer()).returnPoint();
            pos = Vec3.atBottomCenterOf(ret);
        } else {
            target = from.getServer().getLevel(WFDimensions.THE_FRACTURE);
            if (target == null) {
                return;
            }
            FractureArena.ensureBuilt(target);
            pos = Vec3.atBottomCenterOf(FractureArena.ARRIVAL.above());
        }
        Entity rider = entity.getControllingPassenger();
        Entity moved = entity.teleport(new TeleportTransition(target, pos, Vec3.ZERO, entity.getYRot(), entity.getXRot(), TeleportTransition.PLAY_PORTAL_SOUND));
        if (moved != null) {
            moved.setPortalCooldown(100);
            if (moved instanceof ServerPlayer sp && target.dimension() == WFDimensions.THE_FRACTURE) {
                Progress.award(sp, "below_the_bottom");
            }
        }
        if (rider != null && moved != null && rider.level() != target) {
            Entity movedRider = rider.teleport(new TeleportTransition(target, pos.add(0, 1, 0), Vec3.ZERO, rider.getYRot(), rider.getXRot(),
                TeleportTransition.DO_NOTHING));
            if (movedRider != null) {
                movedRider.startRiding(moved, true, true);
            }
        }
    }
}
