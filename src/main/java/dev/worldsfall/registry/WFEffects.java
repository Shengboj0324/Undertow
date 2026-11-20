package dev.worldsfall.registry;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.effect.SimpleEffect;
import dev.worldsfall.effect.VoidErosionEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WFEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Worldsfall.MODID);
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, Worldsfall.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> VOID_EROSION = EFFECTS.register("void_erosion", VoidErosionEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> STEADINESS = EFFECTS.register("steadiness",
        () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0x8FB0FF));
    public static final DeferredHolder<MobEffect, MobEffect> TIDESIGHT = EFFECTS.register("tidesight",
        () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0x2FC9BB));

    public static final DeferredHolder<Potion, Potion> STEADINESS_POTION = POTIONS.register("steadiness",
        () -> new Potion("steadiness", new MobEffectInstance(STEADINESS, 1800)));
    public static final DeferredHolder<Potion, Potion> LONG_STEADINESS_POTION = POTIONS.register("long_steadiness",
        () -> new Potion("steadiness", new MobEffectInstance(STEADINESS, 4800)));
    public static final DeferredHolder<Potion, Potion> TIDESIGHT_POTION = POTIONS.register("tidesight",
        () -> new Potion("tidesight", new MobEffectInstance(TIDESIGHT, 3600)));
    public static final DeferredHolder<Potion, Potion> LONG_TIDESIGHT_POTION = POTIONS.register("long_tidesight",
        () -> new Potion("tidesight", new MobEffectInstance(TIDESIGHT, 9600)));

    private WFEffects() {}
}
