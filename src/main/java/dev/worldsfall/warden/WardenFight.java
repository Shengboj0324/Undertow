package dev.worldsfall.warden;

import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The server half of the Warden Cycle (proposal "The dragon: the Warden Cycle"). The first fight keeps every vanilla rule
 * and only gains story beats ({@link Wardensong}); a respawn whose crystals carry pacts becomes a trial ({@link PactBehaviours},
 * {@link DescendingWarden}); four Stillness Crystals start the Release ({@link WardenRelease}). Everything is driven from the
 * End's level tick and a few entity events, with no change to vanilla classes.
 */
public final class WardenFight {
    public static final String KIND_PACT = "pact:";
    public static final String KIND_STILLNESS = "stillness";
    /** Radius around the main island that counts as the arena. */
    public static final double ARENA_RADIUS = 160;
    public static final int TIDE_RING_START = 110;
    public static final int TIDE_RING_MIN = 34;
    public static final int TIDE_RING_STEP = 8;
    public static final int TIDE_RING_INTERVAL = 1800;
    public static final int TIDE_RING_RECEDE = 24;
    public static final net.minecraft.resources.Identifier LONE_HEALTH =
        net.minecraft.resources.Identifier.fromNamespaceAndPath(dev.worldsfall.Worldsfall.MODID, "lone_warden_health");

    private WardenFight() {}

    // ------------------------------------------------------------------ crystals on the exit portal

