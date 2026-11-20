package dev.worldsfall.block;

import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Chorus Pod fruit hanging on the side of a chorus plant (§6.2). Three growth stages; ripe pods glow (light 4)
 * and drop as items when touched or after a while, then regrow — the renewable End food source.
 */
public class ChorusPodBlock extends HorizontalDirectionalBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_2;
    public static final int RIPE = 2;
    /** A ripe pod lying on the ground (Pod Drifts, §4.1); does not grow, can be picked up or grazed. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty FALLEN =
        net.minecraft.world.level.block.state.properties.BooleanProperty.create("fallen");
    private static final VoxelShape FALLEN_SHAPE = box(4, 0, 4, 12, 6, 12);
    private static final VoxelShape[] NORTH = {box(6, 6, 0, 10, 11, 4), box(5, 4, 0, 11, 11, 6), box(4, 2, 0, 12, 11, 8)};
    private static final VoxelShape[] SOUTH = {box(6, 6, 12, 10, 11, 16), box(5, 4, 10, 11, 11, 16), box(4, 2, 8, 12, 11, 16)};
    private static final VoxelShape[] WEST = {box(0, 6, 6, 4, 11, 10), box(0, 4, 5, 6, 11, 11), box(0, 2, 4, 8, 11, 12)};
    private static final VoxelShape[] EAST = {box(12, 6, 6, 16, 11, 10), box(10, 4, 5, 16, 11, 11), box(8, 2, 4, 16, 11, 12)};

    public ChorusPodBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(AGE, 0).setValue(FALLEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, AGE, FALLEN);
    }

    /** FACING points toward the supporting chorus plant. */
    public static boolean isSupport(BlockState state) {
        return state.is(Blocks.CHORUS_PLANT);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(FALLEN)) {
            return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        }
        return isSupport(level.getBlockState(pos.relative(state.getValue(FACING))));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction dir : context.getNearestLookingDirections()) {
            if (dir.getAxis().isHorizontal()) {
                BlockState st = defaultBlockState().setValue(FACING, dir);
                if (st.canSurvive(context.getLevel(), context.getClickedPos())) {
                    return st;
                }
            }
        }
        return null;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        Direction support = state.getValue(FALLEN) ? Direction.DOWN : state.getValue(FACING);
        return dir == support && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, ticks, pos, dir, neighbourPos, neighbourState, random);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(FALLEN)) {
            return FALLEN_SHAPE;
        }
        int age = state.getValue(AGE);
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH[age];
            case WEST -> WEST[age];
            case EAST -> EAST[age];
            default -> NORTH[age];
        };
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(FALLEN);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age < RIPE) {
            if (random.nextInt(4) == 0) {
                level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
            }
        } else if (random.nextInt(6) == 0) {
            drop(level, pos, state, true);
        }
    }

    /** Releases a ripe pod as an item. If {@code regrow}, the pod restarts at stage 0 (renewable). */
    public static void drop(ServerLevel level, BlockPos pos, BlockState state, boolean regrow) {
        ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, new ItemStack(WFItems.CHORUS_POD.get()));
        item.setDeltaMovement(level.getRandom().nextGaussian() * 0.02, 0.05, level.getRandom().nextGaussian() * 0.02);
        level.addFreshEntity(item);
        level.playSound(null, pos, WFSounds.ITEM_CHORUS_POD_POP.get(), SoundSource.BLOCKS, 0.7f, 0.9f + level.getRandom().nextFloat() * 0.2f);
        if (regrow && !state.getValue(FALLEN)) {
            level.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_CLIENTS);
        } else {
            level.removeBlock(pos, false);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(AGE) < RIPE) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            drop(server, pos, state, true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                net.minecraft.world.entity.InsideBlockEffectApplier applier, boolean precise) {
        if (state.getValue(AGE) == RIPE && !state.getValue(FALLEN) && level instanceof ServerLevel server && entity instanceof net.minecraft.world.entity.LivingEntity
            && server.getRandom().nextInt(20) == 0) {
            drop(server, pos, state, true);
        }
    }
}
