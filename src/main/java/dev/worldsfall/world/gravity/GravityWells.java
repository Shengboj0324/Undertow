package dev.worldsfall.world.gravity;

import dev.worldsfall.registry.WFAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Gravity Well spheres around large crystal clusters (§5.2), stored per chunk at worldgen as a data
 * attachment {@code worldsfall:gravity_wells} (center + radius).
 */
public final class GravityWells {
    private GravityWells() {}

    public record Well(BlockPos center, int radius) {
        public static final com.mojang.serialization.Codec<Well> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("center").forGetter(Well::center),
            com.mojang.serialization.Codec.INT.fieldOf("radius").forGetter(Well::radius)
        ).apply(i, Well::new));
    }

    public static void add(ChunkAccess chunk, BlockPos center, int radius) {
        List<Well> list = new ArrayList<>(chunk.getData(WFAttachments.GRAVITY_WELLS));
        list.add(new Well(center.immutable(), radius));
        chunk.setData(WFAttachments.GRAVITY_WELLS, List.copyOf(list));
    }

    public static boolean isInWell(Level level, Vec3 p) {
        int cx = ((int) Math.floor(p.x)) >> 4;
        int cz = ((int) Math.floor(p.z)) >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.hasChunk(cx + dx, cz + dz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                for (Well w : chunk.getData(WFAttachments.GRAVITY_WELLS)) {
                    double r = w.radius;
                    if (p.distanceToSqr(Vec3.atCenterOf(w.center)) <= r * r) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static List<Well> near(Level level, ChunkPos center, int range) {
        List<Well> out = new ArrayList<>();
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (level.hasChunk(center.x() + dx, center.z() + dz)) {
                    out.addAll(level.getChunk(center.x() + dx, center.z() + dz).getData(WFAttachments.GRAVITY_WELLS));
                }
            }
        }
        return out;
    }
}
