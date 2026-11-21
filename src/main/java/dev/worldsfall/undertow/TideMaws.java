package dev.worldsfall.undertow;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.tide.TideCycle;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Tide Maws (proposal "Getting in: Tide Maws and the Void Bell"): at High Tide a slow whirlpool of light opens in the tide
 * plane at a fixed place in each 1,500×1,500 area of the Far and Deep Rim. Anything that enters is pulled into the
 * Undertow. Positions are a pure function of the seed, so every maw is always in the same place.
 */
public final class TideMaws {
    public static final int REGION = 1500;
    public static final double MIN_DIST = 2200, RADIUS = 6, PULL_RADIUS = 14;

    private TideMaws() {}

    public static boolean enabled() {
        return WFConfig.get(WFConfig.SERVER.undertow, true);
    }

    /** The maw of region (rx, rz), or null when the region is too close to the main island. */
    public static Vec3 mawOf(long seed, int rx, int rz) {
        double cx = (rx + 0.5) * REGION, cz = (rz + 0.5) * REGION;
        if (cx * cx + cz * cz < MIN_DIST * MIN_DIST) {
            return null;
        }
        RandomSource r = RandomSource.create(seed ^ (rx * 0x2545F491L) ^ (rz * 0x9E3779B9L) ^ 0x3A3A3AL);
        return new Vec3(rx * REGION + 150 + r.nextDouble() * (REGION - 300), 0, rz * REGION + 150 + r.nextDouble() * (REGION - 300));
    }

    public static List<Vec3> mawsNear(long seed, double x, double z) {
        List<Vec3> out = new ArrayList<>();
        int rx = Mth.floor(x / REGION), rz = Mth.floor(z / REGION);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Vec3 m = mawOf(seed, rx + dx, rz + dz);
                if (m != null) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    /** Maws are open while the tide stands High. */
    public static boolean open(Level level) {
        return enabled() && Tides.appliesTo(level) && Tides.phase(level) == TideCycle.Phase.HIGH;
    }

    /** Stable id of the maw at {@code m} (its region), used to tie each maw to its Sunken Archive. */
    public static long id(Vec3 m) {
        return ((long) Mth.floor(m.x / REGION) << 32) ^ (Mth.floor(m.z / REGION) & 0xFFFFFFFFL);
    }

    /** Pulls and swallows anything at an open maw near a player in the End. */
    public static void tick(ServerLevel end) {
        if (!open(end) || end.getGameTime() % 2 != 0) {
            return;
        }
        double tide = Tides.height(end);
        // gather the maws near any player first: swallowing a player moves them out of the End (and out of
        // end.players()) mid-loop, and two players by one maw must not pull it twice
        java.util.Set<Vec3> active = new java.util.LinkedHashSet<>();
        for (ServerPlayer p : List.copyOf(end.players())) {
            for (Vec3 m : mawsNear(end.getSeed(), p.getX(), p.getZ())) {
                if (Math.hypot(m.x - p.getX(), m.z - p.getZ()) <= 160) {
                    active.add(m);
                }
            }
        }
        for (Vec3 m : active) {
            Vec3 centre = new Vec3(m.x, tide, m.z);
            if (end.getGameTime() % 40 == 0) {
                end.playSound(null, BlockPos.containing(centre), WFSounds.EVENT_MAW_OPEN.get(), SoundSource.AMBIENT, 3.0f, 0.8f);
            }
            AABB area = new AABB(centre, centre).inflate(PULL_RADIUS, 8, PULL_RADIUS);
            for (Entity e : end.getEntities((Entity) null, area, e -> !e.isPassenger() && !e.isSpectator() && (e instanceof ServerPlayer
                || e instanceof ItemEntity || e instanceof VoidBell || e instanceof net.minecraft.world.entity.LivingEntity))) {
                if (!e.isRemoved() && e.level() == end) {
                    pull(end, e, centre);
                }
            }
        }
    }

    private static void pull(ServerLevel end, Entity e, Vec3 centre) {
        Vec3 to = centre.subtract(e.position());
        double flat = Math.hypot(to.x, to.z);
        if (flat < RADIUS && e.getY() < centre.y + 1.5) {
            end.playSound(null, e.blockPosition(), WFSounds.EVENT_MAW_PULL.get(), SoundSource.AMBIENT, 2.0f, 1.0f);
            end.sendParticles(WFParticles.MAW_SWIRL.get(), e.getX(), e.getY(), e.getZ(), 40, 1, 1, 1, 0.1);
            UndertowTravel.enter(end, e, centre);
            return;
        }
        // a slow spiral in: swirl around the centre and draw inward and down (players move themselves; the server nudges)
        if (flat < PULL_RADIUS && e.getY() < centre.y + 8) {
            Vec3 tangent = new Vec3(-to.z, 0, to.x).normalize().scale(0.06);
            Vec3 in = new Vec3(to.x, 0, to.z).normalize().scale(0.04 + 0.06 * (1 - flat / PULL_RADIUS));
            e.setDeltaMovement(e.getDeltaMovement().add(tangent).add(in).add(0, -0.02, 0));
            e.needsSync = true;
        }
    }
}
