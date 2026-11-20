package dev.worldsfall.world.tide;

import dev.worldsfall.config.WFConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Side-agnostic accessors for the Void Tide (§5.1). The tide only exists in the End. */
public final class Tides {
    /** Client copy of the server's tide state; replaced by {@link dev.worldsfall.network.TideSyncPayload}. */
    private static volatile TideState clientState = TideState.INITIAL;
    private static volatile TideCycle clientCycle = TideCycle.DEFAULT;
    private static volatile boolean clientEnabled = true;

    private Tides() {}

    public static boolean appliesTo(Level level) {
        return level.dimension() == Level.END;
    }

    public static boolean enabled(Level level) {
        if (level.isClientSide()) {
            return clientEnabled;
        }
        return WFConfig.get(WFConfig.SERVER.tidesEnabled, true);
    }

    public static TideState state(Level level) {
        if (level instanceof ServerLevel server) {
            return TideData.get(server.getServer()).state();
        }
        return clientState;
    }

    public static TideCycle cycle(Level level) {
        TideCycle base = level.isClientSide() ? clientCycle : WFConfig.SERVER.tideCycle();
        return state(level).calmed() ? base.calmed() : base;
    }

    public static long effectiveTime(Level level) {
        return state(level).effectiveTime(level.getGameTime());
    }

    /** Current tide height. When tides are disabled the plane stays at Low. */
    public static double height(Level level, float partialTick) {
        TideCycle cycle = cycle(level);
        if (!enabled(level)) {
            return cycle.lowY();
        }
        TideState state = state(level);
        double time = state.paused() ? state.pausedAt() : level.getGameTime() + state.offset() + partialTick;
        return cycle.height(time);
    }

    public static double height(Level level) {
        return height(level, 0);
    }

    public static TideCycle.Phase phase(Level level) {
        return enabled(level) ? cycle(level).phase(effectiveTime(level)) : TideCycle.Phase.LOW;
    }

    public static boolean isStirring(Level level) {
        return enabled(level) && cycle(level).isStirring(effectiveTime(level));
    }

    public static int redstoneSignal(Level level) {
        return appliesTo(level) && enabled(level) ? cycle(level).redstoneSignal(effectiveTime(level)) : 0;
    }

    public static boolean isBelowTide(Level level, double y) {
        return appliesTo(level) && y < height(level);
    }

    public static void acceptClientSync(TideState state, TideCycle cycle, boolean enabled) {
        clientState = state;
        clientCycle = cycle;
        clientEnabled = enabled;
    }
}
