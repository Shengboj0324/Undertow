package dev.worldsfall.world.gen;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.block.HangingCrystalBlock;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.world.gravity.GravityWells;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The Underside's reversed forest (§4.5): beneath large islands, hang Lumen Crystal columns, Voidroot
 * curtains, Glowbulb clusters and Ceiling Moss from the island's bottom; reshape the ceiling into dark
 * Undershale; add floating shelves; register Gravity Wells around big crystal clusters. Runs per chunk.
 */
public record UndersideGrowthFeature() implements Feature {
    public static final MapCodec<UndersideGrowthFeature> CODEC = MapCodec.unit(UndersideGrowthFeature::new);

    @Override
    public MapCodec<UndersideGrowthFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        int cx = origin.getX() & ~15, cz = origin.getZ() & ~15;
        int crystals = 0;
        long sumX = 0, sumY = 0, sumZ = 0;
        boolean any = false;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = cx + dx, z = cz + dz;
                BlockPos bottom = GenUtil.islandBottom(level, x, z, 8);
                if (bottom == null) {
                    continue;
                }
                any = true;
                // reshape the ceiling into Undershale with violet bands
                for (int k = 0; k < 3; k++) {
                    if (random.nextInt(3) != 0 && GenUtil.isGround(level.getBlockState(bottom.above(k)))) {
                        level.setBlock(bottom.above(k), WFBlocks.UNDERSHALE.get().defaultBlockState(), 2);
                    }
                }
                BlockPos hang = bottom.below();
                int roll = random.nextInt(1000);
                if (roll < 55) {
                    int len = 2 + random.nextInt(8);
                    if (hangColumn(level, hang, WFBlocks.LUMEN_CRYSTAL.get().defaultBlockState(), len)) {
                        crystals++;
                        sumX += x;
                        sumY += hang.getY() - len / 2;
                        sumZ += z;
                    }
                } else if (roll < 120) {
                    hangColumn(level, hang, WFBlocks.VOIDROOT.get().defaultBlockState(), 3 + random.nextInt(10));
                } else if (roll < 145) {
                    level.setBlock(hang, WFBlocks.GLOWBULB_BLOCK.get().defaultBlockState(), 2);
                } else if (roll < 175) {
                    level.setBlock(hang, WFBlocks.LUMEN_CLUSTER.get().defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN), 2);
                } else if (roll < 330) {
                    level.setBlock(hang, WFBlocks.CEILING_MOSS.get().defaultBlockState(), 2);
                }
            }
        }
        if (!any) {
            return false;
        }
        if (random.nextInt(5) == 0) {
            placeShelf(level, random, cx + 4 + random.nextInt(8), cz + 4 + random.nextInt(8));
        }
        if (crystals >= 6) {
            BlockPos center = new BlockPos((int) (sumX / crystals), (int) (sumY / crystals), (int) (sumZ / crystals));
            GravityWells.add(level.getChunk(center), center, 6 + random.nextInt(7));
        }
        return true;
    }

    private static boolean hangColumn(WorldGenLevel level, BlockPos top, BlockState base, int len) {
        int actual = 0;
        for (int k = 0; k < len; k++) {
            if (!level.getBlockState(top.below(k)).isAir()) {
                break;
            }
            actual++;
        }
        if (actual == 0) {
            return false;
        }
        for (int k = 0; k < actual; k++) {
            HangingCrystalBlock.Part part = k == actual - 1 ? HangingCrystalBlock.Part.TIP : k == 0 && actual > 2 ? HangingCrystalBlock.Part.BASE
                : HangingCrystalBlock.Part.MIDDLE;
            level.setBlock(top.below(k), base.setValue(HangingCrystalBlock.PART, part), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    /** A flat Undershale slab hovering a few blocks below the ceiling, mossy underneath (§4.5 Floating Shelves). */
    private static void placeShelf(WorldGenLevel level, RandomSource random, int x, int z) {
        BlockPos bottom = GenUtil.islandBottom(level, x, z, 8);
        if (bottom == null) {
            return;
        }
        BlockPos c = bottom.below(4 + random.nextInt(3));
        int w = 2 + random.nextInt(2), l = 2 + random.nextInt(3);
        for (int dx = -w; dx <= w; dx++) {
            for (int dz = -l; dz <= l; dz++) {
                if (Math.abs(dx) == w && Math.abs(dz) == l) {
                    continue;
                }
                BlockPos p = c.offset(dx, 0, dz);
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, WFBlocks.UNDERSHALE.get().defaultBlockState(), 2);
                    if (random.nextInt(3) == 0 && level.getBlockState(p.below()).isAir()) {
                        level.setBlock(p.below(), WFBlocks.CEILING_MOSS.get().defaultBlockState(), 2);
                    }
                }
            }
        }
    }
}
