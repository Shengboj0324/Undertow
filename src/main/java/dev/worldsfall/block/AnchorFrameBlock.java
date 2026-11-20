package dev.worldsfall.block;

import dev.worldsfall.world.gravity.StableFields;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Anchor Frame: scaffolding-like lattice placeable in midair, but only inside a Stable Field (§6.3, §9.8). */
public class AnchorFrameBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(box(0, 14, 0, 16, 16, 16), box(0, 0, 0, 2, 16, 2), box(14, 0, 0, 16, 16, 2),
        box(0, 0, 14, 2, 16, 16), box(14, 0, 14, 16, 16, 16));

    public AnchorFrameBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return StableFields.isInsideField(level, pos);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return StableFields.isInsideField(context.getLevel(), context.getClickedPos()) ? defaultBlockState() : null;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }
}
