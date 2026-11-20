package dev.worldsfall;

import dev.worldsfall.world.tide.TideCycle;
import dev.worldsfall.world.tide.TideState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure tide math (§5.1): deterministic from world time, survives restarts, configurable. */
class TideCycleTest {
    private final TideCycle c = TideCycle.DEFAULT;

    @Test
    void phasesFollowTheDefaultSchedule() {
        assertEquals(26 * 1200, c.period());
        assertEquals(TideCycle.Phase.LOW, c.phase(0));
        assertEquals(TideCycle.Phase.LOW, c.phase(10 * 1200 - 1));
        assertEquals(TideCycle.Phase.RISING, c.phase(10 * 1200));
        assertEquals(TideCycle.Phase.HIGH, c.phase(13 * 1200));
        assertEquals(TideCycle.Phase.FALLING, c.phase(23 * 1200));
        assertEquals(TideCycle.Phase.LOW, c.phase(26 * 1200));
    }

    @Test
    void heightIsContinuousAndEased() {
        double prev = c.height(0);
        for (int t = 1; t < c.period(); t++) {
            double h = c.height(t);
            assertTrue(Math.abs(h - prev) < 0.05, "tide must move smoothly at t=" + t);
            prev = h;
        }
        assertEquals(0, c.height(5), 1e-9);
        assertEquals(36, c.height(15 * 1200), 1e-9);
        assertEquals(18, c.height(10 * 1200 + 1800), 1e-6, "smoothstep midpoint");
    }

    @Test
    void negativeTimesWrap() {
        assertEquals(c.phase(c.period() - 5), c.phase(-5));
    }

    @Test
    void warningWindowIsTwoMinutesBeforeRising() {
        assertFalse(c.isStirring(8 * 1200 - 1));
        assertTrue(c.isStirring(8 * 1200));
        assertTrue(c.isStirring(10 * 1200 - 1));
        assertFalse(c.isStirring(10 * 1200));
    }

    @Test
    void redstoneSignalSpansZeroToFifteen() {
        assertEquals(0, c.redstoneSignal(0));
        assertEquals(15, c.redstoneSignal(14 * 1200));
        int mid = c.redstoneSignal(10 * 1200 + 1800);
        assertTrue(mid >= 7 && mid <= 8);
    }

    @Test
    void calmingLowersHighTide() {
        TideCycle calm = c.calmed();
        assertEquals(20, calm.highY());
        assertEquals(20, calm.height(15 * 1200), 1e-9);
    }

    @Test
    void invalidConfigurationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TideCycle(0, 36, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new TideCycle(40, 36, 1, 1, 1, 1));
    }

    @Test
    void stateOffsetsPausesAndJumps() {
        TideState s = TideState.INITIAL;
        assertEquals(1000, s.effectiveTime(1000));
        TideState paused = s.pause(500);
        assertTrue(paused.paused());
        assertEquals(500, paused.effectiveTime(99999));
        TideState resumed = paused.resume(700);
        assertEquals(500, resumed.effectiveTime(700), "resuming continues where it paused");
        TideState jumped = s.jumpTo(100, 12000);
        assertEquals(12000, jumped.effectiveTime(100));
        assertEquals(TideCycle.Phase.RISING, c.phase(jumped.effectiveTime(100)));
    }
}
