package dev.worldsfall.client;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.client.block.WFBlockEntityRenderers;
import dev.worldsfall.client.entity.CreatureRenderers;
import dev.worldsfall.client.entity.FxRenderers;
import dev.worldsfall.client.entity.WFLayers;
import dev.worldsfall.client.fx.SunderingFx;
import dev.worldsfall.client.hud.WFHud;
import dev.worldsfall.client.particle.WFParticle;
import dev.worldsfall.client.render.TidePlaneRenderer;
import dev.worldsfall.client.render.WFPipelines;
import dev.worldsfall.client.render.WFSkyRenderer;
import dev.worldsfall.client.screen.SatchelScreen;
import dev.worldsfall.data.EndSkyBiomeModifier;
import dev.worldsfall.entity.RiderInput;
import dev.worldsfall.network.ClientBridge;
import dev.worldsfall.registry.WFBlockEntities;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFFluids;
import dev.worldsfall.registry.WFMenus;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Client entrypoint: renderers, models, sky, tide plane, particles, HUD, screens, fluid rendering. */
@Mod(value = Worldsfall.MODID, dist = Dist.CLIENT)
public final class WorldsfallClient {
    public WorldsfallClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        dev.worldsfall.client.showcase.ShowcaseDirector.init();
        dev.worldsfall.client.showcase.MultiplayerProbe.init();
        modBus.addListener(WFLayers::register);
        modBus.addListener(WorldsfallClient::renderers);
        modBus.addListener(WorldsfallClient::playerLayers);
        modBus.addListener((net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent e) -> e.registerAvatarEntityModifier(
            new net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier() {
                @Override
                public <T extends net.minecraft.world.entity.Avatar & net.minecraft.client.entity.ClientAvatarEntity> void accept(T avatar,
                        net.minecraft.client.renderer.entity.state.AvatarRenderState state) {
                    state.setRenderData(dev.worldsfall.client.entity.WyrmShoulderLayer.KEY,
                        avatar.getExistingDataOrNull(dev.worldsfall.registry.WFAttachments.WYRM_SHOULDERS));
                }
            }));
        modBus.addListener(WFPipelines::register);
        modBus.addListener(WFParticle::register);
        modBus.addListener(WFHud::register);
        modBus.addListener(WorldsfallClient::sky);
        modBus.addListener(WorldsfallClient::fluids);
        modBus.addListener(WorldsfallClient::screens);
        modBus.addListener(WorldsfallClient::itemProperties);
        NeoForge.EVENT_BUS.register(ClientEvents.class);
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentBlocks e) -> TidePlaneRenderer.afterTranslucent(e));
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterLevel e) -> TidePlaneRenderer.afterLevel(e));
        NeoForge.EVENT_BUS.addListener(SunderingFx::tick);
        RiderInput.clientProvider = p -> p instanceof LocalPlayer lp ? lp.input.keyPresses : net.minecraft.world.entity.player.Input.EMPTY;
        ClientBridge.sunderingFx = SunderingFx::start;
        ClientBridge.echoTablet = WFHud::showTablet;
        ClientBridge.voidLens = dev.worldsfall.client.hud.VoidLensOverlay::acceptWrecks;
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterLevel e) -> dev.worldsfall.client.hud.VoidLensOverlay.captureCamera(e));
        modBus.addListener((net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent e) ->
            e.registerItem(dev.worldsfall.client.hud.VoidLensOverlay.itemExtensions(), dev.worldsfall.registry.WFItems.VOID_LENS.get()));
    }

    private static void playerLayers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers e) {
        for (var skin : e.getSkins()) {
            var r = e.getPlayerRenderer(skin);
            if (r != null) {
                r.addLayer(new dev.worldsfall.client.entity.WyrmShoulderLayer(r, e.getEntityModels()));
            }
        }
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(WFEntities.VOID_SKATE.get(), CreatureRenderers.SkateRenderer::new);
        e.registerEntityRenderer(WFEntities.CHORUS_GRAZER.get(), CreatureRenderers.GrazerRenderer::new);
        e.registerEntityRenderer(WFEntities.UNDERSIDE_LURKER.get(), CreatureRenderers.LurkerRenderer::new);
        e.registerEntityRenderer(WFEntities.HOARDER.get(), CreatureRenderers.HoarderRenderer::new);
        e.registerEntityRenderer(WFEntities.SPIRE_WARDEN.get(), CreatureRenderers.WardenRenderer::new);
        e.registerEntityRenderer(WFEntities.ECHO.get(), CreatureRenderers.EchoRenderer::new);
        e.registerEntityRenderer(WFEntities.WILD_SHULKER.get(), CreatureRenderers.WildShulkerRenderer::new);
        e.registerEntityRenderer(WFEntities.WYRM.get(), CreatureRenderers.WyrmRenderer::new);
        e.registerEntityRenderer(WFEntities.SHARDLING.get(), CreatureRenderers.ShardlingRenderer::new);
        e.registerEntityRenderer(WFEntities.TIDEWISP.get(), FxRenderers.WispRenderer::new);
        e.registerEntityRenderer(WFEntities.THE_UNMADE.get(), FxRenderers.UnmadeRenderer::new);
        e.registerEntityRenderer(WFEntities.TETHER_PYLON.get(), FxRenderers.PylonRenderer::new);
        e.registerEntityRenderer(WFEntities.VOID_CAPSULE.get(), FxRenderers.CapsuleRenderer::new);
        e.registerEntityRenderer(WFEntities.RIFT_HOOK.get(), FxRenderers.HookRenderer::new);
        e.registerEntityRenderer(WFEntities.FLYING_BLOCK.get(), FxRenderers.FlyingBlockRenderer::new);
        e.registerEntityRenderer(WFEntities.UNMADE_SHARD.get(), FxRenderers.ShardRenderer::new);
        e.registerEntityRenderer(WFEntities.LURKER_SNARE.get(), FxRenderers.SnareRenderer::new);
        e.registerBlockEntityRenderer(WFBlockEntities.EGG_CRADLE.get(), WFBlockEntityRenderers.CradleRenderer::new);
        e.registerBlockEntityRenderer(WFBlockEntities.GRAVITY_ANCHOR.get(), WFBlockEntityRenderers.AnchorRenderer::new);
    }

    private static void sky(RegisterCustomEnvironmentEffectRendererEvent e) {
        e.registerSkyboxRenderer(EndSkyBiomeModifier.SKY, new WFSkyRenderer(false));
        e.registerSkyboxRenderer(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "fracture_sky"), new WFSkyRenderer(true));
    }

    private static void fluids(RegisterFluidModelsEvent e) {
        Identifier still = Identifier.fromNamespaceAndPath(Worldsfall.MODID, "block/stillwater_still");
        Identifier flow = Identifier.fromNamespaceAndPath(Worldsfall.MODID, "block/stillwater_flow");
        Identifier overlay = Identifier.fromNamespaceAndPath(Worldsfall.MODID, "block/stillwater_overlay");
        e.register(new FluidModel.Unbaked(new Material(still), new Material(flow), new Material(overlay),
            (net.neoforged.neoforge.client.fluid.FluidTintSource) null), WFFluids.STILLWATER, WFFluids.FLOWING_STILLWATER);
    }

    private static void screens(RegisterMenuScreensEvent e) {
        e.register(WFMenus.SATCHEL.get(), SatchelScreen::new);
    }

    private static void itemProperties(RegisterConditionalItemModelPropertyEvent e) {
        e.register(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "tether_cast"), TetherCastProperty.MAP_CODEC);
    }
}
