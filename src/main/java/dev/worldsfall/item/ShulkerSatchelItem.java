package dev.worldsfall.item;

import dev.worldsfall.menu.SatchelMenu;
import dev.worldsfall.registry.WFComponents;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Shulker Satchel (§9.5): a 9-slot (upgradeable to 18) inventory carried as an item, opened into a grid. It
 * never uses the chest slot, so it never competes with an elytra. Cannot hold Shulker Boxes or Satchels.
 */
public class ShulkerSatchelItem extends Item {
    public ShulkerSatchelItem(Properties properties) {
        super(properties);
    }

    public static int size(ItemStack stack) {
        Integer size = stack.get(WFComponents.SATCHEL_SIZE.get());
        return size == null ? 9 : size;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            int slot = hand == InteractionHand.MAIN_HAND ? sp.getInventory().getSelectedSlot() : SatchelMenu.OFFHAND_SLOT;
            int rows = size(stack) / 9;
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new SatchelMenu(id, inv, stack, slot, rows),
                Component.translatable("container.worldsfall.shulker_satchel")), buf -> {
                    buf.writeVarInt(rows);
                });
            level.playSound(null, sp.blockPosition(), WFSounds.ITEM_SATCHEL_OPEN.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }
}
