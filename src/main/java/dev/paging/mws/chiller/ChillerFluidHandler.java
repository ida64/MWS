package dev.paging.mws.chiller;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Pipes push water in and pull sludge out. Nothing else goes either way. */
public class ChillerFluidHandler implements IFluidHandler {
    private final FluidTank water;
    private final FluidTank sludge;

    public ChillerFluidHandler(FluidTank water, FluidTank sludge) {
        this.water = water;
        this.sludge = sludge;
    }

    @Override
    public int getTanks() {
        return 2;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank == 0 ? water.getFluid() : sludge.getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? water.getCapacity() : sludge.getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 ? water.isFluidValid(stack) : sludge.isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !resource.getFluid().is(FluidTags.WATER))
            return 0;
        return water.fill(new FluidStack(Fluids.WATER, resource.getAmount()), action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return sludge.drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return sludge.drain(maxDrain, action);
    }
}
