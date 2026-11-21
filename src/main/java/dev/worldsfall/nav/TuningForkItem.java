package dev.worldsfall.nav;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Tuning Fork: attunes an End Gateway into the Gateway Lattice (see {@link Lattice}). */
public class TuningForkItem extends Item {
    public TuningForkItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return Lattice.useFork(context);
    }
}
