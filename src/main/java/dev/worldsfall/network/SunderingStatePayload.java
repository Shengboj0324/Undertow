package dev.worldsfall.network;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.world.sundering.SunderingData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → client: whether the Sundering has happened. */
public record SunderingStatePayload(boolean done) implements CustomPacketPayload {
    public static final Type<SunderingStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "sundering_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SunderingStatePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, SunderingStatePayload::done, SunderingStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SunderingStatePayload p, IPayloadContext ctx) {
        SunderingData.setClientSundered(p.done);
    }
}
