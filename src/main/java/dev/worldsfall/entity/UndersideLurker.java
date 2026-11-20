package dev.worldsfall.entity;

import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Underside Lurker (§8.4): hangs motionless and hidden from a thread. When a player comes within 6 blocks
 * (3 if sneaking) it opens its eyes, drops and attacks: Snare (0.75 s wind-up) and Bite. After attacking it
 * climbs back and re-hides after 8 s. It cannot hide within 3 blocks of light ≥ 12.
 */
public class UndersideLurker extends Monster {
    public enum State { HIDDEN, ALERT, WINDUP, RETREAT }

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(UndersideLurker.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> ANCHOR = SynchedEntityData.defineId(UndersideLurker.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> LIT = SynchedEntityData.defineId(UndersideLurker.class, EntityDataSerializers.BOOLEAN);
    public static final int SNARE_WINDUP = 15;
    public static final int REHIDE_TICKS = 160;
    public static final double DETECT = 6.0;
    public static final double DETECT_SNEAK = 3.0;

    private int stateTicks;
    private int snareCooldown;
    private int biteCooldown;

    public UndersideLurker(EntityType<? extends UndersideLurker> type, Level level) {
        super(type, level);
        setNoGravity(true);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 28.0).add(Attributes.ARMOR, 4.0)
            .add(Attributes.ATTACK_DAMAGE, 6.0).add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, State.HIDDEN.ordinal());
        builder.define(ANCHOR, BlockPos.ZERO);
        builder.define(LIT, false);
    }

    public State state() {
        return State.values()[entityData.get(STATE)];
    }

    private void setState(State s) {
        entityData.set(STATE, s.ordinal());
        stateTicks = 0;
    }

    public BlockPos anchor() {
        return entityData.get(ANCHOR);
    }

    /** Hidden and not forced visible by light. */
    public boolean isHidden() {
        return state() == State.HIDDEN && !entityData.get(LIT);
    }

    public boolean isLit() {
        return entityData.get(LIT);
    }

