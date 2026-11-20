package dev.worldsfall.world.gen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A deterministic, in-memory description of a procedural structure: blocks plus one-shot actions (block
 * entity configuration, entity spawns). Pieces place only the part inside the chunk being generated, so
 * huge leaning towers generate seamlessly across chunk borders.
 */
public final class Blueprint {
    private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    private final List<Action> actions = new ArrayList<>();
    private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    /** An action anchored at a position; runs once, in the chunk containing that position. */
    public record Action(BlockPos at, Consumer<WorldGenLevel> run) {}

    public Blueprint set(BlockPos pos, BlockState state) {
        BlockPos p = pos.immutable();
        blocks.put(p, state);
        minX = Math.min(minX, p.getX());
        minY = Math.min(minY, p.getY());
        minZ = Math.min(minZ, p.getZ());
        maxX = Math.max(maxX, p.getX());
        maxY = Math.max(maxY, p.getY());
        maxZ = Math.max(maxZ, p.getZ());
        return this;
    }

    public Blueprint setIfAbsent(BlockPos pos, BlockState state) {
        if (!blocks.containsKey(pos)) {
            set(pos, state);
        }
        return this;
    }

    public BlockState get(BlockPos pos) {
        return blocks.get(pos);
    }

    public boolean has(BlockPos pos) {
        return blocks.containsKey(pos);
    }

    public void remove(BlockPos pos) {
        blocks.remove(pos);
    }

    public Blueprint action(BlockPos at, Consumer<WorldGenLevel> run) {
        actions.add(new Action(at.immutable(), run));
        return this;
    }

    public Map<BlockPos, BlockState> blocks() {
        return blocks;
    }

    public BoundingBox bounds() {
        if (blocks.isEmpty()) {
            return new BoundingBox(0, 0, 0, 0, 0, 0);
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public void place(WorldGenLevel level, BoundingBox chunkBox) {
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            if (chunkBox.isInside(e.getKey())) {
                level.setBlock(e.getKey(), e.getValue(), 2);
            }
        }
        for (Action a : actions) {
            if (chunkBox.isInside(a.at)) {
                a.run.accept(level);
            }
        }
    }
}
