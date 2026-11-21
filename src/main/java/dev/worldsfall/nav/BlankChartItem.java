package dev.worldsfall.nav;

import dev.worldsfall.registry.WFSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * An unfilled chart. An empty Void Chart starts a 512-block chart centred where you stand; a City Chart bought from a
 * Shulker Archivist unrolls into a pre-drawn chart of the three nearest End Cities you have not been charted yet.
 */
public class BlankChartItem extends Item {
    private final boolean city;

    public BlankChartItem(Properties properties, boolean city) {
        super(properties);
        this.city = city;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (level.dimension() != Level.END) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.chart.only_end"));
            return InteractionResult.FAIL;
        }
        ItemStack chart = city ? Charts.cityChart(server, player)
            : Charts.create(server, player.getBlockX(), player.getBlockZ(), 2, false, "void");
        if (chart == null) {
            player.sendOverlayMessage(Component.translatable("message.worldsfall.chart.no_cities"));
            return InteractionResult.FAIL;
        }
        level.playSound(null, player.blockPosition(), city ? WFSounds.ENTITY_SHULKER_ARCHIVIST_READ.get() : SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
            SoundSource.PLAYERS, 1.0f, 1.0f);
        ItemStack held = player.getItemInHand(hand);
        held.consume(1, player);
        if (held.isEmpty()) {
            player.setItemInHand(hand, chart);
        } else if (!player.getInventory().add(chart)) {
            player.spawnAtLocation((ServerLevel) level, chart);
        }
        return InteractionResult.SUCCESS;
    }
}
