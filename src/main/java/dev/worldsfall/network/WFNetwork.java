package dev.worldsfall.network;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.world.sundering.SunderingData;
import dev.worldsfall.world.tide.TideData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payload registration and sync helpers (§20.7). */
public final class WFNetwork {
    private WFNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(TideSyncPayload.TYPE, TideSyncPayload.STREAM_CODEC, TideSyncPayload::handle);
        r.playToClient(SunderingFxPayload.TYPE, SunderingFxPayload.STREAM_CODEC, SunderingFxPayload::handle);
        r.playToClient(SunderingStatePayload.TYPE, SunderingStatePayload.STREAM_CODEC, SunderingStatePayload::handle);
        r.playToClient(EchoTabletPayload.TYPE, EchoTabletPayload.STREAM_CODEC, EchoTabletPayload::handle);
        r.playToServer(SkateDismountPayload.TYPE, SkateDismountPayload.STREAM_CODEC, SkateDismountPayload::handle);
    }

    public static TideSyncPayload tidePayload(MinecraftServer server) {
        return TideSyncPayload.of(TideData.get(server).state(), WFConfig.SERVER.tideCycle(), WFConfig.get(WFConfig.SERVER.tidesEnabled, true));
    }

    public static void syncTidesToAll(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(tidePayload(server));
    }

    public static void syncPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, tidePayload(player.level().getServer()),
            new SunderingStatePayload(SunderingData.get(player.level().getServer()).done()));
    }

    public static void syncSundering(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(new SunderingStatePayload(SunderingData.get(server).done()));
    }
}
