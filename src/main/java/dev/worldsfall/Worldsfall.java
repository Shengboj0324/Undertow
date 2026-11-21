package dev.worldsfall;

import com.mojang.logging.LogUtils;
import dev.worldsfall.config.WFConfig;
import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.registry.*;
import dev.worldsfall.world.gen.WFWorldgen;
import dev.worldsfall.world.WFEvents;
import dev.worldsfall.world.WFSetup;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Where Worlds Go to Fall — an End dimension expansion. "The Nether gives power. The End gives reach."
 */
@Mod(Worldsfall.MODID)
public final class Worldsfall {
    public static final String MODID = "worldsfall";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Worldsfall(IEventBus modBus, ModContainer container) {
        WFSounds.SOUNDS.register(modBus);
        WFFluids.FLUID_TYPES.register(modBus);
        WFFluids.FLUIDS.register(modBus);
        WFBlocks.BLOCKS.register(modBus);
        WFItems.registerBlockItems();
        WFItems.ITEMS.register(modBus);
        WFComponents.COMPONENTS.register(modBus);
        dev.worldsfall.registry.WFRecipes.SERIALIZERS.register(modBus);
        WFBlockEntities.BLOCK_ENTITIES.register(modBus);
        WFEntities.ENTITIES.register(modBus);
        WFEffects.EFFECTS.register(modBus);
        WFEffects.POTIONS.register(modBus);
        WFParticles.PARTICLES.register(modBus);
        dev.worldsfall.registry.WFMapDecorations.TYPES.register(modBus);
        WFAttachments.ATTACHMENTS.register(modBus);
        WFMenus.MENUS.register(modBus);
        WFTabs.TABS.register(modBus);
        WFWorldgen.register(modBus);
        dev.worldsfall.data.WFDataTypes.register(modBus);
        dev.worldsfall.gametest.WFTestFunctions.FUNCTIONS.register(modBus);

        modBus.addListener(WFSetup::attributes);
        modBus.addListener(WFSetup::commonSetup);
        modBus.addListener(WFSetup::spawnPlacements);
        modBus.addListener(WFNetwork::register);
        modBus.addListener(WFSetup::gameTests);
        modBus.addListener(dev.worldsfall.fluid.StillwaterCauldron::dispatchers);
        modBus.addListener(dev.worldsfall.fluid.StillwaterCauldron::interactions);
        NeoForge.EVENT_BUS.addListener(dev.worldsfall.fluid.StillwaterCauldron::bottleFromSource);
        NeoForge.EVENT_BUS.register(WFEvents.class);
        NeoForge.EVENT_BUS.register(dev.worldsfall.warden.WardenEvents.class);
        NeoForge.EVENT_BUS.register(dev.worldsfall.city.CityEvents.class);

        container.registerConfig(ModConfig.Type.SYNCED, WFConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, WFConfig.CLIENT_SPEC);
        LOGGER.info("Where Worlds Go to Fall: the End gives reach.");
    }
}
