package dev.worldsfall.nav;

import dev.worldsfall.registry.WFComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

/** A filled Void Chart (also City and Star Charts): a map drawn by {@link Charts} instead of the vanilla colour pass. */
public class VoidChartItem extends MapItem {
    public VoidChartItem(Properties properties) {
        super(properties);
    }

    public static String kind(ItemStack stack) {
        String k = stack.get(WFComponents.CHART_KIND.get());
        return k == null ? "void" : k;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.worldsfall." + kind(stack) + "_chart");
    }

    @Override
    public void update(Level level, Entity holder, MapItemSavedData data) {
        if (level instanceof ServerLevel server && level.dimension() == data.dimension && holder instanceof Player player) {
            Charts.paintAround(server, player, data, data.getHoldingPlayer(player).step++);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, owner, slot);
        if (owner instanceof Player player && slot != null && slot.getType() == EquipmentSlot.Type.HAND && level.getGameTime() % 40 == owner.getId() % 40) {
            MapItemSavedData data = getSavedData(stack, level);
            if (data != null && data.dimension == level.dimension()) {
                Charts.discoverIcons(level, player, stack, data);
            }
        }
    }
}
