package dev.worldsfall.world.gen;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.registry.WFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Zenith Shoal (proposal "A fifth layer above"): a rare cluster of small Skystone islets (4–12 blocks across) at Y 170–230,
 * flat-topped stepping stones and lookouts veined with Halo Glass.
 */
public record ZenithShoalFeature() implements Feature {
    public static final MapCodec<ZenithShoalFeature> CODEC = MapCodec.unit(ZenithShoalFeature::new);

    @Override
    public MapCodec<ZenithShoalFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (!GenUtil.get(level, origin).isAir()) {
            return false;
        }
        int islets = 3 + random.nextInt(5);
        for (int i = 0; i < islets; i++) {
            BlockPos c = i == 0 ? origin : origin.offset(random.nextInt(41) - 20, random.nextInt(13) - 6, random.nextInt(41) - 20);
            islet(level, c, 2 + random.nextInt(5), random);
        }
        return true;
    }

    private static void islet(WorldGenLevel level, BlockPos c, int r, RandomSource random) {
        BlockState sky = WFBlocks.SKYSTONE.get().defaultBlockState(), polished = WFBlocks.POLISHED_SKYSTONE.get().defaultBlockState(),
            halo = WFBlocks.HALO_GLASS.get().defaultBlockState(), chiseled = WFBlocks.CHISELED_SKYSTONE.get().defaultBlockState();
        double rx = r + random.nextDouble(), rz = r + random.nextDouble();
        int depth = 2 + r / 2 + random.nextInt(2);
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double e = (dx * dx) / (rx * rx) + (dz * dz) / (rz * rz);
                if (e > 1.0) {
                    continue;
                }
                int column = (int) Math.round(depth * (1.0 - e)) + 1;
                for (int dy = 0; dy < column; dy++) {
                    BlockPos p = c.offset(dx, -dy, dz);
                    if (!GenUtil.get(level, p).isAir()) {
                        continue;
                    }
                    BlockState s = sky;
                    if (dy == 0 && e > 0.75) {
                        s = polished;
                    } else if (random.nextInt(dy == 0 ? 18 : 10) == 0) {
                        s = halo;
                    } else if (dy == 0 && random.nextInt(60) == 0) {
                        s = chiseled;
                    }
                    GenUtil.set(level, p, s, 2);
                }
            }
        }
    }
}
