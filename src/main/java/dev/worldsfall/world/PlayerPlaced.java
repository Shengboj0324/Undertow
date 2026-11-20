package dev.worldsfall.world;

import dev.worldsfall.registry.WFAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashSet;
import java.util.Set;

/** Bounded player-placed block tracking: only in the End, within 250 blocks of the origin (§20.5). */
public final class PlayerPlaced {
    public static final int RADIUS = 250;

    private PlayerPlaced() {}

    public static boolean tracked(Level level, BlockPos pos) {
        return level.dimension() == Level.END && Math.abs(pos.getX()) <= RADIUS && Math.abs(pos.getZ()) <= RADIUS;
    }

    public static void mark(Level level, BlockPos pos, boolean placed) {
        if (!tracked(level, pos)) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        Set<Long> set = new HashSet<>(chunk.getData(WFAttachments.PLAYER_PLACED));
        boolean changed = placed ? set.add(pos.asLong()) : set.remove(pos.asLong());
        if (changed) {
            chunk.setData(WFAttachments.PLAYER_PLACED, set);
            chunk.markUnsaved();
        }
    }

    public static boolean isPlayerPlaced(Level level, BlockPos pos) {
        if (!tracked(level, pos) || !level.hasChunkAt(pos)) {
            return false;
        }
        return level.getChunkAt(pos).getData(WFAttachments.PLAYER_PLACED).contains(pos.asLong());
    }

    public static boolean anyInBox(Level level, BlockPos min, BlockPos max) {
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            if (isPlayerPlaced(level, p)) {
                return true;
            }
        }
        return false;
    }
}
