package dev.worldsfall.world.sundering;

import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.world.BlockQueue;
import dev.worldsfall.block.HangingCrystalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the Descent and the Undercroft (§11.4) into a {@link BlockQueue}: a 7×7 shaft with a spiral stair
 * down to Y≈25, the Hall of Murals, the Rift Chamber with the Fracture Rift Frame, the Still Throne, two side
 * Root Hollows, and Underside growths beneath the main island.
 */
public final class UndercroftBuilder {
    public static final int FLOOR_Y = 24;
    public static final int HALL_LENGTH = 24;
    public static final int CHAMBER_RADIUS = 7;

    public record Layout(BlockPos descentTop, Direction inward, BlockPos hallStart, BlockPos chamberCenter, BlockPos riftFrame, BlockPos throne,
                         List<BlockPos> lurkerSpots) {}

    private UndercroftBuilder() {}

    public static Layout layout(BlockPos descentTop) {
        Direction inward = Math.abs(descentTop.getX()) >= Math.abs(descentTop.getZ())
            ? (descentTop.getX() > 0 ? Direction.WEST : Direction.EAST)
            : (descentTop.getZ() > 0 ? Direction.NORTH : Direction.SOUTH);
        BlockPos bottom = new BlockPos(descentTop.getX(), FLOOR_Y, descentTop.getZ());
        BlockPos hallStart = bottom.relative(inward, 4);
        BlockPos chamberCenter = hallStart.relative(inward, HALL_LENGTH + CHAMBER_RADIUS);
        BlockPos throne = chamberCenter.relative(inward, CHAMBER_RADIUS - 1).above();
        Direction side = inward.getClockWise();
        List<BlockPos> lurkers = List.of(hallStart.relative(inward, 10).relative(side, 14).below(5),
            hallStart.relative(inward, 16).relative(side.getOpposite(), 14).below(5));
        return new Layout(descentTop, inward, hallStart, chamberCenter, chamberCenter, throne, lurkers);
    }

    public static void build(ServerLevel level, Layout l, BlockQueue q) {
        RandomSource rand = RandomSource.create(l.descentTop.asLong());
        buildShaft(level, l, q);
        buildHall(level, l, q, rand);
        buildChamber(l, q, rand);
        buildRootHollows(l, q, rand);
        decorateUnderside(level, l, q, rand);
    }

    private static BlockState undershale() {
        return WFBlocks.UNDERSHALE.get().defaultBlockState();
    }

    private static void wallOrKeep(BlockQueue q, BlockPos p, BlockState wall) {
        q.set(p, wall);
    }

