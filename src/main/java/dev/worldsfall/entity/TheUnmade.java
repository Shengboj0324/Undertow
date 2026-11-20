package dev.worldsfall.entity;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.registry.WFDamageTypes;
import dev.worldsfall.registry.WFEffects;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFItems;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.world.Progress;
import dev.worldsfall.world.fracture.FractureArena;
import dev.worldsfall.world.fracture.FractureData;
import dev.worldsfall.world.gravity.GravityZones;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Unmade (§13): the Heart of Stillness half-dissolved into the void.
 * Phase 1 The Shell (immune behind three shard rings, each anchored by 3 Tether Pylons) → Phase 2 The
 * Unbinding (fragments drift and crumble, regen when unpressured) → Phase 3 Unmade (inward pull, collapse
 * timer) → final blow: freeze, white core, debris reforms into one stable island.
 */
public class TheUnmade extends Monster {
    public enum Attack { NONE, VOLLEY, SWEEP, LENS, SWARM, PULSE, GRAB }

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(TheUnmade.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RINGS = SynchedEntityData.defineId(TheUnmade.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EXPOSED = SynchedEntityData.defineId(TheUnmade.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(TheUnmade.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<org.joml.Vector3fc> TELE_A = SynchedEntityData.defineId(TheUnmade.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<org.joml.Vector3fc> TELE_B = SynchedEntityData.defineId(TheUnmade.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> FREEZE = SynchedEntityData.defineId(TheUnmade.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HIT_FLASH = SynchedEntityData.defineId(TheUnmade.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ACTIVE = SynchedEntityData.defineId(TheUnmade.class, EntityDataSerializers.BOOLEAN);

    public static final int PHASE_SHELL = 1, PHASE_UNBINDING = 2, PHASE_UNMADE = 3, PHASE_FINALE = 4;
    public static final int EXPOSE_TICKS = 240;
    public static final int PYLON_REGROW = 400;
    public static final int FREEZE_TICKS = 40;
    public static final int REFORM_TICKS = 300;
    public static final double MAX_DAMAGE_RANGE = 64;

    private final ServerBossEvent bossEvent = new ServerBossEvent(getUUID(), Component.translatable("entity.worldsfall.the_unmade"),
        BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.NOTCHED_6);
    private int attackTicks;
    private int attackCooldown = 60;
    private int exposures;
    private final int[] pylonRegrow = {-1, -1, -1};
    private long lastDamagedAt;
    private int driftTimer;
    private int crumbleTimer = -1;
    private int crumbleIndex = -1;
    private int phase3Ticks;
    private int emptyTicks;
    private int finaleTicks;
    private int scaledPlayers = 1;

    public TheUnmade(EntityType<? extends TheUnmade> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
        this.xpReward = 500;
        bossEvent.setDarkenScreen(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 720.0).add(Attributes.ARMOR, 10.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 96.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, PHASE_SHELL);
        builder.define(RINGS, 7);
        builder.define(EXPOSED, 0);
        builder.define(ATTACK, 0);
        builder.define(TELE_A, new Vector3f());
        builder.define(TELE_B, new Vector3f());
        builder.define(FREEZE, 0);
        builder.define(HIT_FLASH, 0);
        builder.define(ACTIVE, false);
    }

    // ------------------------------------------------------------ accessors for renderer / client systems
    public int phase() { return entityData.get(PHASE); }
    public int rings() { return entityData.get(RINGS); }
    public boolean isExposed() { return phase() >= PHASE_UNBINDING || entityData.get(EXPOSED) > 0; }
    public Attack attack() { return Attack.values()[entityData.get(ATTACK)]; }
    public Vector3f telegraphA() { return new Vector3f(entityData.get(TELE_A)); }
    public Vector3f telegraphB() { return new Vector3f(entityData.get(TELE_B)); }
    public int freezeTicks() { return entityData.get(FREEZE); }
    public int hitFlash() { return entityData.get(HIT_FLASH); }
    public boolean isActive() { return entityData.get(ACTIVE); }
    public int attackTicks() { return attackTicks; }

    /** Pulse period in ticks: one pulse every 2 s, quickening as health drops (§13.10). */
    public float pulsePeriod() {
        return 40.0f * (0.45f + 0.55f * getHealth() / getMaxHealth());
    }

    public static float scaledHealth(int players, int base) {
        float factor = switch (Math.max(1, Math.min(4, players))) {
            case 1 -> 1.0f;
            case 2 -> 1.5f;
            case 3 -> 1.9f;
            default -> 2.2f;
        };
        return base * factor;
    }

    public float threshold(int phaseEnd) {
        return getMaxHealth() * (phaseEnd == PHASE_SHELL ? 2f / 3f : 1f / 3f);
    }

    // ------------------------------------------------------------ main loop
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setDeltaMovement(Vec3.ZERO);
        if (entityData.get(HIT_FLASH) > 0) {
            entityData.set(HIT_FLASH, entityData.get(HIT_FLASH) - 1);
        }
        List<ServerPlayer> players = fightPlayers(level);
        updateBossBar(level);
        if (!isActive()) {
            hover(FractureArena.CORE_HEIGHT);
            if (players.stream().anyMatch(this::onRing)) {
                startFight(level, players);
            }
            return;
        }
        if (players.isEmpty()) {
            if (++emptyTicks > 600 && phase() != PHASE_FINALE) {
                resetFight(level);
            }
            return;
        }
        emptyTicks = 0;
        if (phase() == PHASE_FINALE) {
            tickFinale(level, players);
            return;
        }
        int freeze = entityData.get(FREEZE);
        if (freeze > 0) {
            entityData.set(FREEZE, freeze - 1);
            return;
        }
        tickPhaseMechanics(level, players);
        tickAttacks(level, players);
    }

    private void hover(double height) {
        double wantY = FractureArena.CENTER.getY() + height + Math.sin(tickCount * 0.04) * 0.6;
        setPos(FractureArena.CENTER.getX() + 0.5, getY() + (wantY - getY()) * 0.05, FractureArena.CENTER.getZ() + 0.5);
    }

    private boolean onRing(Player p) {
        double d = Math.sqrt(p.distanceToSqr(FractureArena.CENTER.getX() + 0.5, p.getY(), FractureArena.CENTER.getZ() + 0.5));
        return d > FractureArena.RING_RADIUS - 10 && d < FractureArena.RING_RADIUS + 10 && p.onGround();
    }

    private List<ServerPlayer> fightPlayers(ServerLevel level) {
        return level.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) < 128 * 128);
    }

    private void startFight(ServerLevel level, List<ServerPlayer> players) {
        entityData.set(ACTIVE, true);
        scaledPlayers = players.size();
        int base = WFConfig.get(WFConfig.SERVER.bossMaxHealth, 720);
        float hp = WFConfig.get(WFConfig.SERVER.bossPlayerScaling, true) ? scaledHealth(scaledPlayers, base) : base;
        var attr = getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(hp);
        }
        setHealth(hp);
        entityData.set(PHASE, PHASE_SHELL);
        entityData.set(RINGS, 7);
        exposures = 0;
        FractureArena.setExitRift(level, false);
        FractureArena.spawnPylons(level);
        level.playSound(null, blockPosition(), WFSounds.ENTITY_THE_UNMADE_PULSE.get(), SoundSource.HOSTILE, 4.0f, 0.6f);
    }

    private void resetFight(ServerLevel level) {
        entityData.set(ACTIVE, false);
        entityData.set(ATTACK, 0);
        entityData.set(EXPOSED, 0);
        entityData.set(PHASE, PHASE_SHELL);
        entityData.set(RINGS, 7);
        setHealth(getMaxHealth());
        FractureArena.rebuild(level);
        FractureArena.setExitRift(level, true);
        for (Shardling s : level.getEntitiesOfClass(Shardling.class, getBoundingBox().inflate(128))) {
            s.discard();
        }
        phase3Ticks = 0;
        emptyTicks = 0;
    }

    private void tickPhaseMechanics(ServerLevel level, List<ServerPlayer> players) {
        switch (phase()) {
            case PHASE_SHELL -> {
                hover(FractureArena.CORE_HEIGHT);
                int exposed = entityData.get(EXPOSED);
                if (exposed > 0) {
                    entityData.set(EXPOSED, exposed - 1);
                    if (exposed == 1 && exposures >= 3) {
                        setHealth(Math.min(getHealth(), threshold(PHASE_SHELL)));
                        enterPhase(level, PHASE_UNBINDING);
                    }
                }
                for (int r = 0; r < 3; r++) {
                    if (pylonRegrow[r] > 0 && --pylonRegrow[r] == 0) {
                        entityData.set(RINGS, rings() | (1 << r));
                        FractureArena.spawnPylons(level);
                    }
                }
            }
            case PHASE_UNBINDING -> {
                hover(FractureArena.CORE_HEIGHT);
                if (level.getGameTime() - lastDamagedAt > 100 && tickCount % 20 == 0) {
                    heal(2.0f);
                }
                if (++driftTimer >= 400) {
                    driftTimer = 0;
                    driftRandomFragment(level);
                }
                tickCrumble(level);
            }
            case PHASE_UNMADE -> {
                hover(1.5);
                phase3Ticks++;
                if (phase3Ticks == 6 * 1200) {
                    level.playSound(null, blockPosition(), WFSounds.ENTITY_THE_UNMADE_SWARM.get(), SoundSource.HOSTILE, 3.0f, 0.5f);
                }
                if (phase3Ticks > 6 * 1200 && tickCount % 40 == 0) {
                    removeDebrisPiece(level);
                }
            }
            default -> {
            }
        }
        if (tickCount % (int) Math.max(8, pulsePeriod()) == 0) {
            level.playSound(null, blockPosition(), WFSounds.ENTITY_THE_UNMADE_PULSE.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
        }
    }

    /** Inward pull strength for Phase 3 (§13.7); applied client-side to players and server-side to mobs. */
    public double pullStrength() {
        if (phase() != PHASE_UNMADE || freezeTicks() > 0) {
            return 0;
        }
        return phase3Ticks > 4 * 1200 ? 0.03 : 0.015;
    }

    public int phase3Ticks() {
        return phase3Ticks;
    }

    private void driftRandomFragment(ServerLevel level) {
        FractureData data = FractureData.get(level.getServer());
        List<Integer> alive = new ArrayList<>();
        for (int i = 0; i < data.fragments().size(); i++) {
            if (data.fragments().get(i).alive()) {
                alive.add(i);
            }
        }
        if (alive.isEmpty() || crumbleTimer >= 0) {
            return;
        }
        int idx = alive.get(random.nextInt(alive.size()));
        FractureArena.driftFragment(level, idx);
        level.playSound(null, data.fragments().get(idx).center(), WFSounds.EVENT_SUNDERING_RUMBLE.get(), SoundSource.HOSTILE, 2.0f, 1.2f);
        if (FractureArena.fragmentOffset(data.fragments().get(idx)) > 18) {
            crumbleIndex = idx;
            crumbleTimer = 60;
        }
    }

    private void tickCrumble(ServerLevel level) {
        if (crumbleTimer < 0) {
            return;
        }
        FractureData.Fragment f = FractureData.get(level.getServer()).fragments().get(crumbleIndex);
        if (crumbleTimer % 5 == 0) {
            level.sendParticles(WFParticles.ASH_INFLOW.get(), f.center().getX() + 0.5, f.center().getY() + 1, f.center().getZ() + 0.5, 30,
                f.radius() * 0.6, 0.5, f.radius() * 0.6, 0.02);
        }
        if (--crumbleTimer < 0) {
            FractureArena.crumbleFragment(level, crumbleIndex);
            crumbleIndex = -1;
        }
    }

    private void removeDebrisPiece(ServerLevel level) {
        BlockPos c = FractureArena.CENTER;
        for (int i = 0; i < 30; i++) {
            BlockPos p = c.offset(random.nextInt(70) - 35, random.nextInt(6) - 3, random.nextInt(70) - 35);
            if (level.getBlockState(p).is(dev.worldsfall.registry.WFBlocks.DRIFTSTONE.get())) {
                level.destroyBlock(p, false);
                return;
            }
        }
    }

    private void enterPhase(ServerLevel level, int phase) {
        entityData.set(PHASE, phase);
        entityData.set(FREEZE, FREEZE_TICKS);
        entityData.set(ATTACK, 0);
        attackCooldown = 60;
        level.playSound(null, blockPosition(), WFSounds.ENTITY_THE_UNMADE_PHASE_FREEZE.get(), SoundSource.HOSTILE, 4.0f, 1.0f);
        if (phase == PHASE_UNBINDING) {
            entityData.set(RINGS, 0);
            for (TetherPylon p : level.getEntitiesOfClass(TetherPylon.class, getBoundingBox().inflate(96))) {
                p.discard();
            }
        } else if (phase == PHASE_UNMADE) {
            FractureData data = FractureData.get(level.getServer());
            List<Integer> alive = new ArrayList<>();
            for (int i = 0; i < data.fragments().size(); i++) {
                if (data.fragments().get(i).alive()) {
                    alive.add(i);
                }
            }
            java.util.Collections.shuffle(alive, new java.util.Random(random.nextLong()));
            for (int i = 2; i < alive.size(); i++) {
                FractureArena.crumbleFragment(level, alive.get(i));
            }
            scatterDebris(level);
        }
    }

    /** Phase 3 debris field: dozens of small chunks in a loose disc around the Core. */
    private void scatterDebris(ServerLevel level) {
        BlockPos c = FractureArena.CENTER;
        for (int i = 0; i < 46; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 12 + random.nextDouble() * 28;
            BlockPos p = c.offset((int) (Math.cos(a) * r), random.nextInt(7) - 3, (int) (Math.sin(a) * r));
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    if (level.getBlockState(p.offset(dx, 0, dz)).isAir()) {
                        level.setBlock(p.offset(dx, 0, dz), dev.worldsfall.registry.WFBlocks.DRIFTSTONE.get().defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ attacks
    private void tickAttacks(ServerLevel level, List<ServerPlayer> players) {
        Attack current = attack();
        if (current == Attack.NONE) {
            if (--attackCooldown > 0) {
                return;
            }
            ServerPlayer target = players.get(random.nextInt(players.size()));
            setTarget(target);
            beginAttack(level, chooseAttack(), target);
            return;
        }
        attackTicks++;
        LivingEntity target = getTarget();
        switch (current) {
            case VOLLEY -> {
                if (attackTicks == 20 && target != null) {
                    fireVolley(level, target);
                    endAttack();
                }
            }
            case SWEEP -> {
                if (attackTicks == 25) {
                    resolveSweep(level);
                } else if (attackTicks > 35) {
                    endAttack();
                }
            }
            case LENS -> {
                if (attackTicks == 20) {
                    Vector3f c = telegraphA();
                    GravityZones.addLens(level, new Vec3(c.x, c.y, c.z), 4.0, 80);
                } else if (attackTicks > 100) {
                    endAttack();
                }
            }
            case SWARM -> {
                if (attackTicks == 20) {
                    int count = players.size() >= 3 ? 6 : 4;
                    for (int i = 0; i < count; i++) {
                        Shardling s = WFEntities.SHARDLING.get().create(level, EntitySpawnReason.MOB_SUMMONED);
                        if (s != null) {
                            double a = i * Math.PI * 2 / count;
                            s.snapTo(getX() + Math.cos(a) * 3, getY(), getZ() + Math.sin(a) * 3, 0, 0);
                            level.addFreshEntity(s);
                        }
                    }
                    endAttack();
                }
            }
            case PULSE -> {
                if (attackTicks == 40) {
                    resolvePulse(level, players);
                } else if (attackTicks > 60) {
                    endAttack();
                }
            }
            case GRAB -> {
                if (target != null && attackTicks < 30) {
                    entityData.set(TELE_B, target.position().add(0, 1, 0).toVector3f());
                }
                if (attackTicks == 30 && target != null) {
                    resolveGrab(level, target);
                    endAttack();
                }
            }
            default -> endAttack();
        }
    }

    private Attack chooseAttack() {
        List<Attack> pool = new ArrayList<>(List.of(Attack.VOLLEY, Attack.SWEEP));
        if (phase() == PHASE_SHELL) {
            pool.add(Attack.LENS);
        } else {
            pool.add(Attack.SWARM);
            pool.add(Attack.PULSE);
            pool.add(Attack.GRAB);
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private void beginAttack(ServerLevel level, Attack a, ServerPlayer target) {
        entityData.set(ATTACK, a.ordinal());
        attackTicks = 0;
        Vec3 t = target.position();
        switch (a) {
            case VOLLEY -> playSound(WFSounds.ENTITY_THE_UNMADE_SHARD_VOLLEY.get(), 3.0f, 1.0f);
            case SWEEP -> {
                Vec3 radial = t.subtract(position()).multiply(1, 0, 1).normalize();
                Vec3 perp = new Vec3(-radial.z, 0, radial.x);
                entityData.set(TELE_A, t.add(perp.scale(-8)).toVector3f());
                entityData.set(TELE_B, t.add(perp.scale(8)).toVector3f());
                playSound(WFSounds.ENTITY_THE_UNMADE_TENDRIL_SWEEP.get(), 3.0f, 0.8f);
            }
            case LENS -> {
                entityData.set(TELE_A, t.toVector3f());
                playSound(WFSounds.ENTITY_THE_UNMADE_LENS_HUM.get(), 3.0f, 1.0f);
            }
            case SWARM -> playSound(WFSounds.ENTITY_THE_UNMADE_SWARM.get(), 3.0f, 1.0f);
            case PULSE -> playSound(WFSounds.ENTITY_THE_UNMADE_VOID_PULSE_INHALE.get(), 4.0f, 1.0f);
            case GRAB -> {
                entityData.set(TELE_A, position().toVector3f());
                entityData.set(TELE_B, t.add(0, 1, 0).toVector3f());
                playSound(WFSounds.ENTITY_THE_UNMADE_TENDRIL_GRAB.get(), 3.0f, 1.0f);
            }
            default -> {
            }
        }
    }

    private void endAttack() {
        entityData.set(ATTACK, 0);
        attackTicks = 0;
        attackCooldown = switch (phase()) {
            case PHASE_SHELL -> 70;
            case PHASE_UNBINDING -> 60;
            default -> 40;
        } + random.nextInt(20);
    }

    private void fireVolley(ServerLevel level, LivingEntity target) {
        Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
        for (int i = 0; i < 6; i++) {
            UnmadeShard shard = new UnmadeShard(WFEntities.UNMADE_SHARD.get(), level).withDamage(4.0f);
            shard.setOwner(this);
            Vec3 aim = target.getEyePosition().subtract(from).normalize();
            Vec3 jitter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.06);
            double a = i * Math.PI / 3;
            shard.launch(from.add(Math.cos(a) * 2.5, Math.sin(a) * 2.5, 0), aim.add(jitter).normalize().scale(1.1));
            level.addFreshEntity(shard);
        }
    }

    private void resolveSweep(ServerLevel level) {
        Vec3 a = new Vec3(telegraphA());
        Vec3 b = new Vec3(telegraphB());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(2.5), e -> e != this)) {
            if (distanceToSegment(e.position().add(0, 0.5, 0), a, b) < 1.6) {
                e.hurtServer(level, WFDamageTypes.source(level, WFDamageTypes.UNMADE_TENDRIL, this, this), 8.0f);
                Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                e.push(away.x * 1.4, 0.5, away.z * 1.4);
                dev.worldsfall.util.WFUtil.syncMotion(e);
            }
        }
    }

    static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / Math.max(1e-6, ab.lengthSqr()), 0, 1);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    private void resolvePulse(ServerLevel level, List<ServerPlayer> players) {
        level.playSound(null, blockPosition(), WFSounds.ENTITY_THE_UNMADE_VOID_PULSE_EXHALE.get(), SoundSource.HOSTILE, 4.0f, 1.0f);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(64), e -> e != this && !(e instanceof Shardling))) {
            if (e.onGround() && !e.isPassenger()) {
                e.hurtServer(level, WFDamageTypes.source(level, WFDamageTypes.VOID_PULSE, this, this), 10.0f);
                e.addEffect(new MobEffectInstance(WFEffects.VOID_EROSION, 60, 0), this);
            }
        }
    }

    private void resolveGrab(ServerLevel level, LivingEntity target) {
        Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
        HitResult hit = level.clip(new ClipContext(from, target.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean blockedByPylon = !level.getEntitiesOfClass(TetherPylon.class, new AABB(from, target.position()).inflate(0.5),
            p -> distanceToSegment(p.position(), from, target.position()) < 1.2).isEmpty();
        if (hit.getType() != HitResult.Type.MISS || blockedByPylon) {
            return;
        }
        target.hurtServer(level, WFDamageTypes.source(level, WFDamageTypes.UNMADE_TENDRIL, this, this), 6.0f);
        Vec3 pull = from.subtract(target.position()).normalize().scale(1.6);
        target.push(pull.x, pull.y * 0.5 + 0.3, pull.z);
        dev.worldsfall.util.WFUtil.syncMotion(target);
    }

    // ------------------------------------------------------------ damage
    public void onPylonBroken(ServerLevel level, int ring) {
        if (phase() != PHASE_SHELL || !isActive()) {
            return;
        }
        boolean remaining = !level.getEntitiesOfClass(TetherPylon.class, getBoundingBox().inflate(96), p -> p.ring() == ring && p.isAlive()).isEmpty();
        if (!remaining && (rings() & (1 << ring)) != 0) {
            entityData.set(RINGS, rings() & ~(1 << ring));
            entityData.set(EXPOSED, EXPOSE_TICKS);
            exposures++;
            pylonRegrow[ring] = exposures >= 3 ? -1 : PYLON_REGROW;
            level.playSound(null, blockPosition(), WFSounds.ENTITY_THE_UNMADE_PYLON_BREAK.get(), SoundSource.HOSTILE, 4.0f, 0.6f);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (!isActive() || phase() == PHASE_FINALE || freezeTicks() > 0) {
            return false;
        }
        var attacker = source.getEntity();
        if (attacker != null && attacker.distanceToSqr(this) > MAX_DAMAGE_RANGE * MAX_DAMAGE_RANGE) {
            return false;
        }
        boolean projectile = source.getDirectEntity() instanceof Projectile;
        if (!isExposed() && !source.isCreativePlayer()) {
            playSound(WFSounds.ENTITY_THE_UNMADE_HURT.get(), 1.0f, 2.0f);
            return false;
        }
        if (!projectile && attacker instanceof Player p && p.distanceToSqr(this) > 6 * 6) {
            return false;
        }
        float before = getHealth();
        float floor = phase() == PHASE_SHELL ? threshold(PHASE_SHELL) : phase() == PHASE_UNBINDING ? threshold(PHASE_UNBINDING) : 0;
        float after = Math.max(floor, before - Math.max(0, amount - 0f));
        if (after <= 0.5f && phase() == PHASE_UNMADE) {
            beginFinale(level);
            return true;
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (getHealth() < floor) {
            setHealth(floor);
        }
        if (hurt) {
            lastDamagedAt = level.getGameTime();
            entityData.set(HIT_FLASH, 8);
            playSound(WFSounds.ENTITY_THE_UNMADE_HURT.get(), 2.0f, 1.0f);
        }
        if (phase() == PHASE_SHELL && getHealth() <= threshold(PHASE_SHELL) + 0.01f) {
            enterPhase(level, PHASE_UNBINDING);
        } else if (phase() == PHASE_UNBINDING && getHealth() <= threshold(PHASE_UNBINDING) + 0.01f) {
            enterPhase(level, PHASE_UNMADE);
        }
        return hurt;
    }

    // ------------------------------------------------------------ finale (§13.7)
    private void beginFinale(ServerLevel level) {
        setHealth(1.0f);
        entityData.set(PHASE, PHASE_FINALE);
        entityData.set(ATTACK, 0);
        entityData.set(FREEZE, FREEZE_TICKS);
        finaleTicks = 0;
        level.playSound(null, blockPosition(), WFSounds.ENTITY_THE_UNMADE_DEATH.get(), SoundSource.HOSTILE, 4.0f, 1.0f);
        for (Shardling s : level.getEntitiesOfClass(Shardling.class, getBoundingBox().inflate(128))) {
            s.discard();
        }
        for (UnmadeShard s : level.getEntitiesOfClass(UnmadeShard.class, getBoundingBox().inflate(128))) {
            s.discard();
        }
    }

    private void tickFinale(ServerLevel level, List<ServerPlayer> players) {
        finaleTicks++;
        if (finaleTicks == FREEZE_TICKS) {
            level.playSound(null, blockPosition(), WFSounds.ENTITY_THE_UNMADE_REFORM.get(), SoundSource.HOSTILE, 4.0f, 1.0f);
            FractureArena.reform(level);
        }
        if (finaleTicks >= FREEZE_TICKS + REFORM_TICKS) {
            reward(level, players);
            FractureData.get(level.getServer()).markDefeated();
            FractureArena.setExitRift(level, true);
            bossEvent.removeAllPlayers();
            discard();
        }
    }

    private void reward(ServerLevel level, List<ServerPlayer> players) {
        FractureData data = FractureData.get(level.getServer());
        for (ServerPlayer p : players) {
            if (p.distanceToSqr(this) > 64 * 64) {
                continue;
            }
            Progress.award(p, "unmade");
            if (data.recordKill(p.getUUID())) {
                dev.worldsfall.util.WFUtil.give(p, new ItemStack(WFItems.HEART_OF_STILLNESS.get()));
                dev.worldsfall.util.WFUtil.give(p, new ItemStack(WFItems.MUSIC_DISC_UNMADE.get()));
            }
            dev.worldsfall.util.WFUtil.give(p, new ItemStack(WFItems.STILLNESS_FRAGMENT.get(), 2 + random.nextInt(3)));
        }
    }

    // ------------------------------------------------------------ boss bar
    private void updateBossBar(ServerLevel level) {
        bossEvent.setProgress(getHealth() / getMaxHealth());
        bossEvent.setVisible(isActive());
        for (ServerPlayer p : level.getPlayers(p -> true)) {
            boolean near = p.distanceToSqr(this) < 128 * 128;
            if (near && !bossEvent.getPlayers().contains(p)) {
                bossEvent.addPlayer(p);
            } else if (!near && bossEvent.getPlayers().contains(p)) {
                bossEvent.removePlayer(p);
            }
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected boolean canRide(net.minecraft.world.entity.Entity vehicle) {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return WFSounds.ENTITY_THE_UNMADE_HURT.get();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("phase", phase());
        out.putInt("rings", rings());
        out.putInt("exposures", exposures);
        out.putBoolean("active", isActive());
        out.putInt("phase3", phase3Ticks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(PHASE, in.getIntOr("phase", PHASE_SHELL));
        entityData.set(RINGS, in.getIntOr("rings", 7));
        exposures = in.getIntOr("exposures", 0);
        entityData.set(ACTIVE, in.getBooleanOr("active", false));
        phase3Ticks = in.getIntOr("phase3", 0);
        if (hasCustomName()) {
            bossEvent.setName(getDisplayName());
        }
    }
}
