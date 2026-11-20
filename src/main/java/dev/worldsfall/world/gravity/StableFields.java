package dev.worldsfall.world.gravity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loaded Gravity Anchor positions per dimension and side (§9.8). Block entities register on load and
 * unregister on removal, so only loaded fields count — exactly the ones that can affect anything.
 */
public final class StableFields {
    private static final Map<Boolean, Map<ResourceKey<Level>, Map<BlockPos, Integer>>> FIELDS = new ConcurrentHashMap<>();

    private StableFields() {}

    private static Map<BlockPos, Integer> of(Level level) {
        return FIELDS.computeIfAbsent(level.isClientSide(), k -> new ConcurrentHashMap<>())
            .computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>());
    }

    public static void add(Level level, BlockPos pos, int radius) {
        of(level).put(pos.immutable(), radius);
    }

    public static void remove(Level level, BlockPos pos) {
        of(level).remove(pos);
    }

    public static Map<BlockPos, Integer> all(Level level) {
        return of(level);
    }

    public static boolean isInsideField(LevelReader reader, BlockPos pos) {
        if (!(reader instanceof Level level)) {
            return false;
        }
        return isInside(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public static boolean isInside(Level level, double x, double y, double z) {
        for (Map.Entry<BlockPos, Integer> e : of(level).entrySet()) {
            BlockPos c = e.getKey();
            double dx = x - (c.getX() + 0.5), dy = y - (c.getY() + 0.5), dz = z - (c.getZ() + 0.5);
            int r = e.getValue();
            if (dx * dx + dy * dy + dz * dz <= (double) r * r) {
                return true;
            }
        }
        return false;
    }

    /** True if the point is below a field (within its column, under its sphere) — for the slow-fall rescue. */
    public static boolean isBelowField(Level level, double x, double y, double z) {
        for (Map.Entry<BlockPos, Integer> e : of(level).entrySet()) {
            BlockPos c = e.getKey();
            double dx = x - (c.getX() + 0.5), dz = z - (c.getZ() + 0.5);
            int r = e.getValue();
            if (dx * dx + dz * dz <= (double) r * r && y < c.getY() - Math.sqrt(Math.max(0, r * r - dx * dx - dz * dz)) && y > c.getY() - r - 40) {
                return true;
            }
        }
        return false;
    }

    public static void clear(boolean client) {
        FIELDS.remove(client);
    }
}
