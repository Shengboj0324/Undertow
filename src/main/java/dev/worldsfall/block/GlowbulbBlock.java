package dev.worldsfall.block;

import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Glowbulb: hanging magenta light-fruit (light 10). Right-click harvests; regrows in ~10 minutes (§6.2). */
public class GlowbulbBlock extends Block {
    public static final BooleanProperty RIPE = BooleanProperty.create("ripe");
    private static final VoxelShape SHAPE = box(4, 2, 4, 12, 16, 12);
    /** Average regrowth: random tick every ~68 s, 1-in-9 chance => ~10 minutes. */
    private static final int REGROW_CHANCE = 9;

    public GlowbulbBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RIPE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RIPE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return above.isFaceSturdy(level, pos.above(), Direction.DOWN) || above.getBlock() instanceof HangingCrystalBlock;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return dir == Direction.UP && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(RIPE);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(REGROW_CHANCE) == 0) {
            level.setBlock(pos, state.setValue(RIPE, true), Block.UPDATE_ALL);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(RIPE)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            Block.popResource(server, pos, new ItemStack(WFItems.GLOWBULB.get(), 1 + server.getRandom().nextInt(2)));
            server.playSound(null, pos, WFSounds.ITEM_GLOWBULB_HARVEST.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            server.setBlock(pos, state.setValue(RIPE, false), Block.UPDATE_ALL);
        }
        return InteractionResult.SUCCESS;
    }

    public static int light(BlockState state) {
        return state.getValue(RIPE) ? 10 : 3;
    }
}
