package dev.worldsfall.data;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldsfall.Worldsfall;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.common.world.NeoForgeEnvironmentAttributes;

/**
 * Biome modifier {@code worldsfall:end_sky}: gives every End biome the layered End sky (§18.7) through
 * NeoForge's custom skybox environment attribute, without overriding the vanilla dimension type.
 */
public record EndSkyBiomeModifier(HolderSet<Biome> biomes) implements BiomeModifier {
    public static final Identifier SKY = Identifier.fromNamespaceAndPath(Worldsfall.MODID, "end_sky");
    public static final MapCodec<EndSkyBiomeModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Biome.LIST_CODEC.fieldOf("biomes").forGetter(EndSkyBiomeModifier::biomes)
    ).apply(i, EndSkyBiomeModifier::new));

    @Override
    public void modify(RegistryAccess registries, Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.MODIFY && biomes.contains(biome) && builder.getAttributes().get(NeoForgeEnvironmentAttributes.CUSTOM_SKYBOX) == null) {
            builder.getAttributes().set(NeoForgeEnvironmentAttributes.CUSTOM_SKYBOX, SKY);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}
