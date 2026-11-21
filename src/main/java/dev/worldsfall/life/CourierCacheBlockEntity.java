package dev.worldsfall.life;

import dev.worldsfall.registry.WFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A Courier Cache's nine slots: what a Courier picks up from the first cache and leaves in the second. */
public class CourierCacheBlockEntity extends BaseContainerBlockEntity {
    public static final int SIZE = 9;
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public CourierCacheBlockEntity(BlockPos pos, BlockState state) {
        super(WFBlockEntities.COURIER_CACHE.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.worldsfall.courier_cache");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new ChestMenu(MenuType.GENERIC_9x1, containerId, inventory, this, 1);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(in, items);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        net.minecraft.world.ContainerHelper.saveAllItems(out, items);
    }

    /** Takes the first non-empty stack, or empty. */
    public ItemStack takeFirst() {
        for (int i = 0; i < SIZE; i++) {
            if (!items.get(i).isEmpty()) {
                ItemStack s = items.get(i);
                items.set(i, ItemStack.EMPTY);
                setChanged();
                return s;
            }
        }
        return ItemStack.EMPTY;
    }
}
