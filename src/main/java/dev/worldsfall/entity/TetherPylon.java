package dev.worldsfall.entity;

import dev.worldsfall.registry.WFSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * The magenta crystal on top of a Tether Pylon (§13.3). 20 HP from any damage; linked to the Core by a
 * visible beam. Breaking all three pylons of a ring shatters that ring (Phase 1).
 */
public class TetherPylon extends Entity {
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(TetherPylon.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> RING = SynchedEntityData.defineId(TetherPylon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLASH = SynchedEntityData.defineId(TetherPylon.class, EntityDataSerializers.INT);
    public static final float MAX_HEALTH = 20f;
    private int fragment;

    public TetherPylon(EntityType<? extends TetherPylon> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(HEALTH, MAX_HEALTH);
        builder.define(RING, 0);
        builder.define(FLASH, 0);
    }

    public int ring() { return entityData.get(RING); }
    public void setRing(int r) { entityData.set(RING, r); }
    public void setFragment(int f) { fragment = f; }
    public int fragment() { return fragment; }
    public float health() { return entityData.get(HEALTH); }
    public int flash() { return entityData.get(FLASH); }

    @Override
    public void tick() {
        super.tick();
        if (entityData.get(FLASH) > 0) {
            entityData.set(FLASH, entityData.get(FLASH) - 1);
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (isInvulnerableToBase(source)) {
            return false;
        }
        float hp = health() - damage;
        entityData.set(FLASH, 6);
        level.playSound(null, blockPosition(), WFSounds.ENTITY_THE_UNMADE_HURT.get(), SoundSource.HOSTILE, 0.8f, 1.6f);
        if (hp <= 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dev.worldsfall.registry.WFBlocks.UNMADE_SHARD.get().defaultBlockState()),
                getX(), getY() + 0.5, getZ(), 30, 0.4, 0.4, 0.4, 0.2);
            level.playSound(null, blockPosition(), WFSounds.ENTITY_THE_UNMADE_PYLON_BREAK.get(), SoundSource.HOSTILE, 1.5f, 1.0f);
            for (TheUnmade boss : level.getEntitiesOfClass(TheUnmade.class, new AABB(blockPosition()).inflate(96))) {
                boss.onPylonBroken(level, ring());
            }
            discard();
        } else {
            entityData.set(HEALTH, hp);
        }
        return true;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(HEALTH, in.getFloatOr("health", MAX_HEALTH));
        entityData.set(RING, in.getIntOr("ring", 0));
        fragment = in.getIntOr("fragment", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putFloat("health", health());
        out.putInt("ring", ring());
        out.putInt("fragment", fragment);
    }
}
