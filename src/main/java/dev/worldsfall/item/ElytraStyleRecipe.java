package dev.worldsfall.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldsfall.registry.WFComponents;
import dev.worldsfall.registry.WFRecipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleSmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;

import java.util.List;
import java.util.Optional;

/**
 * Smithing an elytra's style: {@code style} is an upgrade id (gale, still, hover, tempest — one upgrade per elytra, the
 * new one replaces the old) or {@code scaled} (the Dragon Scale overlay, cosmetic, kept through later upgrades). The result
 * keeps every component of the base and its wing look is recomputed, so a scaled Gale elytra shows scaled Gale wings.
 */
public final class ElytraStyleRecipe extends SimpleSmithingRecipe {
    public static final String SCALED = "scaled";
    public static final MapCodec<ElytraStyleRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
        Ingredient.CODEC.fieldOf("template").forGetter(o -> o.template),
        Ingredient.CODEC.fieldOf("addition").forGetter(o -> o.addition),
        Codec.STRING.fieldOf("style").forGetter(o -> o.style)
    ).apply(i, ElytraStyleRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ElytraStyleRecipe> STREAM_CODEC = StreamCodec.composite(
        Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo, Ingredient.CONTENTS_STREAM_CODEC, o -> o.template,
        Ingredient.CONTENTS_STREAM_CODEC, o -> o.addition, ByteBufCodecs.STRING_UTF8, o -> o.style, ElytraStyleRecipe::new);
    public static final RecipeSerializer<ElytraStyleRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final Ingredient template;
    private final Ingredient addition;
    private final String style;
    private final Ingredient base = Ingredient.of(Items.ELYTRA);

    public ElytraStyleRecipe(Recipe.CommonInfo info, Ingredient template, Ingredient addition, String style) {
        super(info);
        this.template = template;
        this.addition = addition;
        this.style = style;
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input) {
        ItemStack out = input.base().copyWithCount(1);
        if (SCALED.equals(style)) {
            out.set(WFComponents.ELYTRA_SCALED.get(), true);
        } else {
            out.set(WFComponents.ELYTRA_UPGRADE.get(), ElytraUpgrade.valueOf(style.toUpperCase(java.util.Locale.ROOT)));
        }
        return ElytraUpgrade.restyle(out);
    }

    @Override
    public Optional<Ingredient> templateIngredient() {
        return Optional.of(template);
    }

    @Override
    public Ingredient baseIngredient() {
        return base;
    }

    @Override
    public Optional<Ingredient> additionIngredient() {
        return Optional.of(addition);
    }

    @Override
    public RecipeSerializer<ElytraStyleRecipe> getSerializer() {
        return WFRecipes.ELYTRA_STYLE.get();
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.create(List.of(template, base, addition));
    }

    @Override
    public List<RecipeDisplay> display() {
        ItemStack preview = assemble(new SmithingRecipeInput(ItemStack.EMPTY, new ItemStack(Items.ELYTRA), ItemStack.EMPTY));
        return List.of(new SmithingRecipeDisplay(template.display(), base.display(), addition.display(),
            new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(preview)), new SlotDisplay.ItemSlotDisplay(Items.SMITHING_TABLE)));
    }
}
