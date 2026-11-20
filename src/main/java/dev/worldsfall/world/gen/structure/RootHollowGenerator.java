package dev.worldsfall.world.gen.structure;

import dev.worldsfall.block.HangingCrystalBlock;
import dev.worldsfall.entity.UndersideLurker;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Root Hollows (§11.5): caves cut up into the Underside ceiling, reached by climbing Voidroots, with a chest.
 * Deep Root Hollows (Deep Rim) chain three chambers to the Heartroot Chamber: a giant glowing root knot holding
 * the Under Sigil, guarded by Lurkers and Echoes.
 */
public final class RootHollowGenerator {
    public static final int NORMAL = 0, DEEP = 1;

    private RootHollowGenerator() {}

    public static Blueprint build(BlockPos bottom, int variant, RandomSource r) {
        Blueprint bp = new Blueprint();
        if (variant == DEEP) {
            BlockPos a = chamber(bp, bottom, 4, r);
            BlockPos b = chamber(bp, a.offset(7 + r.nextInt(3), 2, r.nextInt(5) - 2), 4, r);
            BlockPos heart = chamber(bp, b.offset(r.nextInt(5) - 2, 2, 8 + r.nextInt(3)), 6, r);
            tunnel(bp, a, b);
            tunnel(bp, b, heart);
            heartroot(bp, heart, r);
            lurkers(bp, heart, 4 + r.nextInt(3), r);
            bp.action(a, level -> SpireGenerator.spawnEcho(level, a.above(), WFBlocks.UNDERSHALE.get().defaultBlockState()));
        } else {
            BlockPos c = chamber(bp, bottom, 3 + r.nextInt(2), r);
            SpireGenerator.chest(bp, c.above(), SpireGenerator.loot("chests/root_hollow"), r);
            lurkers(bp, c, 1, r);
        }
        // voidroots hang from the hollow's mouth so it can be climbed into
        for (int i = 0; i < 4; i++) {
            BlockPos top = bottom.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2);
            int len = 6 + r.nextInt(6);
            for (int k = 0; k < len; k++) {
                HangingCrystalBlock.Part part = k == len - 1 ? HangingCrystalBlock.Part.TIP : HangingCrystalBlock.Part.MIDDLE;
                bp.setIfAbsent(top.below(k + 1), WFBlocks.VOIDROOT.get().defaultBlockState().setValue(HangingCrystalBlock.PART, part));
            }
        }
        return bp;
    }

    /** A dome-shaped cave carved upward into the island body; returns its floor center. */
    private static BlockPos chamber(Blueprint bp, BlockPos mouth, int radius, RandomSource r) {
        BlockPos floor = mouth.above(2);
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                for (int dy = -1; dy <= radius + 2; dy++) {
                    double d = Math.sqrt(dx * dx + dz * dz + Math.pow(Math.max(0, dy - 1) * 0.9, 2));
                    BlockPos p = floor.offset(dx, dy, dz);
                    if (d <= radius && dy >= 0) {
                        bp.set(p, Blocks.AIR.defaultBlockState());
                    } else if (d <= radius + 1.3) {
                        bp.setIfAbsent(p, r.nextInt(4) == 0 ? WFBlocks.POLISHED_UNDERSHALE.get().defaultBlockState() : WFBlocks.UNDERSHALE.get().defaultBlockState());
                    }
                }
            }
        }
        // the mouth: an opening down through the floor
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -3; dy <= 0; dy++) {
                    bp.set(floor.offset(dx, dy - 1, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int i = 0; i < radius + 2; i++) {
            BlockPos p = floor.offset(r.nextInt(radius * 2 + 1) - radius, radius + 1, r.nextInt(radius * 2 + 1) - radius);
            bp.set(p, WFBlocks.LUMEN_CLUSTER.get().defaultBlockState().setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING,
                net.minecraft.core.Direction.DOWN));
        }
        for (int i = 0; i < radius; i++) {
            bp.setIfAbsent(floor.offset(r.nextInt(radius * 2 + 1) - radius, radius, r.nextInt(radius * 2 + 1) - radius),
                WFBlocks.CEILING_MOSS.get().defaultBlockState());
        }
        return floor;
    }

    private static void tunnel(Blueprint bp, BlockPos a, BlockPos b) {
        int steps = (int) Math.ceil(Math.sqrt(a.distSqr(b)));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            BlockPos c = BlockPos.containing(a.getX() + (b.getX() - a.getX()) * t, a.getY() + 1 + (b.getY() - a.getY()) * t, a.getZ() + (b.getZ() - a.getZ()) * t);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        bp.set(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    /** The Heartroot: a giant knot of glowing roots holding the Under Sigil (§11.5). */
    private static void heartroot(Blueprint bp, BlockPos floor, RandomSource r) {
        BlockState root = WFBlocks.VOIDROOT.get().defaultBlockState().setValue(HangingCrystalBlock.PART, HangingCrystalBlock.Part.MIDDLE);
        for (int i = 0; i < 26; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double tilt = r.nextDouble() * 0.9;
            for (int k = 0; k < 6; k++) {
                BlockPos p = floor.offset((int) Math.round(Math.cos(a) * k * 0.8), 6 - (int) Math.round(k * (1 - tilt)), (int) Math.round(Math.sin(a) * k * 0.8));
                bp.set(p, k % 3 == 0 ? WFBlocks.LUMEN_CRYSTAL_BLOCK.get().defaultBlockState() : root);
            }
        }
        bp.set(floor.above(), Blocks.AIR.defaultBlockState());
        bp.set(floor, WFBlocks.CHISELED_STILLSTONE.get().defaultBlockState());
        DriftwreckGenerator.cache(bp, floor.above(), "vault/under_sigil", true);
        SpireGenerator.chest(bp, floor.offset(3, 1, 0), SpireGenerator.loot("chests/root_hollow"), r);
    }

    private static void lurkers(Blueprint bp, BlockPos floor, int n, RandomSource r) {
        bp.action(floor, level -> {
            for (int i = 0; i < n; i++) {
                UndersideLurker l = WFEntities.UNDERSIDE_LURKER.get().create(level.getLevel(), EntitySpawnReason.STRUCTURE);
                if (l != null) {
                    l.snapTo(floor.getX() + 0.5 + r.nextGaussian() * 2, floor.getY() + 2, floor.getZ() + 0.5 + r.nextGaussian() * 2, 0, 0);
                    l.attachToCeiling(level);
                    l.setPersistenceRequired();
                    level.addFreshEntityWithPassengers(l);
                }
            }
        });
    }
}
