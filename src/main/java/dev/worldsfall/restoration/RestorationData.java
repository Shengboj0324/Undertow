package dev.worldsfall.restoration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldsfall.Worldsfall;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The world's Stillness (proposal "Endgame: the Restoration of the End"): one score that only goes up, a per-player
 * contribution map (so the final advancement only goes to contributors and the hoard-barter cap is per player), and the set
 * of one-time acts already counted (each Driftwreck, Orrery, Codex… counts once).
 */
public final class RestorationData extends SavedData {
    private record Snapshot(double score, Map<String, Double> contributions, List<String> counted, int stage) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("score", 0.0).forGetter(Snapshot::score),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("contributions", Map.of()).forGetter(Snapshot::contributions),
            Codec.STRING.listOf().optionalFieldOf("counted", List.of()).forGetter(Snapshot::counted),
            Codec.INT.optionalFieldOf("stage", 0).forGetter(Snapshot::stage)
        ).apply(i, Snapshot::new));
    }

    public static final Codec<RestorationData> CODEC = Snapshot.CODEC.xmap(RestorationData::new, RestorationData::snapshot);
    public static final SavedDataType<RestorationData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "restoration"),
        RestorationData::new, CODEC);

    private double score;
    private final Map<UUID, Double> contributions = new HashMap<>();
    private final Map<UUID, Double> barter = new HashMap<>();
    private final Set<String> counted = new HashSet<>();
    private int stage;

    public RestorationData() {}

    private RestorationData(Snapshot s) {
        score = s.score;
        s.contributions.forEach((k, v) -> {
            if (k.startsWith("barter:")) {
                barter.put(UUID.fromString(k.substring(7)), v);
            } else {
                contributions.put(UUID.fromString(k), v);
            }
        });
        counted.addAll(s.counted);
        stage = s.stage;
    }

    private Snapshot snapshot() {
        Map<String, Double> m = new HashMap<>();
        contributions.forEach((k, v) -> m.put(k.toString(), v));
        barter.forEach((k, v) -> m.put("barter:" + k, v));
        return new Snapshot(score, m, List.copyOf(counted), stage);
    }

    public static RestorationData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public double score() { return score; }
    public int stage() { return stage; }
    public Map<UUID, Double> contributions() { return Map.copyOf(contributions); }
    public boolean contributed(UUID player) { return contributions.getOrDefault(player, 0.0) > 0; }

    /** Adds points (never removes). {@code key} makes a one-time act count once; {@code player} may be null for world acts. */
    public boolean add(double points, UUID player, String key) {
        if (key != null && !counted.add(key)) {
            return false;
        }
        score += points;
        if (player != null) {
            contributions.merge(player, points, Double::sum);
        }
        setDirty();
        return true;
    }

    public double barterPoints(UUID player) { return barter.getOrDefault(player, 0.0); }

    public void addBarter(UUID player, double points) {
        barter.merge(player, points, Double::sum);
        setDirty();
    }

    public void setStage(int s) {
        stage = s;
        setDirty();
    }

}
