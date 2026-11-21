package dev.worldsfall.life;

import dev.worldsfall.registry.WFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

/** When a King Tide falls it leaves Tidewrack piles (loot) on the island tops around each player in the End. */
public final class Tidewrack {
    private Tidewrack() {}

    public static int scatter(ServerLevel end) {
        RandomSource r = end.getRandom();
        int placed = 0;
        for (ServerPlayer p : end.players()) {
            int n = 3 + r.nextInt(4);
            for (int i = 0; i < n; i++) {
                BlockPos at = WorldEvents.islandTop(end, p.blockPosition(), 6, 48, r);
                if (at != null) {
                    end.setBlock(at, WFBlocks.TIDEWRACK.get().defaultBlockState(), Block.UPDATE_ALL);
                    placed++;
                }
            }
        }
        return placed;
    }
}
