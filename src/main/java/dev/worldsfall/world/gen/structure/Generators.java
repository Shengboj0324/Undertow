package dev.worldsfall.world.gen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

/** Dispatches blueprint generation by generator id. Pure and deterministic for a given seed. */
public final class Generators {
    private Generators() {}

    public static Blueprint build(String generator, BlockPos origin, int variant, long seed) {
        RandomSource random = RandomSource.create(seed);
        return switch (generator) {
            case "fallen_spire" -> SpireGenerator.build(origin, variant, random);
            case "hoard" -> HoardGenerator.build(origin, variant, random);
            case "driftwreck" -> DriftwreckGenerator.build(origin, variant, random);
            case "root_hollow" -> RootHollowGenerator.build(origin, variant, random);
            case "gapwreck" -> GapwreckGenerator.build(origin, variant, random);
            default -> new Blueprint();
        };
    }
}
