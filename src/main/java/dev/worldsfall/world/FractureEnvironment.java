package dev.worldsfall.world;

import dev.worldsfall.entity.TheUnmade;
import dev.worldsfall.registry.WFParticles;
import dev.worldsfall.world.fracture.FractureArena;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fracture ambience and arena physics for non-player mobs (players are moved client-side): Updraft Columns
 * lift gliders (§11.8, §13.3) and the Phase-3 inward pull (§13.7).
 */
public final class FractureEnvironment {
    private FractureEnvironment() {}

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 4 == 0) {
            for (Vec3 col : FractureArena.updrafts()) {
                level.sendParticles(WFParticles.ASH_INFLOW.get(), col.x, col.y - 20 + level.getRandom().nextDouble() * 40, col.z, 3, 0.8, 4, 0.8, 0.0);
            }
        }
        TheUnmade boss = level.getEntitiesOfClass(TheUnmade.class, new AABB(FractureArena.CENTER).inflate(64)).stream().findFirst().orElse(null);
        double pull = boss == null ? 0 : boss.pullStrength();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(FractureArena.CENTER).inflate(96, 64, 96),
            e -> !(e instanceof Player) && !(e instanceof TheUnmade))) {
            for (Vec3 col : FractureArena.updrafts()) {
                if (Math.hypot(e.getX() - col.x, e.getZ() - col.z) < 2.0 && e.getY() > col.y - 40 && e.getY() < col.y + 24) {
                    e.push(0, 0.12, 0);
                }
            }
            if (pull > 0) {
                Vec3 to = Vec3.atCenterOf(FractureArena.CENTER).subtract(e.position());
                double d = to.length();
                e.push(to.normalize().scale(pull * (1 + Math.max(0, 1 - d / 64))));
            }
        }
    }
}
