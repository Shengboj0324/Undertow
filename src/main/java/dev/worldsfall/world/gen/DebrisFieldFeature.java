package dev.worldsfall.world.gen;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.registry.WFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Drift Debris Field (§4.6): a loose cluster of 3–40 floating blocks, sometimes a stepping-stone path. */
public record DebrisFieldFeature() implements Feature {
    public static final MapCodec<DebrisFieldFeature> CODEC = MapCodec.unit(DebrisFieldFeature::new);

    @Override
    public MapCodec<DebrisFieldFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (!level.getBlockState(origin).isAir()) {
            return false;
        }
        boolean path = random.nextInt(4) == 0;
        int count = 3 + random.nextInt(path ? 12 : 38);
        double ax = random.nextGaussian(), az = random.nextGaussian();
        double norm = Math.max(0.2, Math.hypot(ax, az));
        ax /= norm;
        az /= norm;
        for (int i = 0; i < count; i++) {
            BlockPos p;
            if (path) {
                p = origin.offset((int) Math.round(ax * i * 3), (int) Math.round(Math.sin(i * 0.7) * 1.5), (int) Math.round(az * i * 3));
            } else {
                p = origin.offset((int) (random.nextGaussian() * 5), (int) (random.nextGaussian() * 2.5), (int) (random.nextGaussian() * 5));
            }
            BlockState st = pick(random);
            int size = path ? 2 : (random.nextInt(6) == 0 ? 2 : 1);
            for (int dx = 0; dx < size; dx++) {
                for (int dz = 0; dz < size; dz++) {
                    if (level.getBlockState(p.offset(dx, 0, dz)).isAir()) {
                        level.setBlock(p.offset(dx, 0, dz), st, 2);
                    }
                }
            }
        }
        return true;
    }

    private static BlockState pick(RandomSource random) {
        int r = random.nextInt(20);
        if (r < 13) return WFBlocks.DRIFTSTONE.get().defaultBlockState();
        if (r < 18) return Blocks.END_STONE.defaultBlockState();
        if (r < 19) return Blocks.PURPUR_BLOCK.defaultBlockState();
        return WFBlocks.WEATHERED_PURPUR.get().defaultBlockState();
    }
}
