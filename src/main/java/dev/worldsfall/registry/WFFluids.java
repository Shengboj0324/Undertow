package dev.worldsfall.registry;

import dev.worldsfall.Worldsfall;
import dev.worldsfall.fluid.StillwaterFluid;
import dev.worldsfall.fluid.StillwaterFluidType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class WFFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Worldsfall.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Worldsfall.MODID);

    public static final DeferredHolder<FluidType, StillwaterFluidType> STILLWATER_TYPE = FLUID_TYPES.register("stillwater", StillwaterFluidType::new);
    public static final DeferredHolder<Fluid, FlowingFluid> STILLWATER = FLUIDS.register("stillwater", StillwaterFluid.Source::new);
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_STILLWATER = FLUIDS.register("flowing_stillwater", StillwaterFluid.Flowing::new);

    private WFFluids() {}
}
