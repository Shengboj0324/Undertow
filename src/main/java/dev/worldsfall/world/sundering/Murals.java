package dev.worldsfall.world.sundering;

import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.world.BlockQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ascendant Murals (§2.3, §11.4): six 7×3 pixel panels built from purpur variants telling — without words —
 * the tides, the towers, the Heart, the breath it held, the fall, and the warden.
 */
public final class Murals {
    // . = backdrop (weathered purpur), P = purpur, T = lumen (teal), K = stillstone (black), L = end stone bricks, W = chiseled stillstone
    private static final String[][] PANELS = {
        {"TT.T.TT", ".TTTTT.", "KKKKKKK"},   // 1. the tides rising
        {"..P..P.", ".PP.PP.", "LPPLPPL"},   // 2. the towers (ladders)
        {"..W.W..", ".WTTTW.", "..WTW.."},   // 3. the Heart of Stillness
        {"T.....T", ".T.W.T.", "..TTT.."},   // 4. it held for one breath
        {"P..K..P", ".K.P.K.", "K.K.K.K"},   // 5. then nothing held
        {"..L.L..", ".LLWLL.", "L.L.L.L"},   // 6. the warden they built
    };

    private Murals() {}

    public static void place(BlockQueue q, BlockPos center, Direction hallDir, int index) {
        String[] rows = PANELS[index % PANELS.length];
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < 7; c++) {
                BlockPos p = center.relative(hallDir, c - 3).above(rows.length - 1 - r);
                q.set(p, block(rows[r].charAt(c)));
            }
        }
    }

    private static BlockState block(char ch) {
        return switch (ch) {
            case 'P' -> Blocks.PURPUR_BLOCK.defaultBlockState();
            case 'T' -> WFBlocks.LUMEN_CRYSTAL_BLOCK.get().defaultBlockState();
            case 'K' -> WFBlocks.STILLSTONE.get().defaultBlockState();
            case 'L' -> Blocks.END_STONE_BRICKS.defaultBlockState();
            case 'W' -> WFBlocks.CHISELED_STILLSTONE.get().defaultBlockState();
            default -> WFBlocks.WEATHERED_PURPUR.get().defaultBlockState();
        };
    }
}
