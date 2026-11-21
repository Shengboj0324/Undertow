package dev.worldsfall.nav;

import dev.worldsfall.registry.WFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Ship's Bell: rung on a Mooring, it calls your tamed Skates and Drakes to the deck from up to 256 blocks away. */
public class ShipsBellBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty RINGING = BooleanProperty.create("ringing");
    private static final VoxelShape SHAPE = Shapes.or(box(2, 0, 2, 14, 2, 14), box(4, 4, 4, 12, 14, 12), box(2, 14, 6, 14, 16, 10));

    public ShipsBellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RINGING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RINGING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(RINGING)) {
            return InteractionResult.CONSUME;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            level.playSound(null, pos, WFSounds.BLOCK_SHIPS_BELL_RING.get(), SoundSource.BLOCKS, 3.0f, 1.0f);
            level.setBlock(pos, state.setValue(RINGING, true), Block.UPDATE_ALL);
            level.scheduleTick(pos, this, 100);
            Moorings.ringBell(server, pos, sp);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(RINGING)) {
            level.setBlock(pos, state.setValue(RINGING, false), Block.UPDATE_ALL);
        }
    }
}