    public int stateTicks() {
        return stateTicks;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        attachToCeiling(level);
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    public void attachToCeiling(net.minecraft.world.level.LevelReader level) {
        BlockPos p = blockPosition();
        for (int i = 0; i < 20; i++) {
            BlockPos up = p.above(i + 1);
            if (level.getBlockState(up).isFaceSturdy(level, up, Direction.DOWN)) {
                entityData.set(ANCHOR, up);
                setPos(getX(), up.getY() - 2.4, getZ());
                return;
            }
        }
        entityData.set(ANCHOR, p.above(3));
    }

    @Override
    public void tick() {
        super.tick();
        stateTicks++;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (anchor().equals(BlockPos.ZERO)) {
            attachToCeiling(level);
        }
        entityData.set(LIT, isNearBrightLight(level));
        snareCooldown = Math.max(0, snareCooldown - 1);
        biteCooldown = Math.max(0, biteCooldown - 1);
        LivingEntity target = getTarget();
        switch (state()) {
            case HIDDEN -> {
                hangAt(2.4);
                Player p = level.getNearestPlayer(this, DETECT);
                if (p != null && !p.isCreative() && !p.isSpectator() && hasLineOfSight(p)
                    && distanceTo(p) <= (p.isShiftKeyDown() ? DETECT_SNEAK : DETECT)) {
                    setTarget(p);
                    playSound(WFSounds.ENTITY_UNDERSIDE_LURKER_EYES_OPEN.get(), 1.4f, 1.0f);
                    setState(State.ALERT);
                } else if (random.nextInt(160) == 0) {
                    playSound(WFSounds.ENTITY_UNDERSIDE_LURKER_CLICK.get(), 0.35f, 0.9f + random.nextFloat() * 0.2f);
                }
            }
            case ALERT -> {
                if (target == null || !target.isAlive() || distanceTo(target) > 16) {
                    setState(State.RETREAT);
                    break;
                }
                double drop = Math.max(2.4, Math.min(12, anchor().getY() - target.getY() + 0.5));
                hangAt(drop);
                getLookControl().setLookAt(target);
                if (distanceTo(target) < 2.2 && biteCooldown == 0) {
                    doHurtTarget(level, target);
                    playSound(WFSounds.ENTITY_UNDERSIDE_LURKER_BITE.get(), 1.0f, 1.0f);
                    biteCooldown = 20;
                } else if (snareCooldown == 0 && distanceTo(target) <= 12 && hasLineOfSight(target)) {
                    setState(State.WINDUP);
                } else if (stateTicks > 200) {
                    setState(State.RETREAT);
                }
            }
            case WINDUP -> {
                if (target == null) {
                    setState(State.RETREAT);
                    break;
                }
                getLookControl().setLookAt(target);
                if (stateTicks >= SNARE_WINDUP) {
                    fireSnare(level, target);
                    snareCooldown = 70;
                    setState(State.ALERT);
                }
            }
            case RETREAT -> {
                hangAt(2.4);
                if (stateTicks == 1) {
                    playSound(WFSounds.ENTITY_UNDERSIDE_LURKER_RETREAT.get(), 0.8f, 1.0f);
                    setTarget(null);
                }
                if (stateTicks >= REHIDE_TICKS) {
                    setState(State.HIDDEN);
                }
            }
        }
    }

    private void fireSnare(ServerLevel level, LivingEntity target) {
        LurkerSnare snare = new LurkerSnare(WFEntities.LURKER_SNARE.get(), level);
        snare.setOwner(this);
        Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        snare.launch(from, to.subtract(from).normalize().scale(1.1));
        level.addFreshEntity(snare);
        playSound(WFSounds.ENTITY_UNDERSIDE_LURKER_SNARE.get(), 1.2f, 1.0f);
    }

    /** Moves along the thread toward {@code drop} blocks below the anchor. */
    private void hangAt(double drop) {
        BlockPos a = anchor();
        double wantY = a.getY() - drop;
        double dy = wantY - getY();
        double speed = state() == State.RETREAT ? 0.18 : 0.35;
        double vy = Math.max(-speed, Math.min(speed, dy * 0.3));
        double dx = (a.getX() + 0.5 - getX()) * 0.1;
        double dz = (a.getZ() + 0.5 - getZ()) * 0.1;
        setDeltaMovement(dx, vy, dz);
        move(MoverType.SELF, getDeltaMovement());
    }

    private boolean isNearBrightLight(ServerLevel level) {
        BlockPos p = blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-3, -3, -3), p.offset(3, 3, 3))) {
            if (level.getBrightness(LightLayer.BLOCK, q) >= 12 && level.getBlockState(q).getLightEmission(level, q) >= 12) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && state() == State.HIDDEN && source.getEntity() instanceof LivingEntity attacker) {
            setTarget(attacker);
            playSound(WFSounds.ENTITY_UNDERSIDE_LURKER_EYES_OPEN.get(), 1.4f, 1.0f);
            setState(State.ALERT);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            dev.worldsfall.world.Progress.award(sp, "something_clicks");
        }
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
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return WFSounds.ENTITY_UNDERSIDE_LURKER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return WFSounds.ENTITY_UNDERSIDE_LURKER_DEATH.get();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.store("anchor", BlockPos.CODEC, anchor());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.read("anchor", BlockPos.CODEC).ifPresent(p -> entityData.set(ANCHOR, p));
    }

    /** Spawn rule (§8.4): Underside, light ≤ 7, ceiling above. */
    public static boolean checkSpawn(EntityType<UndersideLurker> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
                                     RandomSource random) {
        if (level.getBrightness(LightLayer.BLOCK, pos) > 7 || !level.getBlockState(pos).isAir()) {
            return false;
        }
        for (int i = 1; i <= 12; i++) {
            if (level.getBlockState(pos.above(i)).isFaceSturdy(level, pos.above(i), Direction.DOWN)) {
                return true;
            }
        }
        return false;
    }
}
