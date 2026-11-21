package dev.worldsfall.life;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.registry.WFDimensions;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.warden.ElderWarden;
import dev.worldsfall.world.tide.TideCycle;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * World events (proposal "Living systems"): rare, server-wide moments in the End, announced by sound and sky only.
 * Starfall, the Eclipse of Stillness and Skate Migrations are scheduled from game time like the tide (one chance per
 * period, at a seed-chosen moment), so they are deterministic and survive restarts; the King Tide follows the tide
 * cycle itself ({@link KingTide}). Only one event runs at a time, never during a boss fight, and each can be disabled.
 */
public final class WorldEvents {
    public static final int NONE = 0, STARFALL = 1, KING_TIDE = 2, ECLIPSE = 3, MIGRATION = 4;
    public static final long STARFALL_PERIOD = 144_000, ECLIPSE_PERIOD = 216_000, MIGRATION_PERIOD = 108_000;
    public static final int STARFALL_TELEGRAPH = 200, STARFALL_LIFE = 6000;
    public static final int ECLIPSE_DIM = 200, ECLIPSE_LENGTH = 3600;
    public static final int MIGRATION_TELEGRAPH = 200, MIGRATION_LENGTH = 2400;
    public static final int KING_TELEGRAPH = 4800;
    /** Window after an event's chosen moment in which it may still start (the scheduler checks once a second). */
    private static final int WINDOW = 1200;

    private WorldEvents() {}

    public static int current(MinecraftServer server) {
        return WorldEventData.get(server).current();
    }

    public static long currentStart(MinecraftServer server) {
        return WorldEventData.get(server).start();
    }

    public static boolean enabled(int event) {
        return switch (event) {
            case STARFALL -> WFConfig.get(WFConfig.SERVER.eventStarfall, true);
            case KING_TIDE -> KingTide.enabled();
            case ECLIPSE -> WFConfig.get(WFConfig.SERVER.eventEclipse, true);
            case MIGRATION -> WFConfig.get(WFConfig.SERVER.eventMigration, true);
            default -> false;
        };
    }

    /** The moment in period {@code index} at which an event is due, chosen from the world seed. */
    public static long dueAt(long seed, String key, long index, long period, int length) {
        long h = seed ^ key.hashCode() * 0x632BE59BD9B4E019L ^ index * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        long span = Math.max(1, period - length - WINDOW);
        return index * period + Math.floorMod(h, span);
    }

    // ------------------------------------------------------------------ ticking (the End's level tick)

    public static void tick(ServerLevel end) {
        MinecraftServer server = end.getServer();
        WorldEventData data = WorldEventData.get(server);
        long now = server.overworld().getGameTime();
        switch (data.current()) {
            case STARFALL -> Starfall.tick(end, data, now);
            case ECLIPSE -> Eclipse.tick(end, data, now);
            case MIGRATION -> Migration.tick(end, data, now);
            case KING_TIDE -> {
                if (now >= data.end()) {
                    finishKingTide(end, data);
                }
            }
            default -> { }
        }
        if (now % 20 != 0) {
            return;
        }
        kingTide(end, data, now);
        if (data.current() != NONE || end.players().isEmpty() || bossFight(server)) {
            return;
        }
        long seed = server.overworld().getSeed();
        tryStart(end, data, now, seed, STARFALL, "starfall", STARFALL_PERIOD, STARFALL_TELEGRAPH + STARFALL_LIFE);
        tryStart(end, data, now, seed, ECLIPSE, "eclipse", ECLIPSE_PERIOD, ECLIPSE_DIM + ECLIPSE_LENGTH);
        tryStart(end, data, now, seed, MIGRATION, "migration", dev.worldsfall.restoration.RestorationEffects.migrationPeriod(server),
            MIGRATION_TELEGRAPH + MIGRATION_LENGTH);
    }

    private static void tryStart(ServerLevel end, WorldEventData data, long now, long seed, int event, String key, long period, int length) {
        if (data.current() != NONE || !enabled(event)) {
            return;
        }
        long index = Math.floorDiv(now, period);
        long due = dueAt(seed, key, index, period, length);
        if (now >= due && now < due + WINDOW && data.lastRun(key) != index) {
            start(end, event, key, index);
        }
    }

    /** Starts an event now (also used by commands and tests). Returns false if it could not begin. */
    public static boolean start(ServerLevel end, int event) {
        return start(end, event, null, 0);
    }

    private static boolean start(ServerLevel end, int event, @Nullable String key, long index) {
        WorldEventData data = WorldEventData.get(end.getServer());
        long now = end.getServer().overworld().getGameTime();
        boolean ok = switch (event) {
            case STARFALL -> {
                data.begin(STARFALL, now, now + STARFALL_TELEGRAPH + STARFALL_LIFE, key, index);
                yield Starfall.begin(end, data);
            }
            case ECLIPSE -> {
                data.begin(ECLIPSE, now, now + ECLIPSE_DIM + ECLIPSE_LENGTH, key, index);
                yield Eclipse.begin(end, data);
            }
            case MIGRATION -> {
                data.begin(MIGRATION, now, now + MIGRATION_TELEGRAPH + MIGRATION_LENGTH, key, index);
                yield Migration.begin(end, data);
            }
            default -> false;
        };
        if (!ok) {
            data.finish();
        }
        WFNetwork.syncRestorationToAll(end.getServer());
        return ok;
    }

