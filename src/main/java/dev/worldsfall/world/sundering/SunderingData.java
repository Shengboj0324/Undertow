package dev.worldsfall.world.sundering;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldsfall.Worldsfall;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Sundering state (§5.4): whether it happened, where the Descent and Undercroft rift are. */
public final class SunderingData extends SavedData {
    private record Snapshot(boolean done, BlockPos descent, BlockPos riftFrame, long startAt) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("done").forGetter(Snapshot::done),
            BlockPos.CODEC.fieldOf("descent").forGetter(Snapshot::descent),
            BlockPos.CODEC.fieldOf("rift_frame").forGetter(Snapshot::riftFrame),
            Codec.LONG.fieldOf("start_at").forGetter(Snapshot::startAt)
        ).apply(i, Snapshot::new));
    }

    public static final Codec<SunderingData> CODEC = Snapshot.CODEC.xmap(SunderingData::new, SunderingData::snapshot);
    public static final SavedDataType<SunderingData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "sundering"),
        SunderingData::new, CODEC);
    /** Client-side mirror (for gravity-zone detection near the center before/after the Sundering). */
    private static volatile boolean clientSundered;

    private boolean done;
    private BlockPos descent = BlockPos.ZERO;
    private BlockPos riftFrame = BlockPos.ZERO;
    private long startAt = -1;

    public SunderingData() {
    }

    private SunderingData(Snapshot s) {
        this.done = s.done;
        this.descent = s.descent;
        this.riftFrame = s.riftFrame;
        this.startAt = s.startAt;
    }

    private Snapshot snapshot() {
        return new Snapshot(done, descent, riftFrame, startAt);
    }

    public static SunderingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static boolean isSunderedClient(Level level) {
        if (!level.isClientSide() && level.getServer() != null) {
            return get(level.getServer()).done;
        }
        return clientSundered;
    }

    public static void setClientSundered(boolean value) {
        clientSundered = value;
    }

    public boolean done() { return done; }
    public BlockPos descent() { return descent; }
    public BlockPos riftFrame() { return riftFrame; }
    public long startAt() { return startAt; }

    public void schedule(long at) {
        startAt = at;
        setDirty();
    }

    public void complete(BlockPos descent, BlockPos riftFrame) {
        this.done = true;
        this.descent = descent.immutable();
        this.riftFrame = riftFrame.immutable();
        this.startAt = -1;
        setDirty();
    }

    /** Where travellers return from the Fracture: next to the Rift frame, or the End spawn platform. */
    public BlockPos returnPoint() {
        return riftFrame.equals(BlockPos.ZERO) ? new BlockPos(100, 50, 0) : riftFrame.offset(0, 1, 4);
    }

    public void reset() {
        done = false;
        startAt = -1;
        setDirty();
    }
}