    // ------------------------------------------------------------ shaft
    private static void buildShaft(ServerLevel level, Layout l, BlockQueue q) {
        BlockPos top = l.descentTop;
        List<BlockPos> ring = perimeter(top.getX(), top.getZ());
        int levelIndex = 0;
        for (int y = top.getY(); y > FLOOR_Y; y--) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    q.set(new BlockPos(top.getX() + dx, y, top.getZ() + dz), Blocks.AIR.defaultBlockState());
                }
            }
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (Math.abs(dx) == 4 || Math.abs(dz) == 4) {
                        BlockPos w = new BlockPos(top.getX() + dx, y, top.getZ() + dz);
                        BlockState wall = y < top.getY() - 6 ? ((top.getY() - y) % 7 == 0 ? Blocks.PURPUR_BLOCK.defaultBlockState() : undershale())
                            : Blocks.END_STONE_BRICKS.defaultBlockState();
                        wallOrKeep(q, w, wall);
                    }
                }
            }
            // helical stair: one step per level along the 24-cell inner perimeter
            int i = levelIndex % ring.size();
            BlockPos step = ring.get(i).atY(y);
            BlockPos prev = ring.get((i + ring.size() - 1) % ring.size()).atY(y);
            Direction up = directionBetween(step, prev);
            q.set(step, Blocks.PURPUR_STAIRS.defaultBlockState().setValue(StairBlock.FACING, up).setValue(StairBlock.HALF, Half.BOTTOM));
            BlockPos next = ring.get((i + 1) % ring.size()).atY(y);
            q.set(next, Blocks.END_STONE_BRICK_SLAB.defaultBlockState());
            if ((top.getY() - y) % 6 == 3) {
                q.set(new BlockPos(top.getX(), y, top.getZ()), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 11));
            }
            levelIndex++;
        }
        // shaft floor
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                q.set(new BlockPos(top.getX() + dx, FLOOR_Y, top.getZ() + dz), WFBlocks.UNDERSHALE_TILES.get().defaultBlockState());
            }
        }
    }

    private static Direction directionBetween(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** The 24 cells around the 7×7 shaft's inner edge, clockwise. */
    static List<BlockPos> perimeter(int cx, int cz) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = -3; x <= 3; x++) out.add(new BlockPos(cx + x, 0, cz - 3));
        for (int z = -2; z <= 3; z++) out.add(new BlockPos(cx + 3, 0, cz + z));
        for (int x = 2; x >= -3; x--) out.add(new BlockPos(cx + x, 0, cz + 3));
        for (int z = 2; z >= -2; z--) out.add(new BlockPos(cx - 3, 0, cz + z));
        return out;
    }

    // ------------------------------------------------------------ hall of murals
    private static void buildHall(ServerLevel level, Layout l, BlockQueue q, RandomSource rand) {
        Direction dir = l.inward;
        Direction side = dir.getClockWise();
        for (int i = -4; i <= HALL_LENGTH; i++) {
            for (int w = -4; w <= 4; w++) {
                for (int h = -1; h <= 5; h++) {
                    BlockPos p = l.hallStart.relative(dir, i).relative(side, w).above(h);
                    boolean shell = Math.abs(w) == 4 || h == -1 || h == 5;
                    if (i < 0 && Math.abs(w) <= 3 && h >= 0 && h <= 4) {
                        q.set(p, Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    if (shell) {
                        BlockState s = h == -1 ? WFBlocks.UNDERSHALE_TILES.get().defaultBlockState()
                            : h == 5 ? WFBlocks.POLISHED_UNDERSHALE.get().defaultBlockState()
                            : (h == 0 || h == 4) ? Blocks.PURPUR_PILLAR.defaultBlockState() : undershale();
                        q.set(p, s);
                    } else {
                        q.set(p, Blocks.AIR.defaultBlockState());
                    }
                }
            }
            if (i >= 0 && i % 4 == 2) {
                // hanging lumen crystals light the hall teal (§11.4 look)
                q.set(l.hallStart.relative(dir, i).relative(side, -2).above(4), WFBlocks.LUMEN_CRYSTAL.get().defaultBlockState()
                    .setValue(HangingCrystalBlock.PART, HangingCrystalBlock.Part.TIP));
                q.set(l.hallStart.relative(dir, i).relative(side, 2).above(4), WFBlocks.LUMEN_CRYSTAL.get().defaultBlockState()
                    .setValue(HangingCrystalBlock.PART, HangingCrystalBlock.Part.TIP));
            }
        }
        // six mural panels, three per wall
        for (int m = 0; m < 6; m++) {
            int along = 3 + (m / 2) * 7;
            int wallSide = m % 2 == 0 ? 4 : -4;
            Murals.place(q, l.hallStart.relative(dir, along).relative(side, wallSide).above(1), dir, m);
        }
    }

    // ------------------------------------------------------------ rift chamber
    private static void buildChamber(Layout l, BlockQueue q, RandomSource rand) {
        BlockPos c = l.chamberCenter;
        int r = CHAMBER_RADIUS;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                for (int h = -1; h <= 8; h++) {
                    BlockPos p = c.offset(dx, h, dz);
                    double dome = r - Math.max(0, h - 4) * 1.2;
                    if (d <= dome - 0.5 && h >= 0 && h <= 7) {
                        q.set(p, Blocks.AIR.defaultBlockState());
                    } else if (d <= r + 1.2) {
                        BlockState s = h == -1 ? (d < 2.5 ? WFBlocks.POLISHED_STILLSTONE.get().defaultBlockState()
                            : (rand.nextInt(7) == 0 && d < 5 ? WFBlocks.FRACTURE_GLASS.get().defaultBlockState() : WFBlocks.UNDERSHALE_TILES.get().defaultBlockState()))
                            : h == 0 ? Blocks.PURPUR_BLOCK.defaultBlockState() : undershale();
                        q.set(p, s);
                    }
                }
            }
        }
        // the Rift frame at the floor center with a Null Obsidian ring around the 3×3 rift surface
        q.set(c.below(), WFBlocks.NULL_OBSIDIAN.get().defaultBlockState());
        q.set(c, WFBlocks.FRACTURE_RIFT_FRAME.get().defaultBlockState());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    q.set(c.offset(dx, 1, dz), WFBlocks.NULL_OBSIDIAN.get().defaultBlockState());
                } else if (dx != 0 || dz != 0) {
                    q.set(c.offset(dx, 0, dz), WFBlocks.NULL_OBSIDIAN.get().defaultBlockState());
                }
            }
        }
        // three sigil plinths
        for (int k = 0; k < 3; k++) {
            double a = k * Math.PI * 2 / 3 + Math.PI / 2;
            BlockPos p = c.offset((int) Math.round(Math.cos(a) * 5), 0, (int) Math.round(Math.sin(a) * 5));
            q.set(p, Blocks.PURPUR_PILLAR.defaultBlockState());
            q.set(p.above(), WFBlocks.LUMEN_CRYSTAL_BLOCK.get().defaultBlockState());
        }
        // Still Throne on a dais in the inward ("north") wall
        q.set(l.throne.below(), WFBlocks.CHISELED_STILLSTONE.get().defaultBlockState());
        q.set(l.throne, WFBlocks.STILL_THRONE.get().defaultBlockState());
        q.set(l.throne.above(3), WFBlocks.SPIRE_LANTERN.get().defaultBlockState().setValue(LanternBlock.HANGING, true));
        // crystals in the dome
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3;
            q.set(c.offset((int) (Math.cos(a) * 4), 7, (int) (Math.sin(a) * 4)), WFBlocks.LUMEN_CLUSTER.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, Direction.DOWN));
        }
    }

    // ------------------------------------------------------------ root hollows (side passages into the Underside)
    private static void buildRootHollows(Layout l, BlockQueue q, RandomSource rand) {
        Direction dir = l.inward;
        for (int s = -1; s <= 1; s += 2) {
            Direction side = s > 0 ? dir.getClockWise() : dir.getCounterClockWise();
            BlockPos start = l.hallStart.relative(dir, s > 0 ? 10 : 16).relative(side, 4);
            for (int i = 0; i < 12; i++) {
                BlockPos base = start.relative(side, i).below(i / 2);
                for (int w = -1; w <= 1; w++) {
                    for (int h = 0; h <= 2; h++) {
                        q.set(base.relative(dir, w).above(h), Blocks.AIR.defaultBlockState());
                    }
                    q.set(base.relative(dir, w).below(), undershale());
                    q.set(base.relative(dir, w).above(3), WFBlocks.UNDERSHALE.get().defaultBlockState());
                }
                if (i % 3 == 1) {
                    q.set(base.relative(dir, rand.nextBoolean() ? 1 : -1).above(2), WFBlocks.CEILING_MOSS.get().defaultBlockState());
                }
                if (i == 3) {
                    q.set(base.above(2), WFBlocks.SPIRE_LANTERN.get().defaultBlockState().setValue(LanternBlock.HANGING, true));
                }
            }
            // the passage opens below the island body
            BlockPos end = start.relative(side, 12).below(6);
            for (int dy = 0; dy < 10; dy++) {
                for (int w = -1; w <= 1; w++) {
                    q.set(end.relative(dir, w).below(dy), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    // ------------------------------------------------------------ Underside growth beneath the main island
    private static void decorateUnderside(ServerLevel level, Layout l, BlockQueue q, RandomSource rand) {
        int cx = l.descentTop.getX(), cz = l.descentTop.getZ();
        for (int dx = -60; dx <= 60; dx += 1) {
            for (int dz = -60; dz <= 60; dz += 1) {
                if (dx * dx + dz * dz > 3600 || rand.nextInt(4) != 0) {
                    continue;
                }
                int x = cx + dx, z = cz + dz;
                BlockPos bottom = lowestSolid(level, x, z);
                if (bottom == null) {
                    continue;
                }
                BlockPos hang = bottom.below();
                int roll = rand.nextInt(100);
                if (roll < 7) {
                    int len = 2 + rand.nextInt(6);
                    for (int k = 0; k < len; k++) {
                        HangingCrystalBlock.Part part = k == len - 1 ? HangingCrystalBlock.Part.TIP : k == 0 ? HangingCrystalBlock.Part.BASE : HangingCrystalBlock.Part.MIDDLE;
                        q.fill(hang.below(k), WFBlocks.LUMEN_CRYSTAL.get().defaultBlockState().setValue(HangingCrystalBlock.PART, part));
                    }
                } else if (roll < 13) {
                    int len = 3 + rand.nextInt(7);
                    for (int k = 0; k < len; k++) {
                        HangingCrystalBlock.Part part = k == len - 1 ? HangingCrystalBlock.Part.TIP : k == 0 ? HangingCrystalBlock.Part.BASE : HangingCrystalBlock.Part.MIDDLE;
                        q.fill(hang.below(k), WFBlocks.VOIDROOT.get().defaultBlockState().setValue(HangingCrystalBlock.PART, part));
                    }
                } else if (roll < 16) {
                    q.fill(hang, WFBlocks.GLOWBULB_BLOCK.get().defaultBlockState());
                } else if (roll < 34) {
                    q.fill(hang, WFBlocks.CEILING_MOSS.get().defaultBlockState());
                } else if (roll < 40) {
                    q.set(bottom, undershale());
                }
            }
        }
    }

    private static BlockPos lowestSolid(ServerLevel level, int x, int z) {
        for (int y = level.getMinY() + 1; y < 70; y++) {
            BlockPos p = new BlockPos(x, y, z);
            if (!level.getBlockState(p).isAir()) {
                return level.getBlockState(p).is(Blocks.END_STONE) ? p : null;
            }
        }
        return null;
    }
}
