package dev.worldsfall.life;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.world.tide.TideCycle;
import net.minecraft.server.MinecraftServer;

/**
 * King Tide (proposal "World events"): about one tide cycle in eight, High Tide climbs to Y 60 and floods the undersides of
 * smaller islands. It is a pure function of the world seed and the cycle number, so server and saves always agree; the
 * raised High is applied to the cycle the server uses and sends to clients.
 */
public final class KingTide {
    public static final double HIGH_Y = 60;
    public static final int ONE_IN = 8;

    private KingTide() {}

    public static long cycleIndex(TideCycle cycle, long time) {
        return Math.floorDiv(time, cycle.period());
    }

    public static boolean isKing(long seed, long index) {
        return isKing(seed, index, ONE_IN);
    }

    public static boolean isKing(long seed, long index, int oneIn) {
        if (index <= 0) {
            return false;
        }
        long h = (seed ^ 0x6B696E67L) + index * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return Math.floorMod(h, oneIn) == 0;
    }

    public static boolean enabled() {
        return WFConfig.get(WFConfig.SERVER.eventKingTide, true);
    }

    /** The cycle with the King Tide's High applied when the cycle at {@code time} is a King cycle (never once the tide is calmed). */
    public static TideCycle adjust(MinecraftServer server, TideCycle base, long time, boolean calmed) {
        if (calmed || !enabled() || !isKing(server.overworld().getSeed(), cycleIndex(base, time),
            dev.worldsfall.restoration.RestorationEffects.kingTideOneIn(server))) {
            return base;
        }
        return new TideCycle(base.lowY(), Math.max(base.highY(), HIGH_Y), base.lowTicks(), base.risingTicks(), base.highTicks(), base.fallingTicks());
    }
}
