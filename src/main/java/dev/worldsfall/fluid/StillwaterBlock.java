package dev.worldsfall.fluid;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;

/** The Stillwater liquid block. Entity effects (XP drain, buoyancy, Endermen) live in {@link StillwaterEffects}. */
public class StillwaterBlock extends LiquidBlock {
    public StillwaterBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }
}
