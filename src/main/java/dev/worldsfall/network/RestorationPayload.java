package dev.worldsfall.network;

import dev.worldsfall.Worldsfall;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server → client: the world-wide sky state everyone sees at the same moment — Restoration stage and fraction, the number
 * of aligned Orreries (each adds a permanent bright star), and the current world event (0 none, 1 Starfall, 2 King Tide,
 * 3 Eclipse of Stillness, 4 Skate Migration) with its start time.
 */
public record RestorationPayload(int stage, float fraction, List<Long> orreryStars, int event, long eventStart) implements CustomPacketPayload {
    public static final Type<RestorationPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "restoration"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RestorationPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, RestorationPayload::stage, ByteBufCodecs.FLOAT, RestorationPayload::fraction,
        ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(64)), RestorationPayload::orreryStars,
        ByteBufCodecs.VAR_INT, RestorationPayload::event, ByteBufCodecs.VAR_LONG, RestorationPayload::eventStart, RestorationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RestorationPayload p, IPayloadContext ctx) {
        ClientBridge.restoration.accept(p);
    }
}
