package dev.worldsfall.registry;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.item.ElytraStyleRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Custom recipe serializers. */
public final class WFRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Worldsfall.MODID);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ElytraStyleRecipe>> ELYTRA_STYLE = SERIALIZERS.register("elytra_style",
        () -> ElytraStyleRecipe.SERIALIZER);

    private WFRecipes() {}
}
