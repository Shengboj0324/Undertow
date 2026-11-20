package dev.worldsfall.world.gen;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.Worldsfall;
import dev.worldsfall.block.entity.PerPlayerLootBlockEntity;
import dev.worldsfall.registry.WFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Echo Shrine (§11.7): a small island, a purpur plinth holding one Echo Tablet, lit by one Spire Lantern. */
public record EchoShrineFeature() implements Feature {
    public static final MapCodec<EchoShrineFeature> CODEC = MapCodec.unit(EchoShrineFeature::new);

    @Override
    public MapCodec<EchoShrineFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-4, -4, -4), origin.offset(4, 5, 4))) {
            if (!level.getBlockState(p).isAir()) {
                return false;
            }
        }
        float size = 3.5f;
        for (int y = 0; size > 0.5f; y--) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (dx * dx + dz * dz <= size * size) {
                        level.setBlock(origin.offset(dx, y, dz), Blocks.END_STONE.defaultBlockState(), 2);
                    }
                }
            }
            size -= 0.9f + random.nextFloat() * 0.4f;
        }
        level.setBlock(origin.above(), Blocks.PURPUR_PILLAR.defaultBlockState(), 2);
        BlockPos vault = origin.above(2);
        level.setBlock(vault, WFBlocks.SPIRE_VAULT.get().defaultBlockState(), 2);
        if (level.getBlockEntity(vault) instanceof PerPlayerLootBlockEntity be) {
            be.configure(ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(Worldsfall.MODID, "vault/echo_shrine")), false);
        }
        level.setBlock(origin.offset(2, 1, 0), Blocks.END_STONE_BRICK_WALL.defaultBlockState(), 2);
        level.setBlock(origin.offset(2, 2, 0), WFBlocks.SPIRE_LANTERN.get().defaultBlockState().setValue(LanternBlock.HANGING, false), 2);
        return true;
    }
}
