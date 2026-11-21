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

    /** Current stage (0..5), or 0 when stage effects are turned off. */
    public static int stage(MinecraftServer server) {
        if (!dev.worldsfall.config.WFConfig.get(dev.worldsfall.config.WFConfig.SERVER.restorationStageEffects, true)) {
            return 0;
        }
        return RestorationData.get(server).stage();
    }
}
