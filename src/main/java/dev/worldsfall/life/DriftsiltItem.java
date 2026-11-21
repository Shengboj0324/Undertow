package dev.worldsfall.life;

import dev.worldsfall.block.PaleLoamBlock;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;

/**
 * Driftsilt: teal-grey silt the tide leaves behind (tide fishing, Tidewrack). Scattered on open end stone it seeds Pale
 * Loam in a small patch, the gardener's way to green an island without a Grazer herd.
 */
public class DriftsiltItem extends Item {
    public static final int RADIUS = 1;

    public DriftsiltItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos pos = context.getClickedPos();
        if (!context.getLevel().getBlockState(pos).is(Blocks.END_STONE) && !context.getLevel().getBlockState(pos).is(WFBlocks.PALE_LOAM.get())) {
            return InteractionResult.PASS;
        }
        if (context.getLevel() instanceof ServerLevel level) {
            int changed = 0;
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-RADIUS, 0, -RADIUS), pos.offset(RADIUS, 0, RADIUS))) {
                if (level.getBlockState(p).is(Blocks.END_STONE) && !PaleLoamBlock.isCovered(level, p) && (p.equals(pos) || level.getRandom().nextInt(3) > 0)) {
                    level.setBlockAndUpdate(p, WFBlocks.PALE_LOAM.get().defaultBlockState());
                    changed++;
                }
            }
            if (changed == 0) {
                return InteractionResult.FAIL;
            }
            level.sendParticles(WFParticles.POLLEN_MOTE.get(), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 20, 1.0, 0.1, 1.0, 0.01);
            level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0f, 0.9f);
            context.getItemInHand().consume(1, context.getPlayer());
        }
        return InteractionResult.SUCCESS;
    }
}
