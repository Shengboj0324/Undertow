package dev.worldsfall.life;

import net.minecraft.server.MinecraftServer;

/** Placeholder until world events land (1.5). */
public final class WorldEvents {
    public static final int NONE = 0, STARFALL = 1, KING_TIDE = 2, ECLIPSE = 3, MIGRATION = 4;

    private WorldEvents() {}

    public static int current(MinecraftServer server) {
        return 0;
    }

    public static long currentStart(MinecraftServer server) {
        return 0;
    }
}
