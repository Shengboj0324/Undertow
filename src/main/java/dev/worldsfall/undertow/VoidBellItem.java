package dev.worldsfall.undertow;

import dev.worldsfall.registry.WFComponents;
import dev.worldsfall.registry.WFEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.function.Consumer;

/** A folded Void Bell: use it on a block to set the bell down there (its charge goes with it). */
public class VoidBellItem extends Item {
    public VoidBellItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos at = context.getClickedPos().relative(context.getClickedFace());
        VoidBell bell = WFEntities.VOID_BELL.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (bell == null) {
            return InteractionResult.FAIL;
        }
        bell.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, context.getRotation(), 0);
        if (!level.noCollision(bell, bell.getBoundingBox())) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        bell.addCharge(stack.getOrDefault(WFComponents.BELL_CHARGE.get(), 0));
        if (context.getPlayer() != null) {
            bell.setOwner(context.getPlayer().getUUID());
        }
        level.addFreshEntity(bell);
        stack.consume(1, context.getPlayer());
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        int charge = stack.getOrDefault(WFComponents.BELL_CHARGE.get(), 0);
        tooltip.accept(Component.translatable("tooltip.worldsfall.bell_charge", charge / 1200, charge % 1200 / 20)
            .withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
    }
}
