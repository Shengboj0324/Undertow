package dev.worldsfall.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** The single elytra upgrade an elytra may hold (§9.7, D8). */
public enum ElytraUpgrade implements StringRepresentable {
    GALE, STILL, HOVER;

    public static final Codec<ElytraUpgrade> CODEC = StringRepresentable.fromEnum(ElytraUpgrade::values);
    public static final StreamCodec<ByteBuf, ElytraUpgrade> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Enum::ordinal);

    /** The wing equipment asset that shows this upgrade (assets/worldsfall/equipment/elytra_<name>.json). */
    public net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> asset() {
        return net.minecraft.resources.ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID,
            net.minecraft.resources.Identifier.fromNamespaceAndPath(dev.worldsfall.Worldsfall.MODID, "elytra_" + getSerializedName()));
    }

    /** Writes the upgrade and its matching wing look onto an elytra stack (the smithing recipe does the same through data). */
    public net.minecraft.world.item.ItemStack applyTo(net.minecraft.world.item.ItemStack elytra) {
        elytra.set(dev.worldsfall.registry.WFComponents.ELYTRA_UPGRADE.get(), this);
        elytra.set(net.minecraft.core.component.DataComponents.EQUIPPABLE,
            net.minecraft.world.item.equipment.Equippable.builder(net.minecraft.world.entity.EquipmentSlot.CHEST)
                .setEquipSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_ELYTRA).setAsset(asset()).setDamageOnHurt(false).build());
        return elytra;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
