package dev.worldsfall.world.gen.structure;

import dev.worldsfall.registry.WFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gapwrecks (proposal "The Debris Belt"): smaller, safer Driftwrecks in the Debris Belt 500–750 blocks out. They always
 * sit above the tide, so new players learn what wrecks are before they dive for the real ones.
 */
public final class GapwreckGenerator {
    private GapwreckGenerator() {}

    public static Blueprint build(BlockPos o, int variant, RandomSource r) {
        Blueprint bp = new Blueprint();
        DriftwreckGenerator.rock(bp, o, 4.5f + r.nextFloat() * 1.5f, r);
        // a broken purpur room on the rock: two walls, a fallen pillar, a lantern still hanging
        BlockState wall = WFBlocks.WEATHERED_PURPUR.get().defaultBlockState();
        for (int x = -3; x <= 3; x++) {
            for (int y = 1; y <= 3 + (x == 0 ? 1 : 0); y++) {
                if (r.nextInt(5) != 0) {
                    bp.set(o.offset(x, y, -3), y == 1 ? Blocks.PURPUR_BLOCK.defaultBlockState() : wall);
                }
                if (x <= 0 && r.nextInt(4) != 0) {
                    bp.set(o.offset(-3, y, x + 3), wall);
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            bp.set(o.offset(1 + i, 1, 2), WFBlocks.CRACKED_PURPUR_PILLAR.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X));
        }
        bp.set(o.offset(0, 4, -2), Blocks.PURPUR_SLAB.defaultBlockState());
        bp.set(o.offset(0, 3, -2), WFBlocks.SPIRE_LANTERN.get().defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        for (int i = 0; i < 5; i++) {
            bp.setIfAbsent(o.offset(r.nextInt(7) - 3, 1, r.nextInt(5) - 2), WFBlocks.PURPUR_RUBBLE.get().defaultBlockState());
        }
        DriftwreckGenerator.cache(bp, o.offset(-1, 1, -1), "vault/gapwreck", false);
        if (variant == 1) {
            DriftwreckGenerator.cache(bp, o.offset(2, 1, -2), "vault/gapwreck", false);
        }
        return bp;
    }
}
