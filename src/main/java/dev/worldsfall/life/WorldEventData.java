package dev.worldsfall.life;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldsfall.Worldsfall;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The running world event and what each scheduled event has already done (so restarts never repeat or lose one). */
public final class WorldEventData extends SavedData {
    private record Snapshot(int current, long start, long end, BlockPos site, List<BlockPos> placed, Map<String, Long> lastRun,
                            long kingTelegraphed, long kingFinished, boolean pausedTide) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("current", 0).forGetter(Snapshot::current),
            Codec.LONG.optionalFieldOf("start", 0L).forGetter(Snapshot::start),
            Codec.LONG.optionalFieldOf("end", 0L).forGetter(Snapshot::end),
            BlockPos.CODEC.optionalFieldOf("site", BlockPos.ZERO).forGetter(Snapshot::site),
            BlockPos.CODEC.listOf().optionalFieldOf("placed", List.of()).forGetter(Snapshot::placed),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("last_run", Map.of()).forGetter(Snapshot::lastRun),
            Codec.LONG.optionalFieldOf("king_telegraphed", -1L).forGetter(Snapshot::kingTelegraphed),
            Codec.LONG.optionalFieldOf("king_finished", -1L).forGetter(Snapshot::kingFinished),
            Codec.BOOL.optionalFieldOf("paused_tide", false).forGetter(Snapshot::pausedTide)
        ).apply(i, Snapshot::new));
    }

    public static final Codec<WorldEventData> CODEC = Snapshot.CODEC.xmap(WorldEventData::new, WorldEventData::snapshot);
    public static final SavedDataType<WorldEventData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "world_events"),
        WorldEventData::new, CODEC);

    private int current;
    private long start, end;
    private BlockPos site = BlockPos.ZERO;
    private final List<BlockPos> placed = new ArrayList<>();
    private final Map<String, Long> lastRun = new HashMap<>();
    private long kingTelegraphed = -1, kingFinished = -1;
    private boolean pausedTide;

    public WorldEventData() {}

    private WorldEventData(Snapshot s) {
        current = s.current;
        start = s.start;
        end = s.end;
        site = s.site;
        placed.addAll(s.placed);
        lastRun.putAll(s.lastRun);
        kingTelegraphed = s.kingTelegraphed;
        kingFinished = s.kingFinished;
        pausedTide = s.pausedTide;
    }

    private Snapshot snapshot() {
        return new Snapshot(current, start, end, site, List.copyOf(placed), Map.copyOf(lastRun), kingTelegraphed, kingFinished, pausedTide);
    }

    public static WorldEventData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public int current() { return current; }
    public long start() { return start; }
    public long end() { return end; }
    public BlockPos site() { return site; }
    public List<BlockPos> placed() { return List.copyOf(placed); }
    public long lastRun(String key) { return lastRun.getOrDefault(key, Long.MIN_VALUE); }
    public long kingTelegraphed() { return kingTelegraphed; }
    public long kingFinished() { return kingFinished; }
    public boolean pausedTide() { return pausedTide; }

    public void begin(int event, long now, long until, String key, long period) {
        current = event;
        start = now;
        end = until;
        site = BlockPos.ZERO;
        placed.clear();
        if (key != null) {
            lastRun.put(key, period);
        }
        setDirty();
    }

    public void finish() {
        current = WorldEvents.NONE;
        site = BlockPos.ZERO;
        placed.clear();
        pausedTide = false;
        setDirty();
    }

    public void setSite(BlockPos pos) { site = pos.immutable(); setDirty(); }
    public void addPlaced(BlockPos pos) { placed.add(pos.immutable()); setDirty(); }
    public void setEnd(long t) { end = t; setDirty(); }
    public void setKingTelegraphed(long index) { kingTelegraphed = index; setDirty(); }
    public void setKingFinished(long index) { kingFinished = index; setDirty(); }
    public void setPausedTide(boolean p) { pausedTide = p; setDirty(); }
}
