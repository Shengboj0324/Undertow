package dev.worldsfall.data;

import com.mojang.serialization.MapCodec;
import dev.worldsfall.item.EchoTabletItem;
import dev.worldsfall.registry.WFComponents;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;

/** Loot function {@code worldsfall:random_tablet}: gives an Echo Tablet a random fragment index 1–12 (§2.4). */
public final class RandomTabletFunction extends LootItemConditionalFunction {
    public static final MapCodec<RandomTabletFunction> CODEC = MapCodec.unit(() -> new RandomTabletFunction(Optional.empty()));

    private RandomTabletFunction(Optional<Holder<LootItemCondition>> condition) {
        super(condition);
    }

    @Override
    public MapCodec<RandomTabletFunction> codec() {
        return CODEC;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        stack.set(WFComponents.TABLET_INDEX.get(), 1 + context.getRandom().nextInt(EchoTabletItem.FRAGMENTS));
        return stack;
    }
}
