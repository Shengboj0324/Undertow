package dev.worldsfall.entity;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Base for the mod's simple, dodgeable, straight-line projectiles (snare, shards, splinters). No gravity. */
public abstract class StraightProjectile extends Projectile {
    protected int maxLife = 80;
    protected int life;

    protected StraightProjectile(EntityType<? extends StraightProjectile> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    public void launch(Vec3 from, Vec3 velocity) {
        setPos(from.x, from.y, from.z);
        setDeltaMovement(velocity);
        double h = velocity.horizontalDistance();
        setYRot((float) (Math.atan2(velocity.x, velocity.z) * 57.2957763671875));
        setXRot((float) (Math.atan2(velocity.y, h) * 57.2957763671875));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult ehr) {
                onHitTarget((ServerLevel) level(), ehr.getEntity());
                discard();
                return;
            }
            if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
                onHitWall((ServerLevel) level(), bhr);
                discard();
                return;
            }
            if (++life > maxLife) {
                discard();
                return;
            }
        }
        Vec3 v = getDeltaMovement();
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        trail();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity instanceof LivingEntity && entity != getOwner() && !(entity instanceof StraightProjectile);
    }

    protected abstract void onHitTarget(ServerLevel level, Entity target);

    protected void onHitWall(ServerLevel level, BlockHitResult hit) {
    }

    protected void trail() {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        life = in.getIntOr("life", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("life", life);
    }
}
