package dev.worldsfall.client.expansion;

import dev.worldsfall.entity.VoidSkate;
import dev.worldsfall.entity.Wyrm;
import dev.worldsfall.network.ZenithPayload;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.zenith.LeviathanDrifter;
import dev.worldsfall.zenith.SkyCurrents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Client side of the Zenith: the player's client moves its own glider, so Sky Currents carry and Downdrafts push here
 * (elytra, or a Skate or Drake the player steers). Also the faint ribbons and dust columns, riding a Leviathan's back,
 * and the high-wind ambience above Y 150.
 */
public final class ZenithClient {
    public static final double ZENITH_Y = 150;
    private static List<SkyCurrents.Current> currents = List.of();
    private static List<SkyCurrents.Downdraft> drafts = List.of();
    private static List<Vec3> maws = List.of();
    private static boolean inCurrent;
    private static Vec3 lastLeviathan = null;
    private static int leviathanId = -1;
    private static WindLoop wind;

    private ZenithClient() {}

    public static void accept(ZenithPayload p) {
        currents = List.copyOf(p.currents());
        drafts = List.copyOf(p.drafts());
        maws = List.copyOf(p.maws());
    }

    public static List<SkyCurrents.Current> currents() {
        return currents;
    }

    public static void tick(Minecraft mc, LocalPlayer player) {
        if (mc.level == null || mc.level.dimension() != Level.END) {
            inCurrent = false;
            return;
        }
        Entity glider = player.isFallFlying() ? player
            : player.getVehicle() instanceof VoidSkate || player.getVehicle() instanceof Wyrm ? player.getVehicle() : null;
        if (glider != null && glider.getControllingPassenger() != player && glider != player) {
            glider = null;
        }
        boolean nowIn = false;
        if (glider != null && !player.isShiftKeyDown()) {
            Vec3 carry = SkyCurrents.carry(currents, glider.position(), glider.getDeltaMovement());
            if (carry != null) {
                glider.setDeltaMovement(glider.getDeltaMovement().lerp(carry, 0.2));
                nowIn = true;
                if (!inCurrent) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(WFSounds.EVENT_SKY_CURRENT_ENTER.get(), 1.0f, 0.6f));
                }
            }
            if (SkyCurrents.inDraft(drafts, glider.position())) {
                Vec3 v = glider.getDeltaMovement();
                glider.setDeltaMovement(v.x * 0.97, Math.max(v.y - 0.12, -1.6), v.z * 0.97);
            }
        }
        inCurrent = nowIn;
        rideLeviathan(mc, player);
        particles(mc, player);
        ambience(mc, player);
    }

    /** Standing on a Leviathan's back moves you with it. */
    private static void rideLeviathan(Minecraft mc, LocalPlayer player) {
        LeviathanDrifter on = null;
        for (LeviathanDrifter l : mc.level.getEntitiesOfClass(LeviathanDrifter.class, player.getBoundingBox().inflate(16))) {
            if (l.carries(player)) {
                on = l;
                break;
            }
        }
        if (on == null) {
            lastLeviathan = null;
            leviathanId = -1;
            return;
        }
        Vec3 now = on.position();
        if (lastLeviathan != null && leviathanId == on.getId()) {
            Vec3 d = now.subtract(lastLeviathan);
            if (d.lengthSqr() < 4) {
                player.setPos(player.getX() + d.x, player.getY() + d.y, player.getZ() + d.z);
            }
        }
        lastLeviathan = now;
        leviathanId = on.getId();
    }

    private static void particles(Minecraft mc, LocalPlayer player) {
        RandomSource r = player.getRandom();
        Vec3 eye = player.getEyePosition();
        for (SkyCurrents.Current c : currents) {
            List<Vec3> pts = c.points();
            int density = dev.worldsfall.client.ClientWorldState.get().restorationStage() >= 4 ? 8 : 4; // Lanterns of the Ascendancy: brighter currents
            for (int k = 0; k < density; k++) {
                int i = r.nextInt(pts.size() - 1);
                Vec3 p = pts.get(i).lerp(pts.get(i + 1), r.nextDouble());
                if (p.distanceToSqr(eye) < 96 * 96) {
                    Vec3 dir = pts.get(i + 1).subtract(pts.get(i)).normalize().scale(0.4);
                    mc.level.addParticle(WFParticles.SKY_CURRENT.get(), p.x + r.nextGaussian() * 1.5, p.y + r.nextGaussian(), p.z + r.nextGaussian() * 1.5,
                        dir.x, dir.y, dir.z);
                }
            }
        }
        // Tide Maws: slow whirlpools of light in the tide plane while the tide stands High
        if (dev.worldsfall.world.tide.Tides.appliesTo(mc.level) && dev.worldsfall.world.tide.Tides.phase(mc.level) == dev.worldsfall.world.tide.TideCycle.Phase.HIGH) {
            double tide = dev.worldsfall.world.tide.Tides.height(mc.level);
            for (Vec3 m : maws) {
                if (Math.hypot(m.x - player.getX(), m.z - player.getZ()) < 192) {
                    for (int k = 0; k < 6; k++) {
                        double a = r.nextDouble() * Math.PI * 2, rad = 1 + r.nextDouble() * dev.worldsfall.undertow.TideMaws.PULL_RADIUS;
                        double x = m.x + Math.cos(a) * rad, z = m.z + Math.sin(a) * rad;
                        mc.level.addParticle(WFParticles.MAW_SWIRL.get(), x, tide + 0.2 + r.nextDouble() * 0.6, z, -Math.sin(a) * 0.25, -0.02, Math.cos(a) * 0.25);
                    }
                }
            }
        }
        for (SkyCurrents.Downdraft d : drafts) {
            double dx = d.x() - player.getX(), dz = d.z() - player.getZ();
            if (dx * dx + dz * dz < 64 * 64) {
                for (int k = 0; k < 3; k++) {
                    double a = r.nextDouble() * Math.PI * 2, rad = r.nextDouble() * d.radius();
                    double y = Mth.clamp(player.getY() + r.nextGaussian() * 18, SkyCurrents.DRAFT_MIN_Y, SkyCurrents.DRAFT_MAX_Y);
                    mc.level.addParticle(WFParticles.DOWNDRAFT_DUST.get(), d.x() + Math.cos(a) * rad, y, d.z() + Math.sin(a) * rad, 0, -0.6, 0);
                }
            }
        }
    }

    /** High wind above Y 150 in the End, swelling with height. */
    private static void ambience(Minecraft mc, LocalPlayer player) {
        if (player.getY() > ZENITH_Y && (wind == null || wind.isStopped())) {
            wind = new WindLoop(player);
            mc.getSoundManager().play(wind);
        }
    }

    /** The Zenith's wind loop: follows the player, fades out below Y 150 or outside the End. */
    static final class WindLoop extends AbstractTickableSoundInstance {
        private final LocalPlayer player;

        WindLoop(LocalPlayer player) {
            super(WFSounds.AMBIENT_ZENITH_LOOP.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.relative = true;
        }

        @Override
        public void tick() {
            if (player.isRemoved() || player.level().dimension() != Level.END) {
                stop();
                return;
            }
            float target = (float) Mth.clamp((player.getY() - ZENITH_Y) / 60.0, 0, 1) * 0.8f;
            volume += (target - volume) * 0.05f;
            if (target == 0 && volume < 0.01f) {
                stop();
            }
        }
    }
}
