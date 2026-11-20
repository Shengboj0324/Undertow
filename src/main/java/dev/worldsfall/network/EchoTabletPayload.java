package dev.worldsfall.network;

import dev.worldsfall.Worldsfall;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → client: show an Echo Tablet line (§18.9 glyph reveal). */
public record EchoTabletPayload(int index) implements CustomPacketPayload {
    public static final Type<EchoTabletPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "echo_tablet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoTabletPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, EchoTabletPayload::index, EchoTabletPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EchoTabletPayload p, IPayloadContext ctx) {
        ClientBridge.echoTablet.accept(p.index);
    }
}
