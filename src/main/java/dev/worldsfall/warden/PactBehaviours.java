package dev.worldsfall.warden;

import dev.worldsfall.entity.Echo;
import dev.worldsfall.entity.Hoarder;
import dev.worldsfall.entity.UnmadeShard;
import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.registry.WFEffects;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.gravity.GravityZones;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The seven pacts that change a repeat fight in place (Pact of Depths has its own class, {@link DescendingWarden}).
 * Every effect is telegraphed and has the counterplay named in the proposal's table.
 */
public final class PactBehaviours {
    public static final String THIEF_TAG = "worldsfall_warden_thief";
    public static final String MOVE_GRAVITY_BREATH = "gravity_breath", MOVE_TIDE_DIVE = "tide_dive", MOVE_WING_GALE = "wing_gale";
    private static final List<String> MOVE_ORDER = List.of(MOVE_GRAVITY_BREATH, MOVE_TIDE_DIVE, MOVE_WING_GALE);

    /** An orbiting trio of Unmade Shards left by a destroyed pillar crystal (Pact of Shards). */
    private static final class Emitter {
        final Vec3 at;
        int timer;
        Emitter(Vec3 at, int timer) {
            this.at = at;
            this.timer = timer;
        }
    }

    private static final List<Emitter> EMITTERS = new ArrayList<>();
    private static boolean wasSitting;
    private static boolean thievesArrived;
    private static int diveTimer = 400, galeTimer = 240, diveWindup = -1, galeWindup = -1;
    private static Vec3 diveAt = Vec3.ZERO;
    private static int ringTimer;

    private PactBehaviours() {}

    static void clear(ServerLevel end) {
        EMITTERS.clear();
        wasSitting = false;
        thievesArrived = false;
        diveTimer = 400;
        galeTimer = 240;
        diveWindup = -1;
        galeWindup = -1;
        ringTimer = 0;
    }

    static void idle(ServerLevel end) {
        EMITTERS.clear();
    }

    static void onFightStart(ServerLevel end, EnderDragon dragon, WardenData data) {
        clear(end);
        if (data.has(Pact.ECHOES)) {
            spawnEchoes(end);
        }
    }

    static void tick(ServerLevel end, EnderDragon dragon, WardenData data) {
        long t = end.getGameTime();
        if (data.has(Pact.TIDE)) {
            tideRing(end, data, t);
        }
        if (data.has(Pact.SHARDS)) {
            shards(end, dragon);
        }
        if (data.has(Pact.HOARD)) {
            hoard(end, dragon);
        }
        if (data.has(Pact.MEMORY)) {
            memory(end, dragon, data);
        }
        if (data.has(Pact.STILLNESS) && t % 10 == 0) {
            stillness(end);
        }
        if (data.has(Pact.LONE)) {
            dragon.nearestCrystal = null; // crystals no longer heal
        }
    }

    static void onPillarCrystal(ServerLevel end, EnderDragon dragon, EndCrystal crystal, WardenData data) {
        if (data.has(Pact.TIDE)) {
            int r = Math.min(WardenFight.TIDE_RING_START, data.tideRadius() + WardenFight.TIDE_RING_RECEDE);
            data.setTideRadius(r);
            for (ServerPlayer p : WardenFight.fighters(end)) {
                WardenFight.play(p, WFSounds.EVENT_WARDEN_TIDE_RECEDE.get(), 1.0f, 1.0f);
            }
        }
        if (data.has(Pact.SHARDS)) {
            EMITTERS.add(new Emitter(crystal.position().add(0, 1.5, 0), 60 + EMITTERS.size() * 13));
        }
    }

