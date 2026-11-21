package dev.worldsfall.undertow;

import dev.worldsfall.network.CodexPayload;
import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.registry.WFComponents;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.restoration.Restoration;
import dev.worldsfall.world.Progress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

/**
 * Archive Codex (proposal "What is down there"): the Ascendancy's tide records, six in all. Reading one shows its record
 * in glyph text; recovering each counts toward the Restoration; reading all six earns "The Whole Story, Truly" and marks the
 * Overmoon on your Star Charts.
 */
public class ArchiveCodexItem extends Item {
    public static final int COUNT = 6;

    public ArchiveCodexItem(Properties properties) {
        super(properties);
    }

    public static int index(ItemStack stack) {
        Integer i = stack.get(WFComponents.CODEX_INDEX.get());
        return i == null ? 1 : i;
    }

    public static ItemStack of(int index) {
        ItemStack s = new ItemStack(WFItems.ARCHIVE_CODEX.get());
        s.set(WFComponents.CODEX_INDEX.get(), Math.max(1, Math.min(COUNT, index)));
        return s;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.worldsfall.archive_codex.numbered", index(stack));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            read(sp, index(stack));
        }
        player.getCooldowns().addCooldown(stack, 60);
        return InteractionResult.SUCCESS;
    }

    /** Shows a Codex's record and counts it as read (also used by the Archive Shelf). */
    public static void read(ServerPlayer sp, int index) {
        WFNetwork.send(sp, new CodexPayload(index));
        sp.level().playSound(null, sp.blockPosition(), WFSounds.BLOCK_ARCHIVE_SHELF_READ.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        Set<Integer> read = new HashSet<>(sp.getData(WFAttachments.CODICES_READ));
        if (read.add(index)) {
            sp.setData(WFAttachments.CODICES_READ, read);
            Restoration.award(sp.level().getServer(), sp, Restoration.Act.ARCHIVE_CODEX, "codex" + index);
            Progress.award(sp, "first_codex");
            if (read.size() >= COUNT) {
                Progress.award(sp, "the_whole_story_truly");
            }
        }
    }

    public static boolean readAll(ServerPlayer sp) {
        return sp.getData(WFAttachments.CODICES_READ).size() >= COUNT;
    }
}
