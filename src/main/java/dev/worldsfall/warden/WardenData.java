package dev.worldsfall.warden;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldsfall.Worldsfall;
import dev.worldsfall.world.tide.TideState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * World state of the Warden Cycle: the pacts read from the respawn crystals, the pacts of the fight in progress, the
 * Descending Warden stage, the first fight's Wardensong progress and the Release.
 */
public final class WardenData extends SavedData {
    /** Release states: none → listening (60 s vigil) → bowing → departing → released. */
    public static final int RELEASE_NONE = 0, RELEASE_LISTENING = 1, RELEASE_BOWING = 2, RELEASE_DEPARTING = 3, RELEASE_DONE = 4;
    /** Descending Warden stages. */
    public static final int DEPTHS_RIM = 0, DEPTHS_DIVE = 1, DEPTHS_GALLERY = 2, DEPTHS_DRIFT = 3;

    private record Snapshot(List<Pact> pending, boolean pendingStillness, List<Pact> active, Optional<UUID> dragon, int release, int releaseTicks,
                            Optional<BlockPos> elderPerch, boolean elderSpawned, int songLines, int crackFlags, boolean galleryBuilt, int depthsStage,
                            int tideRadius, List<String> learned, Optional<TideState> savedTide, long fightStart) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Pact.CODEC.listOf().optionalFieldOf("pending", List.of()).forGetter(Snapshot::pending),
            Codec.BOOL.optionalFieldOf("pending_stillness", false).forGetter(Snapshot::pendingStillness),
            Pact.CODEC.listOf().optionalFieldOf("active", List.of()).forGetter(Snapshot::active),
            UUIDUtil.CODEC.optionalFieldOf("dragon").forGetter(Snapshot::dragon),
            Codec.INT.optionalFieldOf("release", 0).forGetter(Snapshot::release),
            Codec.INT.optionalFieldOf("release_ticks", 0).forGetter(Snapshot::releaseTicks),
            BlockPos.CODEC.optionalFieldOf("elder_perch").forGetter(Snapshot::elderPerch),
            Codec.BOOL.optionalFieldOf("elder_spawned", false).forGetter(Snapshot::elderSpawned),
            Codec.INT.optionalFieldOf("song_lines", 0).forGetter(Snapshot::songLines),
            Codec.INT.optionalFieldOf("crack_flags", 0).forGetter(Snapshot::crackFlags),
            Codec.BOOL.optionalFieldOf("gallery_built", false).forGetter(Snapshot::galleryBuilt),
            Codec.INT.optionalFieldOf("depths_stage", 0).forGetter(Snapshot::depthsStage),
            Codec.INT.optionalFieldOf("tide_radius", 0).forGetter(Snapshot::tideRadius),
            Codec.STRING.listOf().optionalFieldOf("learned", List.of()).forGetter(Snapshot::learned),
            TideState.CODEC.optionalFieldOf("saved_tide").forGetter(Snapshot::savedTide),
            Codec.LONG.optionalFieldOf("fight_start", 0L).forGetter(Snapshot::fightStart)
        ).apply(i, Snapshot::new));
    }

    public static final Codec<WardenData> CODEC = Snapshot.CODEC.xmap(WardenData::new, WardenData::snapshot);
    public static final SavedDataType<WardenData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "warden"),
        WardenData::new, CODEC);

    private final Set<Pact> pending = EnumSet.noneOf(Pact.class);
    private boolean pendingStillness;
    private final Set<Pact> active = EnumSet.noneOf(Pact.class);
    private UUID dragon;
    private int release;
    private int releaseTicks;
    private BlockPos elderPerch;
    private boolean elderSpawned;
    private int songLines;
    private int crackFlags;
    private boolean galleryBuilt;
    private int depthsStage;
    private int tideRadius;
    private final List<String> learned = new ArrayList<>();
    private TideState savedTide;
    private long fightStart;

    public WardenData() {}

    private WardenData(Snapshot s) {
        pending.addAll(s.pending);
        pendingStillness = s.pendingStillness;
        active.addAll(s.active);
        dragon = s.dragon.orElse(null);
        release = s.release;
        releaseTicks = s.releaseTicks;
        elderPerch = s.elderPerch.orElse(null);
        elderSpawned = s.elderSpawned;
        songLines = s.songLines;
        crackFlags = s.crackFlags;
        galleryBuilt = s.galleryBuilt;
        depthsStage = s.depthsStage;
        tideRadius = s.tideRadius;
        learned.addAll(s.learned);
        savedTide = s.savedTide.orElse(null);
        fightStart = s.fightStart;
    }

    private Snapshot snapshot() {
        return new Snapshot(List.copyOf(pending), pendingStillness, List.copyOf(active), Optional.ofNullable(dragon), release, releaseTicks,
            Optional.ofNullable(elderPerch), elderSpawned, songLines, crackFlags, galleryBuilt, depthsStage, tideRadius, List.copyOf(learned),
            Optional.ofNullable(savedTide), fightStart);
    }

    public static WardenData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // ---- pending (read from the four respawn crystals)
    public Set<Pact> pending() { return pending.isEmpty() ? EnumSet.noneOf(Pact.class) : EnumSet.copyOf(pending); }
    public boolean pendingStillness() { return pendingStillness; }

    public void setPending(Set<Pact> pacts, boolean stillness) {
        if (!pending.equals(pacts) || pendingStillness != stillness) {
            pending.clear();
            pending.addAll(pacts);
            pendingStillness = stillness;
            setDirty();
        }
    }

    // ---- the fight in progress
    public Set<Pact> active() { return active.isEmpty() ? EnumSet.noneOf(Pact.class) : EnumSet.copyOf(active); }
    public boolean has(Pact p) { return active.contains(p); }
    public int weight() { return Pact.weight(active); }
    public UUID dragon() { return dragon; }
    public long fightStart() { return fightStart; }

    public void beginFight(UUID dragonId, Set<Pact> pacts, long now) {
        active.clear();
        active.addAll(pacts);
        dragon = dragonId;
        fightStart = now;
        depthsStage = DEPTHS_RIM;
        tideRadius = pacts.contains(Pact.TIDE) ? WardenFight.TIDE_RING_START : 0;
        learned.clear();
        pending.clear();
        pendingStillness = false;
        setDirty();
    }

    public void endFight() {
        active.clear();
        dragon = null;
        depthsStage = DEPTHS_RIM;
        tideRadius = 0;
        learned.clear();
        setDirty();
    }

    public int depthsStage() { return depthsStage; }
    public void setDepthsStage(int s) { depthsStage = s; setDirty(); }
    public int tideRadius() { return tideRadius; }
    public void setTideRadius(int r) { tideRadius = r; setDirty(); }
    public List<String> learned() { return List.copyOf(learned); }

    public boolean learn(String move) {
        if (learned.contains(move)) {
            return false;
        }
        learned.add(move);
        setDirty();
        return true;
    }

    public TideState savedTide() { return savedTide; }
    public void setSavedTide(TideState t) { savedTide = t; setDirty(); }

    // ---- the first fight's story beats
    public int songLines() { return songLines; }
    public void setSongLines(int n) { songLines = n; setDirty(); }
    public boolean crackFlag(int bit) { return (crackFlags & bit) != 0; }
    public void setCrackFlag(int bit) { crackFlags |= bit; setDirty(); }
    public void clearCrackFlags() { crackFlags = 0; setDirty(); }

    // ---- the Warden's Gallery (Pact of Depths)
    public boolean galleryBuilt() { return galleryBuilt; }
    public void setGalleryBuilt() { galleryBuilt = true; setDirty(); }

    // ---- the Release and the Elder Warden
    public int release() { return release; }
    public int releaseTicks() { return releaseTicks; }
    public boolean released() { return release == RELEASE_DONE; }

    public void setRelease(int state) {
        release = state;
        releaseTicks = 0;
        setDirty();
    }

    public int tickRelease() {
        releaseTicks++;
        setDirty();
        return releaseTicks;
    }

    public BlockPos elderPerch() { return elderPerch; }
    public boolean elderSpawned() { return elderSpawned; }

    public void setElderPerch(BlockPos pos) {
        elderPerch = pos;
        setDirty();
    }

    public void setElderSpawned(boolean v) {
        elderSpawned = v;
        setDirty();
    }
}
