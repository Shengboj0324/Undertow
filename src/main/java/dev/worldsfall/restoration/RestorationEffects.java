package dev.worldsfall.restoration;

import net.minecraft.server.MinecraftServer;

/**
 * Runtime effects of the Restoration stages; they change behaviour (random-tick spreading, tide timing, sky uniforms)
 * rather than regenerating chunks, so they also work in old worlds.
 */
public final class RestorationEffects {
    private RestorationEffects() {}

    static void onStage(MinecraftServer server, int stage) {
        RestorationWorld.onStage(server, stage);
    }

    /** Steady Water (stage 3): Low Tide lasts half again as long. */
    public static dev.worldsfall.world.tide.TideCycle steady(MinecraftServer server, dev.worldsfall.world.tide.TideCycle c) {
        if (stage(server) < 3) {
            return c;
        }
        return new dev.worldsfall.world.tide.TideCycle(c.lowY(), c.highY(), c.lowTicks() * 3 / 2, c.risingTicks(), c.highTicks(), c.fallingTicks());
    }

    /** One King Tide in this many cycles: 8, or 16 once the water is steady (stage 3). */
    public static int kingTideOneIn(MinecraftServer server) {
        return stage(server) >= 3 ? 16 : 8;
    }

    /** Skate Migrations come twice as often at stage 4. */
    public static long migrationPeriod(MinecraftServer server) {
        return stage(server) >= 4 ? dev.worldsfall.life.WorldEvents.MIGRATION_PERIOD / 2 : dev.worldsfall.life.WorldEvents.MIGRATION_PERIOD;
    }

    /** Current stage (0..5), or 0 when stage effects are turned off. */
    public static int stage(MinecraftServer server) {
        if (!dev.worldsfall.config.WFConfig.get(dev.worldsfall.config.WFConfig.SERVER.restorationStageEffects, true)) {
            return 0;
        }
        return RestorationData.get(server).stage();
    }
}
