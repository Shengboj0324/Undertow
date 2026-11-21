package dev.worldsfall.life;

import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tide Bell (proposal "Building and redstone"): rings and sends a two-second redstone pulse at every tide phase change.
 * Pairs with the Tide Gauge (a steady signal) for tide-timed builds.
 */
public class TideBellBlock extends HorizontalDirectionalBlock {
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 3);
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final int PULSE = 40, CHECK = 20;
    private static final VoxelShape SHAPE = Shapes.or(box(1, 0, 6, 15, 2, 10), box(1, 2, 7, 3, 15, 9), box(13, 2, 7, 15, 15, 9),
        box(1, 13, 7, 15, 15, 9), box(5, 5, 5, 11, 12, 11));

    public TideBellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PHASE, 0).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PHASE, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        int phase = Tides.appliesTo(context.getLevel()) ? Tides.phase(context.getLevel()).ordinal() : 0;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(PHASE, phase);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, CHECK);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            state = state.setValue(POWERED, false);
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
        if (Tides.appliesTo(level)) {
            int phase = Tides.phase(level).ordinal();
            if (phase != state.getValue(PHASE)) {
                level.setBlock(pos, state.setValue(PHASE, phase).setValue(POWERED, true), Block.UPDATE_ALL);
                level.playSound(null, pos, WFSounds.BLOCK_TIDE_BELL_RING.get(), SoundSource.BLOCKS, 2.0f, 0.8f + phase * 0.12f);
                level.scheduleTick(pos, this, PULSE);
                return;
            }
        }
        level.scheduleTick(pos, this, CHECK);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }
}
