package dev.worldsfall.registry;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.entity.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity types (Appendix C). Sizes follow §18.4 scale references. */
public final class WFEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Worldsfall.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<VoidSkate>> VOID_SKATE = ENTITIES.registerEntityType("void_skate",
        VoidSkate::new, MobCategory.CREATURE, b -> b.sized(4.0f, 0.8f).eyeHeight(0.5f).clientTrackingRange(16).passengerAttachments(0.85f));
    public static final DeferredHolder<EntityType<?>, EntityType<ChorusGrazer>> CHORUS_GRAZER = ENTITIES.registerEntityType("chorus_grazer",
        ChorusGrazer::new, MobCategory.CREATURE, b -> b.sized(0.9f, 1.5f).eyeHeight(1.45f).clientTrackingRange(10));
    public static final DeferredHolder<EntityType<?>, EntityType<UndersideLurker>> UNDERSIDE_LURKER = ENTITIES.registerEntityType("underside_lurker",
        UndersideLurker::new, MobCategory.MONSTER, b -> b.sized(1.0f, 1.2f).eyeHeight(0.6f).clientTrackingRange(8));
    public static final DeferredHolder<EntityType<?>, EntityType<Hoarder>> HOARDER = ENTITIES.registerEntityType("hoarder",
        Hoarder::new, MobCategory.MONSTER, b -> b.sized(0.6f, 2.6f).eyeHeight(2.3f).clientTrackingRange(8));
    public static final DeferredHolder<EntityType<?>, EntityType<SpireWarden>> SPIRE_WARDEN = ENTITIES.registerEntityType("spire_warden",
        SpireWarden::new, MobCategory.MONSTER, b -> b.sized(1.1f, 3.5f).eyeHeight(3.1f).clientTrackingRange(10).fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<Echo>> ECHO = ENTITIES.registerEntityType("echo",
        Echo::new, MobCategory.MONSTER, b -> b.sized(0.6f, 2.9f).eyeHeight(2.55f).clientTrackingRange(8));
    public static final DeferredHolder<EntityType<?>, EntityType<WildShulker>> WILD_SHULKER = ENTITIES.registerEntityType("wild_shulker",
        WildShulker::new, MobCategory.MONSTER, b -> b.sized(1.0f, 1.0f).fireImmune().canSpawnFarFromPlayer().clientTrackingRange(10));
    public static final DeferredHolder<EntityType<?>, EntityType<Tidewisp>> TIDEWISP = ENTITIES.registerEntityType("tidewisp",
        Tidewisp::new, MobCategory.AMBIENT, b -> b.sized(0.4f, 0.4f).eyeHeight(0.2f).clientTrackingRange(8).noLootTable());
    public static final DeferredHolder<EntityType<?>, EntityType<Shardling>> SHARDLING = ENTITIES.registerEntityType("shardling",
        Shardling::new, MobCategory.MONSTER, b -> b.sized(0.7f, 1.0f).fireImmune().clientTrackingRange(10).noLootTable());
    public static final DeferredHolder<EntityType<?>, EntityType<TheUnmade>> THE_UNMADE = ENTITIES.registerEntityType("the_unmade",
        TheUnmade::new, MobCategory.MONSTER, b -> b.sized(4.0f, 4.0f).eyeHeight(2.0f).fireImmune().clientTrackingRange(16).noLootTable());
    public static final DeferredHolder<EntityType<?>, EntityType<Wyrm>> WYRM = ENTITIES.registerEntityType("wyrm",
        Wyrm::new, MobCategory.CREATURE, b -> b.sized(0.6f, 0.6f).eyeHeight(0.45f).clientTrackingRange(10).fireImmune().noLootTable());
    public static final DeferredHolder<EntityType<?>, EntityType<VoidCapsule>> VOID_CAPSULE = ENTITIES.registerEntityType("void_capsule",
        VoidCapsule::new, MobCategory.MISC, b -> b.sized(0.8f, 1.2f).clientTrackingRange(16).fireImmune().noLootTable());
    public static final DeferredHolder<EntityType<?>, EntityType<FlyingBlock>> FLYING_BLOCK = ENTITIES.registerEntityType("flying_block",
        FlyingBlock::new, MobCategory.MISC, b -> b.sized(0.98f, 0.98f).clientTrackingRange(10).updateInterval(20).noLootTable());
    public static final DeferredHolder<EntityType<?>, EntityType<RiftHook>> RIFT_HOOK = ENTITIES.registerEntityType("rift_hook",
        RiftHook::new, MobCategory.MISC, b -> b.sized(0.3f, 0.3f).clientTrackingRange(6).updateInterval(1).noSave().noSummon().noLootTable());
    public static final DeferredHolder<EntityType<?>, EntityType<LurkerSnare>> LURKER_SNARE = ENTITIES.registerEntityType("lurker_snare",
        LurkerSnare::new, MobCategory.MISC, b -> b.sized(0.3f, 0.3f).clientTrackingRange(6).updateInterval(1).noSave().noLootTable());
    public static final DeferredHolder<EntityType<?>, EntityType<UnmadeShard>> UNMADE_SHARD = ENTITIES.registerEntityType("unmade_shard",
        UnmadeShard::new, MobCategory.MISC, b -> b.sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(1).noSave().noLootTable());
    public static final DeferredHolder<EntityType<?>, EntityType<TetherPylon>> TETHER_PYLON = ENTITIES.registerEntityType("tether_pylon",
        TetherPylon::new, MobCategory.MISC, b -> b.sized(1.4f, 2.0f).clientTrackingRange(16).fireImmune().noLootTable());

    // ================= Expansion 1.2: the Warden Cycle
    public static final DeferredHolder<EntityType<?>, EntityType<dev.worldsfall.warden.LumenHeart>> LUMEN_HEART = ENTITIES.registerEntityType("lumen_heart",
        dev.worldsfall.warden.LumenHeart::new, MobCategory.MISC, b -> b.sized(2.0f, 2.0f).clientTrackingRange(16).updateInterval(Integer.MAX_VALUE)
            .fireImmune().noLootTable());
    public static final DeferredHolder<EntityType<?>, EntityType<dev.worldsfall.warden.ElderWarden>> ELDER_WARDEN = ENTITIES.registerEntityType("elder_warden",
        dev.worldsfall.warden.ElderWarden::new, MobCategory.MONSTER, b -> b.sized(16.0f, 8.0f).passengerAttachments(3.0f).fireImmune()
            .clientTrackingRange(16).noLootTable());

    // ================= Expansion 1.4: cities and navigation
    public static final DeferredHolder<EntityType<?>, EntityType<dev.worldsfall.city.ShulkerArchivist>> SHULKER_ARCHIVIST = ENTITIES.registerEntityType(
        "shulker_archivist", dev.worldsfall.city.ShulkerArchivist::new, MobCategory.CREATURE, b -> b.sized(1.0f, 1.0f).fireImmune().clientTrackingRange(10));

    private WFEntities() {}
}
