package dev.worldsfall.client.expansion;

import dev.worldsfall.client.model.JsonModels;
import dev.worldsfall.network.ClientBridge;
import dev.worldsfall.registry.WFEntities;
import net.minecraft.world.entity.EntityTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client registration for the expansion (1.2 → 2.0): renderers, model layers, payload handlers, HUD and sky hooks. */
public final class ExpansionClient {
    private ExpansionClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ExpansionClient::layers);
        modBus.addListener(ExpansionClient::renderers);
        ClientBridge.wardenFx = WardenClient::handle;
        ClientBridge.latticeMenu = LatticeScreen::open;
        ClientBridge.zenith = ZenithClient::accept;
        ClientBridge.restoration = p -> dev.worldsfall.client.ClientWorldState.get().acceptRestoration(p);
    }

    private static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(ZenithRenderers.LEVIATHAN, () -> JsonModels.load("leviathan_drifter", "body", "head", "jaw", "left_fin", "left_fin_tip",
            "right_fin", "right_fin_tip", "tail_1", "tail_2", "tail_fluke", "back_ridge"));
        e.registerLayerDefinition(ZenithRenderers.HARRIER, () -> JsonModels.load("gale_harrier", "body", "head", "beak", "left_wing", "left_wing_tip",
            "right_wing", "right_wing_tip", "tail", "left_leg", "right_leg"));
        e.registerLayerDefinition(LifeRenderers.LANTERN_MOTH, () -> JsonModels.load("lantern_moth", "body", "antennae", "left_wing", "right_wing"));
        e.registerLayerDefinition(WardenRenderers.LUMEN_HEART, () -> JsonModels.load("lumen_heart", "core", "shard_1", "shard_2", "shard_3", "shard_4", "roots"));
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        // the vanilla dragon renderer plus the Warden Cycle poses; the Elder Warden uses the same model
        e.registerEntityRenderer(EntityTypes.ENDER_DRAGON, WardenRenderers.DragonRenderer::new);
        e.registerEntityRenderer(WFEntities.ELDER_WARDEN.get(), WardenRenderers.DragonRenderer::new);
        e.registerEntityRenderer(WFEntities.LUMEN_HEART.get(), WardenRenderers.LumenHeartRenderer::new);
        e.registerEntityRenderer(WFEntities.SHULKER_ARCHIVIST.get(), ArchivistRenderer::new);
        e.registerEntityRenderer(WFEntities.LANTERN_MOTH.get(), LifeRenderers.MothRenderer::new);
        e.registerEntityRenderer(WFEntities.LEVIATHAN_DRIFTER.get(), ZenithRenderers.LeviathanRenderer::new);
        e.registerEntityRenderer(WFEntities.GALE_HARRIER.get(), ZenithRenderers.HarrierRenderer::new);
    }
}
