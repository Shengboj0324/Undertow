package dev.worldsfall.registry;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.block.entity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WFBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Worldsfall.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PerPlayerLootBlockEntity>> PER_PLAYER_LOOT = BLOCK_ENTITIES.register(
        "per_player_loot", () -> new BlockEntityType<>(PerPlayerLootBlockEntity::new, WFBlocks.SPIRE_VAULT.get(), WFBlocks.DRIFT_CACHE.get(),
            WFBlocks.SIGIL_PEDESTAL.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HoardCacheBlockEntity>> HOARD_CACHE = BLOCK_ENTITIES.register(
        "hoard_cache", () -> new BlockEntityType<>(HoardCacheBlockEntity::new, WFBlocks.HOARD_CACHE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KeystoneSocketBlockEntity>> KEYSTONE_SOCKET = BLOCK_ENTITIES.register(
        "keystone_socket", () -> new BlockEntityType<>(KeystoneSocketBlockEntity::new, WFBlocks.KEYSTONE_SOCKET.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EggCradleBlockEntity>> EGG_CRADLE = BLOCK_ENTITIES.register(
        "egg_cradle", () -> new BlockEntityType<>(EggCradleBlockEntity::new, WFBlocks.EGG_CRADLE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GravityAnchorBlockEntity>> GRAVITY_ANCHOR = BLOCK_ENTITIES.register(
        "gravity_anchor", () -> new BlockEntityType<>(GravityAnchorBlockEntity::new, WFBlocks.GRAVITY_ANCHOR.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftFrameBlockEntity>> RIFT_FRAME = BLOCK_ENTITIES.register(
        "rift_frame", () -> new BlockEntityType<>(RiftFrameBlockEntity::new, WFBlocks.FRACTURE_RIFT_FRAME.get()));

    private WFBlockEntities() {}
}
