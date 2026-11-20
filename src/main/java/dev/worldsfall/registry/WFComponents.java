package dev.worldsfall.registry;

import com.mojang.serialization.Codec;
import dev.worldsfall.Worldsfall;
import dev.worldsfall.item.ElytraUpgrade;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data components (§20.5): elytra upgrade, satchel size, tablet index, slumbering Wyrm data, compass binding. */
public final class WFComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Worldsfall.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ElytraUpgrade>> ELYTRA_UPGRADE = COMPONENTS.registerComponentType(
        "elytra_upgrade", b -> b.persistent(ElytraUpgrade.CODEC).networkSynchronized(ElytraUpgrade.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SATCHEL_SIZE = COMPONENTS.registerComponentType(
        "satchel_size", b -> b.persistent(Codec.intRange(9, 18)).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TABLET_INDEX = COMPONENTS.registerComponentType(
        "tablet_index", b -> b.persistent(Codec.intRange(1, 12)).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CustomData>> WYRM_DATA = COMPONENTS.registerComponentType(
        "wyrm_data", b -> b.persistent(CustomData.CODEC).networkSynchronized(CustomData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> GATEWAY_BOUND = COMPONENTS.registerComponentType(
        "gateway_bound", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    private WFComponents() {}
}
