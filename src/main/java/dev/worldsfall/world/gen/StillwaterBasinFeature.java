package dev.worldsfall.world.gen;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Stillwater Basin (§4.2): a heart-shaped crater 3–6 deep taking much of the island top, Stillstone floor and
 * walls, a raised lip with Mirror Shards and Rimglass, Floating Relics on the black surface, and a spill
 * point where a slow Stillfall pours off the edge. Only on islands ≥ 32 across.
 */
public record StillwaterBasinFeature() implements Feature {
    public static final MapCodec<StillwaterBasinFeature> CODEC = MapCodec.unit(StillwaterBasinFeature::new);
    private static final BlockState[] RELICS = {
        Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), Blocks.PUMPKIN.defaultBlockState(),
        Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.HAY_BLOCK.defaultBlockState(), Blocks.RED_MUSHROOM_BLOCK.defaultBlockState(),
        Blocks.MELON.defaultBlockState(), Blocks.BIRCH_LOG.defaultBlockState()};

    @Override
    public MapCodec<StillwaterBasinFeature> codec() {
        return CODEC;
    }

    /** Heart-ish shape: two overlapping lobes and a point. */
    static boolean inShape(double x, double z, double r) {
        double lobe = r * 0.62;
        double a = Math.hypot(x + r * 0.33, z + r * 0.15);
        double b = Math.hypot(x - r * 0.33, z + r * 0.15);
        boolean lobes = a <= lobe || b <= lobe;
        boolean point = z >= 0 && Math.abs(x) <= (r * 0.9 - z) * 0.85 && z <= r * 0.85;
        return lobes || point;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        BlockPos center = GenUtil.surface(level, origin.getX(), origin.getZ());
        if (center == null) {
            return false;
        }
        int half = GenUtil.halfWidth(level, center, 20);
        if (half < 16) {
            return false;
        }
        double r = Math.min(half - 4, 9 + random.nextInt(4));
        int depth = 3 + random.nextInt(4);
        int surfaceY = center.getY();
        BlockState stillstone = WFBlocks.STILLSTONE.get().defaultBlockState();
        BlockState water = WFFluids.STILLWATER.get().defaultFluidState().createLegacyBlock();
        int ir = (int) Math.ceil(r) + 2;
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dz = -ir; dz <= ir; dz++) {
                boolean inside = inShape(dx, dz, r);
                boolean lip = !inside && (inShape(dx + 1, dz, r) || inShape(dx - 1, dz, r) || inShape(dx, dz + 1, r) || inShape(dx, dz - 1, r));
                int x = center.getX() + dx, z = center.getZ() + dz;
                if (inside) {
                    double edge = 1 - Math.min(1, edgeDistance(dx, dz, r) / 3.0);
                    int d = Math.max(1, (int) Math.round(depth - edge * (depth - 1)));
                    for (int y = surfaceY + 2; y > surfaceY - d; y--) {
                        level.setBlock(new BlockPos(x, y, z), y <= surfaceY - 1 ? water : Blocks.AIR.defaultBlockState(), 2);
                    }
                    for (int y = surfaceY - d; y > surfaceY - d - 2; y--) {
                        level.setBlock(new BlockPos(x, y, z), stillstone, 2);
                    }
                } else if (lip) {
                    BlockPos top = new BlockPos(x, surfaceY, z);
                    level.setBlock(top, random.nextInt(3) == 0 ? stillstone : Blocks.END_STONE.defaultBlockState(), 2);
                    for (int y = surfaceY - 1; y > surfaceY - depth - 1; y--) {
                        level.setBlock(new BlockPos(x, y, z), stillstone, 2);
                    }
                    if (random.nextInt(5) == 0) {
                        level.setBlock(top.above(), random.nextInt(4) == 0 ? WFBlocks.RIMGLASS.get().defaultBlockState()
                            : stillstone, 2);
                    }
                    if (random.nextInt(6) == 0 && level.getBlockState(top.above()).isAir()) {
                        level.setBlock(top.above(), WFBlocks.MIRROR_SHARD.get().defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP), 2);
                    }
                }
            }
        }
        // Floating relics: blocks resting on the black surface
        int relics = 2 + random.nextInt(4);
        for (int i = 0; i < relics; i++) {
            int dx = random.nextInt((int) r) - (int) (r / 2), dz = random.nextInt((int) r) - (int) (r / 2);
            if (inShape(dx, dz, r * 0.7)) {
                level.setBlock(new BlockPos(center.getX() + dx, surfaceY - 1, center.getZ() + dz), RELICS[random.nextInt(RELICS.length)], 2);
            }
        }
        // Spill point toward the nearest edge: a notch and a slow Stillfall
        Direction spill = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int ext = GenUtil.extent(level, center, spill, 30);
        BlockPos.MutableBlockPos p = center.mutable();
        for (int i = (int) (r * 0.6); i <= ext + 1; i++) {
            p.set(center.relative(spill, i));
            level.setBlock(p.atY(surfaceY), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(p.atY(surfaceY - 1), i <= ext ? water : Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(p.atY(surfaceY - 2), stillstone, 2);
            if (i <= ext) {
                level.scheduleTick(p.atY(surfaceY - 1).immutable(), WFFluids.STILLWATER.get(), 1);
            }
        }
        return true;
    }

    private static double edgeDistance(int dx, int dz, double r) {
        for (int k = 1; k <= 4; k++) {
            if (!inShape(dx + k, dz, r) || !inShape(dx - k, dz, r) || !inShape(dx, dz + k, r) || !inShape(dx, dz - k, r)) {
                return k - 1;
            }
        }
        return 4;
    }
}
