package dev.worldsfall.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldsfall.Worldsfall;
import net.minecraft.core.UUIDUtil;
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
import java.util.UUID;

/** End City bookkeeping: cities already visited by the Archivist spawner, and the cities each player has been charted. */
public final class CityData extends SavedData {
    private record Charted(UUID player, List<Long> cities) {
        static final Codec<Charted> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("player").forGetter(Charted::player), Codec.LONG.listOf().fieldOf("cities").forGetter(Charted::cities)
        ).apply(i, Charted::new));
    }

    private record Snapshot(List<Long> seen, List<Charted> charted) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.listOf().optionalFieldOf("seen", List.of()).forGetter(Snapshot::seen),
            Charted.CODEC.listOf().optionalFieldOf("charted", List.of()).forGetter(Snapshot::charted)
        ).apply(i, Snapshot::new));
    }

    public static final Codec<CityData> CODEC = Snapshot.CODEC.xmap(CityData::new, CityData::snapshot);
    public static final SavedDataType<CityData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "cities"),
        CityData::new, CODEC);

    private final Set<Long> seen = new HashSet<>();
    private final Map<UUID, Set<Long>> charted = new HashMap<>();

    public CityData() {}

    private CityData(Snapshot s) {
        seen.addAll(s.seen);
        s.charted.forEach(c -> charted.put(c.player, new HashSet<>(c.cities)));
    }

    private Snapshot snapshot() {
        List<Charted> c = new ArrayList<>();
        charted.forEach((p, set) -> c.add(new Charted(p, List.copyOf(set))));
        return new Snapshot(List.copyOf(seen), c);
    }

    public static CityData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean seen(long city) {
        return seen.contains(city);
    }

    /** Marks a city (by start chunk) as handled; false if it already was. */
    public boolean markSeen(long city) {
        boolean added = seen.add(city);
        if (added) {
            setDirty();
        }
        return added;
    }

    public Set<Long> chartedBy(UUID player) {
        return Set.copyOf(charted.getOrDefault(player, Set.of()));
    }

    public void chart(UUID player, Iterable<Long> cities) {
        Set<Long> set = charted.computeIfAbsent(player, p -> new HashSet<>());
        for (long c : cities) {
            set.add(c);
        }
        setDirty();
    }
}
