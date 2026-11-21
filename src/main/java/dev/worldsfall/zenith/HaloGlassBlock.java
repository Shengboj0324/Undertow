package dev.worldsfall.zenith;

import dev.worldsfall.world.tide.TideCycle;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Halo Glass (proposal "A fifth layer above"): translucent white-blue glass that glows brighter as the tide rises, from
 * light 6 at Low Tide to 15 at High, for tide-reactive builds. Outside the End it holds a steady middle glow.
 */
public class HaloGlassBlock extends TransparentBlock {
    public static final IntegerProperty GLOW = IntegerProperty.create("glow", 0, 3);

    public HaloGlassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GLOW, 1));
    }

    public static int lightFor(BlockState state) {
        return 6 + state.getValue(GLOW) * 3;
    }

    /** 0 at Low Tide .. 3 at High Tide. */
    public static int glowFor(Level level) {
        if (!Tides.appliesTo(level)) {
            return 1;
        }
        TideCycle c = Tides.cycle(level);
        double k = (Tides.height(level) - c.lowY()) / Math.max(1, c.highY() - c.lowY());
        return Mth.clamp((int) Math.round(k * 3), 0, 3);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GLOW);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(GLOW, glowFor(context.getLevel()));
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int glow = glowFor(level);
        if (glow != state.getValue(GLOW)) {
            level.setBlock(pos, state.setValue(GLOW, glow), Block.UPDATE_CLIENTS);
        }
    }
}
