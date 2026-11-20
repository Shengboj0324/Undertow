package dev.worldsfall.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Stillwater (§7.1): heavy (movement ×0.5, no sprint-swimming), drownable, cancels fall damage, extinguishes,
 * no infinite sources, light level 3, evaporates in the Nether like water.
 */
public class StillwaterFluidType extends FluidType {
    public StillwaterFluidType() {
        super(Properties.create()
            .descriptionId("block.worldsfall.stillwater")
            .motionScale(0.007)
            .canPushEntity(true)
            .canSwim(true)
            .canDrown(true)
            .fallDistanceModifier(0.0f)
            .canExtinguish(true)
            .canConvertToSource(false)
            .supportsBoating(false)
            .canHydrate(false)
            .pathType(PathType.WATER)
            .adjacentPathType(null)
            .lightLevel(3)
            .density(3000)
            .viscosity(4000)
            .temperature(250)
            .sound(SoundActions.BUCKET_FILL, dev.worldsfall.registry.WFSounds.ITEM_BUCKET_FILL_STILLWATER.get())
            .sound(SoundActions.BUCKET_EMPTY, dev.worldsfall.registry.WFSounds.ITEM_BUCKET_EMPTY_STILLWATER.get()));
    }

    @Override
    public boolean isVaporizedOnPlacement(Level level, BlockPos pos, FluidStack stack) {
        return level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos);
    }
}
