package dev.worldsfall.world.tide;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The mutable, persisted part of the tide: an offset applied to world time (used by /worldsfall tide set),
 * an optional pause point, and whether the Still Throne has calmed the tides. Shared by server and client.
 */
public record TideState(long offset, long pausedAt, boolean calmed) {
    public static final TideState INITIAL = new TideState(0, -1, false);
    public static final Codec<TideState> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.LONG.fieldOf("offset").forGetter(TideState::offset),
        Codec.LONG.fieldOf("paused_at").forGetter(TideState::pausedAt),
        Codec.BOOL.fieldOf("calmed").forGetter(TideState::calmed)
    ).apply(i, TideState::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, TideState> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, TideState::offset,
        ByteBufCodecs.VAR_LONG, TideState::pausedAt,
        ByteBufCodecs.BOOL, TideState::calmed,
        TideState::new);

    public boolean paused() {
        return pausedAt >= 0;
    }

    /** Effective tide time for a given world game time. */
    public long effectiveTime(long gameTime) {
        return paused() ? pausedAt : gameTime + offset;
    }

    public TideState pause(long gameTime) {
        return paused() ? this : new TideState(offset, gameTime + offset, calmed);
    }

    public TideState resume(long gameTime) {
        return paused() ? new TideState(pausedAt - gameTime, -1, calmed) : this;
    }

    /** Shifts the offset so the effective time equals {@code target} right now. */
    public TideState jumpTo(long gameTime, long target) {
        return paused() ? new TideState(offset, target, calmed) : new TideState(target - gameTime, -1, calmed);
    }

    public TideState withCalmed(boolean value) {
        return new TideState(offset, pausedAt, value);
    }
}
