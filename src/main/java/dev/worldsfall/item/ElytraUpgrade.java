package dev.worldsfall.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** The single elytra upgrade an elytra may hold (§9.7, D8). */
public enum ElytraUpgrade implements StringRepresentable {
    GALE, STILL, HOVER,
    /** A stronger Gale (Harrier Plumes, the Zenith): a bigger boost on a shorter cooldown. */
    TEMPEST;

    public static final Codec<ElytraUpgrade> CODEC = StringRepresentable.fromEnum(ElytraUpgrade::values);
    public static final StreamCodec<ByteBuf, ElytraUpgrade> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Enum::ordinal);

    /** The wing equipment asset that shows this upgrade (assets/worldsfall/equipment/elytra_<name>.json). */
    public net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> asset() {
        return asset(this, false);
    }

    /** Whether the boost-style upgrade (Gale or Tempest). */
    public boolean boosts() {
        return this == GALE || this == TEMPEST;
    }

    /**
     * The wing look for an elytra's upgrade and Dragon Scale overlay (the scales are cosmetic and stack with any upgrade):
     * {@code elytra_<upgrade>[_scaled]}, {@code elytra_scaled}, or vanilla wings when neither applies.
     */
    public static net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> asset(
            @org.jspecify.annotations.Nullable ElytraUpgrade upgrade, boolean scaled) {
        if (upgrade == null && !scaled) {
            return net.minecraft.world.item.equipment.EquipmentAssets.ELYTRA;
        }
        String name = "elytra" + (upgrade == null ? "" : "_" + upgrade.getSerializedName()) + (scaled ? "_scaled" : "");
        return net.minecraft.resources.ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID,
            net.minecraft.resources.Identifier.fromNamespaceAndPath(dev.worldsfall.Worldsfall.MODID, name));
    }

    /** Rewrites the elytra's equippable component so the vanilla wings layer draws the right look. */
    public static net.minecraft.world.item.ItemStack restyle(net.minecraft.world.item.ItemStack elytra) {
        ElytraUpgrade up = elytra.get(dev.worldsfall.registry.WFComponents.ELYTRA_UPGRADE.get());
        boolean scaled = Boolean.TRUE.equals(elytra.get(dev.worldsfall.registry.WFComponents.ELYTRA_SCALED.get()));
        elytra.set(net.minecraft.core.component.DataComponents.EQUIPPABLE,
            net.minecraft.world.item.equipment.Equippable.builder(net.minecraft.world.entity.EquipmentSlot.CHEST)
                .setEquipSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_ELYTRA).setAsset(asset(up, scaled)).setDamageOnHurt(false).build());
        return elytra;
    }

    /** Writes the upgrade and its matching wing look onto an elytra stack (the smithing recipe does the same through data). */
    public net.minecraft.world.item.ItemStack applyTo(net.minecraft.world.item.ItemStack elytra) {
        elytra.set(dev.worldsfall.registry.WFComponents.ELYTRA_UPGRADE.get(), this);
        return restyle(elytra);
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
