package dev.worldsfall.world.tide;

/**
 * Pure, deterministic Void Tide math (§5.1). Height is a function of an effective world time only, so the
 * tide survives restarts and is identical on server and clients. No Minecraft classes: unit-testable.
 */
public record TideCycle(double lowY, double highY, int lowTicks, int risingTicks, int highTicks, int fallingTicks) {
    public static final TideCycle DEFAULT = new TideCycle(0, 36, 10 * 1200, 3 * 1200, 10 * 1200, 3 * 1200);
    /** Two-minute warning window before Rising (§5.1 telegraphing). */
    public static final int WARNING_TICKS = 2 * 1200;
    /** High tide level once the Heart of Stillness sits in the Still Throne (§11.4). */
    public static final double CALMED_HIGH_Y = 20;

    public enum Phase {
        LOW("low"), RISING("rising"), HIGH("high"), FALLING("falling");
        public final String id;
        Phase(String id) { this.id = id; }
        public static Phase byId(String id) {
            for (Phase p : values()) {
                if (p.id.equals(id)) {
                    return p;
                }
            }
            throw new IllegalArgumentException("Unknown tide phase: " + id);
        }
    }

    public TideCycle {
        if (lowTicks <= 0 || risingTicks <= 0 || highTicks <= 0 || fallingTicks <= 0) {
            throw new IllegalArgumentException("Tide phase durations must be positive");
        }
        if (highY < lowY) {
            throw new IllegalArgumentException("highY must be >= lowY");
        }
    }

    public TideCycle calmed() {
        return new TideCycle(lowY, Math.min(highY, CALMED_HIGH_Y), lowTicks, risingTicks, highTicks, fallingTicks);
    }

    public long period() {
        return (long) lowTicks + risingTicks + highTicks + fallingTicks;
    }

    private long position(long time) {
        return Math.floorMod(time, period());
    }

    public Phase phase(long time) {
        long t = position(time);
        if (t < lowTicks) {
            return Phase.LOW;
        }
        t -= lowTicks;
        if (t < risingTicks) {
            return Phase.RISING;
        }
        t -= risingTicks;
        return t < highTicks ? Phase.HIGH : Phase.FALLING;
    }

    /** Ticks since the start of the current phase. */
    public long phaseElapsed(long time) {
        long t = position(time);
        long start = phaseStart(phase(time));
        return t - start;
    }

    public long phaseStart(Phase phase) {
        return switch (phase) {
            case LOW -> 0;
            case RISING -> lowTicks;
            case HIGH -> (long) lowTicks + risingTicks;
            case FALLING -> (long) lowTicks + risingTicks + highTicks;
        };
    }

    public long phaseLength(Phase phase) {
        return switch (phase) {
            case LOW -> lowTicks;
            case RISING -> risingTicks;
            case HIGH -> highTicks;
            case FALLING -> fallingTicks;
        };
    }

    public long ticksUntilNextPhase(long time) {
        return phaseLength(phase(time)) - phaseElapsed(time);
    }

    /** Smoothstep-eased tide height at a (fractional) time. */
    public double height(double time) {
        long whole = (long) Math.floor(time);
        double frac = time - whole;
        Phase phase = phase(whole);
        double elapsed = phaseElapsed(whole) + frac;
        return switch (phase) {
            case LOW -> lowY;
            case HIGH -> highY;
            case RISING -> lowY + (highY - lowY) * ease(elapsed / risingTicks);
            case FALLING -> highY - (highY - lowY) * ease(elapsed / fallingTicks);
        };
    }

    /** True during the warning window before Rising starts. */
    public boolean isStirring(long time) {
        return phase(time) == Phase.LOW && ticksUntilNextPhase(time) <= WARNING_TICKS;
    }

    /** 0..1 progress from low to high. */
    public double normalized(double time) {
        return highY == lowY ? 0 : (height(time) - lowY) / (highY - lowY);
    }

    public int redstoneSignal(long time) {
        return (int) Math.round(Math.max(0, Math.min(1, normalized(time))) * 15);
    }

    static double ease(double x) {
        double c = Math.max(0, Math.min(1, x));
        return c * c * (3 - 2 * c);
    }
}
