package dev.worldsfall.world.fracture;

import dev.worldsfall.entity.FlyingBlock;
import dev.worldsfall.entity.TetherPylon;
import dev.worldsfall.entity.TheUnmade;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The Fracture arena (§11.8, §13.3): nine ash-grey fragments in a ring (radius 40) around the Core, each with
 * a Tether Pylon; Updraft Columns between some fragments; Anchorstones for the Tether; an Arrival Platform 60
 * blocks outside the ring connected by a debris path.
 */
public final class FractureArena {
    public static final BlockPos CENTER = new BlockPos(0, 64, 0);
    public static final int RING_RADIUS = 40;
    public static final int FRAGMENTS = 9;
    public static final BlockPos ARRIVAL = new BlockPos(RING_RADIUS + 60, 64, 0);
    public static final double CORE_HEIGHT = 12;
    /** Placed blocks to decay during a fight (§13.11): pos + tick to remove. */
    private static final Deque<long[]> DECAY = new ArrayDeque<>();

    private FractureArena() {}

    public static int fragmentRadius(int index) {
        return 5 + (index * 7 % 4);
    }

    public static BlockPos fragmentCenter(int index, double extraRadius) {
        double a = index * Math.PI * 2 / FRAGMENTS;
        double r = RING_RADIUS + extraRadius;
        return CENTER.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r));
    }

    /** Positions (between fragments) of the rising-ash Updraft Columns. */
    public static List<Vec3> updrafts() {
        List<Vec3> list = new ArrayList<>();
        for (int i = 0; i < FRAGMENTS; i += 2) {
            double a = (i + 0.5) * Math.PI * 2 / FRAGMENTS;
            list.add(new Vec3(CENTER.getX() + Math.cos(a) * RING_RADIUS, CENTER.getY(), CENTER.getZ() + Math.sin(a) * RING_RADIUS));
        }
        return list;
    }

    public static void ensureBuilt(ServerLevel level) {
        FractureData data = FractureData.get(level.getServer());
        if (data.built()) {
            ensureBoss(level);
            return;
        }
        List<FractureData.Fragment> frags = new ArrayList<>();
        for (int i = 0; i < FRAGMENTS; i++) {
            BlockPos c = fragmentCenter(i, 0);
            int r = fragmentRadius(i);
            buildFragment(level, c, r, level.getRandom().nextLong());
            frags.add(new FractureData.Fragment(c, r, true));
        }
        buildPlatform(level);
        buildAnchorstones(level);
        data.setBuilt(frags);
        ensureBoss(level);
    }

    static void buildFragment(ServerLevel level, BlockPos c, int r, long seed) {
        net.minecraft.util.RandomSource rand = net.minecraft.util.RandomSource.create(seed);
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz) + rand.nextDouble() * 0.9;
                if (d > r) {
                    continue;
                }
                int depth = (int) Math.round((1 - d / r) * 5) + 1;
                for (int dy = 0; dy < depth; dy++) {
                    BlockPos p = c.offset(dx, -dy, dz);
                    BlockState st = dy == 0 ? (rand.nextInt(9) == 0 ? WFBlocks.NULL_OBSIDIAN.get().defaultBlockState() : WFBlocks.ASH_STONE.get().defaultBlockState())
                        : rand.nextInt(5) == 0 ? WFBlocks.NULL_OBSIDIAN.get().defaultBlockState() : WFBlocks.ASH_STONE.get().defaultBlockState();
                    level.setBlock(p, st, Block.UPDATE_CLIENTS);
                }
                if (d > r - 1.5 && rand.nextInt(6) == 0) {
                    level.setBlock(c.offset(dx, 1, dz), WFBlocks.UNMADE_SHARD.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        // Tether Pylon pillar
        for (int y = 1; y <= 3; y++) {
            level.setBlock(c.above(y), WFBlocks.NULL_OBSIDIAN.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    static void buildPlatform(ServerLevel level) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dx * dx + dz * dz <= 26) {
                    level.setBlock(ARRIVAL.offset(dx, 0, dz), WFBlocks.POLISHED_DRIFTSTONE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(ARRIVAL.offset(dx, -1, dz), WFBlocks.DRIFTSTONE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        // debris path toward the ring
        for (int x = ARRIVAL.getX() - 7; x > RING_RADIUS + 6; x -= 3) {
            int z = (int) Math.round(Math.sin(x * 0.2) * 2);
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    level.setBlock(new BlockPos(x + dx, ARRIVAL.getY(), z + dz), WFBlocks.DRIFTSTONE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        setExitRift(level, true);
    }

    public static void setExitRift(ServerLevel level, boolean open) {
        BlockState state = open ? WFBlocks.FRACTURE_RIFT.get().defaultBlockState() : Blocks.AIR.defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(ARRIVAL.offset(2, 1, -1), ARRIVAL.offset(4, 1, 1))) {
            BlockState cur = level.getBlockState(p);
            if (open ? cur.isAir() : cur.is(WFBlocks.FRACTURE_RIFT.get())) {
                level.setBlock(p, state, Block.UPDATE_ALL);
            }
        }
    }

    static void buildAnchorstones(ServerLevel level) {
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI * 2 / 18 + 0.17;
            double r = i % 2 == 0 ? RING_RADIUS + 13 : RING_RADIUS - 13;
            BlockPos p = CENTER.offset((int) (Math.cos(a) * r), 6 + (i % 3) * 3, (int) (Math.sin(a) * r));
            level.setBlock(p, WFBlocks.DRIFTSTONE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public static void ensureBoss(ServerLevel level) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            BlockPos ped = CENTER.above(2);
            if (!level.getBlockState(ped).is(WFBlocks.SIGIL_PEDESTAL.get())) {
                level.setBlock(ped.below(), WFBlocks.ASH_STONE.get().defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(ped, WFBlocks.SIGIL_PEDESTAL.get().defaultBlockState(), Block.UPDATE_ALL);
                if (level.getBlockEntity(ped) instanceof dev.worldsfall.block.entity.PerPlayerLootBlockEntity vault) {
                    vault.configure(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(dev.worldsfall.Worldsfall.MODID, "vault/peaceful_heart")), false);
                }
            }
            return;
        }
        level.getChunkAt(CENTER);
        List<TheUnmade> existing = level.getEntitiesOfClass(TheUnmade.class, new AABB(CENTER).inflate(160));
        if (!existing.isEmpty()) {
            return;
        }
        TheUnmade boss = WFEntities.THE_UNMADE.get().create(level, EntitySpawnReason.TRIGGERED);
        if (boss != null) {
            boss.snapTo(CENTER.getX() + 0.5, CENTER.getY() + CORE_HEIGHT, CENTER.getZ() + 0.5, 0, 0);
            level.addFreshEntity(boss);
        }
        spawnPylons(level);
    }

    public static void spawnPylons(ServerLevel level) {
        FractureData data = FractureData.get(level.getServer());
        for (int i = 0; i < data.fragments().size(); i++) {
            FractureData.Fragment f = data.fragments().get(i);
            if (!f.alive()) {
                continue;
            }
            Vec3 top = Vec3.atBottomCenterOf(f.center().above(4));
            if (level.getEntitiesOfClass(TetherPylon.class, new AABB(f.center()).inflate(3, 8, 3)).isEmpty()) {
                TetherPylon pylon = WFEntities.TETHER_PYLON.get().create(level, EntitySpawnReason.TRIGGERED);
                if (pylon != null) {
                    pylon.setRing(i % 3);
                    pylon.setFragment(i);
                    pylon.snapTo(top.x, top.y, top.z, 0, 0);
                    level.addFreshEntity(pylon);
                }
            }
        }
    }

    /** Phase 2 (§13.6): moves a fragment 6 blocks outward, carrying entities standing on it. */
    public static void driftFragment(ServerLevel level, int index) {
        FractureData data = FractureData.get(level.getServer());
        FractureData.Fragment f = data.fragments().get(index);
        if (!f.alive()) {
            return;
        }
        Vec3 dirF = Vec3.atCenterOf(f.center()).subtract(Vec3.atCenterOf(CENTER)).multiply(1, 0, 1).normalize();
        BlockPos shift = BlockPos.containing(Math.round(dirF.x * 6), 0, Math.round(dirF.z * 6));
        int r = f.radius() + 1;
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(f.center().offset(-r, -7, -r), f.center().offset(r, 5, r))) {
            BlockState st = level.getBlockState(p);
            if (!st.isAir()) {
                positions.add(p.immutable());
                states.add(st);
            }
        }
        for (BlockPos p : positions) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
        for (int i = 0; i < positions.size(); i++) {
            level.setBlock(positions.get(i).offset(shift), states.get(i), Block.UPDATE_CLIENTS);
        }
        for (Entity e : level.getEntities((Entity) null, new AABB(f.center()).inflate(r + 1, 8, r + 1), e -> !(e instanceof TheUnmade))) {
            e.teleportTo(e.getX() + shift.getX(), e.getY(), e.getZ() + shift.getZ());
        }
        BlockPos moved = f.center().offset(shift);
        data.setFragment(index, new FractureData.Fragment(moved, f.radius(), true));
    }

    public static double fragmentOffset(FractureData.Fragment f) {
        return Math.sqrt(f.center().distSqr(CENTER.atY(f.center().getY()))) - RING_RADIUS;
    }

    /** Crumbles a fragment: blocks fall away as vanishing debris (§13.6). */
    public static void crumbleFragment(ServerLevel level, int index) {
        FractureData data = FractureData.get(level.getServer());
        FractureData.Fragment f = data.fragments().get(index);
        int r = f.radius() + 1;
        int flying = 0;
        for (BlockPos p : BlockPos.betweenClosed(f.center().offset(-r, -7, -r), f.center().offset(r, 5, r))) {
            BlockState st = level.getBlockState(p);
            if (st.isAir()) {
                continue;
            }
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            if (flying < 60 && level.getRandom().nextInt(3) == 0) {
                level.addFreshEntity(FlyingBlock.createFall(level, st, p.immutable(), 30 + level.getRandom().nextInt(20), 50 + level.getRandom().nextInt(30)));
                flying++;
            }
        }
        data.setFragment(index, new FractureData.Fragment(f.center(), f.radius(), false));
    }

    /** Final blow (§13.7): debris rearranges into one flat, stable island under the players (~15 s). */
    public static void reform(ServerLevel level) {
        int radius = 9;
        List<BlockPos> targets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    targets.add(CENTER.offset(dx, 0, dz));
                }
            }
        }
        targets.sort((a, b) -> Double.compare(a.distSqr(CENTER), b.distSqr(CENTER)));
        int flights = 0;
        for (int i = 0; i < targets.size(); i++) {
            BlockPos t = targets.get(i);
            BlockState st = (i % 7 == 0) ? WFBlocks.POLISHED_DRIFTSTONE.get().defaultBlockState() : WFBlocks.ASH_STONE.get().defaultBlockState();
            if (flights < 120 && i % 2 == 0) {
                double a = level.getRandom().nextDouble() * Math.PI * 2;
                Vec3 from = Vec3.atCenterOf(CENTER).add(Math.cos(a) * 30, -10 + level.getRandom().nextInt(20), Math.sin(a) * 30);
                FlyingBlock fb = FlyingBlock.create(level, st, from, t, 120 + level.getRandom().nextInt(160), 6, FlyingBlock.MODE_PLACE);
                level.addFreshEntity(fb);
                flights++;
            } else {
                level.setBlock(t.below(), WFBlocks.ASH_STONE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(t, st, Block.UPDATE_ALL);
            }
            level.setBlock(t.below(), WFBlocks.ASH_STONE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Resets the arena after a wipe (§13.2): rebuilds missing fragments at their original positions. */
    public static void rebuild(ServerLevel level) {
        FractureData data = FractureData.get(level.getServer());
        for (int i = 0; i < data.fragments().size(); i++) {
            FractureData.Fragment f = data.fragments().get(i);
            if (f.alive() && Mth.abs((float) fragmentOffset(f)) < 1) {
                continue;
            }
            if (f.alive()) {
                crumbleFragmentSilently(level, f);
            }
            BlockPos c = fragmentCenter(i, 0);
            buildFragment(level, c, f.radius(), level.getRandom().nextLong());
            data.setFragment(i, new FractureData.Fragment(c, f.radius(), true));
        }
        spawnPylons(level);
    }

    private static void crumbleFragmentSilently(ServerLevel level, FractureData.Fragment f) {
        int r = f.radius() + 1;
        for (BlockPos p : BlockPos.betweenClosed(f.center().offset(-r, -7, -r), f.center().offset(r, 5, r))) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    public static void scheduleDecay(ServerLevel level, BlockPos pos) {
        synchronized (DECAY) {
            DECAY.add(new long[] {pos.asLong(), level.getGameTime() + 200});
        }
    }

    public static void tickDecay(ServerLevel level) {
        synchronized (DECAY) {
            while (!DECAY.isEmpty() && DECAY.peek()[1] <= level.getGameTime()) {
                BlockPos p = BlockPos.of(DECAY.poll()[0]);
                if (!level.getBlockState(p).isAir()) {
                    level.destroyBlock(p, false);
                }
            }
        }
    }
}
