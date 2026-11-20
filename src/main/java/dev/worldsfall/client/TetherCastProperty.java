package dev.worldsfall.client;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.entity.RiftHook;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** {@code worldsfall:tether_cast}: true while the holder's Rift Tether hook is out. */
public record TetherCastProperty() implements ConditionalItemModelProperty {
    public static final MapCodec<TetherCastProperty> MAP_CODEC = MapCodec.unit(new TetherCastProperty());

    @Override
    public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext context) {
        return owner instanceof Player p && RiftHook.activeFor(p) != null;
    }

    @Override
    public MapCodec<TetherCastProperty> type() {
        return MAP_CODEC;
    }
}
