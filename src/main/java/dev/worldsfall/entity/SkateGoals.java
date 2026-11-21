package dev.worldsfall.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Wild Void Skate behaviours (§8.2): circle islands in slow loops, perch to rest, flee when hit. */
final class SkateGoals {
    private SkateGoals() {}

    /**
     * A migrating flock (proposal "Skate Migration") flies straight to its far point and leaves. It pauses for a player
     * holding Chorus Pods nearby, the only time a Dusk Skate can be won over.
     */
    static final class Migrate extends Goal {
        private final VoidSkate skate;

        Migrate(VoidSkate skate) {
            this.skate = skate;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private boolean tempted() {
            var p = skate.level().getNearestPlayer(skate, 8);
            return p != null && (p.getMainHandItem().is(dev.worldsfall.registry.WFTags.Items.SKATE_FOOD)
                || p.getOffhandItem().is(dev.worldsfall.registry.WFTags.Items.SKATE_FOOD));
        }

        @Override
        public boolean canUse() {
            return skate.isMigrating() && !skate.isVehicle() && !tempted();
        }

        @Override
        public void start() {
            skate.setPerched(false);
        }

        @Override
        public void tick() {
            Vec3 target = skate.migrationTarget();
            if (target == null) {
                return;
            }
            Vec3 to = target.subtract(skate.position());
            if (to.horizontalDistanceSqr() < 24 * 24) {
                skate.discard();
                return;
            }
            Vec3 step = skate.position().add(to.normalize().scale(16));
            double bob = Math.sin((skate.tickCount + skate.getId() * 7) * 0.05) * 2;
            skate.getMoveControl().setWantedPosition(step.x, target.y + bob, step.z, 1.35);
        }
    }

    /** Slow orbits around a chosen center at a comfortable altitude. */
    static final class CircleIsland extends Goal {
        private final VoidSkate skate;
        private Vec3 center;
        private double radius;
        private double angle;
        private double altitude;
        private int remaining;

        CircleIsland(VoidSkate skate) {
            this.skate = skate;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !skate.isVehicle() && !skate.isPerched() && !skate.isInSittingPose();
        }

        @Override
        public void start() {
            BlockPos ground = findIslandNear(skate.level(), skate.blockPosition(), 24);
            center = ground != null ? Vec3.atCenterOf(ground) : skate.position();
            radius = 12 + skate.getRandom().nextDouble() * 18;
            altitude = Math.max(30, Math.min(100, (ground != null ? ground.getY() : skate.getY()) + 6 + skate.getRandom().nextInt(14)));
            angle = Math.atan2(skate.getZ() - center.z, skate.getX() - center.x);
            remaining = 400 + skate.getRandom().nextInt(600);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() && remaining > 0;
        }

        @Override
        public void tick() {
            remaining--;
            angle += 0.012;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            double y = altitude + Math.sin(angle * 2.0) * 2.0;
            skate.getMoveControl().setWantedPosition(x, y, z, 1.0);
        }
    }

    /** Lands on an island edge or Skate Roost to rest for a while. */
    static final class Perch extends Goal {
        private final VoidSkate skate;
        private BlockPos target;
        private int restTicks;

        Perch(VoidSkate skate) {
            this.skate = skate;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (skate.isVehicle() || skate.getRandom().nextInt(900) != 0) {
                return false;
            }
            // Drift Buoys mark routes across the Gap: Skates prefer to perch on one when it is near
            if (skate.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                BlockPos buoy = dev.worldsfall.gap.DriftBuoyBlock.nearest(sl, skate.blockPosition(), 40);
                if (buoy != null) {
                    target = buoy;
                    return true;
                }
            }
            target = findIslandNear(skate.level(), skate.blockPosition(), 20);
            return target != null;
        }

        @Override
        public void start() {
            restTicks = 300 + skate.getRandom().nextInt(600);
        }

        @Override
        public boolean canContinueToUse() {
            return !skate.isVehicle() && restTicks > 0 && target != null;
        }

        @Override
        public void tick() {
            if (!skate.isPerched()) {
                skate.getMoveControl().setWantedPosition(target.getX() + 0.5, target.getY() + 1.2, target.getZ() + 0.5, 0.8);
                if (skate.position().distanceToSqr(Vec3.atBottomCenterOf(target.above())) < 4.0) {
                    skate.setPerched(true);
                }
            } else {
                restTicks--;
            }
        }

        @Override
        public void stop() {
            skate.setPerched(false);
            target = null;
        }
    }

    /** Hovers patiently near the edge once tamed and not ridden (§8.2 step 3). */
    static final class WaitWhenTrusted extends Goal {
        private final VoidSkate skate;

        WaitWhenTrusted(VoidSkate skate) {
            this.skate = skate;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return skate.isTame() && !skate.isVehicle() && !skate.isPerched();
        }

        @Override
        public void tick() {
            skate.getMoveControl().setWantedPosition(skate.getX(), skate.getY() + Math.sin(skate.tickCount * 0.05) * 0.05, skate.getZ(), 0.3);
            skate.setDeltaMovement(skate.getDeltaMovement().multiply(0.8, 0.8, 0.8));
        }
    }

    /** Neutral and shy: flees fast and high when hurt; never attacks. */
    static final class FleeWhenHurt extends Goal {
        private final VoidSkate skate;
        private Vec3 away;
        private int ticks;

        FleeWhenHurt(VoidSkate skate) {
            this.skate = skate;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !skate.isVehicle() && skate.getLastHurtByMob() != null && skate.tickCount - skate.getLastHurtByMobTimestamp() < 60;
        }

        @Override
        public void start() {
            skate.setPerched(false);
            Vec3 from = skate.getLastHurtByMob() != null ? skate.getLastHurtByMob().position() : skate.position();
            Vec3 dir = skate.position().subtract(from).multiply(1, 0, 1).normalize();
            away = skate.position().add(dir.scale(28)).add(0, 10, 0);
            ticks = 80;
        }

        @Override
        public boolean canContinueToUse() {
            return ticks-- > 0;
        }

        @Override
        public void tick() {
            skate.getMoveControl().setWantedPosition(away.x, away.y, away.z, 1.8);
        }
    }

    /** Finds a solid island-top block (with air above) within a horizontal radius, preferring edges. */
    static BlockPos findIslandNear(Level level, BlockPos from, int radius) {
        BlockPos best = null;
        for (int i = 0; i < 24; i++) {
            int x = from.getX() + level.getRandom().nextInt(radius * 2 + 1) - radius;
            int z = from.getZ() + level.getRandom().nextInt(radius * 2 + 1) - radius;
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            if (y > level.getMinY() + 1) {
                BlockPos p = new BlockPos(x, y, z);
                boolean edge = level.getBlockState(p.east(2)).isAir() || level.getBlockState(p.west(2)).isAir()
                    || level.getBlockState(p.north(2)).isAir() || level.getBlockState(p.south(2)).isAir();
                if (best == null || edge) {
                    best = p;
                    if (edge) {
                        return p;
                    }
                }
            }
        }
        return best;
    }
}
