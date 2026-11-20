package dev.worldsfall.world.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Shared worldgen helpers. */
final class GenUtil {
    private GenUtil() {}

    /** True when {@code pos} is inside the 3x3-chunk write window of the chunk being decorated. */
    static boolean inBounds(WorldGenLevel level, BlockPos pos) {
        if (level instanceof net.minecraft.server.level.WorldGenRegion region) {
            net.minecraft.world.level.ChunkPos c = region.getCenter();
            return Math.abs((pos.getX() >> 4) - c.x()) <= 1 && Math.abs((pos.getZ() >> 4) - c.z()) <= 1;
        }
        return true;
    }

    /** Bounds-checked read: outside the write window the world is treated as void. */
    static BlockState get(WorldGenLevel level, BlockPos pos) {
        return inBounds(level, pos) ? level.getBlockState(pos) : Blocks.VOID_AIR.defaultBlockState();
    }

    /** Bounds-checked write; silently clips features at the edge of the write window. */
    static boolean set(WorldGenLevel level, BlockPos pos, BlockState state, int flags) {
        return inBounds(level, pos) && level.setBlock(pos, state, flags);
    }

    static boolean isGround(BlockState s) {
        return s.is(Blocks.END_STONE) || s.is(dev.worldsfall.registry.WFBlocks.PALE_LOAM.get())
            || s.is(dev.worldsfall.registry.WFBlocks.STILLSTONE.get()) || s.is(dev.worldsfall.registry.WFBlocks.DRIFTSTONE.get());
    }

    /** Top solid ground block at x/z, or null. */
    static BlockPos surface(WorldGenLevel level, int x, int z) {
        if (!inBounds(level, new BlockPos(x, 0, z))) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        if (y <= level.getMinY()) {
            return null;
        }
        BlockPos p = new BlockPos(x, y, z);
        return isGround(get(level, p)) ? p : null;
    }

    /** Distance along a direction until the island edge (no ground), capped. */
    static int extent(WorldGenLevel level, BlockPos center, Direction dir, int cap) {
        for (int i = 1; i <= cap; i++) {
            BlockPos p = center.relative(dir, i);
            if (surface(level, p.getX(), p.getZ()) == null) {
                return i - 1;
            }
        }
        return cap;
    }

    /** Minimum island half-width around a point (in the four cardinal directions). */
    static int halfWidth(WorldGenLevel level, BlockPos center, int cap) {
        int min = cap;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            min = Math.min(min, extent(level, center, d, cap));
        }
        return min;
    }

    /** The lowest solid block of an island column at x/z between y 6 and 64 that has air below; null if none. */
    static BlockPos islandBottom(WorldGenLevel level, int x, int z, int minThickness) {
        for (int y = 8; y < 64; y++) {
            BlockPos p = new BlockPos(x, y, z);
            if (!get(level, p).isAir() && get(level, p.below()).isAir()) {
                for (int k = 1; k < minThickness; k++) {
                    if (get(level, p.above(k)).isAir()) {
                        return null;
                    }
                }
                return p;
            }
        }
        return null;
    }
}
