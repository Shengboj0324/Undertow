package dev.worldsfall.world.gen;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.block.ChorusPodBlock;
import dev.worldsfall.entity.VoidSkate;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Skate Roost (§11.6): a tall, thin Driftstone spike (8–20) on a tiny islet with perching ledges. Skates rest
 * here; Gap roosts have a little pile of Chorus Pods nearby (§17.1 onboarding).
 */
public record SkateRoostFeature() implements Feature {
    public static final MapCodec<SkateRoostFeature> CODEC = MapCodec.unit(SkateRoostFeature::new);

    @Override
    public MapCodec<SkateRoostFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-3, -6, -3), origin.offset(3, 20, 3))) {
            if (!level.getBlockState(p).isAir()) {
                return false;
            }
        }
        // islet
        float size = 2.5f + random.nextFloat() * 1.5f;
        for (int y = 0; size > 0.6f; y--) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx * dx + dz * dz <= size * size) {
                        level.setBlock(origin.offset(dx, y, dz), y == 0 ? Blocks.END_STONE.defaultBlockState() : WFBlocks.DRIFTSTONE.get().defaultBlockState(), 2);
                    }
                }
            }
            size -= 0.8f + random.nextFloat() * 0.5f;
        }
        // spike
        int height = 8 + random.nextInt(13);
        double lean = random.nextGaussian() * 0.06, leanZ = random.nextGaussian() * 0.06;
        for (int y = 1; y <= height; y++) {
            double t = y / (double) height;
            int w = t < 0.35 ? 1 : 0;
            int ox = (int) Math.round(lean * y), oz = (int) Math.round(leanZ * y);
            for (int dx = -w; dx <= w; dx++) {
                for (int dz = -w; dz <= w; dz++) {
                    if (w == 0 || Math.abs(dx) + Math.abs(dz) <= 1) {
                        level.setBlock(origin.offset(ox + dx, y, oz + dz), WFBlocks.DRIFTSTONE.get().defaultBlockState(), 2);
                    }
                }
            }
            if (y > 3 && y % 4 == 0) {
                Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                level.setBlock(origin.offset(ox, y, oz).relative(d), WFBlocks.DRIFTSTONE.get().defaultBlockState(), 2);
                level.setBlock(origin.offset(ox, y, oz).relative(d, 2), WFBlocks.FAMILIES.get("driftstone").get(0).get().defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
            }
        }
        double dist = Math.hypot(origin.getX(), origin.getZ());
        if (dist < 1000) {
            for (int i = 0; i < 5; i++) {
                BlockPos g = origin.offset(random.nextInt(3) - 1, 1, random.nextInt(3) - 1);
                if (level.getBlockState(g).isAir() && !level.getBlockState(g.below()).isAir()) {
                    level.setBlock(g, WFBlocks.CHORUS_POD.get().defaultBlockState().setValue(ChorusPodBlock.FALLEN, true)
                        .setValue(ChorusPodBlock.AGE, ChorusPodBlock.RIPE), 2);
                }
            }
        }
        int skates = 1 + random.nextInt(2);
        for (int i = 0; i < skates; i++) {
            VoidSkate skate = WFEntities.VOID_SKATE.get().create(level.getLevel(), EntitySpawnReason.STRUCTURE);
            if (skate != null) {
                skate.snapTo(origin.getX() + 0.5 + random.nextGaussian() * 4, origin.getY() + 6 + random.nextInt(8),
                    origin.getZ() + 0.5 + random.nextGaussian() * 4, random.nextFloat() * 360, 0);
                skate.setVariant(random.nextInt(12) == 0 ? 1 : 0);
                level.addFreshEntityWithPassengers(skate);
            }
        }
        return true;
    }
}
