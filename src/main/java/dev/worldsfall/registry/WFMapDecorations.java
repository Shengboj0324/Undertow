package dev.worldsfall.registry;

import dev.worldsfall.Worldsfall;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Void Chart icons (proposal "Void Charts"); sprites live in {@code textures/map/decorations/}. */
public final class WFMapDecorations {
    public static final DeferredRegister<MapDecorationType> TYPES = DeferredRegister.create(Registries.MAP_DECORATION_TYPE, Worldsfall.MODID);

    public static final DeferredHolder<MapDecorationType, MapDecorationType> CITY = reg("end_city");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> SPIRE = reg("fallen_spire");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> HOARD = reg("hoard");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> BASIN = reg("stillwater_basin");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> WRECK = reg("driftwreck");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> ROOST = reg("skate_roost");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> MAW = reg("tide_maw");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> MOORING = reg("mooring");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> GATEWAY = reg("gateway");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> CAPSULE = reg("void_capsule");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> ORRERY = reg("orrery");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> CURRENT = reg("sky_current");

    private WFMapDecorations() {}

    private static DeferredHolder<MapDecorationType, MapDecorationType> reg(String name) {
        return TYPES.register(name, () -> new MapDecorationType(Identifier.fromNamespaceAndPath(Worldsfall.MODID, name), true, false));
    }
}
