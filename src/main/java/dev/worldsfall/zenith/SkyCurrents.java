package dev.worldsfall.zenith;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Sky Currents and Downdrafts of the Zenith (proposal "A fifth layer above"). Both are pure functions of the world seed
 * and a 1,536-block region grid over the outer End (region centres beyond 2,600 blocks), so the server never stores them; nearby ones are sent to clients
 * ({@link dev.worldsfall.network.ZenithPayload}), which apply the push to gliders they control.
 *
 * <p>A current is a gentle polyline at a fixed altitude (Y 165–225) that carries gliders along at 25 blocks a second;
 * some run one way only. A downdraft is a column of falling dust (Y 140–260) that pushes gliders down hard.
 */
public final class SkyCurrents {
    public static final int REGION = 1536;
    /** Regions whose centre is closer than this to the main island have no currents or downdrafts. */
    public static final double MIN_DIST = 2600;
    /** 25 blocks per second. */
    public static final double SPEED = 1.25;
    public static final double CAPTURE = 5.0;
    public static final double DRAFT_MIN_Y = 140, DRAFT_MAX_Y = 260;

    public record Current(long id, List<Vec3> points, boolean oneWay) {}

    public record Downdraft(double x, double z, double radius) {}

    /** The nearest point of a current to a position, its segment direction and distance. */
    public record Hit(Current current, Vec3 point, Vec3 dir, double distance) {}

    private SkyCurrents() {}

    private static RandomSource rng(long seed, int rx, int rz, long salt) {
        return RandomSource.create(seed ^ (rx * 0x1F3D5B79L) ^ (rz * 0x6C8E9CF5L) ^ salt);
    }

    private static boolean outside(int rx, int rz) {
        double cx = (rx + 0.5) * REGION, cz = (rz + 0.5) * REGION;
        return cx * cx + cz * cz >= MIN_DIST * MIN_DIST;
    }

    public static @Nullable Current currentOf(long seed, int rx, int rz) {
        if (!outside(rx, rz)) {
            return null;
        }
        RandomSource r = rng(seed, rx, rz, 0x5C0FFEEL);
        if (r.nextFloat() > 0.7f) {
            return null;
        }
        double x = rx * REGION + 200 + r.nextDouble() * (REGION - 400), z = rz * REGION + 200 + r.nextDouble() * (REGION - 400);
        double y = 165 + r.nextInt(61);
        double heading = r.nextDouble() * Math.PI * 2;
        double length = 1200 + r.nextDouble() * 1200;
        int segments = 6;
        List<Vec3> pts = new ArrayList<>();
        double sx = x - Math.cos(heading) * length / 2, sz = z - Math.sin(heading) * length / 2;
        double wiggle = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double bend = Math.sin(t * Math.PI * 2 + wiggle) * 60;
            double px = sx + Math.cos(heading) * length * t - Math.sin(heading) * bend;
            double pz = sz + Math.sin(heading) * length * t + Math.cos(heading) * bend;
            pts.add(new Vec3(px, y + Math.sin(t * Math.PI * 3 + wiggle) * 6, pz));
        }
        return new Current(((long) rx << 32) ^ (rz & 0xFFFFFFFFL), List.copyOf(pts), r.nextFloat() < 0.4f);
    }

    public static List<Downdraft> draftsOf(long seed, int rx, int rz) {
        List<Downdraft> out = new ArrayList<>();
        if (!outside(rx, rz)) {
            return out;
        }
        RandomSource r = rng(seed, rx, rz, 0xD0D0AL);
        int n = 2 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            out.add(new Downdraft(rx * REGION + r.nextDouble() * REGION, rz * REGION + r.nextDouble() * REGION, 5 + r.nextInt(4)));
        }
        return out;
    }

    /** Currents whose region is within one region of {@code (x, z)}. */
    public static List<Current> currentsNear(long seed, double x, double z) {
        List<Current> out = new ArrayList<>();
        int rx = Mth.floor(x / REGION), rz = Mth.floor(z / REGION);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Current c = currentOf(seed, rx + dx, rz + dz);
                if (c != null) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    public static List<Downdraft> draftsNear(long seed, double x, double z) {
        List<Downdraft> out = new ArrayList<>();
        int rx = Mth.floor(x / REGION), rz = Mth.floor(z / REGION);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out.addAll(draftsOf(seed, rx + dx, rz + dz));
            }
        }
        return out;
    }

    /** The nearest point on any of {@code currents} to {@code p}, or null if there are none. */
    public static @Nullable Hit nearest(List<Current> currents, Vec3 p) {
        Hit best = null;
        for (Current c : currents) {
            List<Vec3> pts = c.points();
            for (int i = 0; i + 1 < pts.size(); i++) {
                Vec3 a = pts.get(i), b = pts.get(i + 1);
                Vec3 ab = b.subtract(a);
                double t = Mth.clamp(p.subtract(a).dot(ab) / ab.lengthSqr(), 0, 1);
                Vec3 q = a.add(ab.scale(t));
                double d = q.distanceTo(p);
                if (best == null || d < best.distance()) {
                    best = new Hit(c, q, ab.normalize(), d);
                }
            }
        }
        return best;
    }

    /**
     * The velocity a glider inside a current is carried at: along the current at {@link #SPEED}, following its heading on
     * two-way currents; drawn gently back toward the current's line. Null when {@code p} is not inside one.
     */
    public static @Nullable Vec3 carry(List<Current> currents, Vec3 p, Vec3 velocity) {
        Hit hit = nearest(currents, p);
        if (hit == null || hit.distance() > CAPTURE) {
            return null;
        }
        Vec3 dir = hit.dir();
        if (!hit.current().oneWay() && velocity.dot(dir) < 0) {
            dir = dir.scale(-1);
        }
        Vec3 toLine = hit.point().subtract(p).scale(0.08);
        return dir.scale(SPEED).add(toLine);
    }

    public static boolean inDraft(List<Downdraft> drafts, Vec3 p) {
        if (p.y < DRAFT_MIN_Y || p.y > DRAFT_MAX_Y) {
            return false;
        }
        for (Downdraft d : drafts) {
            double dx = p.x - d.x(), dz = p.z - d.z();
            if (dx * dx + dz * dz <= d.radius() * d.radius()) {
                return true;
            }
        }
        return false;
    }
}