    // ---- Pact of the Tide: a local tide rings the main island and creeps inward
    private static void tideRing(ServerLevel end, WardenData data, long t) {
        int r = data.tideRadius();
        if (r <= 0) {
            r = WardenFight.TIDE_RING_START;
            data.setTideRadius(r);
        }
        if (++ringTimer >= WardenFight.TIDE_RING_INTERVAL && r > WardenFight.TIDE_RING_MIN) {
            ringTimer = 0;
            r = Math.max(WardenFight.TIDE_RING_MIN, r - WardenFight.TIDE_RING_STEP);
            data.setTideRadius(r);
            for (ServerPlayer p : WardenFight.fighters(end)) {
                WardenFight.play(p, WFSounds.EVENT_WARDEN_TIDE_CREEP.get(), 1.0f, 1.0f);
                p.sendOverlayMessage(Component.translatable("message.worldsfall.warden.tide_creeps"));
            }
        }
        for (ServerPlayer p : WardenFight.fighters(end)) {
            double d = Math.sqrt(p.getX() * p.getX() + p.getZ() * p.getZ());
            if (d > r && WardenFight.isFighter(p) && !dev.worldsfall.world.tide.TideEffects.isProtected(p)) {
                p.addEffect(new MobEffectInstance(WFEffects.VOID_EROSION, 30, 0, true, true, true));
            }
            if (t % 4 == 0) {
                // a wall of teal light on the ring near each fighter, so it reads from wherever they stand
                double a0 = Math.atan2(p.getZ(), p.getX());
                for (int i = -12; i <= 12; i++) {
                    double a = a0 + i * 0.035;
                    double y = p.getY() - 6 + end.getRandom().nextDouble() * 14;
                    end.sendParticles(p, WFParticles.TIDE_CREST.get(), true, false, Math.cos(a) * r, y, Math.sin(a) * r, 1, 0.1, 0.4, 0.1, 0.0);
                }
            }
        }
    }

    // ---- Pact of Shards: three orbiting shards fire telegraphed volleys
    private static void shards(ServerLevel end, EnderDragon dragon) {
        long t = end.getGameTime();
        for (Emitter e : EMITTERS) {
            for (int k = 0; k < 3; k++) {
                double a = t * 0.12 + k * Math.PI * 2 / 3;
                end.sendParticles(WFParticles.SHARD_SPARK.get(), e.at.x + Math.cos(a) * 1.6, e.at.y + Math.sin(t * 0.07 + k) * 0.4,
                    e.at.z + Math.sin(a) * 1.6, 1, 0, 0, 0, 0);
            }
            if (--e.timer == 20) {
                WardenFight.playAt(end, e.at, WFSounds.EVENT_WARDEN_SHARD_CHARGE.get(), 2.0f, 1.0f);
                end.sendParticles(ParticleTypes.END_ROD, e.at.x, e.at.y, e.at.z, 20, 0.8, 0.8, 0.8, 0.02);
            }
            if (e.timer <= 0) {
                e.timer = 120;
                ServerPlayer target = nearest(end, e.at, 64);
                if (target == null) {
                    continue;
                }
                for (int k = 0; k < 3; k++) {
                    UnmadeShard shard = WFEntities.UNMADE_SHARD.get().create(end, EntitySpawnReason.TRIGGERED);
                    if (shard == null) {
                        continue;
                    }
                    double a = t * 0.12 + k * Math.PI * 2 / 3;
                    Vec3 from = e.at.add(Math.cos(a) * 1.6, 0, Math.sin(a) * 1.6);
                    Vec3 aim = target.getEyePosition().subtract(from).normalize().scale(1.1);
                    shard.setOwner(dragon);
                    shard.withDamage(4.0f);
                    shard.launch(from, aim);
                    end.addFreshEntity(shard);
                }
            }
        }
    }

