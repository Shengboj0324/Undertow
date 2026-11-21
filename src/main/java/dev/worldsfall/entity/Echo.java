package dev.worldsfall.entity;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.registry.WFAttachments;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Echo (§8.5.3): the mimic. Sits disguised as a block (the last block the nearest player placed in the End,
 * fallback Purpur Rubble). Within 3 blocks — or when touched — it unfolds and attacks for 8 s (5 HP +
 * Scramble: swaps 2 hotbar slots, wind-up 0.5 s), then teleports ≤ 24 blocks and disguises again.
 */
public class Echo extends EnderCaste {
    private static final EntityDataAccessor<BlockState> DISGUISE = SynchedEntityData.defineId(Echo.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Boolean> DISGUISED = SynchedEntityData.defineId(Echo.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SHIMMER = SynchedEntityData.defineId(Echo.class, EntityDataSerializers.INT);
    public static final int ACTIVE_TICKS = 160;
    public static final int SCRAMBLE_WINDUP = 10;
    private static final EntityDimensions BLOCK_DIMS = EntityDimensions.fixed(0.98f, 0.98f);

    private int activeTicks;
    private int attackCooldown;
    /** A memory figure of the Eclipse of Stillness: harmless, glowing, walking openly; it fades when approached. */
    private boolean memory;

    public Echo(EntityType<? extends Echo> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24.0).add(Attributes.ATTACK_DAMAGE, 5.0)
            .add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DISGUISE, WFBlocks.PURPUR_RUBBLE.get().defaultBlockState());
        builder.define(DISGUISED, true);
        builder.define(SHIMMER, 0);
    }

    public boolean isDisguised() {
        return entityData.get(DISGUISED);
    }

    public BlockState disguise() {
        return entityData.get(DISGUISE);
    }

    public int shimmer() {
        return entityData.get(SHIMMER);
    }

    public void setDisguise(BlockState state) {
        entityData.set(DISGUISE, state);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return isDisguised() ? BLOCK_DIMS : super.getDefaultDimensions(pose);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DISGUISED.equals(key)) {
            refreshDimensions();
        }
    }

    private void unfold(@Nullable Player target) {
        entityData.set(DISGUISED, false);
        activeTicks = ACTIVE_TICKS;
        playSound(WFSounds.ENTITY_ECHO_UNFOLD.get(), 1.2f, 1.0f);
        if (target != null) {
            setTarget(target);
        }
    }

    /** Memories belong to the Eclipse, not to the difficulty: they stay even in Peaceful, and fade when it ends. */
    @Override
    public void checkDespawn() {
        if (!memory) {
            super.checkDespawn();
        }
    }

    public boolean isMemory() {
        return memory;
    }

    /** Turns this Echo into a harmless memory figure for an Eclipse of Stillness. */
    public void makeMemory() {
        memory = true;
        entityData.set(DISGUISED, false);
        setGlowingTag(true);
        setPersistenceRequired();
    }

    private void redisguise(ServerLevel level) {
        if (dev.worldsfall.life.WyrmRoostBlock.guarded(level, blockPosition())) {
            activeTicks = ACTIVE_TICKS;
            teleportRandomly(level, 32);
            return;
        }
        teleportRandomly(level, 24);
        Player nearest = level.getNearestPlayer(this, 48);
        BlockState mimic = WFBlocks.PURPUR_RUBBLE.get().defaultBlockState();
        if (nearest instanceof ServerPlayer sp) {
            mimic = sp.getData(WFAttachments.LAST_PLACED).orElse(mimic);
        }
        setDisguise(mimic);
        setTarget(null);
        entityData.set(DISGUISED, true);
        Vec3 snapped = Vec3.atBottomCenterOf(blockPosition());
        setPos(snapped.x, snapped.y, snapped.z);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        attackCooldown = Math.max(0, attackCooldown - 1);
        if (memory) {
            dev.worldsfall.life.Eclipse.tickMemory(level, this);
            return;
        }
        if (isDisguised()) {
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            Player p = level.getNearestPlayer(this, 3.0);
            if (p != null && !p.isCreative() && !p.isSpectator()) {
                unfold(p);
            } else if (random.nextInt(200) == 0) {
                playSound(WFSounds.ENTITY_ECHO_HUM.get(), 0.25f, 1.0f);
            }
            return;
        }
        if (--activeTicks <= 0) {
            redisguise(level);
            return;
        }
        var target = getTarget();
        if (target == null) {
            return;
        }
        getLookControl().setLookAt(target);
        getNavigation().moveTo(target, 1.2);
        int sh = entityData.get(SHIMMER);
        if (sh > 0) {
            entityData.set(SHIMMER, sh - 1);
            if (sh == 1 && distanceTo(target) < 2.6) {
                doHurtTarget(level, target);
                if (target instanceof ServerPlayer sp && WFConfig.get(WFConfig.SERVER.echoScramble, true)) {
                    scramble(sp);
                }
            }
        } else if (attackCooldown == 0 && distanceTo(target) < 2.6) {
            entityData.set(SHIMMER, SCRAMBLE_WINDUP);
            attackCooldown = 30;
        }
    }

    /** Swaps two random hotbar slots (never armor or offhand). */
    public static void scramble(ServerPlayer player) {
        Inventory inv = player.getInventory();
        int a = player.getRandom().nextInt(9);
        int b = (a + 1 + player.getRandom().nextInt(8)) % 9;
        ItemStack sa = inv.getItem(a);
        inv.setItem(a, inv.getItem(b));
        inv.setItem(b, sa);
        player.level().playSound(null, player.blockPosition(), WFSounds.ENTITY_ECHO_SCRAMBLE.get(), player.getSoundSource(), 1.0f, 1.0f);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isDisguised() && !memory) {
            if (!level().isClientSide()) {
                unfold(player);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (memory) {
            return false;
        }
        if (isDisguised() && source.getEntity() instanceof Player p) {
            unfold(p);
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean isPushable() {
        return !isDisguised();
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return WFSounds.ENTITY_ECHO_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return WFSounds.ENTITY_ECHO_DEATH.get();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.store("disguise", BlockState.CODEC, disguise());
        out.putBoolean("disguised", isDisguised());
        out.putBoolean("memory", memory);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.read("disguise", BlockState.CODEC).ifPresent(this::setDisguise);
        entityData.set(DISGUISED, in.getBooleanOr("disguised", true));
        memory = in.getBooleanOr("memory", false);
    }
}
