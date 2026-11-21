package dev.worldsfall.gap;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/** A Drift Buoy is set out over the void: used in the air, it is placed three blocks in front of the player. */
public class DriftBuoyItem extends BlockItem {
    public DriftBuoyItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        Vec3 at = player.getEyePosition().add(player.getViewVector(1.0f).scale(3.5));
        BlockPos pos = BlockPos.containing(at);
        if (!level.getBlockState(pos).isAir() || !DriftBuoyBlock.openAir(level, pos)) {
            if (player instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(Component.translatable("message.worldsfall.buoy.open_air"));
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            level.setBlockAndUpdate(pos, getBlock().defaultBlockState());
            level.playSound(null, pos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 1.0f, 0.9f);
            ItemStack stack = player.getItemInHand(hand);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}
