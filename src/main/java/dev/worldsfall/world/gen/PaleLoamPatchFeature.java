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
                double edge = Math.sqrt(dx * dx + dz * dz) / Math.max(1.0, r + wobble);
                // contiguous patch with a ragged, moss-like fringe instead of per-block speckle
                double grain = EndRegions.valueNoise((ground.getX() + dx) / 1.7 + oz, (ground.getZ() + dz) / 1.7 + ox, 13);
                if (edge > 1.0 || grain < edge * 1.3 - 0.75) {
                    continue;
                }
                BlockPos g = GenUtil.surface(level, ground.getX() + dx, ground.getZ() + dz);
                if (g == null || !GenUtil.get(level, g).is(Blocks.END_STONE) || !GenUtil.get(level, g.above()).isAir()) {
                    continue;
                }
                GenUtil.set(level, g, WFBlocks.PALE_LOAM.get().defaultBlockState(), 2);
                if (random.nextInt(4) == 0) {
                    GenUtil.set(level, g.above(), WFBlocks.LILAC_TUFT.get().defaultBlockState(), 2);
                }
                any = true;
            }
        }
        return any;
    }
}
