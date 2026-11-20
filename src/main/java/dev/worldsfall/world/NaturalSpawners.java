package dev.worldsfall.world;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.entity.VoidSkate;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.registry.WFTags;
import dev.worldsfall.world.tide.Tides;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Void Skate spawning in the Drift (§8.2): groups of 2–3 near islands at Y 30–100, max 6 per player within
 * 128 blocks, inside the global budget of ≤ 20 new mobs per player (§16.5). Skates appear in the Gap too.
 */
public final class NaturalSpawners {
    public static final int NEW_MOB_BUDGET = 20;

    private NaturalSpawners() {}

    public static void tickEnd(ServerLevel level) {
        if (level.getGameTime() % 200 != 0 || !level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS)) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.position().horizontalDistance() < 220) {
                continue;
            }
            AABB area = p.getBoundingBox().inflate(128, 96, 128);
            int skates = level.getEntitiesOfClass(VoidSkate.class, area).size();
            int newMobs = level.getEntitiesOfClass(LivingEntity.class, area, e -> e.typeHolder().is(WFTags.Entities.NEW_END_MOBS)).size();
            int cap = WFConfig.get(WFConfig.SERVER.skateMaxPerPlayer, 6);
            if (skates >= cap || newMobs >= NEW_MOB_BUDGET || level.getRandom().nextInt(3) != 0) {
                continue;
            }
            trySpawnGroup(level, p, Math.min(cap - skates, 2 + level.getRandom().nextInt(2)));
        }
    }

    static void trySpawnGroup(ServerLevel level, ServerPlayer p, int count) {
        for (int attempt = 0; attempt < 6; attempt++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double r = 40 + level.getRandom().nextDouble() * 70;
            int x = (int) (p.getX() + Math.cos(a) * r);
            int z = (int) (p.getZ() + Math.sin(a) * r);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int y = ground > level.getMinY() + 1 ? ground + 6 + level.getRandom().nextInt(10) : 40 + level.getRandom().nextInt(50);
            y = Math.max(y, (int) Tides.height(level) + 10);
            if (y < 30 || y > 100) {
                continue;
            }
            BlockPos at = new BlockPos(x, y, z);
            if (!level.getBlockState(at).isAir()) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                VoidSkate skate = WFEntities.VOID_SKATE.get().create(level, EntitySpawnReason.NATURAL);
                if (skate != null) {
                    skate.snapTo(x + i * 6, y + level.getRandom().nextInt(4), z + i * 3, level.getRandom().nextFloat() * 360, 0);
                    skate.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.NATURAL, null);
                    level.addFreshEntity(skate);
                }
            }
            return;
        }
    }
}