    /** The (up to four) crystals standing on the exit portal's respawn spots. */
    public static List<EndCrystal> respawnCrystals(ServerLevel end) {
        List<EndCrystal> out = new ArrayList<>();
        for (EndCrystal c : end.getEntitiesOfClass(EndCrystal.class, new AABB(-6, 30, -6, 7, 110, 7))) {
            double dx = c.getX() - 0.5, dz = c.getZ() - 0.5;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 2.4 && d < 3.6 && !(c instanceof LumenHeart)) {
                out.add(c);
            }
        }
        return out;
    }

    public static @Nullable Pact pactOf(EndCrystal c) {
        String kind = c.getData(WFAttachments.CRYSTAL_KIND);
        if (kind.startsWith(KIND_PACT)) {
            try {
                return Pact.byId(kind.substring(KIND_PACT.length()));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    public static boolean isStillness(EndCrystal c) {
        return KIND_STILLNESS.equals(c.getData(WFAttachments.CRYSTAL_KIND));
    }

    /** Reads what the respawn crystals carry; the next dragon to appear takes these pacts. */
    public static void readRespawnCrystals(ServerLevel end) {
        List<EndCrystal> crystals = respawnCrystals(end);
        if (crystals.isEmpty()) {
            return;
        }
        Set<Pact> pacts = EnumSet.noneOf(Pact.class);
        int stillness = 0;
        for (EndCrystal c : crystals) {
            Pact p = pactOf(c);
            if (p != null) {
                pacts.add(p);
            } else if (isStillness(c)) {
                stillness++;
            }
        }
        WardenData.get(end.getServer()).setPending(pacts, stillness >= 4 && crystals.size() >= 4);
    }

    // ------------------------------------------------------------------ dragon lifecycle

    /** A freshly spawned dragon (not one loaded from disk). */
    public static void onDragonSpawned(ServerLevel end, EnderDragon dragon) {
        if (dragon instanceof ElderWarden) {
            return;
        }
        EnderDragonFight fight = end.getDragonFight();
        WardenData data = WardenData.get(end.getServer());
        if (fight == null || !fight.hasPreviouslyKilledDragon()) {
            // the first fight: vanilla rules, Wardensong story beats
            data.endFight();
            data.clearCrackFlags();
            return;
        }
        if (data.pendingStillness() && WardenRelease.ready(end)) {
            data.endFight();
            WardenRelease.begin(end, dragon, data);
            return;
        }
        Set<Pact> pacts = data.pending();
        if (pacts.isEmpty()) {
            data.endFight();
            return;
        }
        data.beginFight(dragon.getUUID(), pacts, end.getGameTime());
        int weight = Pact.weight(pacts);
        dragon.setCustomName(Component.translatable("entity.worldsfall.warden_dragon", weight));
        if (pacts.contains(Pact.LONE)) {
            var health = dragon.getAttribute(Attributes.MAX_HEALTH);
            if (health != null && health.getModifier(LONE_HEALTH) == null) {
                health.addPermanentModifier(new AttributeModifier(LONE_HEALTH, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                dragon.setHealth(dragon.getMaxHealth());
            }
        }
        PactBehaviours.onFightStart(end, dragon, data);
        for (ServerPlayer p : fighters(end)) {
            p.sendSystemMessage(Component.translatable("message.worldsfall.warden.pacts", describe(pacts), weight));
            play(p, WFSounds.EVENT_WARDEN_LEARN.get(), 1.0f, 0.7f);
        }
    }

    public static Component describe(Set<Pact> pacts) {
        Component out = null;
        for (Pact p : pacts) {
            Component name = Component.translatable("pact.worldsfall." + p.id()).withColor(p.color);
            out = out == null ? name : Component.empty().append(out).append(", ").append(name);
        }
        return out == null ? Component.empty() : out;
    }

    /** The End's level tick. */
    public static void tick(ServerLevel end) {
        WardenData data = WardenData.get(end.getServer());
        long t = end.getGameTime();
        if (t % 10 == 0) {
            readRespawnCrystals(end);
        }
        WardenRelease.tick(end, data);
        EnderDragonFight fight = end.getDragonFight();
        EnderDragon dragon = currentDragon(end);
        if (dragon == null) {
            if (!data.active().isEmpty() && fight != null && fight.dragonUUID() == null) {
                finishFight(end, data);
            }
            PactBehaviours.idle(end);
            return;
        }
        if (fight != null && !fight.hasPreviouslyKilledDragon()) {
            Wardensong.tick(end, dragon, data);
            return;
        }
        if (!data.active().isEmpty() && dragon.getUUID().equals(data.dragon())) {
            PactBehaviours.tick(end, dragon, data);
            if (data.has(Pact.DEPTHS)) {
                DescendingWarden.tick(end, dragon, data);
            }
        }
    }

    public static @Nullable EnderDragon currentDragon(ServerLevel end) {
        for (EnderDragon d : end.getDragons()) {
            if (d.isAlive() && !(d instanceof ElderWarden)) {
                return d;
            }
        }
        return null;
    }

    /** A pillar crystal was destroyed (not one of the respawn crystals). */
    public static void onCrystalRemoved(ServerLevel end, EndCrystal crystal) {
        if (crystal instanceof LumenHeart) {
            return;
        }
        double dx = crystal.getX() - 0.5, dz = crystal.getZ() - 0.5;
        if (dx * dx + dz * dz < 36) {
            return; // a respawn crystal
        }
        EnderDragonFight fight = end.getDragonFight();
        EnderDragon dragon = currentDragon(end);
        if (fight == null || dragon == null) {
            return;
        }
        WardenData data = WardenData.get(end.getServer());
        if (!fight.hasPreviouslyKilledDragon()) {
            Wardensong.onPillarCrystal(end, crystal, data);
        } else if (!data.active().isEmpty()) {
            PactBehaviours.onPillarCrystal(end, dragon, crystal, data);
        }
    }

    public static void onDragonDeath(ServerLevel end, EnderDragon dragon) {
        if (dragon instanceof ElderWarden) {
            return;
        }
        WardenData data = WardenData.get(end.getServer());
        if (!data.active().isEmpty() && dragon.getUUID().equals(data.dragon())) {
            WardenRewards.grant(end, data);
            finishFight(end, data);
        }
    }

    static void finishFight(ServerLevel end, WardenData data) {
        DescendingWarden.restoreTide(end, data);
        PactBehaviours.clear(end);
        data.endFight();
    }

    /** Whether incoming damage to a dragon must be ignored (dives, submerged phases, the calm Release). */
    public static boolean ignoresDamage(ServerLevel end, EnderDragon dragon, DamageSource source) {
        if (dragon instanceof ElderWarden) {
            return true;
        }
        WardenData data = WardenData.get(end.getServer());
        if (data.release() == WardenData.RELEASE_LISTENING) {
            // hitting the listening dragon breaks the vigil; the fight goes on as a plain one
            WardenRelease.abort(end, dragon, data, true);
            return false;
        }
        if (data.release() == WardenData.RELEASE_BOWING || data.release() == WardenData.RELEASE_DEPARTING) {
            return true;
        }
        return data.has(Pact.DEPTHS) && dragon.getUUID().equals(data.dragon()) && DescendingWarden.invulnerable(end, dragon, data);
    }

    /** Whether the dragon may break blocks right now (never while scripted underground or calm). */
    public static boolean mayGrief(ServerLevel end, EnderDragon dragon) {
        if (dragon instanceof ElderWarden) {
            return false;
        }
        WardenData data = WardenData.get(end.getServer());
        if (data.release() != WardenData.RELEASE_NONE && data.release() != WardenData.RELEASE_DONE) {
            return false;
        }
        return !(data.has(Pact.DEPTHS) && data.depthsStage() != WardenData.DEPTHS_RIM);
    }

    /** Knockback scale for a player hit by the dragon (clamped in the Drift so nobody is thrown below the tide). */
    public static float knockbackScale(ServerLevel end, LivingEntity target) {
        WardenData data = WardenData.get(end.getServer());
        if (target instanceof ServerPlayer && data.has(Pact.DEPTHS) && data.depthsStage() == WardenData.DEPTHS_DRIFT) {
            return 0.35f;
        }
        return 1.0f;
    }

    // ------------------------------------------------------------------ helpers

    public static List<ServerPlayer> fighters(ServerLevel end) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : end.players()) {
            if (!p.isSpectator() && p.isAlive() && p.getX() * p.getX() + p.getZ() * p.getZ() < ARENA_RADIUS * ARENA_RADIUS) {
                out.add(p);
            }
        }
        return out;
    }

    /** Steers a dragon toward a point using the vanilla charge phase, so it keeps its flight animation and turning. */
    public static void steer(EnderDragon dragon, Vec3 target) {
        dragon.setNoAi(false);
        if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.CHARGING_PLAYER) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
        }
        dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(target);
    }

    /** Holds a dragon perfectly still at a point (perches), wings folded. */
    public static void hold(EnderDragon dragon, Vec3 at, float yaw) {
        dragon.setNoAi(true);
        dragon.setDeltaMovement(Vec3.ZERO);
        dragon.snapTo(at.x, at.y, at.z, yaw, 0);
    }

    public static void play(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        dev.worldsfall.util.WFUtil.playTo(p, sound, SoundSource.HOSTILE, volume, pitch);
    }

    public static void playAt(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** Teal ring of particles at a height, used for tide and dive telegraphs. */
    public static void ring(ServerLevel level, Vec3 center, double radius, int points, net.minecraft.core.particles.ParticleOptions particle) {
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points;
            level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    public static BlockPos portalTop(ServerLevel end) {
        BlockPos top = end.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            EnderDragonFight.getPodiumLocation(BlockPos.ZERO));
        return top;
    }

    static boolean isFighter(LivingEntity e) {
        return e instanceof ServerPlayer p && !p.isSpectator() && !p.isCreative();
    }

    static void sparkAt(ServerLevel level, Vec3 at, int n) {
        level.sendParticles(WFParticles.SHARD_SPARK.get(), at.x, at.y, at.z, n, 0.4, 0.4, 0.4, 0.02);
    }

    static boolean isLumen(net.minecraft.world.entity.Entity e) {
        return e.getType() == WFEntities.LUMEN_HEART.get();
    }

    static boolean dimensionIsEnd(Level level) {
        return level.dimension() == Level.END;
    }
}
