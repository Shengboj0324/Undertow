package dev.worldsfall.menu;

import dev.worldsfall.registry.WFMenus;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.registry.WFTags;
import dev.worldsfall.world.Progress;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** Satchel grid menu: 1 or 2 rows, rejects shulker boxes and satchels (§9.5), writes back to the item on change. */
public class SatchelMenu extends AbstractContainerMenu {
    public static final int OFFHAND_SLOT = -1;
    private final Container container;
    private final ItemStack satchel;
    private final int satchelSlot;
    private final int rows;
    private final Player player;

    /** Client constructor. */
    public SatchelMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, ItemStack.EMPTY, Integer.MIN_VALUE, buf.readVarInt());
    }

    public SatchelMenu(int id, Inventory inv, ItemStack satchel, int satchelSlot, int rows) {
        super(WFMenus.SATCHEL.get(), id);
        this.satchel = satchel;
        this.satchelSlot = satchelSlot;
        this.rows = rows;
        this.player = inv.player;
        this.container = new SimpleContainer(rows * 9) {
            @Override
            public void setChanged() {
                super.setChanged();
                save();
            }

            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return allowed(stack);
            }
        };
        if (!satchel.isEmpty()) {
            ItemContainerContents contents = satchel.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            NonNullList<ItemStack> list = NonNullList.withSize(rows * 9, ItemStack.EMPTY);
            contents.copyInto(list);
            for (int i = 0; i < list.size(); i++) {
                container.setItem(i, list.get(i));
            }
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(container, c + r * 9, 8 + c * 18, 18 + r * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return allowed(stack);
                    }
                });
            }
        }
        int invTop = 18 + rows * 18 + 13;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, invTop + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            final int index = c;
            addSlot(new Slot(inv, c, 8 + c * 18, invTop + 58) {
                @Override
                public boolean mayPickup(Player p) {
                    return index != satchelSlot;
                }
            });
        }
    }

    public int rows() {
        return rows;
    }

    public static boolean allowed(ItemStack stack) {
        return !stack.is(WFTags.Items.SATCHEL_FORBIDDEN);
    }

    private void save() {
        if (satchel.isEmpty()) {
            return;
        }
        NonNullList<ItemStack> list = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        boolean full = true;
        for (int i = 0; i < list.size(); i++) {
            list.set(i, container.getItem(i).copy());
            full &= !container.getItem(i).isEmpty();
        }
        satchel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(list));
        if (full && player instanceof ServerPlayer sp) {
            Progress.award(sp, "carry_the_world");
        }
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput input, Player p) {
        if (slotIndex >= 0 && slotIndex < slots.size() && input == ContainerInput.SWAP && button == satchelSlot) {
            return;
        }
        super.clicked(slotIndex, button, input, p);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int size = rows * 9;
        if (index < size) {
            if (!moveItemStackTo(stack, size, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!allowed(stack) || !moveItemStackTo(stack, 0, size, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        if (satchelSlot == Integer.MIN_VALUE) {
            return true;
        }
        ItemStack current = satchelSlot == OFFHAND_SLOT ? p.getOffhandItem() : p.getInventory().getItem(satchelSlot);
        return current == satchel && !satchel.isEmpty();
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (!p.level().isClientSide()) {
            save();
            p.level().playSound(null, p.blockPosition(), WFSounds.ITEM_SATCHEL_CLOSE.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        }
    }
}
