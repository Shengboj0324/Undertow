package dev.worldsfall.world.gen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * Pure biome-choice rules for the outer End (§3.2 distance rings, §4 placement weights). Uses vanilla island
 * density {@code h} plus a smooth, deterministic 2D value noise for regions, so every island gets one biome.
 */
public final class EndRegions {
    private EndRegions() {}

    public enum Kind { VANILLA_HIGHLANDS, VANILLA_MIDLANDS, VANILLA_ISLANDS, VANILLA_BARRENS, CHORUS_REACH, STILLWATER, SPIRES, HOARD, UNDERSIDE, DRIFT }

    static Holder<Biome> pick(WFEndBiomeSource s, int x, int y, int z, double h) {
        return switch (classify(x, y, z, h)) {
            case VANILLA_HIGHLANDS -> s.highlands();
            case VANILLA_MIDLANDS -> s.midlands();
            case VANILLA_ISLANDS -> s.islands();
            case VANILLA_BARRENS -> s.barrens();
            case CHORUS_REACH -> s.chorusReach();
            case STILLWATER -> s.stillwater();
            case SPIRES -> s.spires();
            case HOARD -> s.hoard();
            case UNDERSIDE -> s.underside();
            case DRIFT -> s.drift();
        };
    }

    /** Biome classification for an outer-End position (distance > 1,024). */
    public static Kind classify(int x, int y, int z, double h) {
        double dist = Math.sqrt((double) x * x + (double) z * z);
        double n1 = valueNoise(x / 420.0, z / 420.0, 17);
        double n2 = valueNoise(x / 700.0, z / 700.0, 91);
        if (h > 0.25) {
            if (y >= WFEndBiomeSource.UNDERSIDE_MIN_Y && y <= WFEndBiomeSource.UNDERSIDE_MAX_Y) {
                return Kind.UNDERSIDE;
            }
            if (dist >= 2000 && n2 > (dist >= 3000 ? 0.05 : 0.35)) {
                return Kind.SPIRES;
            }
            if (n1 < -0.12) {
                return Kind.CHORUS_REACH;
            }
            if (n1 > 0.42 || (dist < 2500 && n1 > 0.55)) {
                return Kind.STILLWATER;
            }
            return Kind.VANILLA_HIGHLANDS;
        }
        if (h >= -0.0625) {
            if (n1 < -0.18) {
                return Kind.CHORUS_REACH;
            }
            if (dist >= 2500 && n2 > 0.3) {
                return Kind.SPIRES;
            }
            return Kind.VANILLA_MIDLANDS;
        }
        if (h < -0.21875) {
            double n3 = valueNoise(x / 160.0, z / 160.0, 233);
            if (n3 > 0.62) {
                return Kind.HOARD;
            }
            return h < -0.42 ? Kind.DRIFT : Kind.VANILLA_ISLANDS;
        }
        return Kind.VANILLA_BARRENS;
    }

    /** Smooth value noise in [-1, 1] (cubic-interpolated lattice hash). */
    public static double valueNoise(double x, double z, int salt) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        double u = fx * fx * (3 - 2 * fx), v = fz * fz * (3 - 2 * fz);
        double a = hash(x0, z0, salt), b = hash(x0 + 1, z0, salt), c = hash(x0, z0 + 1, salt), d = hash(x0 + 1, z0 + 1, salt);
        return lerp(v, lerp(u, a, b), lerp(u, c, d));
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }

    static double hash(int x, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= (h >>> 33);
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }
}
