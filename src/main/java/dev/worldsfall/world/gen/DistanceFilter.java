package dev.worldsfall.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;

/** Keeps placements whose horizontal distance from (0, 0) lies in [min, max) — the distance rings of §3.2. */
public record DistanceFilter(int min, int max) implements PlacementFilter {
    public static final MapCodec<DistanceFilter> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.INT.optionalFieldOf("min", 0).forGetter(DistanceFilter::min),
        Codec.INT.optionalFieldOf("max", Integer.MAX_VALUE).forGetter(DistanceFilter::max)
    ).apply(i, DistanceFilter::new));

    @Override
    public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
        double d = Math.sqrt((double) origin.getX() * origin.getX() + (double) origin.getZ() * origin.getZ());
        return d >= min && d < max;
    }

    @Override
    public MapCodec<? extends PlacementFilter> codec() {
        return CODEC;
    }
}
