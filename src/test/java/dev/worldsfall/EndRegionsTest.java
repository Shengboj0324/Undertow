package dev.worldsfall;

import dev.worldsfall.world.gen.EndRegions;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Biome placement rules (§3.2, §4): distance rings, weights and determinism. */
class EndRegionsTest {
    @Test
    void noiseIsDeterministicAndBounded() {
        for (int i = 0; i < 2000; i++) {
            double x = i * 13.7, z = i * -7.3;
            double a = EndRegions.valueNoise(x, z, 5);
            assertEquals(a, EndRegions.valueNoise(x, z, 5));
            assertTrue(a >= -1 && a <= 1);
        }
    }

    @Test
    void spiresOnlyBeyondTwoThousand() {
        for (int x = 1100; x < 1990; x += 37) {
            for (int z = -900; z < 900; z += 41) {
                if (Math.hypot(x, z) >= 2000) {
                    continue;
                }
                assertNotEquals(EndRegions.Kind.SPIRES, EndRegions.classify(x, 64, z, 0.5));
            }
        }
    }

    @Test
    void undersideIsAThreeDimensionalBandUnderLargeIslands() {
        assertEquals(EndRegions.Kind.UNDERSIDE, EndRegions.classify(3000, 30, 3000, 0.6));
        assertNotEquals(EndRegions.Kind.UNDERSIDE, EndRegions.classify(3000, 70, 3000, 0.6));
        assertNotEquals(EndRegions.Kind.UNDERSIDE, EndRegions.classify(3000, 30, 3000, -0.5));
    }

    @Test
    void largeIslandAreaGetsRoughlyTheDesignedShares() {
        Map<EndRegions.Kind, Integer> counts = new EnumMap<>(EndRegions.Kind.class);
        int total = 0;
        for (int x = -20000; x < 20000; x += 97) {
            for (int z = -20000; z < 20000; z += 101) {
                if (Math.hypot(x, z) < 1100) {
                    continue;
                }
                counts.merge(EndRegions.classify(x, 64, z, 0.5), 1, Integer::sum);
                total++;
            }
        }
        double chorus = counts.getOrDefault(EndRegions.Kind.CHORUS_REACH, 0) / (double) total;
        double still = counts.getOrDefault(EndRegions.Kind.STILLWATER, 0) / (double) total;
        assertTrue(chorus > 0.15 && chorus < 0.45, "Chorus Reach should be common (~30%), was " + chorus);
        assertTrue(still > 0.03 && still < 0.2, "Stillwater should be ~10%, was " + still);
    }
}
