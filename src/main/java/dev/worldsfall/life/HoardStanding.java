package dev.worldsfall.life;

import dev.worldsfall.registry.WFAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hoarder reputation (proposal "Hoarder Couriers and reputation"): fair barters raise a player's standing with Hoarders,
 * theft lowers it a lot. Standing improves the barter table (luck) and, from {@link #COURIER}, unlocks Courier Tokens.
 */
public final class HoardStanding {
    public static final int MIN = -100, MAX = 100, PER_BARTER = 2, THEFT = -25, COURIER = 30;

    private HoardStanding() {}

    public static int get(ServerPlayer player) {
        return player.getData(WFAttachments.HOARD_STANDING);
    }

    public static int add(ServerPlayer player, int delta) {
        int before = get(player);
        int after = Math.max(MIN, Math.min(MAX, before + delta));
        player.setData(WFAttachments.HOARD_STANDING, after);
        if (before < COURIER && after >= COURIER) {
            player.sendSystemMessage(Component.translatable("message.worldsfall.hoard.trusted"));
        } else if (before >= 0 && after < 0) {
            player.sendSystemMessage(Component.translatable("message.worldsfall.hoard.distrusted"));
        }
        return after;
    }

    /** Barter luck from standing: up to +4 at full standing, down to −2 when distrusted. */
    public static float luck(ServerPlayer player) {
        int s = get(player);
        return s >= 0 ? s / 25.0f : s / 50.0f;
    }
}
