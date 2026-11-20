package dev.worldsfall.world.gen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldsfall.registry.WFBiomes;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

/**
 * The End's biome source with the mod's biomes added in the outer region (> 1,000 blocks) on top of vanilla
 * island density (§20.4 option a, data-driven via {@code data/minecraft/dimension/the_end.json}). Vanilla
 * biomes keep their islands; the main island and the Gap stay vanilla. The Underside is a true 3D biome
 * under large islands (Y 12–46).
 */
public final class WFEndBiomeSource extends BiomeSource {
    public static final MapCodec<WFEndBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        RegistryOps.retrieveElement(Biomes.THE_END),
        RegistryOps.retrieveElement(Biomes.END_HIGHLANDS),
        RegistryOps.retrieveElement(Biomes.END_MIDLANDS),
        RegistryOps.retrieveElement(Biomes.SMALL_END_ISLANDS),
        RegistryOps.retrieveElement(Biomes.END_BARRENS),
        RegistryOps.retrieveElement(WFBiomes.CHORUS_REACH),
        RegistryOps.retrieveElement(WFBiomes.STILLWATER_BASINS),
        RegistryOps.retrieveElement(WFBiomes.FALLEN_SPIRES),
        RegistryOps.retrieveElement(WFBiomes.THE_HOARD),
        RegistryOps.retrieveElement(WFBiomes.THE_UNDERSIDE),
        RegistryOps.retrieveElement(WFBiomes.THE_DRIFT)
    ).apply(i, i.stable(WFEndBiomeSource::new)));

    public static final int UNDERSIDE_MIN_Y = 12;
    public static final int UNDERSIDE_MAX_Y = 46;

    private final Holder<Biome> end, highlands, midlands, islands, barrens;
    private final Holder<Biome> chorusReach, stillwater, spires, hoard, underside, drift;

    private WFEndBiomeSource(Holder<Biome> end, Holder<Biome> highlands, Holder<Biome> midlands, Holder<Biome> islands, Holder<Biome> barrens,
                             Holder<Biome> chorusReach, Holder<Biome> stillwater, Holder<Biome> spires, Holder<Biome> hoard,
                             Holder<Biome> underside, Holder<Biome> drift) {
        this.end = end;
        this.highlands = highlands;
        this.midlands = midlands;
        this.islands = islands;
        this.barrens = barrens;
        this.chorusReach = chorusReach;
        this.stillwater = stillwater;
        this.spires = spires;
        this.hoard = hoard;
        this.underside = underside;
        this.drift = drift;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(end, highlands, midlands, islands, barrens, chorusReach, stillwater, spires, hoard, underside, drift);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        return (qx, qy, qz) -> getNoiseBiome(qx, qy, qz, sampler);
    }

    private Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        int bx = QuartPos.toBlock(qx);
        int by = QuartPos.toBlock(qy);
        int bz = QuartPos.toBlock(qz);
        int cx = SectionPos.blockToSectionCoord(bx);
        int cz = SectionPos.blockToSectionCoord(bz);
        if ((long) cx * cx + (long) cz * cz <= 4096L) {
            return end;
        }
        int wx = (cx * 2 + 1) * 8;
        int wz = (cz * 2 + 1) * 8;
        double h = sampler.erosion().sampleValue(wx, by, wz);
        return EndRegions.pick(this, bx, by, bz, h);
    }

    Holder<Biome> end() { return end; }
    Holder<Biome> highlands() { return highlands; }
    Holder<Biome> midlands() { return midlands; }
    Holder<Biome> islands() { return islands; }
    Holder<Biome> barrens() { return barrens; }
    Holder<Biome> chorusReach() { return chorusReach; }
    Holder<Biome> stillwater() { return stillwater; }
    Holder<Biome> spires() { return spires; }
    Holder<Biome> hoard() { return hoard; }
    Holder<Biome> underside() { return underside; }
    Holder<Biome> drift() { return drift; }
}
