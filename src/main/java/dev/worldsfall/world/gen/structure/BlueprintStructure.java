package dev.worldsfall.world.gen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldsfall.world.gen.WFStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * A procedural End structure (§11). The {@code generator} selects placement rules and the blueprint:
 * fallen_spire, hoard, driftwreck, root_hollow.
 */
public class BlueprintStructure extends Structure {
    public static final MapCodec<BlueprintStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        settingsCodec(i),
        Codec.STRING.fieldOf("generator").forGetter(s -> s.generator)
    ).apply(i, BlueprintStructure::new));

    private final String generator;

    public BlueprintStructure(StructureSettings settings, String generator) {
        super(settings);
        this.generator = generator;
    }

    @Override
    public StructureType<?> type() {
        return WFStructures.BLUEPRINT.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        int x = ctx.chunkPos().getMiddleBlockX();
        int z = ctx.chunkPos().getMiddleBlockZ();
        double dist = Math.sqrt((double) x * x + (double) z * z);
        WorldgenRandom random = ctx.random();
        Optional<Placement> placement = switch (generator) {
            case "fallen_spire" -> spire(ctx, x, z, dist, random);
            case "hoard" -> hoard(ctx, x, z, dist, random);
            case "driftwreck" -> driftwreck(ctx, x, z, dist, random);
            case "root_hollow" -> rootHollow(ctx, x, z, dist, random);
            case "gapwreck" -> gapwreck(ctx, x, z, dist, random);
            default -> Optional.empty();
        };
        return placement.map(p -> new GenerationStub(p.pos, builder -> builder.addPiece(new BlueprintPiece(generator, p.pos, p.variant, p.seed))));
    }

    private record Placement(BlockPos pos, int variant, long seed) {}

    private static int surface(GenerationContext ctx, int x, int z) {
        return ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
    }

    private static Optional<Placement> spire(GenerationContext ctx, int x, int z, double dist, WorldgenRandom random) {
        if (dist < 2000) {
            return Optional.empty();
        }
        int y = surface(ctx, x, z);
        if (y < 50) {
            return Optional.empty();
        }
        for (int[] o : new int[][] {{14, 0}, {-14, 0}, {0, 14}, {0, -14}}) {
            if (surface(ctx, x + o[0], z + o[1]) < y - 10) {
                return Optional.empty();
            }
        }
        int roll = random.nextInt(100);
        int variant = roll < 50 ? SpireGenerator.LEANING : roll < 85 ? SpireGenerator.SHATTERED : SpireGenerator.DROWNED;
        if (dist >= 6000) {
            variant |= SpireGenerator.DEEP_FLAG;
        }
        return Optional.of(new Placement(new BlockPos(x, y, z), variant, random.nextLong()));
    }

    private static Optional<Placement> hoard(GenerationContext ctx, int x, int z, double dist, WorldgenRandom random) {
        if (dist < 1000 || surface(ctx, x, z) > ctx.heightAccessor().getMinY() + 1) {
            return Optional.empty();
        }
        int y = 52 + random.nextInt(20);
        int variant = random.nextInt(15) == 0 ? HoardGenerator.GRAND : HoardGenerator.NORMAL;
        return Optional.of(new Placement(new BlockPos(x, y, z), variant, random.nextLong()));
    }

    private static Optional<Placement> driftwreck(GenerationContext ctx, int x, int z, double dist, WorldgenRandom random) {
        if (dist < 2500) {
            return Optional.empty();
        }
        NoiseColumn col = ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
        for (int y = 2; y < 44; y++) {
            if (!col.getBlock(y).isAir()) {
                return Optional.empty();
            }
        }
        int y = 8 + random.nextInt(21);
        int variant = random.nextInt(6) == 0 ? DriftwreckGenerator.TIDE_SHRINE : 1 + random.nextInt(3);
        return Optional.of(new Placement(new BlockPos(x, y, z), variant, random.nextLong()));
    }

    /** In the Debris Belt (500–750 blocks out), in open void, always above the highest tide. */
    private static Optional<Placement> gapwreck(GenerationContext ctx, int x, int z, double dist, WorldgenRandom random) {
        if (dist < 500 || dist > 750) {
            return Optional.empty();
        }
        NoiseColumn col = ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
        for (int y = 40; y < 90; y++) {
            if (!col.getBlock(y).isAir()) {
                return Optional.empty();
            }
        }
        int y = 54 + random.nextInt(14);
        return Optional.of(new Placement(new BlockPos(x, y, z), random.nextInt(3) == 0 ? 1 : 0, random.nextLong()));
    }

    private static Optional<Placement> rootHollow(GenerationContext ctx, int x, int z, double dist, WorldgenRandom random) {
        if (dist < 1100) {
            return Optional.empty();
        }
        NoiseColumn col = ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
        int bottom = -1, top = -1;
        for (int y = 4; y < 100; y++) {
            boolean solid = !col.getBlock(y).isAir();
            if (solid && bottom < 0) {
                bottom = y;
            }
            if (solid) {
                top = y;
            } else if (bottom >= 0) {
                break;
            }
        }
        int thickness = top - bottom;
        if (bottom < 0 || thickness < 12) {
            return Optional.empty();
        }
        int variant = dist >= 6000 && thickness >= 18 ? RootHollowGenerator.DEEP : RootHollowGenerator.NORMAL;
        return Optional.of(new Placement(new BlockPos(x, bottom, z), variant, random.nextLong()));
    }
}
