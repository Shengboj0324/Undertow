package dev.worldsfall.network;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.zenith.SkyCurrents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Server → client: the Sky Currents, Downdrafts and Tide Maws around a player in the outer End (sent when they cross into a new region). */
public record ZenithPayload(List<SkyCurrents.Current> currents, List<SkyCurrents.Downdraft> drafts, List<Vec3> maws) implements CustomPacketPayload {
    private static final StreamCodec<RegistryFriendlyByteBuf, SkyCurrents.Current> CURRENT = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, SkyCurrents.Current::id, Vec3.STREAM_CODEC.apply(ByteBufCodecs.list(32)), SkyCurrents.Current::points,
        ByteBufCodecs.BOOL, SkyCurrents.Current::oneWay, SkyCurrents.Current::new);
    private static final StreamCodec<RegistryFriendlyByteBuf, SkyCurrents.Downdraft> DRAFT = StreamCodec.composite(
        ByteBufCodecs.DOUBLE, SkyCurrents.Downdraft::x, ByteBufCodecs.DOUBLE, SkyCurrents.Downdraft::z, ByteBufCodecs.DOUBLE,
        SkyCurrents.Downdraft::radius, SkyCurrents.Downdraft::new);
    public static final Type<ZenithPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "zenith"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ZenithPayload> STREAM_CODEC = StreamCodec.composite(
        CURRENT.apply(ByteBufCodecs.list(16)), ZenithPayload::currents, DRAFT.apply(ByteBufCodecs.list(64)), ZenithPayload::drafts,
        Vec3.STREAM_CODEC.apply(ByteBufCodecs.list(16)), ZenithPayload::maws, ZenithPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ZenithPayload p, IPayloadContext ctx) {
        ClientBridge.zenith.accept(p);
    }
}
