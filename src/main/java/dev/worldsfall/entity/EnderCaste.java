package dev.worldsfall.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Shared Enderman-like traits of the castes (§8.5): tall, hurt by water and Stillwater, teleport. */
public abstract class EnderCaste extends Monster {
    protected EnderCaste(EntityType<? extends EnderCaste> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isSensitiveToWater() {
        return true;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if ((isInWater() || dev.worldsfall.fluid.StillwaterEffects.isIn(this)) && tickCount % 10 == 0) {
            teleportRandomly(level, 16);
        }
    }

    public boolean teleportRandomly(ServerLevel level, int radius) {
        for (int i = 0; i < 16; i++) {
            double x = getX() + (random.nextDouble() - 0.5) * 2 * radius;
            double y = getY() + random.nextInt(radius) - radius / 2.0;
            double z = getZ() + (random.nextDouble() - 0.5) * 2 * radius;
            if (teleportNear(level, new Vec3(x, y, z))) {
                return true;
            }
        }
        return false;
    }

    public boolean teleportNear(ServerLevel level, Vec3 target) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(target).mutable();
        while (pos.getY() > level.getMinY() && !level.getBlockState(pos).isSolid()) {
            pos.move(0, -1, 0);
        }
        BlockState ground = level.getBlockState(pos);
        if (!ground.isSolid() || ground.getFluidState().isEmpty() == false) {
            return false;
        }
        Vec3 from = position();
        boolean ok = randomTeleport(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, true, st -> false);
        if (ok) {
            level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 24, 0.3, 1.0, 0.3, 0.2);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, getSoundSource(), 1.0f, 0.9f);
        }
        return ok;
    }
}
