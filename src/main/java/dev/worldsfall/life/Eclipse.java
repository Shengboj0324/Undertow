package dev.worldsfall.life;

import dev.worldsfall.entity.Echo;
import dev.worldsfall.item.EchoTabletItem;
import dev.worldsfall.network.EchoTabletPayload;
import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.util.WFUtil;
import dev.worldsfall.warden.WardenData;
import dev.worldsfall.world.Progress;
import dev.worldsfall.world.tide.TideData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Eclipse of Stillness (proposal "World events"): for three minutes the tide freezes, the stars go dark, sound is muffled,
 * and Echoes walk openly as harmless glowing figures acting out memories. Coming close to one makes it fade, leaving
 * Eclipse Dust and one remembered line.
 */
public final class Eclipse {
    public static final int FIGURES_PER_PLAYER = 4;

    private Eclipse() {}

    static boolean begin(ServerLevel end, WorldEventData data) {
        var server = end.getServer();
        TideData tide = TideData.get(server);
        if (!tide.state().paused() && WardenData.get(server).savedTide() == null) {
            tide.set(tide.state().pause(server.overworld().getGameTime()));
            data.setPausedTide(true);
            WFNetwork.syncTidesToAll(server);
        }
        WorldEvents.playToAll(end, WFSounds.EVENT_ECLIPSE_BEGIN.get(), 1.0f, 1.0f);
        return true;
    }

    static void tick(ServerLevel end, WorldEventData data, long now) {
        long t = now - data.start();
        if (t == WorldEvents.ECLIPSE_DIM) {
            for (ServerPlayer p : end.players()) {
                for (int i = 0; i < FIGURES_PER_PLAYER; i++) {
                    BlockPos at = WorldEvents.islandTop(end, p.blockPosition(), 8, 24, end.getRandom());
                    Echo e = at == null ? null : WFEntities.ECHO.get().create(end, EntitySpawnReason.EVENT);
                    if (e != null) {
                        e.makeMemory();
                        e.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, end.getRandom().nextFloat() * 360, 0);
                        end.addFreshEntity(e);
                    }
                }
            }
        }
        if (t > 0 && t % 10 == 0) {
            for (ServerPlayer p : end.players()) {
                end.sendParticles(p, WFParticles.ECLIPSE_MOTE.get(), false, false, p.getX(), p.getY() + 1.5, p.getZ(), 3, 8, 4, 8, 0.0);
            }
        }
        if (now >= data.end()) {
            end(end, data);
            WorldEvents.finish(end, data);
        }
    }

    static void end(ServerLevel end, WorldEventData data) {
        var server = end.getServer();
        if (data.pausedTide()) {
            TideData tide = TideData.get(server);
            if (tide.state().paused() && WardenData.get(server).savedTide() == null) {
                tide.set(tide.state().resume(server.overworld().getGameTime()));
                WFNetwork.syncTidesToAll(server);
            }
            data.setPausedTide(false);
        }
        for (Echo e : end.getEntities(WFEntities.ECHO.get(), Echo::isMemory)) {
            fade(end, e);
        }
        WorldEvents.playToAll(end, WFSounds.EVENT_ECLIPSE_END.get(), 1.0f, 1.0f);
    }

    /** A memory figure wanders slowly; when a player comes within three blocks it fades, leaving Eclipse Dust and a memory. */
    public static void tickMemory(ServerLevel level, Echo e) {
        if (WorldEvents.current(level.getServer()) != WorldEvents.ECLIPSE) {
            fade(level, e);
            return;
        }
        if (e.tickCount % 80 == 0 || e.getNavigation().isDone()) {
            e.getNavigation().moveTo(e.getX() + e.getRandom().nextGaussian() * 6, e.getY(), e.getZ() + e.getRandom().nextGaussian() * 6, 0.45);
        }
        Player p = level.getNearestPlayer(e, 3.0);
        if (p instanceof ServerPlayer sp && !sp.isSpectator()) {
            WFUtil.give(sp, new ItemStack(WFItems.ECLIPSE_DUST.get(), 1 + e.getRandom().nextInt(2)));
            int line = 1 + e.getRandom().nextInt(EchoTabletItem.FRAGMENTS);
            WFNetwork.send(sp, new EchoTabletPayload(line));
            Progress.award(sp, "a_quiet_hour");
            fade(level, e);
        }
    }

    static void fade(ServerLevel level, Echo e) {
        level.sendParticles(WFParticles.ECLIPSE_MOTE.get(), e.getX(), e.getY() + 1.4, e.getZ(), 30, 0.4, 0.9, 0.4, 0.02);
        level.playSound(null, e.blockPosition(), WFSounds.ENTITY_ECHO_HUM.get(), net.minecraft.sounds.SoundSource.HOSTILE, 0.6f, 1.4f);
        e.discard();
    }
}
