package dev.worldsfall.world;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.item.EchoTabletItem;
import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.registry.WFBlocks;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * Grants the "Where Worlds Go to Fall" advancements (§12.3). Each advancement has a single {@code done}
 * criterion with an impossible trigger, completed from code at the right moment.
 */
public final class Progress {
    private Progress() {}

    public static void award(ServerPlayer player, String name) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath(Worldsfall.MODID, name));
        if (holder == null) {
            Worldsfall.LOGGER.warn("Unknown advancement {}", name);
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (!progress.isDone()) {
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(holder, criterion);
            }
        }
    }

    public static boolean has(ServerPlayer player, String name) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath(Worldsfall.MODID, name));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    public static void readTablet(ServerPlayer player, int index) {
        Set<Integer> read = new HashSet<>(player.getData(WFAttachments.TABLETS_READ));
        read.add(index);
        player.setData(WFAttachments.TABLETS_READ, read);
        if (read.size() >= 6) {
            award(player, "echoes_of_them");
        }
        if (read.size() >= EchoTabletItem.FRAGMENTS) {
            award(player, "the_whole_story");
        }
    }

    public static void onVaultClaim(ServerPlayer player, BlockState state) {
        if (state.is(WFBlocks.DRIFT_CACHE.get())) {
            award(player, "the_tide_remembers");
        }
    }
}
