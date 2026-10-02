package dev.paging.mws.power;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;

import java.util.List;

/**
 * A 240V machine with two terminals and a single variable resistive load between them.
 * Subclasses pick how many watts they want via {@link #setLoadPower(double)}, and the
 * Power Grid simulation decides what they actually get.
 */
public abstract class MachineBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation {
    // Assigned in buildCircuit, which Power Grid calls from inside the super constructor:
    // no field initializers here, and idlePower() must not depend on subclass fields.
    private ElectricWire load;
    private double loadResistance;
    /** Measured voltage across the terminals, last tick. Synced to clients. */
    protected double voltage;
    /** Measured power consumed, last tick. Synced to clients. */
    protected double power;

    public MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(2);
        if (loadResistance <= 0)
            loadResistance = Grid.resistanceFor(idlePower());
        load = builder.connect((float) loadResistance, builder.terminalNode(0), builder.terminalNode(1));
    }

    /** Power drawn while the machine is doing nothing. */
    protected abstract double idlePower();

    /** Runs every server tick after the terminals have been measured. */
    protected abstract void serverTick();

    @Override
    public void electricalTick() {
        if (load == null)
            return;
        applyPower(load);
        voltage = Math.abs(load.potentialDifference());
        power = Math.abs(load.power());
        serverTick();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level != null && !level.isClientSide)
            sendData();
    }

    /** Ask for {@code watts} at nominal voltage. Small changes are ignored to keep the solver calm. */
    protected void setLoadPower(double watts) {
        double resistance = Grid.resistanceFor(watts);
        if (loadResistance > 0 && Math.abs(resistance - loadResistance) / loadResistance < 0.01)
            return;
        loadResistance = resistance;
        if (load != null)
            load.setResistance(resistance);
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    public double getVoltage() {
        return voltage;
    }

    public double getPower() {
        return power;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putDouble("Voltage", voltage);
        tag.putDouble("Power", power);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        voltage = tag.getDouble("Voltage");
        power = tag.getDouble("Power");
    }

    protected void electricalLines(List<Component> tooltip) {
        ChatFormatting color = Grid.runnable(voltage) ? ChatFormatting.GREEN : ChatFormatting.RED;
        Goggles.line(tooltip, "voltage", String.format("%.1f V / %.0f V", voltage, Grid.nominal()), color);
        Goggles.line(tooltip, "power", String.format("%.0f W", power), ChatFormatting.AQUA);
    }
}
