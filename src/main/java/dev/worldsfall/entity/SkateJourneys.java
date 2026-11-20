package dev.worldsfall.entity;

import dev.worldsfall.world.Progress;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks "Across the Gap": ride a Skate from the main island ring (< 300 blocks) to the outer islands (§12.3). */
public final class SkateJourneys {
    private static final Map<UUID, UUID> STARTED_NEAR_CENTER = new ConcurrentHashMap<>();

    private SkateJourneys() {}

    public static void onMount(ServerPlayer player, VoidSkate skate) {
        if (skate.position().horizontalDistance() < 300) {
            STARTED_NEAR_CENTER.put(player.getUUID(), skate.getUUID());
        } else {
            STARTED_NEAR_CENTER.remove(player.getUUID());
        }
    }

    public static void onDismount(ServerPlayer player) {
        STARTED_NEAR_CENTER.remove(player.getUUID());
    }

    static void onRiddenFar(ServerPlayer player, VoidSkate skate) {
        if (skate.getUUID().equals(STARTED_NEAR_CENTER.remove(player.getUUID()))) {
            Progress.award(player, "across_the_gap");
        }
    }
}
