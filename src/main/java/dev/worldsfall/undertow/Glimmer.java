package dev.worldsfall.undertow;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Glimmer (proposal "Creatures"): tiny lights that school in the Undertow. A shoal drifts with the slow current, so it
 * shows which way the current runs, and scatters from a Void Bell. Tide Eels follow them.
 */
public class Glimmer extends AmbientCreature {
    public Glimmer(EntityType<? extends Glimmer> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1.0);
    }

    /** The Undertow's slow current at a position: a gentle, steady flow that bends over hundreds of blocks. */
    public static Vec3 current(double x, double y, double z) {
        double a = Math.sin(x * 0.004) * 1.7 + Math.cos(z * 0.0035) * 1.3;
        return new Vec3(Math.cos(a), Math.sin(y * 0.02) * 0.15, Math.sin(a)).scale(0.05);
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    public void aiStep() {
        super.aiStep();
        Vec3 v = getDeltaMovement().scale(0.9).add(current(getX(), getY(), getZ()).scale(0.2));
        // school: drift toward neighbours, scatter from a bell
        if (tickCount % 5 == getId() % 5) {
            Vec3 centre = Vec3.ZERO;
            int n = 0;
            for (Glimmer g : level().getEntitiesOfClass(Glimmer.class, getBoundingBox().inflate(4), g -> g != this)) {
                centre = centre.add(g.position());
                n++;
            }
            if (n > 0) {
                v = v.add(centre.scale(1.0 / n).subtract(position()).scale(0.01));
            }
            for (Entity bell : level().getEntitiesOfClass(VoidBell.class, getBoundingBox().inflate(7))) {
                v = v.add(position().subtract(bell.position()).normalize().scale(0.12));
            }
        }
        v = v.add(Math.sin(tickCount * 0.3 + getId()) * 0.004, Math.cos(tickCount * 0.21 + getId()) * 0.003, 0);
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        if (v.horizontalDistanceSqr() > 1.0e-5) {
            setYRot((float) Math.toDegrees(Math.atan2(-v.x, v.z)));
            yBodyRot = yHeadRot = getYRot();
            setXRot((float) Mth.clamp(-Math.toDegrees(Math.atan2(v.y, v.horizontalDistance())), -40, 40));
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return true;
    }
}
