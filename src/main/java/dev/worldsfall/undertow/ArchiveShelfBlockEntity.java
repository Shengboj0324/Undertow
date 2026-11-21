package dev.worldsfall.undertow;

import dev.worldsfall.registry.WFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The six slots of an Archive Shelf and which one it reads next. */
public class ArchiveShelfBlockEntity extends BlockEntity {
    public static final int SIZE = 6;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int next;

    public ArchiveShelfBlockEntity(BlockPos pos, BlockState state) {
        super(WFBlockEntities.ARCHIVE_SHELF.get(), pos, state);
    }

    public int count() {
        return (int) items.stream().filter(s -> !s.isEmpty()).count();
    }

    public boolean insert(ItemStack one) {
        for (int i = 0; i < SIZE; i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, one);
                setChanged();
                return true;
            }
        }
        return false;
    }

    public ItemStack takeLast() {
        for (int i = SIZE - 1; i >= 0; i--) {
            if (!items.get(i).isEmpty()) {
                ItemStack s = items.get(i);
                items.set(i, ItemStack.EMPTY);
                setChanged();
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    /** The next stored record to read aloud, cycling through the shelf. */
    public ItemStack nextToRead() {
        for (int k = 0; k < SIZE; k++) {
            int i = (next + k) % SIZE;
            if (!items.get(i).isEmpty()) {
                next = (i + 1) % SIZE;
                setChanged();
                return items.get(i);
            }
        }
        return ItemStack.EMPTY;
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        ContainerHelper.loadAllItems(in, items);
        next = in.getIntOr("next", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putInt("next", next);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            for (ItemStack s : items) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
            }
        }
    }
}