    /** Ends whatever event is running, cleaning up after it. */
    public static void stop(ServerLevel end) {
        WorldEventData data = WorldEventData.get(end.getServer());
        switch (data.current()) {
            case STARFALL -> Starfall.end(end, data);
            case ECLIPSE -> Eclipse.end(end, data);
            case MIGRATION -> Migration.end(end, data);
            case KING_TIDE -> finishKingTide(end, data);
            default -> { }
        }
        data.finish();
        WFNetwork.syncRestorationToAll(end.getServer());
    }

    static void finish(ServerLevel end, WorldEventData data) {
        data.finish();
        WFNetwork.syncRestorationToAll(end.getServer());
    }

    // ------------------------------------------------------------------ King Tide

    private static void kingTide(ServerLevel end, WorldEventData data, long now) {
        if (!KingTide.enabled() || !Tides.appliesTo(end) || Tides.state(end).calmed()) {
            return;
        }
        TideCycle cycle = Tides.cycle(end);
        long time = Tides.effectiveTime(end);
        long index = KingTide.cycleIndex(cycle, time);
        long seed = end.getServer().overworld().getSeed();
        TideCycle.Phase phase = cycle.phase(time);
        int oneIn = dev.worldsfall.restoration.RestorationEffects.kingTideOneIn(end.getServer());
        if (KingTide.isKing(seed, index, oneIn) && data.kingTelegraphed() != index && phase == TideCycle.Phase.LOW
            && cycle.ticksUntilNextPhase(time) <= KING_TELEGRAPH) {
            data.setKingTelegraphed(index);
            for (ServerPlayer p : end.players()) {
                playTo(p, dev.worldsfall.registry.WFSounds.ENTITY_LEVIATHAN_SONG.get(), 1.0f, 0.8f);
                for (int i = 0; i < 6; i++) {
                    var wisp = WFEntities.TIDEWISP.get().create(end, net.minecraft.world.entity.EntitySpawnReason.EVENT);
                    if (wisp != null) {
                        RandomSource r = end.getRandom();
                        wisp.snapTo(p.getX() + r.nextGaussian() * 6, p.getY() + 2 + r.nextDouble() * 3, p.getZ() + r.nextGaussian() * 6, 0, 0);
                        end.addFreshEntity(wisp);
                    }
                }
            }
            if (data.current() == NONE) {
                long left = cycle.ticksUntilNextPhase(time) + cycle.risingTicks() + cycle.highTicks() + cycle.fallingTicks();
                data.begin(KING_TIDE, now, now + left, null, 0);
                WFNetwork.syncRestorationToAll(end.getServer());
            }
            WFNetwork.syncTidesToAll(end.getServer());
        }
        if (KingTide.isKing(seed, index, oneIn) && phase == TideCycle.Phase.HIGH) {
            for (ServerPlayer p : end.players()) {
                dev.worldsfall.world.Progress.award(p, "high_water_mark");
            }
        }
    }

    private static void finishKingTide(ServerLevel end, WorldEventData data) {
        Tidewrack.scatter(end);
        finish(end, data);
    }

    // ------------------------------------------------------------------ helpers

    /** True while the dragon (not the Elder Warden) or The Unmade is fighting. */
    public static boolean bossFight(MinecraftServer server) {
        ServerLevel end = server.getLevel(Level.END);
        if (end != null && end.getDragons().stream().anyMatch(d -> !(d instanceof ElderWarden) && d.isAlive())) {
            return true;
        }
        ServerLevel fracture = server.getLevel(WFDimensions.THE_FRACTURE);
        return fracture != null && !fracture.getEntities(WFEntities.THE_UNMADE.get(), e -> e.isAlive()).isEmpty();
    }

    /** A sound every End player hears where they stand (events are announced by sound, never chat). */
    static void playTo(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(Holder.direct(sound), SoundSource.AMBIENT,
            p.getX(), p.getY() + 30, p.getZ(), volume * 4, pitch, p.getRandom().nextLong()));
    }

    static void playToAll(ServerLevel end, SoundEvent sound, float volume, float pitch) {
        for (ServerPlayer p : end.players()) {
            playTo(p, sound, volume, pitch);
        }
    }

    /** A random End player, or null. */
    static @Nullable ServerPlayer anyPlayer(ServerLevel end) {
        List<ServerPlayer> ps = end.players().stream().filter(p -> !p.isSpectator()).toList();
        return ps.isEmpty() ? null : ps.get(end.getRandom().nextInt(ps.size()));
    }

    /** An open island-top position near {@code around} in a loaded chunk, or null. */
    static @Nullable BlockPos islandTop(ServerLevel end, BlockPos around, int minR, int maxR, RandomSource r) {
        for (int i = 0; i < 40; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = minR + r.nextDouble() * (maxR - minR);
            int x = around.getX() + (int) (Math.cos(a) * d), z = around.getZ() + (int) (Math.sin(a) * d);
            if (!end.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int y = end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (y > end.getMinY() + 8 && end.getBlockState(p.below()).isSolid() && end.getBlockState(p).isAir()
                && !(Tides.appliesTo(end) && Tides.isBelowTide(end, y))) {
                return p;
            }
        }
        return null;
    }
}
