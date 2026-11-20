package dev.worldsfall.fluid;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.registry.WFEffects;
import dev.worldsfall.registry.WFFluids;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.registry.WFSounds;
import dev.worldsfall.registry.WFTags;
import dev.worldsfall.world.Progress;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Stillwater's effects on entities (§7.2): XP drain while submerged, orbs absorbed, items float on the surface
 * indefinitely (capped per chunk), Endermen-kin hurt and teleport.
 */
public final class StillwaterEffects {
    private StillwaterEffects() {}

    public static boolean isIn(Entity e) {
        return e.isInFluidType(WFFluids.STILLWATER_TYPE.get());
    }

    public static boolean isEyeIn(Entity e) {
        return e.isEyeInFluidType(WFFluids.STILLWATER_TYPE.get());
    }

    public static void tick(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !isIn(entity)) {
            return;
        }
        if (entity instanceof ServerPlayer player) {
            if (entity.tickCount % 20 == 0 && isEyeIn(player) && !player.hasEffect(WFEffects.TIDESIGHT)) {
                int drain = WFConfig.get(WFConfig.SERVER.stillwaterXpDrain, 1);
                if (drain > 0 && player.totalExperience > 0) {
                    player.giveExperiencePoints(-drain);
                    level.playSound(null, player.blockPosition(), WFSounds.BLOCK_STILLWATER_XP_DRAIN.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
                }
            }
            if (entity.tickCount % 40 == 0) {
                Progress.award(player, "a_hole_full_of_stars");
            }
        } else if (entity instanceof ExperienceOrb orb) {
            orb.discard();
        } else if (entity instanceof ItemEntity item) {
            floatItem(level, item);
        } else if (entity instanceof LivingEntity living && living.typeHolder().is(WFTags.Entities.STILLWATER_SENSITIVE) && entity.tickCount % 10 == 0) {
            living.hurtServer(level, level.damageSources().drown(), 1.0f);
        }
    }

    /** Items float on the surface and do not despawn while floating (≤ 64 stacks per chunk, §7.1). */
    private static void floatItem(ServerLevel level, ItemEntity item) {
        BlockPos pos = item.blockPosition();
        FluidState here = level.getFluidState(pos);
        double surface = pos.getY() + here.getHeight(level, pos);
        boolean aboveFluid = level.getFluidState(pos.above()).isEmpty();
        Vec3 v = item.getDeltaMovement();
        if (aboveFluid) {
            double target = surface - 0.12 + Math.sin((item.tickCount + item.getId()) * (Math.PI * 2 / 120.0)) * 0.03;
            item.setDeltaMovement(v.x * 0.6, (target - item.getY()) * 0.2, v.z * 0.6);
        } else {
            item.setDeltaMovement(v.x * 0.6, 0.06, v.z * 0.6);
        }
        int cap = WFConfig.get(WFConfig.SERVER.maxFloatingStacksPerChunk, 64);
        if (item.tickCount % 100 == 0) {
            int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
            AABB chunk = new AABB(cx << 4, level.getMinY(), cz << 4, (cx << 4) + 16, level.getMaxY(), (cz << 4) + 16);
            long floating = level.getEntitiesOfClass(ItemEntity.class, chunk, StillwaterEffects::isIn).size();
            if (floating <= cap) {
                item.setUnlimitedLifetime();
            }
        }
        if (item.tickCount % 60 == 0) {
            level.sendParticles(WFParticles.STILL_RING.get(), item.getX(), surface + 0.01, item.getZ(), 1, 0, 0, 0, 0);
        }
    }
}
