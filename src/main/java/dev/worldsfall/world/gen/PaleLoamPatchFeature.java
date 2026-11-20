package dev.worldsfall.world.gen;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.registry.WFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Irregular Pale Loam patch (~60/40 with end stone) with scattered Lilac Tufts (§4.1 palette). */
public record PaleLoamPatchFeature() implements Feature {
    public static final MapCodec<PaleLoamPatchFeature> CODEC = MapCodec.unit(PaleLoamPatchFeature::new);

    @Override
    public MapCodec<PaleLoamPatchFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        BlockPos ground = GenUtil.surface(level, origin.getX(), origin.getZ());
        if (ground == null) {
            return false;
        }
        int r = 4 + random.nextInt(6);
        double ox = random.nextDouble() * 100, oz = random.nextDouble() * 100;
        boolean any = false;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double wobble = EndRegions.valueNoise((ground.getX() + dx) / 3.0 + ox, (ground.getZ() + dz) / 3.0 + oz, 5) * 2.2;
                if (dx * dx + dz * dz > (r + wobble) * (r + wobble)) {
                    continue;
                }
                BlockPos g = GenUtil.surface(level, ground.getX() + dx, ground.getZ() + dz);
                if (g == null || !level.getBlockState(g).is(Blocks.END_STONE) || !level.getBlockState(g.above()).isAir() || random.nextInt(10) >= 6) {
                    continue;
                }
                level.setBlock(g, WFBlocks.PALE_LOAM.get().defaultBlockState(), 2);
                if (random.nextInt(4) == 0) {
                    level.setBlock(g.above(), WFBlocks.LILAC_TUFT.get().defaultBlockState(), 2);
                }
                any = true;
            }
        }
        return any;
    }
}
