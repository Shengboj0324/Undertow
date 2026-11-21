package dev.worldsfall.life;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Tidewrack: what a King Tide leaves behind. A half-height pile of driftstone chips, teal silt and voidroot; break it for loot. */
public class TidewrackBlock extends Block {
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 8, 16);

    public TidewrackBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

}
