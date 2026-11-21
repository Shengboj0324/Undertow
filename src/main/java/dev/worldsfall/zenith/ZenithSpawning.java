package dev.worldsfall.zenith;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.registry.WFEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Who lives in the Zenith: Gale Harriers near gliding players high over the outer End, and Leviathan Drifters that keep the
 * sky from ever feeling empty, at most one within 600 blocks. Called once a second from {@link ZenithEvents}.
 */
public final class ZenithSpawning {
    public static final double ZENITH_Y = 140;
    public static final int HARRIER_CAP = 2, LEVIATHAN_RANGE = 600;
    /** Zenith creatures appear only beyond this distance from the main island. */
    public static final double OUTER = 1500;

    private ZenithSpawning() {}

    public static boolean enabled() {
        return WFConfig.get(WFConfig.SERVER.zenith, true);
    }

    static void tick(ServerLevel end) {
        if (!enabled()) {
            return;
        }
        RandomSource r = end.getRandom();
        long t = end.getGameTime();
        for (ServerPlayer p : end.players()) {
            if (p.isSpectator() || p.getX() * p.getX() + p.getZ() * p.getZ() < OUTER * OUTER) {
                continue;
            }
            if (t % 400 < 20 && p.getY() > ZENITH_Y && r.nextFloat() < 0.3f
                && end.getEntitiesOfClass(GaleHarrier.class, new AABB(p.blockPosition()).inflate(96)).size() < HARRIER_CAP) {
                double a = r.nextDouble() * Math.PI * 2;
                GaleHarrier h = WFEntities.GALE_HARRIER.get().create(end, EntitySpawnReason.NATURAL);
                if (h != null) {
                    h.snapTo(p.getX() + Math.cos(a) * 40, Mth.clamp(p.getY() + 20, 150, 250), p.getZ() + Math.sin(a) * 40, 0, 0);
                    end.addFreshEntity(h);
                }
            }
            if (t % 1200 < 20 && p.getY() > 60 && r.nextFloat() < 0.25f
                && end.getEntitiesOfClass(LeviathanDrifter.class, new AABB(p.blockPosition()).inflate(LEVIATHAN_RANGE, 256, LEVIATHAN_RANGE)).isEmpty()) {
                spawnLeviathan(end, p.position(), r);
            }
        }
    }

    /** A Leviathan 160–240 blocks away, on the nearest Sky Current when there is one. */
    public static LeviathanDrifter spawnLeviathan(ServerLevel end, Vec3 near, RandomSource r) {
        SkyCurrents.Hit hit = SkyCurrents.nearest(SkyCurrents.currentsNear(end.getSeed(), near.x, near.z), near);
        Vec3 at;
        if (hit != null && hit.distance() < 400) {
            at = hit.point().add(hit.dir().scale(-(160 + r.nextInt(80)))).add(0, -14, 0);
        } else {
            double a = r.nextDouble() * Math.PI * 2, d = 160 + r.nextInt(80);
            at = new Vec3(near.x + Math.cos(a) * d, 180 + r.nextInt(36), near.z + Math.sin(a) * d);
        }
        LeviathanDrifter l = WFEntities.LEVIATHAN_DRIFTER.get().create(end, EntitySpawnReason.NATURAL);
        if (l != null) {
            l.snapTo(at.x, at.y, at.z, r.nextFloat() * 360, 0);
            end.addFreshEntity(l);
        }
        return l;
    }
}
