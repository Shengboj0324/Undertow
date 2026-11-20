package dev.worldsfall.world.gen.structure;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.block.entity.KeystoneSocketBlockEntity;
import dev.worldsfall.block.entity.PerPlayerLootBlockEntity;
import dev.worldsfall.entity.Echo;
import dev.worldsfall.entity.SpireWarden;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFFluids;
import dev.worldsfall.world.reassembly.ReassemblyPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootTable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fallen Spire complexes (§11.1): Leaning (50%), Shattered (35%), Drowned (15%) towers of the Ascendancy,
 * with rubble fans, magenta lantern posts, loose blocks drifting into the sky, a half-buried sealed vault room
 * restored by Reassembly, a Keystone Socket guarded by Spire Wardens, and Echoes hiding in the rubble.
 */
public final class SpireGenerator {
    public static final int LEANING = 0, SHATTERED = 1, DROWNED = 2;
    public static final int DEEP_FLAG = 8;
    private static final int HALF = 4;

    private SpireGenerator() {}

    static ResourceKey<LootTable> loot(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(Worldsfall.MODID, path));
    }

    public static Blueprint build(BlockPos origin, int variantFlags, RandomSource r) {
        int variant = variantFlags & 7;
        boolean deep = (variantFlags & DEEP_FLAG) != 0;
        Blueprint bp = new Blueprint();
        Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(r);
        int height = 26 + r.nextInt(13);
        int sunk = 3 + r.nextInt(4);
        double angle = switch (variant) {
            case SHATTERED -> Math.toRadians(84 + r.nextInt(5));
            default -> Math.toRadians(10 + r.nextInt(16));
        };
        if (variant == DROWNED) {
            basin(bp, origin, r);
        }
        BlockPos towerOrigin = origin;
        if (variant == SHATTERED) {
            // standing stub of the base
            tower(bp, origin, Direction.UP, 0, 5 + r.nextInt(4), sunk, r, false, height);
            towerOrigin = origin.relative(lean, 3).above(1);
            tower(bp, towerOrigin, lean, angle, height - 8, 0, r, true, height);
        } else {
            tower(bp, origin, lean, angle, height, sunk, r, false, height);
        }
        rooms(bp, towerOrigin, lean, variant == SHATTERED ? angle : angle, r, variant);
        if (variant == DROWNED) {
            flood(bp, origin);
        }
        rubbleFan(bp, origin, lean, r);
        lanternPosts(bp, origin, r);
        driftingBlocks(bp, towerOrigin, lean, angle, height, r);
        vaultComplex(bp, origin, lean.getOpposite(), r, false);
        if (deep) {
            vaultComplex(bp, origin, lean.getClockWise(), r, true);
        }
        return bp;
    }

    // ------------------------------------------------------------ tower body
    /** Rotation that tilts local +Y toward {@code lean} by {@code angle}. */
    static Matrix3d tilt(Direction lean, double angle) {
        Matrix3d m = new Matrix3d();
        if (lean == Direction.UP || angle == 0) {
            return m.identity();
        }
        // axis perpendicular to lean in the horizontal plane
        Direction axis = lean.getClockWise();
        Vector3d ax = new Vector3d(axis.getStepX(), 0, axis.getStepZ());
        m.rotation(-angle, ax);
        // ensure +Y tips toward lean
        Vector3d test = m.transform(new Vector3d(0, 1, 0));
        if (test.x * lean.getStepX() + test.z * lean.getStepZ() < 0) {
            m.rotation(angle, ax);
        }
        return m;
    }

    private static void tower(Blueprint bp, BlockPos base, Direction lean, double angle, int height, int sunk, RandomSource r,
                              boolean fallen, int fullHeight) {
        Matrix3d m = tilt(lean, angle);
        Matrix3d inv = new Matrix3d(m).invert();
        int[] jag = new int[(2 * HALF + 1) * (2 * HALF + 1)];
        for (int i = 0; i < jag.length; i++) {
            jag[i] = height - 2 - r.nextInt(8);
        }
        int reach = fullHeight + 6;
        boolean horizontalPillars = angle > Math.toRadians(45);
        for (int wx = -reach; wx <= reach; wx++) {
            for (int wy = -sunk - 2; wy <= reach; wy++) {
                for (int wz = -reach; wz <= reach; wz++) {
                    Vector3d local = inv.transform(new Vector3d(wx, wy, wz));
                    int lx = (int) Math.round(local.x), ly = (int) Math.round(local.y), lz = (int) Math.round(local.z);
                    if (Math.abs(lx) > HALF || Math.abs(lz) > HALF || ly < -sunk || ly >= height) {
                        continue;
                    }
                    if (ly > jag[(lx + HALF) * (2 * HALF + 1) + (lz + HALF)]) {
                        continue;
                    }
                    BlockState s = towerBlock(lx, ly, lz, r, horizontalPillars, lean);
                    BlockPos p = base.offset(wx, wy, wz);
                    if (s != null) {
                        bp.set(p, s);
                    } else if (!bp.has(p)) {
                        bp.set(p, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        if (fallen) {
            // open the rooms along the length: remove much of the upward-facing wall
            Vector3d up = new Vector3d(0, 1, 0);
            for (BlockPos p : new ArrayList<>(bp.blocks().keySet())) {
                Vector3d local = inv.transform(new Vector3d(p.getX() - base.getX(), p.getY() - base.getY(), p.getZ() - base.getZ()));
                Vector3d n = m.transform(new Vector3d(Math.signum(Math.round(local.x)) * (Math.abs(Math.round(local.x)) == HALF ? 1 : 0), 0,
                    Math.signum(Math.round(local.z)) * (Math.abs(Math.round(local.z)) == HALF ? 1 : 0)));
                if (n.dot(up) > 0.7 && r.nextInt(10) < 7 && Math.round(local.y) % 6 != 0) {
                    bp.set(p, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static BlockState towerBlock(int lx, int ly, int lz, RandomSource r, boolean horizontalPillars, Direction lean) {
        boolean wallX = Math.abs(lx) == HALF, wallZ = Math.abs(lz) == HALF;
        boolean corner = wallX && wallZ;
        boolean floor = ly >= 0 && ly % 6 == 0;
        if (!wallX && !wallZ) {
            if (floor) {
                if (lx == 0 && lz == 0 && ly > 0) {
                    return Blocks.AIR.defaultBlockState();
                }
                return r.nextInt(12) == 0 ? WFBlocks.PURPUR_RUBBLE.get().defaultBlockState()
                    : (r.nextInt(4) == 0 ? WFBlocks.WEATHERED_PURPUR.get().defaultBlockState() : Blocks.PURPUR_BLOCK.defaultBlockState());
            }
            return null;
        }
        if (corner) {
            BlockState pillar = r.nextInt(3) == 0 ? WFBlocks.CRACKED_PURPUR_PILLAR.get().defaultBlockState() : Blocks.PURPUR_PILLAR.defaultBlockState();
            return pillar.setValue(RotatedPillarBlock.AXIS, horizontalPillars ? lean.getAxis() : Direction.Axis.Y);
        }
        int along = wallX ? lz : lx;
        int inFloor = Math.floorMod(ly, 6);
        if (ly > 0 && (inFloor == 2 || inFloor == 3) && Math.abs(along) <= 1) {
            return Blocks.AIR.defaultBlockState(); // dark window
        }
        if (inFloor == 5) {
            return Blocks.END_STONE_BRICKS.defaultBlockState();
        }
        if (r.nextInt(16) == 0 && ly > 2) {
            return Blocks.AIR.defaultBlockState(); // crack
        }
        return r.nextInt(10) < 3 ? WFBlocks.WEATHERED_PURPUR.get().defaultBlockState() : Blocks.PURPUR_BLOCK.defaultBlockState();
    }

    // ------------------------------------------------------------ rooms
    private static BlockPos at(BlockPos base, Matrix3d m, int lx, int ly, int lz) {
        Vector3d w = m.transform(new Vector3d(lx, ly, lz));
        return base.offset((int) Math.round(w.x), (int) Math.round(w.y), (int) Math.round(w.z));
    }

    private static void rooms(Blueprint bp, BlockPos base, Direction lean, double angle, RandomSource r, int variant) {
        Matrix3d m = tilt(lean, angle);
        // Lantern Hall (floor 0): hanging lanterns and an out-of-place grass "block" (the first Echo, §17.1)
        for (int[] c : new int[][] {{-2, -2}, {2, 2}, {-2, 2}, {2, -2}}) {
            BlockPos p = at(base, m, c[0], 5, c[1]);
            bp.set(p, WFBlocks.SPIRE_LANTERN.get().defaultBlockState().setValue(LanternBlock.HANGING, variant != SHATTERED));
        }
        BlockPos echoPos = at(base, m, 1, 1, -2);
        bp.action(echoPos, level -> spawnEcho(level, echoPos, Blocks.GRASS_BLOCK.defaultBlockState()));
        // Library (floor 1)
        BlockPos lectern = at(base, m, 0, 7, 0);
        bp.set(lectern, Blocks.LECTERN.defaultBlockState());
        for (int lx = -3; lx <= 3; lx += 2) {
            bp.set(at(base, m, lx, 7, -3), Blocks.PURPUR_STAIRS.defaultBlockState());
            bp.set(at(base, m, lx, 8, -3), Blocks.PURPUR_SLAB.defaultBlockState());
        }
        BlockPos libChest = at(base, m, 3, 7, 3);
        chest(bp, libChest, loot("chests/spire_library"), r);
        // Quarters (floor 2): purpur-slab beds, shell bowls, an empty cradle
        bp.set(at(base, m, -2, 13, -2), Blocks.PURPUR_SLAB.defaultBlockState());
        bp.set(at(base, m, -2, 13, -1), Blocks.PURPUR_SLAB.defaultBlockState());
        bp.set(at(base, m, 2, 13, -2), Blocks.PURPUR_SLAB.defaultBlockState());
        bp.set(at(base, m, 2, 13, -1), Blocks.PURPUR_SLAB.defaultBlockState());
        bp.set(at(base, m, 0, 13, 2), Blocks.FLOWER_POT.defaultBlockState());
        bp.set(at(base, m, -1, 13, 3), Blocks.END_ROD.defaultBlockState());
        bp.set(at(base, m, 1, 13, 3), Blocks.END_ROD.defaultBlockState());
        BlockPos shulkerPos = at(base, m, 3, 13, 0);
        bp.action(shulkerPos, level -> {
            if (r.nextInt(2) == 0) {
                Shulker s = net.minecraft.world.entity.EntityTypes.SHULKER.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
                if (s != null) {
                    s.snapTo(shulkerPos.getX() + 0.5, shulkerPos.getY(), shulkerPos.getZ() + 0.5, 0, 0);
                    s.setPersistenceRequired();
                    level.addFreshEntityWithPassengers(s);
                }
            }
        });
        // Workshop (floor 3): smithing table and a chest (Gale/Still template 25%)
        bp.set(at(base, m, -2, 19, 2), Blocks.SMITHING_TABLE.defaultBlockState());
        chest(bp, at(base, m, 2, 19, 2), loot("chests/spire_workshop"), r);
        // unstable floors: rubble bridging the stair holes
        for (int f = 2; f <= 4; f++) {
            bp.set(at(base, m, 0, f * 6, 0), WFBlocks.PURPUR_RUBBLE.get().defaultBlockState());
        }
    }

    static void chest(Blueprint bp, BlockPos pos, ResourceKey<LootTable> table, RandomSource r) {
        bp.set(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(r)));
        long seed = r.nextLong();
        bp.action(pos, level -> {
            if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity c) {
                c.setLootTable(table, seed);
            }
        });
    }

    static void spawnEcho(WorldGenLevel level, BlockPos pos, BlockState disguise) {
        Echo echo = WFEntities.ECHO.get().create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (echo != null) {
            echo.setDisguise(disguise);
            echo.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            echo.setPersistenceRequired();
            level.addFreshEntityWithPassengers(echo);
        }
    }

    // ------------------------------------------------------------ surroundings
    private static void basin(Blueprint bp, BlockPos origin, RandomSource r) {
        int rad = 10 + r.nextInt(3);
        BlockState water = WFFluids.STILLWATER.get().defaultFluidState().createLegacyBlock();
        for (int dx = -rad - 1; dx <= rad + 1; dx++) {
            for (int dz = -rad - 1; dz <= rad + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= rad) {
                    int depth = d < rad - 2 ? 5 : 3;
                    for (int dy = 0; dy >= -depth; dy--) {
                        bp.set(origin.offset(dx, dy, dz), dy == 0 ? Blocks.AIR.defaultBlockState() : water);
                    }
                    bp.set(origin.offset(dx, -depth - 1, dz), WFBlocks.STILLSTONE.get().defaultBlockState());
                } else if (d <= rad + 1) {
                    bp.set(origin.offset(dx, 0, dz), r.nextBoolean() ? WFBlocks.STILLSTONE.get().defaultBlockState() : Blocks.END_STONE.defaultBlockState());
                }
            }
        }
    }

    private static void flood(Blueprint bp, BlockPos origin) {
        BlockState water = WFFluids.STILLWATER.get().defaultFluidState().createLegacyBlock();
        for (var e : new ArrayList<>(bp.blocks().entrySet())) {
            if (e.getValue().isAir() && e.getKey().getY() < origin.getY()) {
                bp.set(e.getKey(), water);
            }
        }
    }

    private static void rubbleFan(Blueprint bp, BlockPos origin, Direction lean, RandomSource r) {
        for (int i = 0; i < 70; i++) {
            double a = Math.atan2(lean.getStepZ(), lean.getStepX()) + r.nextGaussian() * 0.6;
            double d = 4 + Math.abs(r.nextGaussian()) * 12;
            BlockPos p = origin.offset((int) (Math.cos(a) * d), 1, (int) (Math.sin(a) * d));
            if (!bp.has(p)) {
                int roll = r.nextInt(10);
                bp.set(p, roll < 6 ? WFBlocks.PURPUR_RUBBLE.get().defaultBlockState()
                    : roll < 8 ? WFBlocks.WEATHERED_PURPUR.get().defaultBlockState()
                    : WFBlocks.CRACKED_PURPUR_PILLAR.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                        r.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z));
            }
        }
    }

    private static void lanternPosts(Blueprint bp, BlockPos origin, RandomSource r) {
        int posts = 3 + r.nextInt(3);
        for (int i = 0; i < posts; i++) {
            double a = i * Math.PI * 2 / posts + r.nextDouble() * 0.5;
            double d = 9 + r.nextInt(5);
            BlockPos base = origin.offset((int) (Math.cos(a) * d), 1, (int) (Math.sin(a) * d));
            int h = 2 + r.nextInt(2);
            for (int y = 0; y < h; y++) {
                bp.set(base.above(y), Blocks.END_STONE_BRICK_WALL.defaultBlockState());
            }
            bp.set(base.above(h), WFBlocks.SPIRE_LANTERN.get().defaultBlockState().setValue(LanternBlock.HANGING, false));
        }
    }

    /** Loose purpur drifting off into the sky — "as if the tower is still slowly coming apart". */
    private static void driftingBlocks(Blueprint bp, BlockPos base, Direction lean, double angle, int height, RandomSource r) {
        Matrix3d m = tilt(lean, angle);
        BlockPos top = at(base, m, 0, height - 4, 0);
        int n = 7 + r.nextInt(8);
        for (int i = 0; i < n; i++) {
            BlockPos p = top.offset((int) (r.nextGaussian() * 4) + lean.getStepX() * i, 3 + i + r.nextInt(3), (int) (r.nextGaussian() * 4) + lean.getStepZ() * i);
            bp.setIfAbsent(p, r.nextInt(3) == 0 ? WFBlocks.WEATHERED_PURPUR.get().defaultBlockState() : Blocks.PURPUR_BLOCK.defaultBlockState());
        }
    }

    // ------------------------------------------------------------ sealed vault + Reassembly plan
    private static void vaultComplex(Blueprint bp, BlockPos origin, Direction side, RandomSource r, boolean grand) {
        BlockPos center = origin.relative(side, grand ? 13 : 11).atY(origin.getY() - 2);
        int half = grand ? 4 : 3;
        int h = grand ? 6 : 5;
        List<ReassemblyPlan.Target> targets = new ArrayList<>();
        List<BlockPos> shell = new ArrayList<>();
        // clear interior
        for (int dx = -half + 1; dx <= half - 1; dx++) {
            for (int dz = -half + 1; dz <= half - 1; dz++) {
                for (int dy = 1; dy < h; dy++) {
                    bp.set(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
                bp.set(center.offset(dx, 0, dz), Blocks.END_STONE_BRICKS.defaultBlockState());
            }
        }
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                for (int dy = 0; dy <= h; dy++) {
                    boolean wall = Math.abs(dx) == half || Math.abs(dz) == half;
                    boolean roof = dy == h;
                    if (!wall && !roof) {
                        continue;
                    }
                    BlockPos p = center.offset(dx, dy, dz);
                    BlockState s = roof ? Blocks.PURPUR_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE)
                        : (Math.abs(dx) == half && Math.abs(dz) == half ? Blocks.PURPUR_PILLAR.defaultBlockState()
                        : (dy == 3 && (dx == 0 || dz == 0) ? WFBlocks.CHISELED_STILLSTONE.get().defaultBlockState() : Blocks.PURPUR_BLOCK.defaultBlockState()));
                    boolean buried = dy <= 2;
                    if (dy > 0) {
                        shell.add(p);
                    }
                    if (!buried && r.nextInt(100) < 55) {
                        targets.add(new ReassemblyPlan.Target(p, s));
                        bp.set(p, Blocks.AIR.defaultBlockState());
                    } else {
                        bp.set(p, s);
                    }
                }
            }
        }
        // rubble sources scattered around the room
        List<BlockPos> rubble = new ArrayList<>();
        for (int i = 0; i < targets.size() + 6; i++) {
            BlockPos p = center.offset(r.nextInt(17) - 8, 3 + r.nextInt(2), r.nextInt(17) - 8);
            if (Math.abs(p.getX() - center.getX()) <= half && Math.abs(p.getZ() - center.getZ()) <= half) {
                continue;
            }
            if (!bp.has(p) || bp.get(p).isAir()) {
                bp.set(p, WFBlocks.PURPUR_RUBBLE.get().defaultBlockState());
                rubble.add(p);
            }
        }
        List<BlockPos> lanterns = List.of(center.offset(-half + 1, h - 1, -half + 1), center.offset(half - 1, h - 1, half - 1),
            center.offset(-half + 1, h - 1, half - 1), center.offset(half - 1, h - 1, -half + 1));
        BlockPos vaultPos = center.above();
        bp.set(vaultPos, grand ? WFBlocks.SIGIL_PEDESTAL.get().defaultBlockState() : WFBlocks.SPIRE_VAULT.get().defaultBlockState());
        ResourceKey<LootTable> table = loot(grand ? "vault/grand_vault" : "vault/spire_vault");
        bp.action(vaultPos, level -> {
            if (level.getBlockEntity(vaultPos) instanceof PerPlayerLootBlockEntity v) {
                v.configure(table, true);
            }
        });
        // Keystone Socket(s) by the door side, guarded by Spire Wardens
        int sockets = grand ? 2 : 1;
        Direction toTower = side.getOpposite();
        for (int s = 0; s < sockets; s++) {
            List<ReassemblyPlan.Target> part = sockets == 1 ? targets
                : targets.subList(s * targets.size() / 2, (s + 1) * targets.size() / 2);
            List<BlockPos> partRubble = sockets == 1 ? rubble : rubble.subList(s * rubble.size() / 2, (s + 1) * rubble.size() / 2);
            BlockPos socket = center.relative(toTower, half + 3).relative(toTower.getClockWise(), sockets == 1 ? 0 : (s == 0 ? -3 : 3)).atY(center.getY() + 3);
            bp.set(socket.below(), Blocks.END_STONE_BRICKS.defaultBlockState());
            bp.set(socket, WFBlocks.KEYSTONE_SOCKET.get().defaultBlockState());
            ReassemblyPlan plan = new ReassemblyPlan(List.copyOf(part), List.copyOf(partRubble), s == sockets - 1 ? lanterns : List.of(),
                Optional.of(vaultPos), List.copyOf(shell));
            boolean isGrand = grand;
            bp.action(socket, level -> {
                if (level.getBlockEntity(socket) instanceof KeystoneSocketBlockEntity be) {
                    be.setPlan(plan, isGrand);
                }
                int wardens = 1 + (r.nextInt(3) == 0 ? 1 : 0);
                for (int w = 0; w < wardens; w++) {
                    SpireWarden warden = WFEntities.SPIRE_WARDEN.get().create(level.getLevel(), EntitySpawnReason.STRUCTURE);
                    if (warden != null) {
                        warden.snapTo(socket.getX() + 0.5 + w * 2, socket.getY(), socket.getZ() + 1.5, 0, 0);
                        warden.setHome(socket);
                        warden.setPersistenceRequired();
                        level.addFreshEntityWithPassengers(warden);
                    }
                }
            });
        }
        if (!rubble.isEmpty()) {
            BlockPos echoPos = rubble.get(r.nextInt(rubble.size())).above();
            bp.action(echoPos, level -> spawnEcho(level, echoPos, WFBlocks.PURPUR_RUBBLE.get().defaultBlockState()));
        }
    }
}
