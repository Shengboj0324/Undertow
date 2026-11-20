package dev.worldsfall.item;

import dev.worldsfall.network.EchoTabletPayload;
import dev.worldsfall.registry.WFComponents;
import dev.worldsfall.registry.WFSounds;
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
import net.neoforged.neoforge.network.PacketDistributor;

/** Echo Tablet (§2.3): right-click to read one line, shown as glyphs resolving into text. Twelve unique fragments. */
public class EchoTabletItem extends Item {
    public static final int FRAGMENTS = 12;

    public EchoTabletItem(Properties properties) {
        super(properties);
    }

    public static int index(ItemStack stack) {
        Integer i = stack.get(WFComponents.TABLET_INDEX.get());
        return i == null ? 1 : i;
    }

    public static ItemStack of(int index) {
        ItemStack stack = new ItemStack(dev.worldsfall.registry.WFItems.ECHO_TABLET.get());
        stack.set(WFComponents.TABLET_INDEX.get(), Math.max(1, Math.min(FRAGMENTS, index)));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.worldsfall.echo_tablet.numbered", index(stack));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            int idx = index(stack);
            PacketDistributor.sendToPlayer(sp, new EchoTabletPayload(idx));
            level.playSound(null, sp.blockPosition(), WFSounds.ITEM_ECHO_TABLET_READ.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
            Progress.readTablet(sp, idx);
        }
        player.getCooldowns().addCooldown(stack, 40);
        return InteractionResult.SUCCESS;
    }
}
