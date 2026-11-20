package dev.worldsfall.world.gen;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.block.ChorusPodBlock;
import dev.worldsfall.registry.WFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Attaches occasional single Chorus Pods to existing chorus plants in a chunk (§4.0 Midlands additions). */
public record ChorusPodScatterFeature() implements Feature {
    public static final MapCodec<ChorusPodScatterFeature> CODEC = MapCodec.unit(ChorusPodScatterFeature::new);

    @Override
    public MapCodec<ChorusPodScatterFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        int cx = origin.getX() & ~15, cz = origin.getZ() & ~15;
        int placed = 0;
        for (int i = 0; i < 48 && placed < 3; i++) {
            int x = cx + random.nextInt(16), z = cz + random.nextInt(16);
            BlockPos g = GenUtil.surface(level, x, z);
            if (g == null) {
                continue;
            }
            for (int y = 1; y < 10; y++) {
                BlockPos p = new BlockPos(x, g.getY() + y, z);
                if (!GenUtil.get(level, p).is(Blocks.CHORUS_PLANT)) {
                    continue;
                }
                Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                if (GenUtil.get(level, p.relative(d)).isAir()) {
                    GenUtil.set(level, p.relative(d), WFBlocks.CHORUS_POD.get().defaultBlockState().setValue(ChorusPodBlock.FACING, d.getOpposite())
                        .setValue(ChorusPodBlock.AGE, random.nextInt(3)), 2);
                    placed++;
                    break;
                }
            }
        }
        return placed > 0;
    }
}
