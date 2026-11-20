package dev.worldsfall.world.gen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * One piece holding the generator id, origin, variant and seed. The blueprint is rebuilt deterministically
 * (and cached) whenever a chunk needs it, so saving stays tiny.
 */
public class BlueprintPiece extends StructurePiece {
    private final String generator;
    private final BlockPos origin;
    private final int variant;
    private final long seed;
    private transient Blueprint cached;

    public BlueprintPiece(String generator, BlockPos origin, int variant, long seed) {
        this(generator, origin, variant, seed, Generators.build(generator, origin, variant, seed));
    }

    private BlueprintPiece(String generator, BlockPos origin, int variant, long seed, Blueprint blueprint) {
        super(dev.worldsfall.world.gen.WFStructures.BLUEPRINT_PIECE.get(), 0, blueprint.bounds());
        this.generator = generator;
        this.origin = origin;
        this.variant = variant;
        this.seed = seed;
        this.cached = blueprint;
    }

    public BlueprintPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(dev.worldsfall.world.gen.WFStructures.BLUEPRINT_PIECE.get(), tag);
        this.generator = tag.getStringOr("generator", "");
        this.origin = BlockPos.of(tag.getLongOr("origin", 0L));
        this.variant = tag.getIntOr("variant", 0);
        this.seed = tag.getLongOr("seed", 0L);
    }

    private Blueprint blueprint() {
        if (cached == null) {
            cached = Generators.build(generator, origin, variant, seed);
        }
        return cached;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("generator", generator);
        tag.putLong("origin", origin.asLong());
        tag.putInt("variant", variant);
        tag.putLong("seed", seed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos referencePos) {
        blueprint().place(level, chunkBox);
    }
}
