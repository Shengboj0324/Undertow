package dev.worldsfall.network;

import dev.worldsfall.Worldsfall;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → client: start the Sundering's crack/flash visual sequence (§5.4). */
public record SunderingFxPayload(BlockPos descent, long startTime) implements CustomPacketPayload {
    public static final Type<SunderingFxPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "sundering_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SunderingFxPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SunderingFxPayload::descent, ByteBufCodecs.VAR_LONG, SunderingFxPayload::startTime, SunderingFxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SunderingFxPayload p, IPayloadContext ctx) {
        ClientBridge.sunderingFx.accept(p.descent, p.startTime);
    }
}
