package dev.worldsfall.block;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.worldsfall.registry.WFBlocks;

/** Lilac Tuft: short grass-like plant on pale loam / end stone. */
public class EndPlantBlock extends VegetationBlock {
    private static final VoxelShape SHAPE = net.minecraft.world.level.block.Block.box(2, 0, 2, 14, 10, 14);

    public EndPlantBlock(Properties properties) {
        super(properties);
    }


    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(WFBlocks.PALE_LOAM.get()) || state.is(Blocks.END_STONE) || state.is(BlockTags.DIRT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
