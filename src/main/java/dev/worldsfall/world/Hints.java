package dev.worldsfall.world;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.registry.WFAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/** One-line onboarding hints shown once per player per world (§17.1); can be disabled. */
public final class Hints {
    private Hints() {}

    public static void show(ServerPlayer player, String id) {
        if (!WFConfig.get(WFConfig.SERVER.hintToasts, true)) {
            return;
        }
        Set<String> shown = new HashSet<>(player.getData(WFAttachments.HINTS_SHOWN));
        if (shown.add(id)) {
            player.setData(WFAttachments.HINTS_SHOWN, shown);
            player.sendOverlayMessage(Component.translatable("hint.worldsfall." + id));
        }
    }
}
