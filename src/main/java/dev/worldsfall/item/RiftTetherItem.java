package dev.worldsfall.item;

import dev.worldsfall.entity.RiftHook;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * Rift Tether (§9.3): a pearl-powered grappling hook. Use to fire (range 24); hold use to reel in at 12 b/s;
 * release to swing on the line; sneak to detach. 96 durability, repaired with ender pearls.
 */
public class RiftTetherItem extends Item {
    public static final double RANGE = 24.0;
    public static final double REEL_SPEED = 12.0 / 20.0;
    public static final int DETACH_COOLDOWN = 10;

    public RiftTetherItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        RiftHook hook = RiftHook.activeFor(player);
        if (hook == null) {
            if (level instanceof ServerLevel server) {
                RiftHook fired = RiftHook.fire(server, player);
                server.addFreshEntity(fired);
                stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
                server.playSound(null, player.blockPosition(), WFSounds.ITEM_RIFT_TETHER_FIRE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            return InteractionResult.SUCCESS;
        }
        if (hook.isLatched()) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (entity instanceof Player player) {
            RiftHook hook = RiftHook.activeFor(player);
            if (hook == null || !hook.isLatched()) {
                player.stopUsingItem();
                return;
            }
            hook.reel(player);
            if (!level.isClientSide() && (72000 - remaining) % 8 == 0) {
                level.playSound(null, player.blockPosition(), WFSounds.ITEM_RIFT_TETHER_REEL.get(), SoundSource.PLAYERS, 0.5f, 1.0f);
            }
        }
    }
}
