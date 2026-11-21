package dev.worldsfall.network;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.nav.Lattice;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: travel to a Lattice node; the server checks everything again (held gateway, known node, tide, pearl). */
public record LatticeJumpPayload(BlockPos target) implements CustomPacketPayload {
    public static final Type<LatticeJumpPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "lattice_jump"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LatticeJumpPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, LatticeJumpPayload::target, LatticeJumpPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LatticeJumpPayload p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp) {
            Lattice.jump(sp, p.target());
        }
    }
}
