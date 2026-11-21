package dev.worldsfall.nav;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

/** Placeholder until the Gateway Lattice lands (1.4). */
public final class Lattice {
    private Lattice() {}

    static InteractionResult useFork(UseOnContext context) {
        return InteractionResult.PASS;
    }
}
