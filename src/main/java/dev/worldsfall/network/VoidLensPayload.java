package dev.worldsfall.network;

import dev.worldsfall.Worldsfall;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Server → client: Driftwreck bounds near a player who is looking through a Void Lens (§9.6). Sent about once a second while scoped. */
public record VoidLensPayload(List<BoundingBox> wrecks, List<net.minecraft.core.BlockPos> pillars) implements CustomPacketPayload {
    public static final Type<VoidLensPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "void_lens"));
    private static final StreamCodec<RegistryFriendlyByteBuf, BoundingBox> BOX = StreamCodec.of(
        (buf, b) -> {
            buf.writeVarInt(b.minX());
            buf.writeVarInt(b.minY());
            buf.writeVarInt(b.minZ());
            buf.writeVarInt(b.maxX());
            buf.writeVarInt(b.maxY());
            buf.writeVarInt(b.maxZ());
        },
        buf -> new BoundingBox(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoidLensPayload> STREAM_CODEC = StreamCodec.composite(
        BOX.apply(ByteBufCodecs.list(32)), VoidLensPayload::wrecks,
        net.minecraft.core.BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(16)), VoidLensPayload::pillars, VoidLensPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VoidLensPayload p, IPayloadContext ctx) {
        ClientBridge.voidLens.accept(p);
    }
}
