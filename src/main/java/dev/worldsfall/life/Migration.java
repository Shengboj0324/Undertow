package dev.worldsfall.life;

import dev.worldsfall.entity.VoidSkate;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

/**
 * Skate Migration (proposal "World events"): a distant chorus of Skate calls, then a flock of 15–25 Skates crosses the
 * sky. About a third are Dusk Skates, and a migrating Skate is the only Dusk Skate that can be tamed.
 */
final class Migration {
    static final double DISTANCE = 700, START_BEHIND = 90;

    private Migration() {}

    static boolean begin(ServerLevel end, WorldEventData data) {
        ServerPlayer p = WorldEvents.anyPlayer(end);
        if (p == null) {
            return false;
        }
        data.setSite(p.blockPosition());
        WorldEvents.playToAll(end, WFSounds.EVENT_MIGRATION_CALLS.get(), 1.0f, 1.0f);
        return true;
    }

    static void tick(ServerLevel end, WorldEventData data, long now) {
        long t = now - data.start();
        if (t == WorldEvents.MIGRATION_TELEGRAPH) {
            spawnFlock(end, data);
        }
        if (now >= data.end()) {
            end(end, data);
            WorldEvents.finish(end, data);
        }
    }

    static void spawnFlock(ServerLevel end, WorldEventData data) {
        RandomSource r = end.getRandom();
        Vec3 centre = Vec3.atCenterOf(data.site());
        double a = r.nextDouble() * Math.PI * 2;
        Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        double y = Mth.clamp(centre.y + 30, 90, 140);
        Vec3 start = centre.subtract(dir.scale(START_BEHIND)).add(side.scale(r.nextGaussian() * 20));
        int n = 15 + r.nextInt(11);
        for (int i = 0; i < n; i++) {
            VoidSkate s = WFEntities.VOID_SKATE.get().create(end, EntitySpawnReason.EVENT);
            if (s == null) {
                continue;
            }
            // a loose V: rows behind the leader, alternating sides
            int row = (i + 1) / 2;
            double off = (i % 2 == 0 ? 1 : -1) * row * 5;
            Vec3 at = start.subtract(dir.scale(row * 6)).add(side.scale(off)).add(0, r.nextGaussian() * 3, 0);
            s.snapTo(at.x, y + (at.y - start.y), at.z, (float) Math.toDegrees(Math.atan2(-dir.x, dir.z)), 0);
            s.setVariant(r.nextInt(3) == 0 ? 1 : 0);
            s.setMigrationTarget(new Vec3(at.x, y, at.z).add(dir.scale(DISTANCE)));
            end.addFreshEntity(s);
        }
    }

    /** Untamed migrants still around when the event ends fly on and are gone. */
    static void end(ServerLevel end, WorldEventData data) {
        for (VoidSkate s : end.getEntities(WFEntities.VOID_SKATE.get(), VoidSkate::isMigrating)) {
            s.discard();
        }
    }
}
