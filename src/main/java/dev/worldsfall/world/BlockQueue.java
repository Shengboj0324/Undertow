package dev.worldsfall.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Applies large block edits over many ticks with a per-tick budget (§20.8: Sundering spread over ≥ 40 ticks,
 * no tick > 10 ms). Never touches player-placed blocks (D5).
 */
public final class BlockQueue {
    private static final List<BlockQueue> ACTIVE = new ArrayList<>();
    public static final int DEFAULT_BUDGET = 350;

    private final ServerLevel level;
    private final Deque<Edit> edits = new ArrayDeque<>();
    private final int budget;
    private Runnable onDone = () -> {};

    private record Edit(BlockPos pos, BlockState state, boolean force) {}

    public BlockQueue(ServerLevel level, int budget) {
        this.level = level;
        this.budget = budget;
    }

    public BlockQueue set(BlockPos pos, BlockState state) {
        edits.add(new Edit(pos.immutable(), state, false));
        return this;
    }

    /** Only places into air / replaceable blocks. */
    public BlockQueue fill(BlockPos pos, BlockState state) {
        edits.add(new Edit(pos.immutable(), state, true));
        return this;
    }

    public int size() {
        return edits.size();
    }

    public BlockQueue then(Runnable r) {
        this.onDone = r;
        return this;
    }

    public void start() {
        synchronized (ACTIVE) {
            ACTIVE.add(this);
        }
    }

    /** Runs everything immediately (tests, server stop). */
    public void runAll() {
        while (!edits.isEmpty()) {
            apply(edits.poll());
        }
        onDone.run();
    }

    private void apply(Edit e) {
        if (PlayerPlaced.isPlayerPlaced(level, e.pos)) {
            return;
        }
        BlockState cur = level.getBlockState(e.pos);
        if (e.force && !(cur.isAir() || cur.canBeReplaced())) {
            return;
        }
        if (cur != e.state) {
            level.setBlock(e.pos, e.state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private boolean tick() {
        for (int i = 0; i < budget && !edits.isEmpty(); i++) {
            apply(edits.poll());
        }
        if (edits.isEmpty()) {
            onDone.run();
            return true;
        }
        return false;
    }

    public static void tickAll(ServerLevel level) {
        synchronized (ACTIVE) {
            ACTIVE.removeIf(q -> q.level == level && q.tick());
        }
    }

    public static void finishAll() {
        synchronized (ACTIVE) {
            for (BlockQueue q : ACTIVE) {
                q.runAll();
            }
            ACTIVE.clear();
        }
    }

    public static boolean busy() {
        synchronized (ACTIVE) {
            return !ACTIVE.isEmpty();
        }
    }
}
