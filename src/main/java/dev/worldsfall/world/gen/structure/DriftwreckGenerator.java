package dev.worldsfall.world.gen.structure;

import dev.worldsfall.block.entity.PerPlayerLootBlockEntity;
import dev.worldsfall.registry.WFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Driftwrecks (§11.3): broken chunks of the old world hanging at Y 8–28, submerged at High Tide, reachable at
 * Low Tide. Ladder Wreck, Pod Store, Tide Shrine (Tide Sigil, 1 in 6), Debris Hulk. Containers are per-player.
 */
public final class DriftwreckGenerator {
    public static final int TIDE_SHRINE = 0, LADDER = 1, POD_STORE = 2, HULK = 3;

    private DriftwreckGenerator() {}

    public static Blueprint build(BlockPos origin, int variant, RandomSource r) {
        Blueprint bp = new Blueprint();
        switch (variant) {
            case LADDER -> ladder(bp, origin, r);
            case POD_STORE -> podStore(bp, origin, r);
            case HULK -> hulk(bp, origin, r);
            default -> tideShrine(bp, origin, r);
        }
        return bp;
    }

    static void cache(Blueprint bp, BlockPos pos, String table, boolean pedestal) {
        bp.set(pos, (pedestal ? WFBlocks.SIGIL_PEDESTAL : WFBlocks.DRIFT_CACHE).get().defaultBlockState());
        ResourceKey<LootTable> key = SpireGenerator.loot(table);
        bp.action(pos, level -> {
            if (level.getBlockEntity(pos) instanceof PerPlayerLootBlockEntity v) {
                v.configure(key, false);
            }
        });
    }

    static void rock(Blueprint bp, BlockPos c, float radius, RandomSource r) {
        float size = radius;
        for (int y = 0; size > 0.6f; y--) {
            for (int dx = (int) -radius - 1; dx <= radius + 1; dx++) {
                for (int dz = (int) -radius - 1; dz <= radius + 1; dz++) {
                    if (dx * dx + dz * dz <= size * size) {
                        bp.set(c.offset(dx, y, dz), r.nextInt(5) == 0 ? Blocks.END_STONE.defaultBlockState() : WFBlocks.DRIFTSTONE.get().defaultBlockState());
                    }
                }
            }
            size -= 0.8f + r.nextFloat() * 0.6f;
        }
    }

    private static void ladder(Blueprint bp, BlockPos o, RandomSource r) {
        int h = 12 + r.nextInt(7);
        for (int y = 0; y < h; y++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    boolean wall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                    BlockPos p = o.offset(dx, y, dz);
                    if (wall) {
                        if (r.nextInt(7) == 0 || (y > h - 4 && r.nextInt(2) == 0)) {
                            continue;
                        }
                        bp.set(p, (Math.abs(dx) == 3 && Math.abs(dz) == 3) ? Blocks.PURPUR_PILLAR.defaultBlockState()
                            : r.nextInt(3) == 0 ? WFBlocks.WEATHERED_PURPUR.get().defaultBlockState() : Blocks.PURPUR_BLOCK.defaultBlockState());
                    } else if (y % 6 == 0) {
                        bp.set(p, Blocks.PURPUR_BLOCK.defaultBlockState());
                    }
                }
            }
        }
        bp.set(o.offset(0, 6, 0), Blocks.AIR.defaultBlockState());
        cache(bp, o.offset(1, 1, 1), "vault/driftwreck_ladder", false);
        bp.set(o.offset(-2, 5, -2), WFBlocks.SPIRE_LANTERN.get().defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    private static void podStore(Blueprint bp, BlockPos o, RandomSource r) {
        rock(bp, o, 5 + r.nextInt(2), r);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 1; dy <= 4; dy++) {
                    boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == 4;
                    BlockPos p = o.offset(dx, dy, dz);
                    bp.set(p, shell ? (dy == 4 ? Blocks.PURPUR_SLAB.defaultBlockState() : Blocks.PURPUR_BLOCK.defaultBlockState())
                        : Blocks.AIR.defaultBlockState());
                }
            }
        }
        bp.set(o.offset(0, 1, 2), Blocks.AIR.defaultBlockState());
        bp.set(o.offset(0, 2, 2), Blocks.AIR.defaultBlockState());
        bp.set(o.offset(-1, 1, -1), Blocks.BARREL.defaultBlockState());
        bp.set(o.offset(1, 1, -1), Blocks.BARREL.defaultBlockState());
        cache(bp, o.offset(0, 1, 0), "vault/driftwreck_pod_store", false);
    }

    private static void tideShrine(Blueprint bp, BlockPos o, RandomSource r) {
        rock(bp, o, 6, r);
        // wave mural floor: concentric rings of lumen and stillstone
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= 5) {
                    BlockState s = ((int) Math.round(d + Math.sin(Math.atan2(dz, dx) * 3) * 0.8)) % 2 == 0
                        ? WFBlocks.POLISHED_STILLSTONE.get().defaultBlockState() : WFBlocks.LUMEN_CRYSTAL_BLOCK.get().defaultBlockState();
                    bp.set(o.offset(dx, 0, dz), s);
                }
                if (d > 4.2 && d <= 5 && (dx + dz) % 3 == 0) {
                    bp.set(o.offset(dx, 1, dz), Blocks.PURPUR_PILLAR.defaultBlockState());
                    bp.set(o.offset(dx, 2, dz), Blocks.PURPUR_PILLAR.defaultBlockState());
                    bp.set(o.offset(dx, 3, dz), Blocks.PURPUR_SLAB.defaultBlockState());
                }
            }
        }
        cache(bp, o.above(), "vault/tide_sigil", true);
        cache(bp, o.offset(3, 1, 0), "vault/driftwreck_shrine", false);
    }

    private static void hulk(Blueprint bp, BlockPos o, RandomSource r) {
        int clusters = 5 + r.nextInt(4);
        for (int i = 0; i < clusters; i++) {
            BlockPos c = o.offset((int) (r.nextGaussian() * 5), (int) (r.nextGaussian() * 2), (int) (r.nextGaussian() * 5));
            rock(bp, c, 1.5f + r.nextFloat() * 2, r);
            if (r.nextInt(3) == 0) {
                bp.set(c.above(), Blocks.PURPUR_BLOCK.defaultBlockState());
            }
        }
        cache(bp, o.above(), "vault/driftwreck_hulk", false);
        cache(bp, o.offset(4, 1, -3), "vault/driftwreck_hulk", false);
        if (r.nextBoolean()) {
            cache(bp, o.offset(-4, 1, 3), "vault/driftwreck_hulk", false);
        }
    }
}
