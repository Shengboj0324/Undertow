package dev.worldsfall.world.gen;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.entity.WildShulker;
import dev.worldsfall.registry.WFEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Wild Shulker colony (§8.6): 2–4 weathered shulkers on an exposed ridge. */
public record WildShulkerColonyFeature() implements Feature {
    public static final MapCodec<WildShulkerColonyFeature> CODEC = MapCodec.unit(WildShulkerColonyFeature::new);

    @Override
    public MapCodec<WildShulkerColonyFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        BlockPos ground = GenUtil.surface(level, origin.getX(), origin.getZ());
        if (ground == null) {
            return false;
        }
        int n = 2 + random.nextInt(3);
        for (int i = 0; i < n; i++) {
            BlockPos g = GenUtil.surface(level, ground.getX() + random.nextInt(7) - 3, ground.getZ() + random.nextInt(7) - 3);
            if (g == null || !level.getBlockState(g.above()).isAir()) {
                continue;
            }
            WildShulker s = WFEntities.WILD_SHULKER.get().create(level.getLevel(), EntitySpawnReason.STRUCTURE);
            if (s != null) {
                s.snapTo(g.getX() + 0.5, g.getY() + 1, g.getZ() + 0.5, 0, 0);
                s.setPersistenceRequired();
                level.addFreshEntityWithPassengers(s);
            }
        }
        return true;
    }
}
