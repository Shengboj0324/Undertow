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

    /** Dragon Scale overlay on an elytra (cosmetic; stacks with any upgrade). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ELYTRA_SCALED = COMPONENTS.registerComponentType(
        "elytra_scaled", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    /** What a filled Void Chart is: "void", "city" or "star" (name and icon only; drawing is the same). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> CHART_KIND = COMPONENTS.registerComponentType(
        "chart_kind", b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** A Courier Token's or Tuning Fork's remembered position. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.core.GlobalPos>> LINKED_POS = COMPONENTS.registerComponentType(
        "linked_pos", b -> b.persistent(net.minecraft.core.GlobalPos.CODEC).networkSynchronized(net.minecraft.core.GlobalPos.STREAM_CODEC));
    /** A folded Void Bell's stored charge (ticks). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BELL_CHARGE = COMPONENTS.registerComponentType(
        "bell_charge", b -> b.persistent(Codec.intRange(0, 1_000_000)).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** Which Archive Codex (1–6). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CODEX_INDEX = COMPONENTS.registerComponentType(
        "codex_index", b -> b.persistent(Codec.intRange(1, 6)).networkSynchronized(ByteBufCodecs.VAR_INT));

    private WFComponents() {}
}
