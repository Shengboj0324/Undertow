package dev.worldsfall.undertow;

import dev.worldsfall.registry.WFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Drowned Ascendant (proposal "Creatures"): a silent, translucent echo of the old civilisation that repeats one action
 * forever: reading, writing, or pointing. Following where a pointing one points leads to the hidden Codex. Never hostile,
 * never harmed.
 */
public class DrownedAscendant extends Mob {
    public static final int READING = 0, WRITING = 1, POINTING = 2;
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(DrownedAscendant.class, EntityDataSerializers.INT);
    private @Nullable BlockPos pointsAt;

    public DrownedAscendant(EntityType<? extends DrownedAscendant> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ACTION, READING);
    }

    public int action() {
        return entityData.get(ACTION);
    }

    public void setAction(int action, @Nullable BlockPos target) {
        entityData.set(ACTION, action);
        pointsAt = target;
    }

    public @Nullable BlockPos pointsAt() {
        return pointsAt;
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (pointsAt != null) {
            Vec3 to = Vec3.atCenterOf(pointsAt).subtract(getEyePosition());
            setYRot((float) Math.toDegrees(Math.atan2(-to.x, to.z)));
            yBodyRot = yHeadRot = getYRot();
            setXRot((float) -Math.toDegrees(Math.atan2(to.y, to.horizontalDistance())));
        }
        if (!level().isClientSide() && tickCount % 300 == getId() % 300 && random.nextInt(3) == 0) {
            playSound(WFSounds.ENTITY_DROWNED_ASCENDANT_WHISPER.get(), 0.6f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, damage);
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
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("action", action());
        out.storeNullable("points_at", BlockPos.CODEC, pointsAt);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        setAction(in.getIntOr("action", READING), in.read("points_at", BlockPos.CODEC).orElse(null));
    }
}
