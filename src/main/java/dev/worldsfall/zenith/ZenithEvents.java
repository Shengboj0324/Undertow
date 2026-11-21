package dev.worldsfall.zenith;

import dev.worldsfall.network.WFNetwork;
import dev.worldsfall.network.ZenithPayload;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.world.Progress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server side of the Zenith, run from the End's level tick: current sync, the Current Vane, riding credit, spawning. */
public final class ZenithEvents {
    public static final int HIGHWAY_SECONDS = 10;
    /** Region last sent to each player (cleared by {@link #forget}, and dropped with the player object). */
    private static final Map<ServerPlayer, Long> REGION = new java.util.WeakHashMap<>();
    private static final Map<UUID, Integer> RIDING = new HashMap<>();

    private ZenithEvents() {}

    public static void tick(ServerLevel end) {
        long seed = end.getSeed();
        // the currents, drafts and maws near each player: sent the moment they arrive or cross into a new region
        for (ServerPlayer p : end.players()) {
            long region = ((long) Mth.floor(p.getX() / SkyCurrents.REGION) << 32) ^ (Mth.floor(p.getZ() / SkyCurrents.REGION) & 0xFFFFFFFFL);
            Long sent = REGION.get(p);
            if (sent == null || sent != region) {
                REGION.put(p, region);
                WFNetwork.send(p, new ZenithPayload(SkyCurrents.currentsNear(seed, p.getX(), p.getZ()), SkyCurrents.draftsNear(seed, p.getX(), p.getZ()),
                    dev.worldsfall.undertow.TideMaws.enabled() ? dev.worldsfall.undertow.TideMaws.mawsNear(seed, p.getX(), p.getZ()) : java.util.List.of()));
            }
        }
        if (end.getGameTime() % 20 != 7) {
            return;
        }
        for (ServerPlayer p : end.players()) {
            var currents = SkyCurrents.currentsNear(seed, p.getX(), p.getZ());
            boolean gliding = p.isFallFlying() || p.getVehicle() != null && p.getVehicle().getControllingPassenger() == p;
            SkyCurrents.Hit hit = SkyCurrents.nearest(currents, p.position());
            if (gliding && hit != null && hit.distance() <= SkyCurrents.CAPTURE + 1) {
                int t = RIDING.merge(p.getUUID(), 1, Integer::sum);
                if (t >= HIGHWAY_SECONDS) {
                    Progress.award(p, "sky_highway");
                }
            } else {
                RIDING.remove(p.getUUID());
            }
            if (end.getGameTime() % 40 == 7) {
                for (InteractionHand hand : InteractionHand.values()) {
                    ItemStack s = p.getItemInHand(hand);
                    if (s.is(WFItems.CURRENT_VANE.get())) {
                        aimVane(s, hit);
                    }
                }
            }
        }
        ZenithSpawning.tick(end);
    }

    /** Points a Current Vane (a compass) at the nearest Sky Current. */
    static void aimVane(ItemStack vane, SkyCurrents.Hit hit) {
        Optional<GlobalPos> target = hit == null ? Optional.empty() : Optional.of(GlobalPos.of(Level.END, BlockPos.containing(hit.point())));
        LodestoneTracker now = vane.get(DataComponents.LODESTONE_TRACKER);
        if (now == null || !now.target().equals(target)) {
            vane.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(target, false));
        }
    }

    /** For a player logging in or entering the End, so their client has the currents straight away. */
    public static void forget(ServerPlayer p) {
        REGION.remove(p);
        RIDING.remove(p.getUUID());
    }
}
