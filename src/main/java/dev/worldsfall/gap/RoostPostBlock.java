package dev.worldsfall.gap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Roost Post: tamed Skates perch on it and recover stamina twice as fast — a stable for flying mounts. */
public class RoostPostBlock extends Block {
    private static final VoxelShape SHAPE = box(5, 0, 5, 11, 16, 11);

    public RoostPostBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
