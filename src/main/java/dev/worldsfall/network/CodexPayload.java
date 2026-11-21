package dev.worldsfall.network;

import dev.worldsfall.Worldsfall;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → client: show an Archive Codex's record (glyphs resolving into text, like an Echo Tablet). */
public record CodexPayload(int index) implements CustomPacketPayload {
    public static final Type<CodexPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "codex"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CodexPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, CodexPayload::index, CodexPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CodexPayload p, IPayloadContext ctx) {
        ClientBridge.codex.accept(p.index());
    }
}
