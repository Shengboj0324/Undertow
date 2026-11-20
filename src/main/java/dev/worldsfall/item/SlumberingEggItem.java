package dev.worldsfall.item;

import net.minecraft.world.item.Item;

/** A Wyrm curled up after reaching zero health (§10.4). Place it on an Egg Cradle to restore it. */
public class SlumberingEggItem extends Item {
    public SlumberingEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(net.minecraft.world.item.ItemStack stack) {
        return true;
    }
}
