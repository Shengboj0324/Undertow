package dev.worldsfall.block;

import dev.worldsfall.registry.WFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** End stone with lilac moss (§6.2). Reverts to end stone when covered; spreads via grazers and rotting pods. */
public class PaleLoamBlock extends Block {
    public PaleLoamBlock(Properties properties) {
        super(properties);
    }

    public static boolean isCovered(LevelReader level, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState state = level.getBlockState(above);
        return state.isFaceSturdy(level, above, Direction.DOWN) && state.canOcclude();
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isCovered(level, pos)) {
            level.setBlockAndUpdate(pos, Blocks.END_STONE.defaultBlockState());
        }
    }

    /** Tries to convert one end stone block adjacent (incl. diagonals, ±1 y) to pale loam. Returns true on success. */
    public static boolean spreadFrom(ServerLevel level, BlockPos origin, RandomSource random) {
        for (int attempt = 0; attempt < 4; attempt++) {
            BlockPos target = origin.offset(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
            if (level.getBlockState(target).is(Blocks.END_STONE) && !isCovered(level, target)) {
                level.setBlockAndUpdate(target, WFBlocks.PALE_LOAM.get().defaultBlockState());
                return true;
            }
        }
        return false;
    }
}
