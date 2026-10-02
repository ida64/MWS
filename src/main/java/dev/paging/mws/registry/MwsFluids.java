package dev.paging.mws.registry;

import dev.paging.mws.Mws;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class MwsFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Mws.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Mws.MODID);

    /** Thick, warm, and it does not go away by itself. */
    public static final DeferredHolder<FluidType, FluidType> SLUDGE_TYPE = FLUID_TYPES.register("sludge",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("block.mws.sludge")
                    .density(3000)
                    .viscosity(6000)
                    .temperature(320)
                    .canSwim(false)
                    .canDrown(true)
                    .canPushEntity(true)
                    .canExtinguish(false)
                    .canConvertToSource(false)
                    .supportsBoating(false)
                    .canHydrate(false)
                    .motionScale(0.002)
                    .fallDistanceModifier(0.2f)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SLUDGE = FLUIDS.register("sludge",
            () -> new BaseFlowingFluid.Source(MwsFluids.properties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_SLUDGE = FLUIDS.register("flowing_sludge",
            () -> new BaseFlowingFluid.Flowing(MwsFluids.properties()));

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(SLUDGE_TYPE, SLUDGE, FLOWING_SLUDGE)
                .bucket(MwsItems.SLUDGE_BUCKET)
                .block(MwsBlocks.SLUDGE)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .tickRate(30)
                .explosionResistance(100f);
    }

    private MwsFluids() {
    }
}
