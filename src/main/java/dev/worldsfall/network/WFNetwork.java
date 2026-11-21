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
        PayloadRegistrar r = event.registrar("3");
        r.playToClient(TideSyncPayload.TYPE, TideSyncPayload.STREAM_CODEC, TideSyncPayload::handle);
        r.playToClient(SunderingFxPayload.TYPE, SunderingFxPayload.STREAM_CODEC, SunderingFxPayload::handle);
        r.playToClient(SunderingStatePayload.TYPE, SunderingStatePayload.STREAM_CODEC, SunderingStatePayload::handle);
        r.playToClient(RestorationPayload.TYPE, RestorationPayload.STREAM_CODEC, RestorationPayload::handle);
        r.playToClient(WardenFxPayload.TYPE, WardenFxPayload.STREAM_CODEC, WardenFxPayload::handle);
        r.playToClient(VoidLensPayload.TYPE, VoidLensPayload.STREAM_CODEC, VoidLensPayload::handle);
        r.playToClient(EchoTabletPayload.TYPE, EchoTabletPayload.STREAM_CODEC, EchoTabletPayload::handle);
        r.playToServer(SkateDismountPayload.TYPE, SkateDismountPayload.STREAM_CODEC, SkateDismountPayload::handle);
        r.playToServer(ElytraActionPayload.TYPE, ElytraActionPayload.STREAM_CODEC, ElytraActionPayload::handle);
        r.playToClient(LatticeMenuPayload.TYPE, LatticeMenuPayload.STREAM_CODEC, LatticeMenuPayload::handle);
        r.playToServer(LatticeJumpPayload.TYPE, LatticeJumpPayload.STREAM_CODEC, LatticeJumpPayload::handle);
    }

    public static TideSyncPayload tidePayload(MinecraftServer server) {
        var state = TideData.get(server).state();
        var cycle = dev.worldsfall.life.KingTide.adjust(server, WFConfig.SERVER.tideCycle(), state.effectiveTime(server.overworld().getGameTime()),
            state.calmed());
        return TideSyncPayload.of(state, cycle, WFConfig.get(WFConfig.SERVER.tidesEnabled, true));
    }

    /** Sends only to players whose connection negotiated the payload (vanilla clients / mock players are skipped). */
    public static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void sendToAll(MinecraftServer server, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            send(p, payload);
        }
    }

    public static void sendToDimension(net.minecraft.server.level.ServerLevel level, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        for (ServerPlayer p : level.players()) {
            send(p, payload);
        }
    }

    public static void syncTidesToAll(MinecraftServer server) {
        sendToAll(server, tidePayload(server));
    }

    public static RestorationPayload restorationPayload(MinecraftServer server) {
        var data = dev.worldsfall.restoration.RestorationData.get(server);
        return new RestorationPayload(dev.worldsfall.restoration.RestorationEffects.stage(server),
            (float) dev.worldsfall.restoration.Restoration.fraction(server), dev.worldsfall.zenith.Orreries.stars(server),
            dev.worldsfall.life.WorldEvents.current(server), dev.worldsfall.life.WorldEvents.currentStart(server));
    }

    public static void syncRestorationToAll(MinecraftServer server) {
        sendToAll(server, restorationPayload(server));
    }

    public static void syncPlayer(ServerPlayer player) {
        send(player, restorationPayload(player.level().getServer()));
        send(player, tidePayload(player.level().getServer()));
        send(player, new SunderingStatePayload(SunderingData.get(player.level().getServer()).done()));
    }

    public static void syncSundering(MinecraftServer server) {
        sendToAll(server, new SunderingStatePayload(SunderingData.get(server).done()));
    }
}
