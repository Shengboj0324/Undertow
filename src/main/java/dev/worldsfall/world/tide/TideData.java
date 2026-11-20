package dev.worldsfall.world.tide;

import com.mojang.serialization.Codec;
import dev.worldsfall.Worldsfall;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-global persisted tide state, stored with the overworld's data storage. */
public final class TideData extends SavedData {
    public static final Codec<TideData> CODEC = TideState.CODEC.xmap(TideData::new, TideData::state);
    public static final SavedDataType<TideData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Worldsfall.MODID, "tides"), TideData::new, CODEC);

    private TideState state;

    public TideData() {
        this(TideState.INITIAL);
    }

    private TideData(TideState state) {
        this.state = state;
    }

    public TideState state() {
        return state;
    }

    public void set(TideState next) {
        if (!next.equals(state)) {
            state = next;
            setDirty();
        }
    }

    public static TideData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }
}
