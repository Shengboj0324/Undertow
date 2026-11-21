package dev.worldsfall.world.gen.structure;

import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.zenith.OrrerySocketBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Orrery (proposal "A fifth layer above"): a ruined astronomical machine high in the Zenith. Three great rings of
 * purpur and Halo Glass hang jammed at odd angles around a dark lens on a skystone platform; three sockets wait for Spire
 * Keys. The ring geometry is fixed per ring so the alignment can be replayed later from the centre alone.
 */
public final class OrreryGenerator {
    public static final int[] RADII = {11, 15, 19};
    /** Jammed tilts: axis (x, y, z) and angle in degrees, per ring. */
    private static final double[][] TILT = {{1, 0, 0, 38}, {0, 0, 1, -55}, {1, 0, 1, 24}};
    public static final int PLATFORM_DROP = 6;

    private OrreryGenerator() {}

    public static Blueprint build(BlockPos center, int variant, RandomSource r) {
        Blueprint bp = new Blueprint();
        BlockPos floor = center.below(PLATFORM_DROP);
        BlockState sky = WFBlocks.SKYSTONE.get().defaultBlockState(), polished = WFBlocks.POLISHED_SKYSTONE.get().defaultBlockState(),
            tiles = WFBlocks.SKYSTONE_TILES.get().defaultBlockState(), halo = WFBlocks.HALO_GLASS.get().defaultBlockState();
        // the platform: a tiled disc with a polished rim, Halo Glass spokes and a rough skystone underside
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 7.3) {
                    continue;
                }
                BlockState top = d > 6.2 ? polished : (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz)) && d > 1.5 ? halo : tiles;
                bp.set(floor.offset(dx, 0, dz), top);
                int depth = (int) Math.round((7.3 - d) * 0.9) + r.nextInt(2);
                for (int dy = 1; dy <= depth; dy++) {
                    bp.set(floor.offset(dx, -dy, dz), sky);
                }
            }
        }
        // the lens on its column
        for (int dy = 1; dy < PLATFORM_DROP; dy++) {
            bp.set(floor.above(dy), dy == PLATFORM_DROP - 1 ? WFBlocks.CHISELED_SKYSTONE.get().defaultBlockState() : polished);
        }
        bp.set(center, WFBlocks.ORRERY_LENS.get().defaultBlockState());
        // three sockets, one per ring: Low, Rising, High
        for (int i = 0; i < 3; i++) {
            bp.set(socketPos(center, i), WFBlocks.ORRERY_SOCKET.get().defaultBlockState().setValue(OrrerySocketBlock.RING, i));
        }
        // the jammed rings
        for (int i = 0; i < 3; i++) {
            List<BlockPos> ring = ring(center, i, false);
            for (int k = 0; k < ring.size(); k++) {
                bp.set(ring.get(k), ringBlock(k));
            }
        }
        // the final Echo Tablet waits in a cache under the lens
        DriftwreckGenerator.cache(bp, floor.offset(0, 1, 2), "vault/orrery", false);
        // broken shards of the machine drifting nearby
        for (int i = 0; i < 12; i++) {
            BlockPos p = center.offset(r.nextInt(51) - 25, r.nextInt(21) - 14, r.nextInt(51) - 25);
            if (p.distSqr(center) > 22 * 22) {
                bp.setIfAbsent(p, r.nextInt(3) == 0 ? halo : WFBlocks.ORRERY_RING.get().defaultBlockState());
            }
        }
        return bp;
    }

    public static BlockPos socketPos(BlockPos center, int ring) {
        double a = ring * Math.PI * 2 / 3;
        return center.below(PLATFORM_DROP - 1).offset((int) Math.round(Math.cos(a) * 4), 0, (int) Math.round(Math.sin(a) * 4));
    }

    public static BlockState ringBlock(int index) {
        return index % 7 == 3 ? WFBlocks.HALO_GLASS.get().defaultBlockState() : WFBlocks.ORRERY_RING.get().defaultBlockState();
    }

    /** The block positions of ring {@code i}, jammed or aligned (level with the lens). */
    public static List<BlockPos> ring(BlockPos center, int i, boolean aligned) {
        int radius = RADII[i];
        Set<BlockPos> out = new LinkedHashSet<>();
        Vec3 axis = new Vec3(TILT[i][0], TILT[i][1], TILT[i][2]).normalize();
        double ang = Math.toRadians(TILT[i][3]);
        int steps = (int) Math.ceil(radius * Math.PI * 2 * 1.6);
        for (int s = 0; s < steps; s++) {
            double t = s * Math.PI * 2 / steps;
            Vec3 v = new Vec3(Math.cos(t) * radius, 0, Math.sin(t) * radius);
            if (!aligned) {
                v = rotate(v, axis, ang);
            }
            out.add(BlockPos.containing(center.getX() + 0.5 + v.x, center.getY() + 0.5 + v.y, center.getZ() + 0.5 + v.z));
        }
        return new ArrayList<>(out);
    }

    private static Vec3 rotate(Vec3 v, Vec3 k, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return v.scale(c).add(k.cross(v).scale(s)).add(k.scale(k.dot(v) * (1 - c)));
    }
}