    // ---- Pact of Echoes: Echoes disguised as end stone and obsidian scatter around the arena
    private static void spawnEchoes(ServerLevel end) {
        var r = end.getRandom();
        int placed = 0;
        for (int attempt = 0; attempt < 60 && placed < 7; attempt++) {
            double a = r.nextDouble() * Math.PI * 2, d = 12 + r.nextDouble() * 34;
            int x = Mth.floor(Math.cos(a) * d), z = Mth.floor(Math.sin(a) * d);
            int y = end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y < 40) {
                continue;
            }
            Echo echo = WFEntities.ECHO.get().create(end, EntitySpawnReason.EVENT);
            if (echo == null) {
                continue;
            }
            echo.snapTo(x + 0.5, y, z + 0.5, 0, 0);
            echo.setDisguise(r.nextInt(4) == 0 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.END_STONE.defaultBlockState());
            echo.setPersistenceRequired();
            end.addFreshEntity(echo);
            placed++;
        }
    }

    // ---- Pact of the Hoard: two Hoarders steal dropped items and arrows mid-fight
    private static void hoard(ServerLevel end, EnderDragon dragon) {
        if (!thievesArrived && dragon.getHealth() <= dragon.getMaxHealth() * 0.8f) {
            thievesArrived = true;
            BlockPos portal = WardenFight.portalTop(end);
            for (int k = 0; k < 2; k++) {
                Hoarder h = WFEntities.HOARDER.get().create(end, EntitySpawnReason.EVENT);
                if (h == null) {
                    continue;
                }
                double a = k * Math.PI + end.getRandom().nextDouble();
                int x = portal.getX() + (int) (Math.cos(a) * 12), z = portal.getZ() + (int) (Math.sin(a) * 12);
                h.snapTo(x + 0.5, end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5, 0, 0);
                h.addTag(THIEF_TAG);
                h.setPersistenceRequired();
                end.addFreshEntity(h);
                end.sendParticles(ParticleTypes.PORTAL, h.getX(), h.getY() + 1, h.getZ(), 40, 0.5, 1.0, 0.5, 0.4);
                for (ServerPlayer p : WardenFight.fighters(end)) {
                    p.sendOverlayMessage(Component.translatable("message.worldsfall.warden.thieves"));
                }
            }
        }
        if (end.getGameTime() % 5 != 0) {
            return;
        }
        for (Hoarder h : end.getEntitiesOfClass(Hoarder.class, new AABB(-WardenFight.ARENA_RADIUS, 0, -WardenFight.ARENA_RADIUS,
            WardenFight.ARENA_RADIUS, 256, WardenFight.ARENA_RADIUS), x -> x.entityTags().contains(THIEF_TAG))) {
            List<ItemStack> stolen = new ArrayList<>(h.getData(WFAttachments.STOLEN));
            boolean took = false;
            for (Entity e : end.getEntities(h, h.getBoundingBox().inflate(2.5), x -> x instanceof ItemEntity || x instanceof AbstractArrow)) {
                if (e instanceof ItemEntity item && !item.getItem().isEmpty()) {
                    stolen.add(item.getItem().copy());
                    item.discard();
                    took = true;
                } else if (e instanceof AbstractArrow arrow && arrow.isAlive()) {
                    stolen.add(new ItemStack(net.minecraft.world.item.Items.ARROW));
                    arrow.discard();
                    took = true;
                }
            }
            if (took) {
                h.setData(WFAttachments.STOLEN, List.copyOf(stolen));
                end.sendParticles(ParticleTypes.PORTAL, h.getX(), h.getY() + 1.5, h.getZ(), 12, 0.3, 0.5, 0.3, 0.2);
            }
            // drift toward the nearest loose item
            ItemEntity goal = null;
            double best = 16 * 16;
            for (ItemEntity item : end.getEntitiesOfClass(ItemEntity.class, h.getBoundingBox().inflate(16))) {
                double d = item.distanceToSqr(h);
                if (d < best) {
                    best = d;
                    goal = item;
                }
            }
            if (goal != null) {
                h.getNavigation().moveTo(goal, 1.1);
            }
        }
    }

    /** A pact thief died: everything it took comes back, plus a bonus. */
    public static void onThiefDeath(ServerLevel level, Hoarder thief, java.util.Collection<ItemEntity> drops) {
        for (ItemStack s : thief.getData(WFAttachments.STOLEN)) {
            drops.add(new ItemEntity(level, thief.getX(), thief.getY() + 0.5, thief.getZ(), s.copy()));
        }
        drops.add(new ItemEntity(level, thief.getX(), thief.getY() + 0.5, thief.getZ(), new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 2)));
        drops.add(new ItemEntity(level, thief.getX(), thief.getY() + 0.5, thief.getZ(),
            new ItemStack(dev.worldsfall.registry.WFItems.DRAGON_SCALE.get(), 1)));
    }

    // ---- Pact of Memory: after each perch the dragon learns one new move for the rest of the fight
    private static void memory(ServerLevel end, EnderDragon dragon, WardenData data) {
        boolean sitting = dragon.getPhaseManager().getCurrentPhase().isSitting();
        if (wasSitting && !sitting) {
            for (String move : MOVE_ORDER) {
                if (data.learn(move)) {
                    WardenFight.playAt(end, dragon.position(), WFSounds.EVENT_WARDEN_LEARN.get(), 6.0f, 1.0f);
                    for (ServerPlayer p : WardenFight.fighters(end)) {
                        p.sendOverlayMessage(Component.translatable("message.worldsfall.warden.learns",
                            Component.translatable("move.worldsfall." + move)));
                    }
                    break;
                }
            }
        }
        wasSitting = sitting;
        List<String> moves = data.learned();
        if (moves.contains(MOVE_GRAVITY_BREATH)) {
            gravityBreath(end, dragon);
        }
        if (moves.contains(MOVE_TIDE_DIVE) && !sitting) {
            tideDive(end, dragon);
        }
        if (moves.contains(MOVE_WING_GALE) && !sitting) {
            wingGale(end, dragon);
        }
    }

    /** The dragon's breath clouds reverse gravity for four seconds. */
    private static void gravityBreath(ServerLevel end, EnderDragon dragon) {
        for (AreaEffectCloud cloud : end.getEntitiesOfClass(AreaEffectCloud.class, dragon.getBoundingBox().inflate(120),
            c -> c.getOwner() == dragon && !c.entityTags().contains("worldsfall_lensed"))) {
            cloud.addTag("worldsfall_lensed");
            GravityZones.addLens(end, cloud.position(), Math.max(3.0, cloud.getRadius()), 80);
            WardenFight.playAt(end, cloud.position(), WFSounds.ENTITY_THE_UNMADE_LENS_HUM.get(), 2.0f, 0.8f);
        }
    }

    /** Dives below the island and bursts up under a player, after a 1.5 s ring telegraph. */
    private static void tideDive(ServerLevel end, EnderDragon dragon) {
        if (diveWindup >= 0) {
            WardenFight.ring(end, diveAt.add(0, 0.2, 0), 3.0, 24, WFParticles.TIDE_CREST.get());
            if (--diveWindup < 0) {
                dragon.snapTo(diveAt.x, diveAt.y - 10, diveAt.z, dragon.getYRot(), 0);
                WardenFight.steer(dragon, diveAt.add(0, 16, 0));
                WardenFight.playAt(end, diveAt, WFSounds.EVENT_WARDEN_BREACH.get(), 4.0f, 1.0f);
                end.sendParticles(ParticleTypes.END_ROD, diveAt.x, diveAt.y + 1, diveAt.z, 60, 1.5, 2.0, 1.5, 0.2);
                for (ServerPlayer p : end.getEntitiesOfClass(ServerPlayer.class, new AABB(diveAt, diveAt).inflate(3.2, 3, 3.2))) {
                    if (WardenFight.isFighter(p)) {
                        p.hurtServer(end, end.damageSources().mobAttack(dragon), 8.0f);
                        p.push(0, 1.1, 0);
                        p.needsSync = true;
                    }
                }
            }
            return;
        }
        if (--diveTimer > 0) {
            return;
        }
        diveTimer = 500;
        List<ServerPlayer> grounded = WardenFight.fighters(end).stream().filter(p -> p.onGround() && WardenFight.isFighter(p)).toList();
        if (grounded.isEmpty()) {
            return;
        }
        ServerPlayer target = grounded.get(end.getRandom().nextInt(grounded.size()));
        diveAt = target.position();
        diveWindup = 30;
        WardenFight.playAt(end, diveAt, WFSounds.EVENT_WARDEN_DIVE_WARNING.get(), 3.0f, 1.0f);
    }

    /** A cone of wind in front of the dragon that pushes players toward the edge; Steadiness holds you in place. */
    private static void wingGale(ServerLevel end, EnderDragon dragon) {
        if (galeWindup >= 0) {
            Vec3 look = dragon.getLookAngle();
            end.sendParticles(ParticleTypes.CLOUD, dragon.getX() + look.x * 6, dragon.getY(), dragon.getZ() + look.z * 6, 10, 2, 1, 2, 0.1);
            if (--galeWindup < 0) {
                for (ServerPlayer p : WardenFight.fighters(end)) {
                    Vec3 to = p.position().subtract(dragon.position());
                    if (to.length() > 34 || !WardenFight.isFighter(p) || p.hasEffect(WFEffects.STEADINESS)) {
                        continue;
                    }
                    Vec3 dir = to.normalize();
                    Vec3 facing = new Vec3(-Mth.sin(dragon.getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(dragon.getYRot() * Mth.DEG_TO_RAD)).scale(-1);
                    if (dir.dot(facing) < 0.35) {
                        continue;
                    }
                    Vec3 out = new Vec3(p.getX(), 0, p.getZ()).normalize();
                    p.push(out.x * 1.5, 0.35, out.z * 1.5);
                    p.needsSync = true;
                }
            }
            return;
        }
        if (--galeTimer > 0) {
            return;
        }
        ServerPlayer near = nearest(end, dragon.position(), 30);
        if (near == null) {
            galeTimer = 40;
            return;
        }
        galeTimer = 320;
        galeWindup = 16;
        WardenFight.playAt(end, dragon.position(), WFSounds.EVENT_WARDEN_GALE.get(), 6.0f, 1.0f);
    }

    // ---- Pact of Stillness: potion effects stop working near the portal
    private static void stillness(ServerLevel end) {
        for (ServerPlayer p : end.players()) {
            if (p.getX() * p.getX() + p.getZ() * p.getZ() > 128 * 128 || !WardenFight.isFighter(p)) {
                continue;
            }
            List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> remove = new ArrayList<>();
            for (MobEffectInstance e : p.getActiveEffects()) {
                if (e.getEffect().value().isBeneficial() && !e.getEffect().is(WFEffects.STEADINESS)) {
                    remove.add(e.getEffect());
                }
            }
            for (var h : remove) {
                p.removeEffect(h);
            }
            if (!remove.isEmpty()) {
                end.sendParticles(WFParticles.STILL_RING.get(), p.getX(), p.getY() + 1, p.getZ(), 6, 0.4, 0.6, 0.4, 0.0);
            }
        }
    }

    // ---- Pact of the Lone Warden: +20 % speed (applied after the dragon's own movement)
    public static void loneWardenMove(EnderDragon dragon) {
        if (!dragon.getPhaseManager().getCurrentPhase().isSitting() && !dragon.isNoAi()) {
            Vec3 v = dragon.getDeltaMovement();
            dragon.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(v.x * 0.2, v.y * 0.2, v.z * 0.2));
        }
    }

    private static ServerPlayer nearest(ServerLevel end, Vec3 at, double range) {
        ServerPlayer best = null;
        double bestD = range * range;
        for (ServerPlayer p : WardenFight.fighters(end)) {
            double d = p.distanceToSqr(at);
            if (d < bestD && WardenFight.isFighter(p)) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    static boolean isThief(Entity e) {
        return e.entityTags().contains(THIEF_TAG);
    }
}
