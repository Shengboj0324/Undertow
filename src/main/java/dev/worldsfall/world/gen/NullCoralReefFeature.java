package dev.worldsfall.world.gen;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.registry.WFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Null Coral reef: a drifting knot of black-and-silver coral in the Undertow, branching up and down from a dense core. */
public record NullCoralReefFeature() implements Feature {
    public static final MapCodec<NullCoralReefFeature> CODEC = MapCodec.unit(NullCoralReefFeature::new);

    @Override
    public MapCodec<NullCoralReefFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (!GenUtil.get(level, origin).isAir()) {
            return false;
        }
        BlockState core = WFBlocks.NULL_CORAL_BLOCK.get().defaultBlockState(), coral = WFBlocks.NULL_CORAL.get().defaultBlockState(),
            fan = WFBlocks.NULL_CORAL_FAN.get().defaultBlockState();
        int r = 2 + random.nextInt(3);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz) + random.nextDouble() * 0.9;
                    if (d <= r) {
                        GenUtil.set(level, origin.offset(dx, dy, dz), core, 2);
                    }
                }
            }
        }
        for (int i = 0; i < 6 + random.nextInt(8); i++) {
            int dx = random.nextInt(r * 2 + 1) - r, dz = random.nextInt(r * 2 + 1) - r;
            BlockPos top = origin.offset(dx, r, dz);
            while (!GenUtil.get(level, top).isAir() && top.getY() < origin.getY() + r + 3) {
                top = top.above();
            }
            if (GenUtil.get(level, top).isAir() && !GenUtil.get(level, top.below()).isAir()) {
                GenUtil.set(level, top, random.nextInt(3) == 0 ? fan : coral, 2);
            }
        }
        return true;
    }
}
