package dev.worldsfall.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds where a random-spread structure actually generates within a square, from the placement grid and the structure's own
 * generation check, without generating any chunks. Used for City and Star Charts.
 */
public final class StructureSearch {
    private StructureSearch() {}

    public static List<ChunkPos> startsIn(ServerLevel level, Identifier set, Identifier structure, BlockPos center, int half) {
        List<ChunkPos> out = new ArrayList<>();
        StructureSet ss = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(ResourceKey.create(Registries.STRUCTURE_SET, set));
        Structure st = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(ResourceKey.create(Registries.STRUCTURE, structure));
        if (ss == null || st == null || !(ss.placement() instanceof RandomSpreadStructurePlacement placement)) {
            return out;
        }
        int spacing = placement.spacing();
        int minRx = Math.floorDiv((center.getX() - half) >> 4, spacing), maxRx = Math.floorDiv((center.getX() + half) >> 4, spacing);
        int minRz = Math.floorDiv((center.getZ() - half) >> 4, spacing), maxRz = Math.floorDiv((center.getZ() + half) >> 4, spacing);
        for (int rx = minRx; rx <= maxRx; rx++) {
            for (int rz = minRz; rz <= maxRz; rz++) {
                ChunkPos c = placement.getPotentialStructureChunk(level.getSeed(), rx * spacing, rz * spacing);
                if (Math.abs(c.getMiddleBlockX() - center.getX()) > half || Math.abs(c.getMiddleBlockZ() - center.getZ()) > half) {
                    continue;
                }
                boolean present = switch (level.structureManager().checkStructurePresence(c, st, placement, false)) {
                    case START_PRESENT -> true;
                    case START_NOT_PRESENT -> false;
                    case CHUNK_LOAD_NEEDED -> {
                        StructureStart start = level.structureManager().getStartForStructure(st, level.getChunk(c.x(), c.z(), ChunkStatus.STRUCTURE_STARTS));
                        yield start != null && start.isValid();
                    }
                };
                if (present) {
                    out.add(c);
                }
            }
        }
        return out;
    }
}
