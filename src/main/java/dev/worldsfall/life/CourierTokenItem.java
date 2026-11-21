package dev.worldsfall.life;

import dev.worldsfall.registry.WFComponents;
import dev.worldsfall.world.Progress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Courier Token: use it on a first Courier Cache, then on a second (End only, up to 2,000 blocks apart) to hire a Hoarder
 * Courier between them. Needs good standing with Hoarders.
 */
public class CourierTokenItem extends Item {
    public CourierTokenItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(WFComponents.LINKED_POS.get());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof CourierCacheBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack token = context.getItemInHand();
        if (level.dimension() != Level.END) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.courier.only_end"));
            return InteractionResult.FAIL;
        }
        if (HoardStanding.get(player) < HoardStanding.COURIER) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.courier.standing", HoardStanding.get(player), HoardStanding.COURIER));
            return InteractionResult.FAIL;
        }
        GlobalPos first = token.get(WFComponents.LINKED_POS.get());
        if (first == null || first.dimension() != Level.END) {
            token.set(WFComponents.LINKED_POS.get(), GlobalPos.of(Level.END, pos.immutable()));
            player.sendOverlayMessage(Component.translatable("message.worldsfall.courier.first"));
            return InteractionResult.SUCCESS;
        }
        if (first.pos().equals(pos)) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.courier.same"));
            return InteractionResult.FAIL;
        }
        if (first.pos().distSqr(pos) > (double) Couriers.MAX_DISTANCE * Couriers.MAX_DISTANCE) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.courier.too_far", Couriers.MAX_DISTANCE));
            return InteractionResult.FAIL;
        }
        long now = server.getServer().overworld().getGameTime();
        Couriers.get(server.getServer()).add(new Couriers.Contract(player.getUUID(), first.pos(), pos.immutable(), now + Couriers.INTERVAL,
            ItemStack.EMPTY));
        token.consume(1, player);
        player.sendSystemMessage(Component.translatable("message.worldsfall.courier.hired", first.pos().getX(), first.pos().getZ(), pos.getX(), pos.getZ()));
        Progress.award(player, "special_delivery");
        return InteractionResult.SUCCESS;
    }
}
