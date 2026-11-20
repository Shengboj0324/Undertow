package dev.worldsfall.world;

import dev.worldsfall.entity.*;
import dev.worldsfall.registry.WFEntities;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/** Mod-bus setup: attributes, spawn rules, GameTests. */
public final class WFSetup {
    private WFSetup() {}

    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(WFEntities.VOID_SKATE.get(), VoidSkate.createAttributes().build());
        e.put(WFEntities.CHORUS_GRAZER.get(), ChorusGrazer.createAttributes().build());
        e.put(WFEntities.UNDERSIDE_LURKER.get(), UndersideLurker.createAttributes().build());
        e.put(WFEntities.HOARDER.get(), Hoarder.createAttributes().build());
        e.put(WFEntities.SPIRE_WARDEN.get(), SpireWarden.createAttributes().build());
        e.put(WFEntities.ECHO.get(), Echo.createAttributes().build());
        e.put(WFEntities.WILD_SHULKER.get(), WildShulker.createWildAttributes().build());
        e.put(WFEntities.TIDEWISP.get(), Tidewisp.createAttributes().build());
        e.put(WFEntities.SHARDLING.get(), Shardling.createAttributes().build());
        e.put(WFEntities.THE_UNMADE.get(), TheUnmade.createAttributes().build());
        e.put(WFEntities.WYRM.get(), Wyrm.createAttributes().build());
    }

    public static void spawnPlacements(RegisterSpawnPlacementsEvent e) {
        e.register(WFEntities.CHORUS_GRAZER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) -> level.getBlockState(pos.below()).isSolid(), RegisterSpawnPlacementsEvent.Operation.REPLACE);
        e.register(WFEntities.UNDERSIDE_LURKER.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            UndersideLurker::checkSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        e.register(WFEntities.VOID_SKATE.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) -> level.getBlockState(pos).isAir(), RegisterSpawnPlacementsEvent.Operation.REPLACE);
        e.register(WFEntities.TIDEWISP.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) -> level.getBlockState(pos).isAir(), RegisterSpawnPlacementsEvent.Operation.REPLACE);
        e.register(WFEntities.WILD_SHULKER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, level, reason, pos, random) -> true, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    public static void gameTests(RegisterGameTestsEvent e) {
        dev.worldsfall.gametest.WFGameTests.register(e);
    }
}
