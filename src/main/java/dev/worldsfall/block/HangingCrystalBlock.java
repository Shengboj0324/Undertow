package dev.worldsfall.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A dripstone-like hanging growth (Lumen Crystal column, Voidroot). Hangs from the block above; the lowest
 * segment is the TIP. Parts are recomputed from neighbours, so columns stay consistent when broken.
 */
public class HangingCrystalBlock extends Block {
    public enum Part implements StringRepresentable {
        BASE("base"), MIDDLE("middle"), TIP("tip");
        private final String name;
        Part(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    private final VoxelShape baseShape;
    private final VoxelShape middleShape;
    private final VoxelShape tipShape;
    private final boolean grows;

    public HangingCrystalBlock(Properties properties, boolean grows) {
        super(properties);
        this.grows = grows;
        this.baseShape = box(2, 0, 2, 14, 16, 14);
        this.middleShape = box(3, 0, 3, 13, 16, 13);
        this.tipShape = box(5, 3, 5, 11, 16, 11);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.TIP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PART)) {
            case BASE -> baseShape;
            case MIDDLE -> middleShape;
            case TIP -> tipShape;
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return above.is(this) || above.isFaceSturdy(level, pos.above(), Direction.DOWN);
    }

    public Part computePart(LevelReader level, BlockPos pos) {
        boolean below = level.getBlockState(pos.below()).is(this);
        boolean above = level.getBlockState(pos.above()).is(this);
        if (!below) {
            return Part.TIP;
        }
        return above ? Part.MIDDLE : Part.BASE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState st = defaultBlockState().setValue(PART, computePart(context.getLevel(), context.getClickedPos()));
        return st.canSurvive(context.getLevel(), context.getClickedPos()) ? st : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (dir == Direction.UP && !state.canSurvive(level, pos)) {
            ticks.scheduleTick(pos, this, 1);
            return state;
        }
        return dir.getAxis().isVertical() ? state.setValue(PART, computePart(level, pos)) : state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return grows && state.getValue(PART) == Part.TIP;
    }

    /** Lumen columns slowly lengthen (max 7) while in low-gravity Underside air. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(40) != 0 || !level.getBlockState(pos.below()).isAir()) {
            return;
        }
        int length = 1;
        BlockPos cursor = pos.above();
        while (level.getBlockState(cursor).is(this) && length < 8) {
            length++;
            cursor = cursor.above();
        }
        if (length < 7 && dev.worldsfall.world.gravity.GravityZones.isUnderside(level, pos)) {
            level.setBlockAndUpdate(pos.below(), defaultBlockState().setValue(PART, Part.TIP));
            level.setBlockAndUpdate(pos, state.setValue(PART, computePart(level, pos)));
        }
    }
}
