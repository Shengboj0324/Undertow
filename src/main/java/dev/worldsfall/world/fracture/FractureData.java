package dev.worldsfall.world.fracture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldsfall.Worldsfall;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persistent state of the Fracture arena and boss (§11.8, §13). */
public final class FractureData extends SavedData {
    public record Fragment(BlockPos center, int radius, boolean alive) {
        public static final Codec<Fragment> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("center").forGetter(Fragment::center),
            Codec.INT.fieldOf("radius").forGetter(Fragment::radius),
            Codec.BOOL.fieldOf("alive").forGetter(Fragment::alive)
        ).apply(i, Fragment::new));
    }

    private record Snapshot(boolean built, boolean defeated, List<Fragment> fragments, List<UUID> firstKills) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("built").forGetter(Snapshot::built),
            Codec.BOOL.fieldOf("defeated").forGetter(Snapshot::defeated),
            Fragment.CODEC.listOf().fieldOf("fragments").forGetter(Snapshot::fragments),
            UUIDUtil.CODEC.listOf().fieldOf("first_kills").forGetter(Snapshot::firstKills)
        ).apply(i, Snapshot::new));
    }

    public static final Codec<FractureData> CODEC = Snapshot.CODEC.xmap(FractureData::new, FractureData::snapshot);
    public static final SavedDataType<FractureData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Worldsfall.MODID, "fracture"), FractureData::new, CODEC);

    private boolean built;
    private boolean defeated;
    private final List<Fragment> fragments = new ArrayList<>();
    private final Set<UUID> firstKills = new HashSet<>();

    public FractureData() {
    }

    private FractureData(Snapshot s) {
        this.built = s.built;
        this.defeated = s.defeated;
        this.fragments.addAll(s.fragments);
        this.firstKills.addAll(s.firstKills);
    }

    private Snapshot snapshot() {
        return new Snapshot(built, defeated, List.copyOf(fragments), List.copyOf(firstKills));
    }

    public static FractureData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean built() { return built; }
    public boolean defeated() { return defeated; }
    public List<Fragment> fragments() { return fragments; }

    public void setBuilt(List<Fragment> frags) {
        built = true;
        fragments.clear();
        fragments.addAll(frags);
        setDirty();
    }

    public void setFragment(int index, Fragment f) {
        fragments.set(index, f);
        setDirty();
    }

    public void markDefeated() {
        defeated = true;
        setDirty();
    }

    /** Returns true if this was the player's first kill (Heart of Stillness, music disc). */
    public boolean recordKill(UUID player) {
        boolean first = firstKills.add(player);
        setDirty();
        return first;
    }

    public void resetBuilt() {
        built = false;
        setDirty();
    }
}
