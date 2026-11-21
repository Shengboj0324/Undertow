package dev.worldsfall.gap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldsfall.Worldsfall;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The world's one Pilgrim's Causeway: its bearing, its floating segments (planned once, built lazily as players come near,
 * so it also appears in existing worlds), which gaps have been restored and by whom.
 */
public final class CausewayData extends SavedData {
    /** One floating segment along the bearing, from radius r0 to r1 at deck height y. */
    public record Segment(int r0, int r1, int y, boolean waystation) {
        public static final Codec<Segment> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("r0").forGetter(Segment::r0), Codec.INT.fieldOf("r1").forGetter(Segment::r1),
            Codec.INT.fieldOf("y").forGetter(Segment::y), Codec.BOOL.fieldOf("waystation").forGetter(Segment::waystation)
        ).apply(i, Segment::new));
    }

    private record Snapshot(boolean planned, double angle, List<Segment> segments, List<Integer> built, Map<String, String> restored, boolean calm) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("planned", false).forGetter(Snapshot::planned),
            Codec.DOUBLE.optionalFieldOf("angle", 0.0).forGetter(Snapshot::angle),
            Segment.CODEC.listOf().optionalFieldOf("segments", List.of()).forGetter(Snapshot::segments),
            Codec.INT.listOf().optionalFieldOf("built", List.of()).forGetter(Snapshot::built),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("restored", Map.of()).forGetter(Snapshot::restored),
            Codec.BOOL.optionalFieldOf("calm", false).forGetter(Snapshot::calm)
        ).apply(i, Snapshot::new));
    }

    public static final Codec<CausewayData> CODEC = Snapshot.CODEC.xmap(CausewayData::new, CausewayData::snapshot);
    public static final SavedDataType<CausewayData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "causeway"),
        CausewayData::new, CODEC);

    private boolean planned;
    private double angle;
    private final List<Segment> segments = new ArrayList<>();
    private final Set<Integer> built = new HashSet<>();
    /** gap index → name of the player who restored it. */
    private final Map<Integer, String> restored = new HashMap<>();
    private boolean calm;

    public CausewayData() {}

    private CausewayData(Snapshot s) {
        planned = s.planned;
        angle = s.angle;
        segments.addAll(s.segments);
        built.addAll(s.built);
        s.restored.forEach((k, v) -> restored.put(Integer.parseInt(k), v));
        calm = s.calm;
    }

    private Snapshot snapshot() {
        Map<String, String> r = new HashMap<>();
        restored.forEach((k, v) -> r.put(Integer.toString(k), v));
        return new Snapshot(planned, angle, List.copyOf(segments), List.copyOf(built), r, calm);
    }

    public static CausewayData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean planned() { return planned; }
    public double angle() { return angle; }
    public List<Segment> segments() { return List.copyOf(segments); }
    public int gaps() { return Math.max(0, segments.size() - 1); }
    public boolean built(int i) { return built.contains(i); }
    public boolean restored(int gap) { return restored.containsKey(gap); }
    public String restoredBy(int gap) { return restored.get(gap); }
    public int restoredCount() { return restored.size(); }
    public boolean calm() { return calm; }

    public void plan(double bearing, List<Segment> segs) {
        planned = true;
        angle = bearing;
        segments.clear();
        segments.addAll(segs);
        setDirty();
    }

    public void markBuilt(int i) {
        if (built.add(i)) {
            setDirty();
        }
    }

    public boolean markRestored(int gap, String by) {
        if (restored.containsKey(gap)) {
            return false;
        }
        restored.put(gap, by);
        setDirty();
        return true;
    }

    public void setCalm() {
        calm = true;
        setDirty();
    }
}
