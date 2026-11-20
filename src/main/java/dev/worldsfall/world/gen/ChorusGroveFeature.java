package dev.worldsfall.world.gen;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.block.ChorusPodBlock;
import dev.worldsfall.registry.WFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Chorus Grove (§4.1): 4–9 tall chorus plants on Pale Loam with Chorus Pods hanging from side branches, Lilac
 * Tufts around, and a Pod Drift (fallen ripe pods) in the nearest bowl.
 */
public record ChorusGroveFeature() implements Feature {
    public static final MapCodec<ChorusGroveFeature> CODEC = MapCodec.unit(ChorusGroveFeature::new);

    @Override
    public MapCodec<ChorusGroveFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        BlockPos ground = GenUtil.surface(level, origin.getX(), origin.getZ());
        if (ground == null || GenUtil.halfWidth(level, ground, 8) < 6) {
            return false;
        }
        int plants = 4 + random.nextInt(6);
        int placed = 0;
        for (int i = 0; i < plants * 3 && placed < plants; i++) {
            int dx = random.nextInt(11) - 5, dz = random.nextInt(11) - 5;
            BlockPos g = GenUtil.surface(level, ground.getX() + dx, ground.getZ() + dz);
            if (g == null || !level.getBlockState(g.above()).isAir()) {
                continue;
            }
            level.setBlock(g, WFBlocks.PALE_LOAM.get().defaultBlockState(), 2);
            ChorusFlowerBlock.generatePlant(level, g.above(), random, 8);
            placed++;
        }
        if (placed == 0) {
            return false;
        }
        // hang pods on the side of chorus stems
        int pods = 0;
        for (BlockPos p : BlockPos.betweenClosed(ground.offset(-7, 1, -7), ground.offset(7, 14, 7))) {
            if (!level.getBlockState(p).is(Blocks.CHORUS_PLANT) || random.nextInt(3) != 0) {
                continue;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos podPos = p.relative(d);
                if (level.getBlockState(podPos).isAir() && random.nextInt(3) == 0 && pods < 14) {
                    int age = random.nextInt(10) < 6 ? ChorusPodBlock.RIPE : random.nextInt(2);
                    level.setBlock(podPos, WFBlocks.CHORUS_POD.get().defaultBlockState().setValue(ChorusPodBlock.FACING, d.getOpposite())
                        .setValue(ChorusPodBlock.AGE, age), 2);
                    pods++;
                    break;
                }
            }
        }
        // ground cover and a pod drift
        BlockState tuft = WFBlocks.LILAC_TUFT.get().defaultBlockState();
        for (int i = 0; i < 26; i++) {
            BlockPos g = GenUtil.surface(level, ground.getX() + random.nextInt(13) - 6, ground.getZ() + random.nextInt(13) - 6);
            if (g != null && level.getBlockState(g.above()).isAir()) {
                if (random.nextInt(3) != 0) {
                    level.setBlock(g, WFBlocks.PALE_LOAM.get().defaultBlockState(), 2);
                }
                level.setBlock(g.above(), tuft, 2);
            }
        }
        BlockPos drift = GenUtil.surface(level, ground.getX() + random.nextInt(7) - 3, ground.getZ() + random.nextInt(7) - 3);
        if (drift != null) {
            int n = 3 + random.nextInt(6);
            for (int i = 0; i < n; i++) {
                BlockPos g = GenUtil.surface(level, drift.getX() + random.nextInt(3) - 1, drift.getZ() + random.nextInt(3) - 1);
                if (g != null && (level.getBlockState(g.above()).isAir() || level.getBlockState(g.above()).is(WFBlocks.LILAC_TUFT.get()))) {
                    level.setBlock(g.above(), WFBlocks.CHORUS_POD.get().defaultBlockState().setValue(ChorusPodBlock.FALLEN, true)
                        .setValue(ChorusPodBlock.AGE, ChorusPodBlock.RIPE).setValue(ChorusPodBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)), 2);
                }
            }
        }
        return true;
    }
}
