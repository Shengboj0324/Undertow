package dev.worldsfall.client.fx;

import dev.worldsfall.config.WFConfig;
import dev.worldsfall.registry.WFParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The Sundering's visuals (§5.4): the exit portal shudders (particles only), then cracks of magenta light race
 * outward across the main island along a fixed pattern, converging on the Descent.
 */
public final class SunderingFx {
    private static BlockPos descent;
    private static long start = -1;
    private static final int RACE_TICKS = 100;

    private SunderingFx() {}

    public static void start(BlockPos d, long serverTime) {
        descent = d;
        Minecraft mc = Minecraft.getInstance();
        start = mc.level != null ? mc.level.getGameTime() : 0;
    }

    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (start < 0 || level == null || descent == null) {
            return;
        }
        long age = level.getGameTime() - start;
        if (age > RACE_TICKS + 160) {
            start = -1;
            return;
        }
        boolean reduced = WFConfig.get(WFConfig.CLIENT.reducedFlashing, false);
        // the portal's bedrock shudders
        if (age < 60) {
            for (int i = 0; i < 6; i++) {
                level.addParticle(ParticleTypes.REVERSE_PORTAL, (level.getRandom().nextDouble() - 0.5) * 7, 62 + level.getRandom().nextDouble() * 4,
                    (level.getRandom().nextDouble() - 0.5) * 7, 0, 0.05, 0);
            }
        }
        // 8 cracks from the portal outward, one leading to the Descent
        float progress = Mth.clamp(age / (float) RACE_TICKS, 0, 1);
        double baseAngle = Math.atan2(descent.getZ(), descent.getX());
        for (int c = 0; c < 8; c++) {
            double ang = baseAngle + c * Math.PI / 4 + Math.sin(c * 3.1) * 0.15;
            double reach = (c == 0 ? Math.hypot(descent.getX(), descent.getZ()) : 55 + (c * 13) % 25) * progress;
            int steps = reduced ? 2 : 5;
            for (int s = 0; s < steps; s++) {
                double r = reach * (0.85 + 0.15 * level.getRandom().nextDouble());
                double wobble = Math.sin(r * 0.35 + c) * 2.2;
                double x = Math.cos(ang) * r - Math.sin(ang) * wobble;
                double z = Math.sin(ang) * r + Math.cos(ang) * wobble;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) x, (int) z);
                level.addParticle(WFParticles.SHARD_SPARK.get(), x, y + 0.1, z, 0, 0.02, 0);
                if (!reduced && s == 0) {
                    level.addParticle(WFParticles.SOCKET_SPARK.get(), x, y + 0.2, z, 0, 0.06, 0);
                }
            }
        }
        // a beam of pale light down the Descent once it has opened
        if (age > RACE_TICKS) {
            for (int i = 0; i < 3; i++) {
                level.addParticle(ParticleTypes.END_ROD, descent.getX() + 0.5 + level.getRandom().nextGaussian() * 0.6, descent.getY() - level.getRandom().nextDouble() * 30,
                    descent.getZ() + 0.5 + level.getRandom().nextGaussian() * 0.6, 0, -0.02, 0);
            }
        }
    }
}
