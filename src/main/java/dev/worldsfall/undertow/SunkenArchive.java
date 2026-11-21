package dev.worldsfall.undertow;

import com.mojang.serialization.Codec;
import dev.worldsfall.Worldsfall;
import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFEntities;
import dev.worldsfall.world.gen.structure.Blueprint;
import dev.worldsfall.world.gen.structure.DriftwreckGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Sunken Archive (proposal "What is down there"): the Ascendancy's drowned library, one below each Tide Maw, built the
 * first time someone comes through that maw. Tilted floors of water-worn tiles, toppled shelves, reading stands and
 * lanterns; five Archive Codices in caches and a sixth sealed in a hidden alcove that a Drowned Ascendant points toward.
 */
public final class SunkenArchive extends SavedData {
    public static final int Y = 150;
    public static final Codec<SunkenArchive> CODEC = Codec.LONG.listOf().xmap(SunkenArchive::new, a -> List.copyOf(a.built));
    public static final SavedDataType<SunkenArchive> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(Worldsfall.MODID, "sunken_archives"),
        SunkenArchive::new, CODEC);

    private final Set<Long> built = new HashSet<>();

    public SunkenArchive() {}

    private SunkenArchive(List<Long> list) {
        built.addAll(list);
    }

    public static SunkenArchive get(ServerLevel undertow) {
        return undertow.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isBuilt(long maw) {
        return built.contains(maw);
    }

    /** The archive's centre below a maw. */
    public static BlockPos centre(Vec3 maw) {
        return BlockPos.containing(maw.x + 24, Y, maw.z + 8);
    }

    /** Builds the archive under {@code maw} once. */
    public static void ensure(ServerLevel undertow, Vec3 maw) {
        SunkenArchive data = get(undertow);
        long id = TideMaws.id(maw);
        if (!data.built.add(id)) {
            return;
        }
        data.setDirty();
        BlockPos c = centre(maw);
        RandomSource r = RandomSource.create(undertow.getSeed() ^ id);
        Layout layout = new Layout();
        Blueprint bp = build(c, r, layout);
        BoundingBox box = bp.bounds();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                undertow.getChunk(cx, cz);
                bp.place(undertow, new BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15));
            }
        }
        spawnAscendants(undertow, layout, r);
    }

    /** Where things ended up: stands for the Ascendants and the hidden alcove. */
    static final class Layout {
        BlockPos reader = BlockPos.ZERO, writer = BlockPos.ZERO, pointer = BlockPos.ZERO, alcove = BlockPos.ZERO;
    }

    static Blueprint build(BlockPos c, RandomSource r, Layout out) {
        Blueprint bp = new Blueprint();
        BlockState tiles = WFBlocks.ARCHIVE_TILES.get().defaultBlockState(), shelf = WFBlocks.ARCHIVE_SHELF.get().defaultBlockState(),
            coral = WFBlocks.NULL_CORAL_BLOCK.get().defaultBlockState();
        // the main hall: a tilted floor, 31×21, two blocks thick, with coral clinging underneath
        double tilt = Math.toRadians(9 + r.nextInt(6));
        platform(bp, c, 15, 10, tilt, tiles, coral, r);
        // two drowned wings at odd heights and tilts
        BlockPos west = c.offset(-27, -7, 4), east = c.offset(25, 5, -6);
        platform(bp, west, 9, 7, -tilt * 0.7, tiles, coral, r);
        platform(bp, east, 10, 8, tilt * 1.3, tiles, coral, r);
        // broken columns and arches over the hall, lanterns still hanging
        for (int k = 0; k < 6; k++) {
            int x = -12 + k * 5;
            for (int side = -1; side <= 1; side += 2) {
                BlockPos base = floorAt(c, x, side * 8, tilt);
                int h = 6 + r.nextInt(5) - (k % 3 == 1 ? 4 : 0);
                for (int y = 1; y <= h; y++) {
                    bp.set(base.above(y), tiles);
                }
                if (h >= 8) {
                    bp.set(base.above(h).relative(Direction.SOUTH, -side), tiles);
                    bp.set(base.above(h - 1).relative(Direction.SOUTH, -side), WFBlocks.SPIRE_LANTERN.get().defaultBlockState().setValue(LanternBlock.HANGING, true));
                }
            }
        }
        // shelves: rows standing on the tilted floor, some toppled flat
        for (int row = 0; row < 4; row++) {
            int z = -6 + row * 4;
            boolean toppled = r.nextInt(3) == 0;
            for (int x = -9; x <= 9; x++) {
                if (r.nextInt(7) == 0) {
                    continue;
                }
                BlockPos f = floorAt(c, x, z, tilt);
                bp.set(f.above(), shelf);
                if (!toppled) {
                    bp.set(f.above(2), shelf);
                } else {
                    bp.set(f.above().relative(Direction.SOUTH), shelf);
                }
            }
        }
        // reading stands and the record caches (Codices I–V); the general archive cache
        out.reader = floorAt(c, -11, 6, tilt).above();
        out.writer = floorAt(c, 11, -7, tilt).above();
        out.pointer = floorAt(c, 0, -9, tilt).above();
        bp.set(out.reader.relative(Direction.NORTH), Blocks.LECTERN.defaultBlockState());
        bp.set(out.writer.relative(Direction.EAST), Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, Direction.WEST));
        BlockPos[] caches = {floorAt(c, -13, -8, tilt), floorAt(c, 13, 8, tilt), floorAt(west, -4, 3, -tilt * 0.7), floorAt(east, 5, -2, tilt * 1.3),
            floorAt(east, -4, 4, tilt * 1.3)};
        for (int i = 0; i < caches.length; i++) {
            DriftwreckGenerator.cache(bp, caches[i].above(), "vault/archive_codex_" + (i + 1), false);
        }
        DriftwreckGenerator.cache(bp, floorAt(c, 2, 2, tilt).above(), "vault/sunken_archive", false);
        // the hidden alcove: a sealed tile cube below the west wing, holding Codex VI
        BlockPos alcove = west.offset(-6, -13, 9);
        out.alcove = alcove;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean shell = Math.abs(dx) == 2 || Math.abs(dy) == 2 || Math.abs(dz) == 2;
                    bp.set(alcove.offset(dx, dy, dz), shell ? (dx == 2 && dy == 0 && dz == 0 ? WFBlocks.MURAL_PANEL.get().defaultBlockState() : tiles)
                        : Blocks.AIR.defaultBlockState());
                }
            }
        }
        DriftwreckGenerator.cache(bp, alcove.below(), "vault/archive_codex_6", false);
        bp.set(alcove.above(), WFBlocks.SPIRE_LANTERN.get().defaultBlockState().setValue(LanternBlock.HANGING, true));
        // null coral reefs on the undersides and around
        for (int i = 0; i < 40; i++) {
            BlockPos p = c.offset(r.nextInt(61) - 30, -3 - r.nextInt(10), r.nextInt(41) - 20);
            bp.setIfAbsent(p, r.nextInt(4) == 0 ? WFBlocks.NULL_CORAL.get().defaultBlockState() : coral);
        }
        return bp;
    }

    private static BlockPos floorAt(BlockPos c, int x, int z, double tilt) {
        return c.offset(x, (int) Math.round(z * Math.tan(tilt)), z);
    }

    private static void platform(Blueprint bp, BlockPos c, int rx, int rz, double tilt, BlockState top, BlockState under, RandomSource r) {
        for (int x = -rx; x <= rx; x++) {
            for (int z = -rz; z <= rz; z++) {
                double e = (x * x) / (double) (rx * rx) + (z * z) / (double) (rz * rz);
                if (e > 1.0 || e > 0.8 && r.nextInt(3) == 0) {
                    continue;
                }
                BlockPos f = floorAt(c, x, z, tilt);
                bp.set(f, top);
                bp.set(f.below(), top);
                if (r.nextInt(4) == 0) {
                    bp.setIfAbsent(f.below(2), under);
                }
            }
        }
    }

    private static void spawnAscendants(ServerLevel u, Layout l, RandomSource r) {
        place(u, l.reader, DrownedAscendant.READING, null);
        place(u, l.writer, DrownedAscendant.WRITING, null);
        place(u, l.pointer, DrownedAscendant.POINTING, l.alcove);
    }

    private static void place(ServerLevel u, BlockPos at, int action, BlockPos target) {
        DrownedAscendant a = WFEntities.DROWNED_ASCENDANT.get().create(u, EntitySpawnReason.STRUCTURE);
        if (a != null) {
            a.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, u.getRandom().nextFloat() * 360, 0);
            a.setAction(action, target);
            u.addFreshEntity(a);
        }
    }
}
