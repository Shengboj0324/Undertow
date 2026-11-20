package dev.worldsfall.world.gen.structure;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.world.gen.WFStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Stillwater Basin (§4.2) as a structure, so one lake can span many chunks: the start re-centres itself on the island
 * from terrain heights, sizes the heart-shaped crater to 40–70% of the island top, and picks the shortest way off the
 * island for the Stillfall. {@link StillwaterBasinPiece} carves it chunk by chunk.
 */
public class StillwaterBasinStructure extends Structure {
    public static final MapCodec<StillwaterBasinStructure> CODEC = simpleCodec(StillwaterBasinStructure::new);
    static final int MAX_EXTENT = 64;

    public StillwaterBasinStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return WFStructures.STILLWATER_BASIN.get();
    }

    private static int surface(GenerationContext ctx, int x, int z) {
        return ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
    }

    /** Distance from (x, z) to the island edge along a direction: the first column more than 6 below {@code y}. */
    private static int extent(GenerationContext ctx, int x, int z, int y, Direction d) {
        for (int i = 2; i <= MAX_EXTENT; i += 2) {
            if (surface(ctx, x + d.getStepX() * i, z + d.getStepZ() * i) < y - 6) {
                return i - 1;
            }
        }
        return MAX_EXTENT;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        int x = ctx.chunkPos().getMiddleBlockX();
        int z = ctx.chunkPos().getMiddleBlockZ();
        if ((double) x * x + (double) z * z < 1000.0 * 1000.0) {
            return Optional.empty();
        }
        int y = surface(ctx, x, z);
        if (y < 40) {
            return Optional.empty();
        }
        // walk to the middle of the island top (two passes of re-centring on the east/west and north/south extents)
        for (int pass = 0; pass < 2; pass++) {
            int e = extent(ctx, x, z, y, Direction.EAST), w = extent(ctx, x, z, y, Direction.WEST);
            int s = extent(ctx, x, z, y, Direction.SOUTH), n = extent(ctx, x, z, y, Direction.NORTH);
            x += (e - w) / 2;
            z += (s - n) / 2;
            y = surface(ctx, x, z);
        }
        int best = Integer.MAX_VALUE;
        Direction spill = Direction.NORTH;
        int min = Integer.MAX_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int ext = extent(ctx, x, z, y, d);
            min = Math.min(min, ext);
            if (ext < best) {
                best = ext;
                spill = d;
            }
        }
        if (min < 16 || y < 40) {
            return Optional.empty();
        }
        WorldgenRandom random = ctx.random();
        int radius = Math.max(9, Math.min(24, (int) (min * (0.62 + random.nextFloat() * 0.12))));
        int depth = 3 + random.nextInt(4);
        BlockPos center = new BlockPos(x, y, z);
        int spillLength = best + 2;
        StillwaterBasinPiece piece = new StillwaterBasinPiece(center, radius, depth, spill, spillLength, random.nextLong());
        return Optional.of(new GenerationStub(center, b -> b.addPiece(piece)));
    }
}
