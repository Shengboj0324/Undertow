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
    }

    private static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(WardenRenderers.LUMEN_HEART, () -> JsonModels.load("lumen_heart", "core", "shard_1", "shard_2", "shard_3", "shard_4", "roots"));
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        // the vanilla dragon renderer plus the Warden Cycle poses; the Elder Warden uses the same model
        e.registerEntityRenderer(EntityTypes.ENDER_DRAGON, WardenRenderers.DragonRenderer::new);
        e.registerEntityRenderer(WFEntities.ELDER_WARDEN.get(), WardenRenderers.DragonRenderer::new);
        e.registerEntityRenderer(WFEntities.LUMEN_HEART.get(), WardenRenderers.LumenHeartRenderer::new);
        e.registerEntityRenderer(WFEntities.SHULKER_ARCHIVIST.get(), ArchivistRenderer::new);
    }
}
