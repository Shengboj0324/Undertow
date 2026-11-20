package dev.worldsfall.fluid;

import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFFluids;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/** Stillwater source/flowing fluids: flow distance 3, every 30 ticks, end stone beneath slowly becomes Stillstone (§7.3). */
public abstract class StillwaterFluid extends BaseFlowingFluid {
    public static final int MAX_CONVERSIONS_NEARBY = 16;

    protected StillwaterFluid() {
        super(new Properties(WFFluids.STILLWATER_TYPE, WFFluids.STILLWATER, WFFluids.FLOWING_STILLWATER)
            .bucket(WFItems.STILLWATER_BUCKET)
            .block(WFBlocks.STILLWATER)
            .slopeFindDistance(2)
            .levelDecreasePerBlock(2)
            .tickRate(30)
            .explosionResistance(100f));
    }

    @Override
    protected boolean isRandomlyTicking() {
        return true;
    }

    /** End stone a stillwater block rests on turns to Stillstone ~every 5 minutes, at most 16 per basin (§7.3). */
    @Override
    protected void randomTick(ServerLevel level, BlockPos pos, FluidState state, RandomSource random) {
        BlockPos below = pos.below();
        BlockState under = level.getBlockState(below);
        if (!under.is(WFTags.Blocks.STILLWATER_CONVERTS) || random.nextInt(5) != 0) {
            return;
        }
        int converted = 0;
        for (BlockPos p : BlockPos.betweenClosed(below.offset(-6, -2, -6), below.offset(6, 0, 6))) {
            if (level.getBlockState(p).is(WFBlocks.STILLSTONE.get()) && ++converted >= MAX_CONVERSIONS_NEARBY) {
                return;
            }
        }
        level.setBlock(below, WFBlocks.STILLSTONE.get().defaultBlockState(), Block.UPDATE_ALL);
    }

    public static final class Source extends StillwaterFluid {
        @Override
        public boolean isSource(FluidState state) {
            return true;
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }
    }

    public static final class Flowing extends StillwaterFluid {
        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }
    }
}
