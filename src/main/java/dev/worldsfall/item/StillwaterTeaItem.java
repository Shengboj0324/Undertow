package dev.worldsfall.item;

import dev.worldsfall.registry.WFEffects;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Stillwater Tea (§9.12/§9.13): instantly clears Levitation and Void Erosion. */
public class StillwaterTeaItem extends Item {
    public StillwaterTeaItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            entity.removeEffect(MobEffects.LEVITATION);
            entity.removeEffect(WFEffects.VOID_EROSION);
            entity.getPersistentData().putInt(dev.worldsfall.world.tide.TideEffects.EROSION_TICKS_KEY, 0);
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
