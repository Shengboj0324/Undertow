package dev.worldsfall.network;

import dev.worldsfall.Worldsfall;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Server → client: open the Gateway Lattice list for the gateway the player stands in. */
public record LatticeMenuPayload(BlockPos from, List<Entry> entries) implements CustomPacketPayload {
    public record Entry(BlockPos pos, int kind, String name, boolean covered, boolean skate) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Entry::pos, ByteBufCodecs.VAR_INT, Entry::kind, ByteBufCodecs.stringUtf8(64), Entry::name,
            ByteBufCodecs.BOOL, Entry::covered, ByteBufCodecs.BOOL, Entry::skate, Entry::new);
    }

    public static final Type<LatticeMenuPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "lattice_menu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LatticeMenuPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, LatticeMenuPayload::from, Entry.STREAM_CODEC.apply(ByteBufCodecs.list(256)), LatticeMenuPayload::entries,
        LatticeMenuPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LatticeMenuPayload p, IPayloadContext ctx) {
        ClientBridge.latticeMenu.accept(p);
    }
}
