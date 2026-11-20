package dev.worldsfall.world.gen.structure;

import dev.worldsfall.registry.WFBlocks;
import dev.worldsfall.registry.WFFluids;
import dev.worldsfall.world.gen.WFStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Carves one Stillwater Basin (§4.2), only inside the chunk being generated. The picture: a heart-shaped ring of pale
 * stone around a flat black lake, a few Overworld relics resting on it, and a three-wide notch at the nearest edge where
 * a Stillfall slides off the island.
 */
public class StillwaterBasinPiece extends StructurePiece {
    private static final BlockState[] RELICS = {
        Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), Blocks.PUMPKIN.defaultBlockState(),
        Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.HAY_BLOCK.defaultBlockState(), Blocks.RED_MUSHROOM_BLOCK.defaultBlockState(),
        Blocks.MELON.defaultBlockState(), Blocks.BIRCH_LOG.defaultBlockState()};
    private final BlockPos center;
    private final int radius, depth, spillLength;
    private final Direction spill;
    private final long seed;

    public StillwaterBasinPiece(BlockPos center, int radius, int depth, Direction spill, int spillLength, long seed) {
        super(WFStructures.STILLWATER_BASIN_PIECE.get(), 0, bounds(center, radius, depth, spill, spillLength));
        this.center = center;
        this.radius = radius;
        this.depth = depth;
        this.spill = spill;
        this.spillLength = spillLength;
        this.seed = seed;
    }

    public StillwaterBasinPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(WFStructures.STILLWATER_BASIN_PIECE.get(), tag);
        center = BlockPos.of(tag.getLongOr("center", 0L));
        radius = tag.getIntOr("radius", 10);
        depth = tag.getIntOr("depth", 4);
        spill = Direction.from3DDataValue(tag.getIntOr("spill", 2));
        spillLength = tag.getIntOr("spill_length", 20);
        seed = tag.getLongOr("seed", 0L);
    }

    public BlockPos center() {
        return center;
    }

    public int radius() {
        return radius;
    }

    public Direction spill() {
        return spill;
    }

    private static BoundingBox bounds(BlockPos c, int r, int depth, Direction spill, int len) {
        int e = r + 3;
        BoundingBox box = new BoundingBox(c.getX() - e, c.getY() - depth - 4, c.getZ() - e, c.getX() + e, c.getY() + 3, c.getZ() + e);
        BlockPos far = c.relative(spill, len + 1);
        return BoundingBox.encapsulatingPositions(java.util.List.of(new BlockPos(box.minX(), box.minY(), box.minZ()),
            new BlockPos(box.maxX(), box.maxY(), box.maxZ()), far.offset(-2, -depth - 4, -2), far.offset(2, 3, 2))).orElse(box);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putLong("center", center.asLong());
        tag.putInt("radius", radius);
        tag.putInt("depth", depth);
        tag.putInt("spill", spill.get3DDataValue());
        tag.putInt("spill_length", spillLength);
        tag.putLong("seed", seed);
    }

    /** Heart shape opening toward {@code spill}: two lobes behind, a point toward the spill side. */
    public static boolean inShape(double along, double across, double r) {
        // lobes far enough apart that the cusp between them reads from the lip
        double lobe = r * 0.55;
        double a = Math.hypot(across + r * 0.4, along + r * 0.1);
        double b = Math.hypot(across - r * 0.4, along + r * 0.1);
        boolean point = along >= 0 && Math.abs(across) <= (r * 0.95 - along) * 0.85 && along <= r * 0.9;
        return a <= lobe || b <= lobe || point;
    }

    private boolean shape(int dx, int dz, double r) {
        double along = dx * spill.getStepX() + dz * spill.getStepZ();
        double across = dx * spill.getStepZ() - dz * spill.getStepX();
        return inShape(along, across, r);
    }

    /** Rough distance (in blocks, capped at 4) from a basin column to the shape's edge, for the sloped floor. */
    private int edgeDistance(int dx, int dz) {
        for (int k = 1; k <= 4; k++) {
            if (!shape(dx + k, dz, radius) || !shape(dx - k, dz, radius) || !shape(dx, dz + k, radius) || !shape(dx, dz - k, radius)) {
                return k - 1;
            }
        }
        return 4;
    }

    private static long columnSeed(long seed, int x, int z) {
        return seed ^ (x * 3129871L) ^ (z * 116129781L);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource ignored,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos referencePos) {
        BlockState stillstone = WFBlocks.STILLSTONE.get().defaultBlockState();
        BlockState endStone = Blocks.END_STONE.defaultBlockState();
        BlockState water = WFFluids.STILLWATER.get().defaultFluidState().createLegacyBlock();
        BlockState air = Blocks.AIR.defaultBlockState();
        int waterY = center.getY() - 1;
        int ir = radius + 2;
        for (int x = chunkBox.minX(); x <= chunkBox.maxX(); x++) {
            for (int z = chunkBox.minZ(); z <= chunkBox.maxZ(); z++) {
                int dx = x - center.getX(), dz = z - center.getZ();
                if ((Math.abs(dx) > ir + 1 || Math.abs(dz) > ir + 1) && !inChannel(dx, dz, 2)) {
                    continue;
                }
                RandomSource r = RandomSource.create(columnSeed(seed, x, z));
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                boolean inside = shape(dx, dz, radius);
                boolean lip = !inside && (shape(dx + 1, dz, radius) || shape(dx - 1, dz, radius) || shape(dx, dz + 1, radius) || shape(dx, dz - 1, radius)
                    || shape(dx + 2, dz, radius) || shape(dx - 2, dz, radius) || shape(dx, dz + 2, radius) || shape(dx, dz - 2, radius));
                if (inChannel(dx, dz, 1) && !inside) {
                    channel(level, x, z, top, waterY, water, stillstone, air);
                    continue;
                }
                if (inside) {
                    double edge = 1 - Math.min(1, edgeDistance(dx, dz) / 3.0);
                    int d = Math.max(1, (int) Math.round(depth - edge * (depth - 1)));
                    for (int y = Math.max(top, waterY) + 2; y > waterY; y--) {
                        level.setBlock(new BlockPos(x, y, z), air, 2);
                    }
                    for (int y = waterY; y > waterY - d; y--) {
                        level.setBlock(new BlockPos(x, y, z), water, 2);
                    }
                    for (int y = waterY - d; y > waterY - d - 2; y--) {
                        level.setBlock(new BlockPos(x, y, z), stillstone, 2);
                    }
                } else if (lip && top > waterY - depth - 3) {
                    // a pale raised ring: end stone with Stillstone patches, one block proud of the island and never below the water
                    int lipTop = Math.max(top + (ring(dx, dz) ? 1 : 0), waterY + 1);
                    for (int y = waterY - depth - 1; y <= lipTop; y++) {
                        boolean patch = r.nextInt(5) == 0 || y < waterY;
                        level.setBlock(new BlockPos(x, y, z), patch ? stillstone : endStone, 2);
                    }
                    BlockPos above = new BlockPos(x, lipTop + 1, z);
                    int roll = r.nextInt(14);
                    if (roll == 0) {
                        level.setBlock(above, WFBlocks.RIMGLASS.get().defaultBlockState(), 2);
                    } else if (roll <= 2) {
                        level.setBlock(above, WFBlocks.MIRROR_SHARD.get().defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP), 2);
                    }
                }
            }
        }
        relics(level, chunkBox, waterY);
    }

    /** The inner lip column (touching the water) gets the raised ring; the outer one slopes back to the island. */
    private boolean ring(int dx, int dz) {
        return shape(dx + 1, dz, radius) || shape(dx - 1, dz, radius) || shape(dx, dz + 1, radius) || shape(dx, dz - 1, radius);
    }

    /** True for the spill channel: half-width {@code w} around the line from the lake toward the island edge. */
    private boolean inChannel(int dx, int dz, int w) {
        int along = dx * spill.getStepX() + dz * spill.getStepZ();
        int across = dx * spill.getStepZ() - dz * spill.getStepX();
        return along >= radius / 2 && along <= spillLength && Math.abs(across) <= w;
    }

    private void channel(WorldGenLevel level, int x, int z, int top, int waterY, BlockState water, BlockState stillstone, BlockState air) {
        boolean ground = top > waterY - 8;
        for (int y = Math.max(top, waterY) + 2; y > waterY; y--) {
            level.setBlock(new BlockPos(x, y, z), air, 2);
        }
        BlockPos surface = new BlockPos(x, waterY, z);
        if (ground) {
            level.setBlock(surface, water, 2);
            level.setBlock(surface.below(), stillstone, 2);
            // side walls keep the channel's water in its notch
            for (Direction side : new Direction[] {spill.getClockWise(), spill.getCounterClockWise()}) {
                BlockPos wall = surface.relative(side, 2);
                if (!inChannel(wall.getX() - center.getX(), wall.getZ() - center.getZ(), 1) && level.getBlockState(wall).isAir()) {
                    level.setBlock(wall, stillstone, 2);
                }
            }
        } else {
            // first column past the edge: the lip of the Stillfall; the fluid pours over from here once ticked
            level.setBlock(surface, water, 2);
            level.scheduleTick(surface, WFFluids.STILLWATER.get(), 2);
        }
    }

    private void relics(WorldGenLevel level, BoundingBox chunkBox, int waterY) {
        RandomSource r = RandomSource.create(seed);
        int count = 2 + r.nextInt(4);
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Mth.TWO_PI;
            double d = r.nextDouble() * radius * 0.55;
            BlockPos p = new BlockPos(center.getX() + (int) Math.round(Math.cos(a) * d), waterY, center.getZ() + (int) Math.round(Math.sin(a) * d));
            BlockState relic = RELICS[r.nextInt(RELICS.length)];
            if (chunkBox.isInside(p) && shape(p.getX() - center.getX(), p.getZ() - center.getZ(), radius * 0.8)) {
                level.setBlock(p, relic, 2);
            }
        }
    }
}
