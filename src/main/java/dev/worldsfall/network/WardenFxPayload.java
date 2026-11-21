package dev.worldsfall.network;

import dev.worldsfall.Worldsfall;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → client: one Warden Cycle visual. {@code kind}: SONG (a Wardensong line, value = index), CRACKS (hairline cracks
 * across the main island, value = 1 at 50 % and 2 at 25 %), PULSE (the cracks pulse with the dragon's breathing),
 * RELEASE (the Stillness Crystals ring white). {@code pos} is the Descent or the crystal.
 */
public record WardenFxPayload(int kind, BlockPos pos, int value) implements CustomPacketPayload {
    public static final int SONG = 0, CRACKS = 1, PULSE = 2, RELEASE = 3;
    public static final Type<WardenFxPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "warden_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WardenFxPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, WardenFxPayload::kind, BlockPos.STREAM_CODEC, WardenFxPayload::pos, ByteBufCodecs.VAR_INT, WardenFxPayload::value,
        WardenFxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WardenFxPayload p, IPayloadContext ctx) {
        ClientBridge.wardenFx.accept(p);
    }
}
