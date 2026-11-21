package dev.worldsfall.restoration;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.world.Progress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Restoration of the End: every act of rebuilding raises the world's Stillness; five stages unlock as it climbs
 * (First Light 20 %, Green Shoots 40 %, Steady Water 60 %, Lanterns of the Ascendancy 80 %, Stillness 100 %).
 * Stillness only goes up; nothing a griefer does lowers it. Stage effects live in {@link RestorationEffects}.
 */
public final class Restoration {
    /** Points per act (proposal table); one-time acts pass a key so they count once per world. */
    public enum Act {
        SPIRE_REASSEMBLY(3), CAUSEWAY_SPAN(2), DRIFTWRECK_LOOT(1), ORRERY_ALIGN(10), ARCHIVE_CODEX(4), GRAVITY_ANCHOR(2),
        MOORING(3), FAIR_BARTER(0.5), UNMADE_FIRST_KILL(15), RELEASE_WARDEN(20);

        public final double points;

        Act(double points) {
            this.points = points;
        }
    }

    public static final int STAGES = 5;
    public static final double BARTER_CAP = 20;

    private Restoration() {}

    public static double pointsFor100(MinecraftServer server) {
        return WFConfig.get(WFConfig.SERVER.restorationPointsFor100, 200);
    }

    public static boolean enabled() {
        return WFConfig.get(WFConfig.SERVER.restorationEnabled, true);
    }

    /** Fraction restored, 0..1. */
    public static double fraction(MinecraftServer server) {
        return Math.min(1.0, RestorationData.get(server).score() / pointsFor100(server));
    }

    /** Stage reached for a fraction: 0..5. */
    public static int stageFor(double fraction) {
        return (int) Math.min(STAGES, Math.floor(fraction * STAGES + 1e-9));
    }

    public static void award(MinecraftServer server, @Nullable ServerPlayer player, Act act) {
        award(server, player, act, null);
    }

    /** Counts an act. {@code key} (for example a structure position) makes it count once per world. */
    public static void award(MinecraftServer server, @Nullable ServerPlayer player, Act act, @Nullable String key) {
        if (!enabled()) {
            return;
        }
        RestorationData data = RestorationData.get(server);
        double points = act.points;
        if (act == Act.FAIR_BARTER) {
            if (player == null || data.barterPoints(player.getUUID()) >= BARTER_CAP) {
                return;
            }
            data.addBarter(player.getUUID(), points);
        }
        String fullKey = key == null ? null : act.name() + "@" + key;
        if (!data.add(points, player == null ? null : player.getUUID(), fullKey)) {
            return;
        }
        int stage = stageFor(Math.min(1.0, data.score() / pointsFor100(server)));
        if (stage > data.stage()) {
            data.setStage(stage);
            onStageReached(server, stage);
        }
    }

    private static void onStageReached(MinecraftServer server, int stage) {
        ServerLevel end = server.getLevel(Level.END);
        if (end != null) {
            for (ServerPlayer p : end.players()) {
                p.sendSystemMessage(Component.translatable("message.worldsfall.restoration.stage." + stage));
                dev.worldsfall.util.WFUtil.playTo(p, dev.worldsfall.registry.WFSounds.EVENT_RESTORATION_STAGE.get(),
                    net.minecraft.sounds.SoundSource.AMBIENT, 1.0f, 1.0f);
            }
        }
        if (stage >= STAGES) {
            RestorationData data = RestorationData.get(server);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (data.contributed(p.getUUID())) {
                    Progress.award(p, "where_worlds_come_to_rest");
                }
            }
        }
        RestorationEffects.onStage(server, stage);
        dev.worldsfall.network.WFNetwork.syncRestorationToAll(server);
    }

    /** Players who join later still get the final advancement if they contributed. */
    public static void onLogin(ServerPlayer p) {
        RestorationData data = RestorationData.get(p.level().getServer());
        if (data.stage() >= STAGES && data.contributed(p.getUUID())) {
            Progress.award(p, "where_worlds_come_to_rest");
        }
    }
}
