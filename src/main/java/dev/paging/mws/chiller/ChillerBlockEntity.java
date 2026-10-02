package dev.paging.mws.chiller;

import dev.paging.mws.Config;
import dev.paging.mws.client.FanSounds;
import dev.paging.mws.compat.cc.ChillerPeripheral;
import dev.paging.mws.power.Goggles;
import dev.paging.mws.power.Grid;
import dev.paging.mws.power.MachineBlockEntity;
import dev.paging.mws.rack.ServerRackBlockEntity;
import dev.paging.mws.registry.MwsFluids;
import dev.paging.mws.sludge.Sludge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaporative chiller. Pulls heat out of every server rack in range, paying for it in water
 * (piped in) and turning part of that water into sludge (piped out, or vented onto the floor).
 */
public class ChillerBlockEntity extends MachineBlockEntity {
    private static final int SCAN_INTERVAL = 20;
    private static final int VENT_DISTANCE = 6;

    private final FluidTank water;
    private final FluidTank sludge;
    private final ChillerFluidHandler fluidHandler;
    private final List<BlockPos> racks = new ArrayList<>();
    private int scanTicks;
    private double waterDebt;
    private double sludgeDebt;
    private double coolingLoad;
    private ChillerStatus status = ChillerStatus.NO_POWER;
    private ChillerPeripheral peripheral;

    public ChillerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        int capacity = Config.CHILLER_TANK.get();
        water = new FluidTank(capacity, stack -> stack.is(Fluids.WATER)) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
        sludge = new FluidTank(capacity, stack -> stack.is(MwsFluids.SLUDGE.get())) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
        fluidHandler = new ChillerFluidHandler(water, sludge);
    }

    @Override
    protected double idlePower() {
        return 30;
    }

    @Override
    protected void serverTick() {
        if (--scanTicks <= 0) {
            scanTicks = SCAN_INTERVAL;
            scanForRacks();
        }

        double removed = 0;
        if (!Grid.runnable(voltage)) {
            status = ChillerStatus.NO_POWER;
        } else if (sludge.getSpace() == 0 && !vent()) {
            status = ChillerStatus.JAMMED;
        } else if (water.isEmpty()) {
            status = ChillerStatus.NO_WATER;
        } else {
            removed = coolRacks();
            consumeWater(removed);
            status = removed > 0 ? ChillerStatus.COOLING : ChillerStatus.IDLE;
        }
        coolingLoad = removed / Grid.TICK;

        double fullLoad = Config.CHILLER_CAPACITY.get();
        setLoadPower(status == ChillerStatus.COOLING
                ? Config.CHILLER_POWER.get() * Math.max(0.25, Math.min(1, coolingLoad / fullLoad))
                : idlePower());

        var state = getBlockState();
        if (state.getBlock() instanceof ChillerBlock) {
            boolean active = status == ChillerStatus.COOLING;
            if (state.getValue(ChillerBlock.ACTIVE) != active)
                level.setBlock(worldPosition, state.setValue(ChillerBlock.ACTIVE, active), 3);
        }
    }

    private void scanForRacks() {
        racks.clear();
        int range = Config.CHILLER_RANGE.get();
        for (var pos : BlockPos.betweenClosed(worldPosition.offset(-range, -range, -range), worldPosition.offset(range, range, range))) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ServerRackBlockEntity)
                racks.add(pos.immutable());
        }
    }

    /** Spread this tick's cooling budget over every rack that is above target. @return joules removed */
    private double coolRacks() {
        double target = Config.CHILLER_TARGET.get();
        double budget = Math.min(Config.CHILLER_CAPACITY.get() * Grid.TICK, water.getFluidAmount() * Config.JOULES_PER_MB.get());
        var hot = new ArrayList<ServerRackBlockEntity>();
        for (var pos : racks) {
            if (level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack && rack.heatAbove(target) > 0)
                hot.add(rack);
        }
        // Racks needing less than an even share get what they need; the rest is split among the others.
        hot.sort((a, b) -> Double.compare(a.heatAbove(target), b.heatAbove(target)));
        double removed = 0;
        for (int i = 0; i < hot.size(); ++i) {
            var rack = hot.get(i);
            double share = (budget - removed) / (hot.size() - i);
            double take = Math.min(share, rack.heatAbove(target));
            rack.removeHeat(take);
            removed += take;
        }
        return removed;
    }

    private void consumeWater(double joules) {
        waterDebt += joules / Config.JOULES_PER_MB.get();
        int used = (int) waterDebt;
        if (used <= 0)
            return;
        waterDebt -= used;
        water.drain(used, IFluidHandler.FluidAction.EXECUTE);

        sludgeDebt += used * Config.SLUDGE_RATIO.get();
        int produced = (int) sludgeDebt;
        if (produced <= 0)
            return;
        sludgeDebt -= produced;
        sludge.fill(new FluidStack(MwsFluids.SLUDGE.get(), produced), IFluidHandler.FluidAction.EXECUTE);
    }

    /** Nobody is draining the sludge tank, so it goes on the floor, a bucket at a time. */
    private boolean vent() {
        if (sludge.getFluidAmount() < 1000 || !Sludge.spill(level, worldPosition, VENT_DISTANCE))
            return false;
        sludge.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    /** Client only (Power Grid strips this on dedicated servers). */
    @Override
    public void tickAudio() {
        FanSounds.tick(this);
    }

    public IFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    public FluidTank getWater() {
        return water;
    }

    public FluidTank getSludge() {
        return sludge;
    }

    public ChillerStatus getStatus() {
        return status;
    }

    public double getCoolingLoad() {
        return coolingLoad;
    }

    public int getRackCount() {
        return racks.size();
    }

    public ChillerPeripheral peripheral() {
        if (peripheral == null)
            peripheral = new ChillerPeripheral(this);
        return peripheral;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Water", water.writeToNBT(registries, new CompoundTag()));
        tag.put("Sludge", sludge.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("WaterDebt", waterDebt);
        tag.putDouble("SludgeDebt", sludgeDebt);
        tag.putString("Status", status.id());
        tag.putDouble("CoolingLoad", coolingLoad);
        tag.putInt("Racks", racks.size());
    }

    private int clientRackCount;

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        water.readFromNBT(registries, tag.getCompound("Water"));
        sludge.readFromNBT(registries, tag.getCompound("Sludge"));
        waterDebt = tag.getDouble("WaterDebt");
        sludgeDebt = tag.getDouble("SludgeDebt");
        status = ChillerStatus.byId(tag.getString("Status"));
        coolingLoad = tag.getDouble("CoolingLoad");
        clientRackCount = tag.getInt("Racks");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Goggles.title(tooltip, "chiller");
        Goggles.line(tooltip, "status", Component.translatable("mws.chiller_status." + status.id()), status.color);
        electricalLines(tooltip);
        Goggles.line(tooltip, "cooling", String.format("%.0f W (%d racks)", coolingLoad, clientRackCount), ChatFormatting.AQUA);
        Goggles.line(tooltip, "water", String.format("%,d / %,d mB", water.getFluidAmount(), water.getCapacity()), ChatFormatting.BLUE);
        Goggles.line(tooltip, "sludge", String.format("%,d / %,d mB", sludge.getFluidAmount(), sludge.getCapacity()),
                sludge.getSpace() < 1000 ? ChatFormatting.RED : ChatFormatting.DARK_GREEN);
        return true;
    }
}
