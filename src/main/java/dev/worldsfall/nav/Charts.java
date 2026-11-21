package dev.worldsfall.nav;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.entity.VoidCapsule;
import dev.worldsfall.registry.WFComponents;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFMapDecorations;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Void Charts (proposal "Navigation"): maps that understand the End. Islands are drawn in their own block colours with
 * height contours every five blocks, the void is a dark star field, and anything under the tide is shaded teal. Icons
 * appear for the places a holder passes near. City and Star Charts are pre-drawn from the terrain generator, so they show
 * islands the player has never loaded.
 */
public final class Charts {
    public static final int BAND = 5;
    private static final byte VOID = MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.LOW);
    private static final byte STAR = MapColor.COLOR_PURPLE.getPackedId(MapColor.Brightness.HIGH);
    private static final byte STAR_DIM = MapColor.COLOR_BLUE.getPackedId(MapColor.Brightness.LOW);

    private Charts() {}

    // ------------------------------------------------------------------ colour

    /** The chart colour of one column: {@code height} is the first free Y (the vanilla heightmap value). */
    public static byte colour(Level level, int x, int z, int height, @Nullable BlockState top, int northHeight) {
        if (height <= level.getMinY() + 1) {
            int h = (x * 73_856_093) ^ (z * 19_349_663);
            h = h * h * 31 + h;
            return (h >>> 7) % 97 == 0 ? STAR : (h >>> 9) % 61 == 0 ? STAR_DIM : VOID;
        }
        int band = Math.floorDiv(height, BAND);
        boolean contour = northHeight > level.getMinY() + 1 && Math.floorDiv(northHeight, BAND) != band;
        MapColor.Brightness b = contour ? MapColor.Brightness.LOW : (band & 1) == 0 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH;
        if (Tides.appliesTo(level) && Tides.isBelowTide(level, height - 0.5)) {
            return MapColor.WARPED_NYLIUM.getPackedId(b);
        }
        MapColor c = top == null ? MapColor.SAND : top.getMapColor(level, BlockPos.ZERO);
        if (c == MapColor.NONE) {
            c = MapColor.SAND;
        }
        return c.getPackedId(b);
    }

    // ------------------------------------------------------------------ painting while held

    /** Paints the part of the chart around the holder whose chunks are loaded; a sixteenth of the columns per tick. */
    public static void paintAround(ServerLevel level, Player holder, MapItemSavedData data, int step) {
        int scale = 1 << data.scale;
        int px = (int) Math.floor((holder.getX() - data.centerX) / scale) + 64;
        int pz = (int) Math.floor((holder.getZ() - data.centerZ) / scale) + 64;
        int radius = 128 / scale;
        for (int ix = px - radius + 1; ix < px + radius; ix++) {
            if ((ix & 15) != (step & 15) || ix < 0 || ix >= 128) {
                continue;
            }
            int north = Integer.MIN_VALUE;
            for (int iz = pz - radius - 1; iz < pz + radius; iz++) {
                if (iz < -1 || iz >= 128) {
                    continue;
                }
                int wx = (data.centerX / scale + ix - 64) * scale + scale / 2;
                int wz = (data.centerZ / scale + iz - 64) * scale + scale / 2;
                LevelChunk chunk = level.getChunkSource().getChunkNow(wx >> 4, wz >> 4);
                if (chunk == null) {
                    north = Integer.MIN_VALUE;
                    continue;
                }
                int h = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx & 15, wz & 15) + 1;
                BlockState top = h > level.getMinY() + 1 ? chunk.getBlockState(new BlockPos(wx, h - 1, wz)) : null;
                if (iz >= 0 && (ix - px) * (ix - px) + (iz - pz) * (iz - pz) < radius * radius) {
                    data.updateColor(ix, iz, colour(level, wx, wz, h, top, north == Integer.MIN_VALUE ? h : north));
                }
                north = h;
            }
        }
    }

    /** Pre-draws a whole chart from the terrain generator (no chunks are loaded); sampled every other pixel. */
    public static void prefill(ServerLevel level, MapItemSavedData data) {
        int scale = 1 << data.scale;
        var gen = level.getChunkSource().getGenerator();
        var rs = level.getChunkSource().randomState();
        int[] heights = new int[65 * 65];
        for (int gx = 0; gx <= 64; gx++) {
            for (int gz = 0; gz <= 64; gz++) {
                int wx = data.centerX + (gx * 2 - 64) * scale, wz = data.centerZ + (gz * 2 - 64) * scale;
                heights[gx * 65 + gz] = gen.getBaseHeight(wx, wz, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
            }
        }
        for (int ix = 0; ix < 128; ix++) {
            for (int iz = 0; iz < 128; iz++) {
                int h = heights[(ix / 2) * 65 + iz / 2];
                int north = iz == 0 ? h : heights[(ix / 2) * 65 + (iz - 1) / 2];
                int wx = data.centerX + (ix - 64) * scale, wz = data.centerZ + (iz - 64) * scale;
                data.setColor(ix, iz, colour(level, wx, wz, h, null, iz % 2 == 0 ? north : h));
            }
        }
        data.setDirty();
    }

    // ------------------------------------------------------------------ icons

    private static final Map<String, Holder<MapDecorationType>> STRUCTURE_ICONS = Map.ofEntries(
        Map.entry("minecraft:end_city", WFMapDecorations.CITY), Map.entry(Worldsfall.MODID + ":fallen_spire", WFMapDecorations.SPIRE),
        Map.entry(Worldsfall.MODID + ":hoard", WFMapDecorations.HOARD), Map.entry(Worldsfall.MODID + ":stillwater_basin", WFMapDecorations.BASIN),
        Map.entry(Worldsfall.MODID + ":driftwreck", WFMapDecorations.WRECK), Map.entry(Worldsfall.MODID + ":gapwreck", WFMapDecorations.WRECK),
        Map.entry(Worldsfall.MODID + ":tide_maw", WFMapDecorations.MAW), Map.entry(Worldsfall.MODID + ":orrery", WFMapDecorations.ORRERY));

    /** Adds icons for places within about eight chunks of the holder, plus known Lattice nodes and the holder's capsules. */
    public static void discoverIcons(ServerLevel level, Player holder, ItemStack chart, MapItemSavedData data) {
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        ChunkPos here = holder.chunkPosition();
        int half = 64 << data.scale;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(here.x() + dx, here.z() + dz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<Structure, it.unimi.dsi.fastutil.longs.LongSet> e : chunk.getAllReferences().entrySet()) {
                    Identifier id = registry.getKey(e.getKey());
                    var icon = id == null ? null : STRUCTURE_ICONS.get(id.toString());
                    if (icon == null) {
                        continue;
                    }
                    for (long start : e.getValue()) {
                        ChunkPos c = ChunkPos.unpack(start);
                        mark(chart, data, half, new BlockPos(c.getMiddleBlockX(), 64, c.getMiddleBlockZ()), id.getPath() + "_" + start, icon);
                    }
                }
            }
        }
        LatticeData lattice = LatticeData.get(level.getServer());
        for (LatticeData.Node n : lattice.knownBy(holder.getUUID())) {
            mark(chart, data, half, n.pos(), "node_" + n.pos().asLong(), n.kind() == LatticeData.MOORING ? WFMapDecorations.MOORING : WFMapDecorations.GATEWAY);
        }
        for (VoidCapsule capsule : level.getEntitiesOfClass(VoidCapsule.class, new AABB(holder.blockPosition()).inflate(256),
            c -> holder.getUUID().equals(c.owner()))) {
            mark(chart, data, half, capsule.blockPosition(), "capsule_" + capsule.getUUID(), WFMapDecorations.CAPSULE);
        }
    }

    private static void mark(ItemStack chart, MapItemSavedData data, int half, BlockPos pos, String key, Holder<MapDecorationType> icon) {
        if (Math.abs(pos.getX() - data.centerX) < half && Math.abs(pos.getZ() - data.centerZ) < half) {
            var existing = chart.get(net.minecraft.core.component.DataComponents.MAP_DECORATIONS);
            if (existing == null || !existing.decorations().containsKey(key)) {
                MapItemSavedData.addTargetDecoration(chart, pos, key, icon);
            }
        }
    }

    // ------------------------------------------------------------------ creating charts

    /** A new Void Chart centred exactly on {@code (x, z)} (vanilla snaps map centres to a grid; charts do not). */
    public static ItemStack create(ServerLevel level, int x, int z, int scale, boolean prefill, String kind) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", level.dimension().identifier().toString());
        tag.putInt("xCenter", x);
        tag.putInt("zCenter", z);
        tag.putByte("scale", (byte) scale);
        tag.put("colors", new ByteArrayTag(new byte[128 * 128]));
        tag.putBoolean("trackingPosition", true);
        tag.putBoolean("unlimitedTracking", true);
        MapItemSavedData data = MapItemSavedData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        if (prefill) {
            prefill(level, data);
        }
        MapId id = level.getFreeMapId();
        level.setMapData(id, data);
        ItemStack stack = new ItemStack(WFItems.VOID_CHART.get());
        stack.set(net.minecraft.core.component.DataComponents.MAP_ID, id);
        stack.set(WFComponents.CHART_KIND.get(), kind);
        return stack;
    }

    /** A City Chart: the three nearest End Cities this player has not been charted yet, pre-drawn. */
    public static @Nullable ItemStack cityChart(ServerLevel level, Player player) {
        var data = dev.worldsfall.city.CityData.get(level.getServer());
        List<ChunkPos> cities = dev.worldsfall.city.Cities.nearestCities(level, player.blockPosition(), 3, data.chartedBy(player.getUUID()));
        if (cities.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (ChunkPos c : cities) {
            minX = Math.min(minX, c.getMiddleBlockX());
            maxX = Math.max(maxX, c.getMiddleBlockX());
            minZ = Math.min(minZ, c.getMiddleBlockZ());
            maxZ = Math.max(maxZ, c.getMiddleBlockZ());
        }
        int span = Math.max(maxX - minX, maxZ - minZ) + 160;
        int scale = 1;
        while (scale < 4 && (128 << scale) < span) {
            scale++;
        }
        ItemStack chart = create(level, (minX + maxX) / 2, (minZ + maxZ) / 2, scale, true, "city");
        for (ChunkPos c : cities) {
            MapItemSavedData.addTargetDecoration(chart, new BlockPos(c.getMiddleBlockX(), 64, c.getMiddleBlockZ()), "city_" + c.pack(),
                WFMapDecorations.CITY);
        }
        data.chart(player.getUUID(), cities.stream().map(ChunkPos::pack).toList());
        chart.set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.translatable("item.worldsfall.city_chart"));
        return chart;
    }
}
