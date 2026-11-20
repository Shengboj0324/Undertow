package dev.worldsfall.world.gen.structure;

import dev.worldsfall.entity.Hoarder;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Hoard (§4.4, §11.2): a small pocket island covered in clumsy piles of stolen Overworld blocks, a single
 * leafy tree, a Hoard Cache and 2–4 Hoarders. Grand Hoards (1 in 15) carry a crude replica (well, hut, patch).
 * Never diamond or emerald.
 */
public final class HoardGenerator {
    public static final int NORMAL = 0, GRAND = 1;
    private static final BlockState[] PILE = {
        Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(),
        Blocks.DIRT.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
        Blocks.SAND.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), Blocks.BIRCH_LOG.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState(),
        Blocks.COAL_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(), Blocks.COPPER_ORE.defaultBlockState(),
        Blocks.GRAVEL.defaultBlockState(), Blocks.PUMPKIN.defaultBlockState()};
    private static final BlockState[] RARE = {Blocks.GOLD_ORE.defaultBlockState(), Blocks.LAPIS_ORE.defaultBlockState()};
    private static final BlockState[] FLOWERS = {Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(),
        Blocks.CORNFLOWER.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.RED_TULIP.defaultBlockState(),
        Blocks.SHORT_GRASS.defaultBlockState(), Blocks.OAK_SAPLING.defaultBlockState()};

    private HoardGenerator() {}

    public static Blueprint build(BlockPos origin, int variant, RandomSource r) {
        Blueprint bp = new Blueprint();
        boolean grand = variant == GRAND;
        float radius = grand ? 11 + r.nextInt(5) : 5 + r.nextInt(5);
        // pocket island, flattish top
        float size = radius;
        for (int y = 0; size > 0.6f; y--) {
            for (int dx = (int) -radius - 1; dx <= radius + 1; dx++) {
                for (int dz = (int) -radius - 1; dz <= radius + 1; dz++) {
                    double wob = EndRegionsAccess.noise(origin.getX() + dx, origin.getZ() + dz) * 1.4;
                    if (dx * dx + dz * dz <= (size + wob) * (size + wob)) {
                        bp.set(origin.offset(dx, y, dz), Blocks.END_STONE.defaultBlockState());
                    }
                }
            }
            size -= y == 0 ? 0.6f : 0.9f + r.nextFloat() * 0.7f;
        }
        BlockPos cache = origin.above();
        bp.set(cache, WFBlocks.HOARD_CACHE.get().defaultBlockState()
            .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(r)));
        // clumsy piles 1–3 high
        int piles = (int) (radius * radius * 0.55);
        for (int i = 0; i < piles; i++) {
            int dx = (int) (r.nextGaussian() * radius * 0.45), dz = (int) (r.nextGaussian() * radius * 0.45);
            if (dx * dx + dz * dz > (radius - 1) * (radius - 1) || (Math.abs(dx) <= 1 && Math.abs(dz) <= 1)) {
                continue;
            }
            int h = 1 + (r.nextInt(5) == 0 ? 2 : r.nextInt(2));
            for (int y = 1; y <= h; y++) {
                BlockState s = r.nextInt(60) == 0 ? RARE[r.nextInt(RARE.length)] : PILE[r.nextInt(PILE.length)];
                bp.set(origin.offset(dx, y, dz), s);
            }
            if (r.nextInt(3) == 0) {
                BlockPos top = origin.offset(dx, h + 1, dz);
                BlockState below = bp.get(top.below());
                if (below != null && (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT) || below.is(Blocks.MOSS_BLOCK))) {
                    bp.set(top, FLOWERS[r.nextInt(FLOWERS.length)]);
                }
            }
        }
        // "arrangement": a ring of blocks around the cache
        if (r.nextBoolean()) {
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                bp.setIfAbsent(origin.offset((int) Math.round(Math.cos(a) * 3), 1, (int) Math.round(Math.sin(a) * 3)), PILE[r.nextInt(4)]);
            }
        }
        // one small leafy tree
        BlockPos treeBase = origin.offset((int) (radius * 0.4) * (r.nextBoolean() ? 1 : -1), 1, (int) (radius * 0.3) * (r.nextBoolean() ? 1 : -1));
        tree(bp, treeBase, r.nextBoolean() ? Blocks.OAK_LOG.defaultBlockState() : Blocks.BIRCH_LOG.defaultBlockState(),
            (r.nextBoolean() ? Blocks.OAK_LEAVES : Blocks.BIRCH_LEAVES).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), r);
        if (grand) {
            replica(bp, origin, radius, r);
        }
        int hoarders = 2 + r.nextInt(3);
        bp.action(cache, level -> {
            for (int i = 0; i < hoarders; i++) {
                Hoarder h = WFEntities.HOARDER.get().create(level.getLevel(), EntitySpawnReason.STRUCTURE);
                if (h != null) {
                    h.snapTo(cache.getX() + 0.5 + (i % 2 == 0 ? 2 : -2), cache.getY() + 2, cache.getZ() + 0.5 + (i < 2 ? 2 : -2), 0, 0);
                    h.setHome(cache);
                    h.setPersistenceRequired();
                    level.addFreshEntityWithPassengers(h);
                }
            }
        });
        return bp;
    }

    private static void tree(Blueprint bp, BlockPos base, BlockState log, BlockState leaves, RandomSource r) {
        int h = 4 + r.nextInt(2);
        bp.set(base.below(), Blocks.DIRT.defaultBlockState());
        for (int y = 0; y < h; y++) {
            bp.set(base.above(y), log);
        }
        for (int dy = h - 2; dy <= h + 1; dy++) {
            int rad = dy >= h ? 1 : 2;
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (Math.abs(dx) == rad && Math.abs(dz) == rad && r.nextBoolean()) {
                        continue;
                    }
                    bp.setIfAbsent(base.offset(dx, dy, dz), leaves);
                }
            }
        }
    }

    /** A crude replica of an Overworld structure (§11.2). */
    private static void replica(Blueprint bp, BlockPos origin, float radius, RandomSource r) {
        BlockPos c = origin.offset((int) -(radius * 0.45), 1, (int) (radius * 0.45));
        switch (r.nextInt(3)) {
            case 0 -> { // a well
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        bp.set(c.offset(dx, 0, dz), dx == 0 && dz == 0 ? Blocks.WATER.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                    }
                }
                for (int[] p : new int[][] {{-1, -1}, {1, 1}}) {
                    bp.set(c.offset(p[0], 1, p[1]), Blocks.OAK_FENCE.defaultBlockState());
                    bp.set(c.offset(p[0], 2, p[1]), Blocks.OAK_FENCE.defaultBlockState());
                }
                bp.set(c.above(3), Blocks.OAK_SLAB.defaultBlockState());
            }
            case 1 -> { // a lopsided hut
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dy = 0; dy <= 2; dy++) {
                            boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                            if (wall && !(dx == 0 && dz == 2 && dy < 2)) {
                                bp.set(c.offset(dx, dy, dz), r.nextInt(5) == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
                            }
                        }
                        bp.set(c.offset(dx, 3, dz), Blocks.OAK_SLAB.defaultBlockState());
                    }
                }
            }
            default -> { // a pumpkin patch
                for (int i = 0; i < 9; i++) {
                    BlockPos p = c.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2);
                    bp.set(p.below(), Blocks.FARMLAND.defaultBlockState());
                    bp.set(p, r.nextBoolean() ? Blocks.PUMPKIN.defaultBlockState() : Blocks.CARVED_PUMPKIN.defaultBlockState());
                }
            }
        }
    }

    /** Tiny bridge to the region noise without exposing worldgen internals. */
    static final class EndRegionsAccess {
        static double noise(int x, int z) {
            return dev.worldsfall.world.gen.EndRegions.valueNoise(x / 3.0, z / 3.0, 77);
        }
    }
}
