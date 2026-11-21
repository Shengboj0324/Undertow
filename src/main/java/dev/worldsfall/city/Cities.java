package dev.worldsfall.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Reading vanilla End Cities without changing them (proposal "End Cities and End Ships"): which city a position is in,
 * which template each piece came from (an End Ship is the piece built from {@code end_city/ship}), and where the nearest
 * cities a player has not been shown yet lie.
 */
public final class Cities {
    public static final Set<String> FLOORS = Set.of("base_floor", "second_floor_1", "second_floor_2", "third_floor_1", "third_floor_2");

    private Cities() {}

    public static @Nullable Structure endCity(ServerLevel level) {
        return level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BuiltinStructures.END_CITY);
    }

    /** The End City whose bounds contain {@code pos}, or null. */
    public static @Nullable StructureStart cityAt(ServerLevel level, BlockPos pos) {
        Structure city = endCity(level);
        if (city == null) {
            return null;
        }
        StructureStart start = level.structureManager().getStructureAt(pos, city);
        return start.isValid() ? start : null;
    }

    /** The template name a vanilla End City piece was built from (for example {@code ship}); empty for other pieces. */
    public static String template(ServerLevel level, StructurePiece piece) {
        return piece.createTag(StructurePieceSerializationContext.fromLevel(level)).getStringOr("Template", "");
    }

    /** The End Ship piece of the city at {@code pos} whose bounds (grown by 2) contain it, or null. */
    public static @Nullable BoundingBox shipAt(ServerLevel level, BlockPos pos) {
        StructureStart start = cityAt(level, pos);
        if (start == null) {
            return null;
        }
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox().inflatedBy(2);
            if (box.isInside(pos) && "ship".equals(template(level, piece))) {
                return piece.getBoundingBox();
            }
        }
        return null;
    }

    /**
     * The nearest End Cities to {@code from} whose start chunks are not in {@code skip}, up to {@code count}, searching
     * the vanilla End City placement grid outward (about 3,800 blocks at most).
     */
    public static List<ChunkPos> nearestCities(ServerLevel level, BlockPos from, int count, Set<Long> skip) {
        List<ChunkPos> out = new ArrayList<>();
        Structure city = endCity(level);
        var set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(BuiltinStructureSets.END_CITIES);
        if (city == null || set == null || !(set.placement() instanceof RandomSpreadStructurePlacement placement)) {
            return out;
        }
        int spacing = placement.spacing();
        int cx = from.getX() >> 4, cz = from.getZ() >> 4;
        int rx0 = Math.floorDiv(cx, spacing), rz0 = Math.floorDiv(cz, spacing);
        List<ChunkPos> candidates = new ArrayList<>();
        int rings = Math.max(4, 240 / spacing);
        for (int dx = -rings; dx <= rings; dx++) {
            for (int dz = -rings; dz <= rings; dz++) {
                ChunkPos c = placement.getPotentialStructureChunk(level.getSeed(), (rx0 + dx) * spacing, (rz0 + dz) * spacing);
                if (!skip.contains(c.pack())) {
                    candidates.add(c);
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(c -> sq(c.getMiddleBlockX() - from.getX()) + sq(c.getMiddleBlockZ() - from.getZ())));
        for (ChunkPos c : candidates) {
            if (out.size() >= count) {
                break;
            }
            if (generates(level, c, city, placement)) {
                out.add(c);
            }
        }
        return out;
    }

    private static boolean generates(ServerLevel level, ChunkPos c, Structure city, StructurePlacement placement) {
        return switch (level.structureManager().checkStructurePresence(c, city, placement, false)) {
            case START_PRESENT -> true;
            case START_NOT_PRESENT -> false;
            case CHUNK_LOAD_NEEDED -> {
                var chunk = level.getChunk(c.x(), c.z(), ChunkStatus.STRUCTURE_STARTS);
                StructureStart start = level.structureManager().getStartForStructure(city, chunk);
                yield start != null && start.isValid();
            }
        };
    }

    private static double sq(double v) {
        return v * v;
    }
}
