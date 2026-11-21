package dev.worldsfall.life;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Game events for the living systems (release 1.5): tide fishing, lost things, Courier leftovers. */
public final class LifeEvents {
    private LifeEvents() {}

    @SubscribeEvent
    public static void entityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof FishingHook hook && hook.level().dimension() == Level.END) {
            TideFishing.tickHook(hook);
        } else if (event.getEntity() instanceof ItemEntity item) {
            LostThings.onItemTick(item);
        }
    }

    /** Reeling in a biting bobber on the tide or in Stillwater lands the catch from the right table. */
    @SubscribeEvent
    public static void reel(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getItemStack().getItem() instanceof FishingRodItem && player.fishing != null
            && TideFishing.reel(player, event.getItemStack(), event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND
            : EquipmentSlot.OFFHAND)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    /** Courier Hoarders are only visiting: they cannot be hurt. */
    @SubscribeEvent
    public static void courierDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity().entityTags().contains(Couriers.TAG)) {
            event.setCanceled(true);
        }
    }

    /** A Courier Hoarder saved mid-visit (the server stopped) is not left standing around. */
    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().entityTags().contains(Couriers.TAG)) {
            event.setCanceled(true);
        }
    }
}
