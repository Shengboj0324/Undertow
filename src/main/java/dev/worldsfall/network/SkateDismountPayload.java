package dev.worldsfall.network;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.entity.VoidSkate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: double-tap sneak to dismount a flying Skate (sneak alone dives; §8.2). */
public record SkateDismountPayload() implements CustomPacketPayload {
    public static final Type<SkateDismountPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "skate_dismount"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkateDismountPayload> STREAM_CODEC = StreamCodec.unit(new SkateDismountPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkateDismountPayload p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp && sp.getVehicle() instanceof VoidSkate) {
            sp.getPersistentData().putBoolean("worldsfall_allow_dismount", true);
            sp.stopRiding();
        }
    }
}
