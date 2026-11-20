package dev.worldsfall.network;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.world.tide.TideCycle;
import dev.worldsfall.world.tide.TideState;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → client: full tide state + cycle. Sent on login/dimension change, phase changes and every 30 s (§5.1). */
public record TideSyncPayload(TideState state, double lowY, double highY, int low, int rising, int high, int falling, boolean enabled)
    implements CustomPacketPayload {
    public static final Type<TideSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "tide_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TideSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TideSyncPayload decode(RegistryFriendlyByteBuf buf) {
            TideState state = TideState.STREAM_CODEC.decode(buf);
            return new TideSyncPayload(state, buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, TideSyncPayload p) {
            TideState.STREAM_CODEC.encode(buf, p.state);
            buf.writeDouble(p.lowY);
            buf.writeDouble(p.highY);
            buf.writeVarInt(p.low);
            buf.writeVarInt(p.rising);
            buf.writeVarInt(p.high);
            buf.writeVarInt(p.falling);
            buf.writeBoolean(p.enabled);
        }
    };

    public static TideSyncPayload of(TideState state, TideCycle c, boolean enabled) {
        return new TideSyncPayload(state, c.lowY(), c.highY(), c.lowTicks(), c.risingTicks(), c.highTicks(), c.fallingTicks(), enabled);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TideSyncPayload p, IPayloadContext ctx) {
        Tides.acceptClientSync(p.state, new TideCycle(p.lowY, p.highY, p.low, p.rising, p.high, p.falling), p.enabled);
    }
}
