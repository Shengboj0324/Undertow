package dev.worldsfall.world.sundering;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.config.WFConfig;
import dev.worldsfall.entity.FlyingBlock;
import dev.worldsfall.entity.UndersideLurker;
import dev.worldsfall.network.SunderingFxPayload;
import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.BlockQueue;
import dev.worldsfall.world.PlayerPlaced;
import dev.worldsfall.world.Progress;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Sundering (§5.4): a one-time world event after the first dragon death. ~20 s: rumble → magenta cracks
 * race across the island → a 7×7 shaft opens 40 blocks from the exit portal → spiral stairs down to the
 * Undercroft. Never removes a player-placed block; if the planned shaft has any, the next candidate is used.
 */
public final class Sundering {
    public static final int DELAY_AFTER_DEATH = 220;
    public static final int DESCENT_DISTANCE = 40;
    public static final int CRACK_TICKS = 100;
    private static Sequence active;

    private Sundering() {}

    /** Called when the dragon dies; schedules the event if it never happened. */
    public static void onDragonDeath(ServerLevel end) {
        SunderingData data = SunderingData.get(end.getServer());
        if (data.done() || data.startAt() >= 0) {
            return;
        }
        data.schedule(end.getGameTime() + DELAY_AFTER_DEATH);
    }

    /** Retroactive trigger (§5.4): first End entry after install when the dragon is already dead. */
    public static void onPlayerEnterEnd(ServerPlayer player, ServerLevel end) {
        SunderingData data = SunderingData.get(end.getServer());
        if (data.done() || data.startAt() >= 0 || !WFConfig.get(WFConfig.SERVER.sunderingRetroactive, true)) {
            return;
        }
        if (end.getDragonFight() != null && end.getDragonFight().hasPreviouslyKilledDragon()) {
            data.schedule(end.getGameTime() + 100);
        }
    }

    public static void tick(ServerLevel end) {
        SunderingData data = SunderingData.get(end.getServer());
        if (active != null && active.level == end) {
            if (active.tick()) {
                active = null;
            }
            return;
        }
        if (!data.done() && data.startAt() >= 0 && end.getGameTime() >= data.startAt()) {
            start(end, data);
        }
    }

    /** Starts immediately (also used by /worldsfall sundering trigger). Returns false if impossible. */
    public static boolean start(ServerLevel end, SunderingData data) {
        if (active != null) {
            return false;
        }
        if (!WFConfig.get(WFConfig.SERVER.sunderingEnabled, true)) {
            buildRiftIsland(end, data);
            return true;
        }
        BlockPos top = chooseDescent(end);
        if (top == null) {
            Worldsfall.LOGGER.warn("Sundering: no free Descent location; falling back to a Rift island");
            buildRiftIsland(end, data);
            return true;
        }
        active = new Sequence(end, top);
        data.schedule(-1);
        return true;
    }

    /** Candidates on a circle 40 blocks from the exit portal; skip any with player blocks or no ground. */
    public static BlockPos chooseDescent(ServerLevel end) {
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI * 2 / 16 + Math.PI / 4;
            int x = (int) Math.round(Math.cos(a) * DESCENT_DISTANCE);
            int z = (int) Math.round(Math.sin(a) * DESCENT_DISTANCE);
            end.getChunkAt(new BlockPos(x, 0, z));
            int y = end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (y < UndercroftBuilder.FLOOR_Y + 12) {
                continue;
            }
            BlockPos top = new BlockPos(x, y, z);
            if (!PlayerPlaced.anyInBox(end, top.offset(-4, UndercroftBuilder.FLOOR_Y - y, -4), top.offset(4, 0, 4))) {
                return top;
            }
        }
        return null;
    }

    private static void buildRiftIsland(ServerLevel end, SunderingData data) {
        BlockPos c = new BlockPos(300, 60, 0);
        BlockQueue q = new BlockQueue(end, BlockQueue.DEFAULT_BUDGET);
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= 8) {
                    int depth = (int) ((8 - d) * 0.8) + 1;
                    for (int dy = 0; dy < depth; dy++) {
                        q.set(c.offset(dx, -dy, dz), dy == 0 ? WFBlocks.POLISHED_DRIFTSTONE.get().defaultBlockState() : WFBlocks.DRIFTSTONE.get().defaultBlockState());
                    }
                }
            }
        }
        BlockPos frame = c.above();
        q.set(frame, WFBlocks.FRACTURE_RIFT_FRAME.get().defaultBlockState());
        q.set(c.offset(0, 1, -6), WFBlocks.STILL_THRONE.get().defaultBlockState());
        q.then(() -> data.complete(c, frame));
        q.start();
    }

    static final class Sequence {
        final ServerLevel level;
        final BlockPos top;
        final UndercroftBuilder.Layout layout;
        int age;

        Sequence(ServerLevel level, BlockPos top) {
            this.level = level;
            this.top = top;
            this.layout = UndercroftBuilder.layout(top);
            level.playSound(null, BlockPos.ZERO.atY(64), WFSounds.EVENT_SUNDERING_RUMBLE.get(), SoundSource.AMBIENT, 6.0f, 1.0f);
            PacketDistributor.sendToPlayersInDimension(level, new SunderingFxPayload(top, level.getGameTime()));
        }

        /** Returns true when the visual sequence has handed off to the block queue. */
        boolean tick() {
            age++;
            if (age == CRACK_TICKS) {
                level.playSound(null, top, WFSounds.EVENT_SUNDERING_CRACK.get(), SoundSource.AMBIENT, 6.0f, 1.0f);
                dropShaftTop();
            }
            if (age == CRACK_TICKS + 40) {
                BlockQueue q = new BlockQueue(level, BlockQueue.DEFAULT_BUDGET);
                UndercroftBuilder.build(level, layout, q);
                q.then(this::finish);
                q.start();
                return true;
            }
            return false;
        }

        /** The end stone "drops away into the dark" — the top layers fall as debris (≤ 120 entities). */
        private void dropShaftTop() {
            int spawned = 0;
            for (int dy = 0; dy < 8; dy++) {
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos p = top.offset(dx, -dy, dz);
                        if (PlayerPlaced.isPlayerPlaced(level, p)) {
                            continue;
                        }
                        BlockState st = level.getBlockState(p);
                        if (st.isAir()) {
                            continue;
                        }
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        if (spawned < 120 && (dy < 3 || level.getRandom().nextInt(3) == 0)) {
                            level.addFreshEntity(FlyingBlock.createFall(level, st, p, 40 + level.getRandom().nextInt(30), 50 + level.getRandom().nextInt(40)));
                            spawned++;
                        }
                    }
                }
            }
        }

        private void finish() {
            SunderingData data = SunderingData.get(level.getServer());
            data.complete(top, layout.riftFrame());
            for (BlockPos spot : layout.lurkerSpots()) {
                UndersideLurker lurker = WFEntities.UNDERSIDE_LURKER.get().create(level, EntitySpawnReason.STRUCTURE);
                if (lurker != null) {
                    lurker.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
                    lurker.attachToCeiling(level);
                    lurker.setPersistenceRequired();
                    level.addFreshEntity(lurker);
                }
            }
            for (ServerPlayer p : level.players()) {
                Progress.award(p, "nothing_holds");
            }
            WFNetwork.syncSundering(level.getServer());
        }
    }

    public static boolean running() {
        return active != null;
    }

    public static void forceFinish() {
        active = null;
    }
}
