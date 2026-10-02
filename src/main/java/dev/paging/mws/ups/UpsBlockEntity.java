package dev.paging.mws.ups;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.paging.mws.Config;
import dev.paging.mws.compat.cc.UpsPeripheral;
import dev.paging.mws.power.Goggles;
import dev.paging.mws.power.Grid;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import java.util.List;

/**
 * Double-conversion UPS. Terminals 0/1 are a charger (a resistive load on the mains), terminals
 * 2/3 are an inverter (a 240V voltage source) fed from the battery. The load never sees the mains
 * directly, so a blackout upstream is invisible to the servers until the battery runs dry.
 */
public class UpsBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation {
    private static final float INVERTER_RESISTANCE = 0.05f;
    private static final double IDLE_RESISTANCE = 1.0E6;
    private static final int OVERLOAD_TICKS = 40;
    private static final int BEEP_INTERVAL = 40;

    // These three are assigned in buildCircuit, which Power Grid calls from inside the super
    // constructor, so they must not have field initializers (those would run afterwards).
    private ElectricWire charger;
    private VoltageSourceCoupling inverter;
    private double chargerResistance;

    /** Stored energy, joules. */
    private double energy;
    private boolean outputEnabled = true;
    private boolean tripped;
    private int overloadTicks;
    private int beepTicks;
    private UpsMode mode = UpsMode.OFF;

    private double inputVoltage;
    private double inputPower;
    private double outputVoltage;
    private double outputPower;

    public UpsBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    public static double capacityJoules() {
        return Config.UPS_CAPACITY.get() * 3600.0;
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(4);
        if (chargerResistance <= 0)
            chargerResistance = IDLE_RESISTANCE;
        charger = builder.connect((float) chargerResistance, builder.terminalNode(0), builder.terminalNode(1));
        inverter = new VoltageSourceCoupling(builder.terminalNode(2), builder.terminalNode(3), INVERTER_RESISTANCE, 0f);
        builder.add(inverter);
    }

    @Override
    public void electricalTick() {
        if (charger == null || inverter == null)
            return;
        applyPower(charger);

        inputVoltage = Math.abs(charger.potentialDifference());
        inputPower = Math.abs(charger.power());
        // Positive current out of the source means the load is drawing from us.
        outputPower = Math.max(0, -inverter.getCurrent() * inverter.getVoltage());
        outputVoltage = Math.abs(inverter.getPositive().getVoltage() - (inverter.getNegative() == null ? 0 : inverter.getNegative().getVoltage()));

        boolean mainsGood = Grid.within(inputVoltage, Config.UPS_INPUT_TOLERANCE.get());
        double efficiency = Config.UPS_EFFICIENCY.get();
        double capacity = capacityJoules();
        if (mainsGood)
            energy += inputPower * Grid.TICK * efficiency;
        energy = Math.min(capacity, Math.max(0, energy - outputPower * Grid.TICK));

        if (outputPower > Config.UPS_MAX_OUTPUT.get()) {
            if (++overloadTicks >= OVERLOAD_TICKS) {
                tripped = true;
                outputEnabled = false;
                level.playSound(null, worldPosition, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 1f, 1f);
            }
        } else {
            overloadTicks = 0;
        }

        if (!outputEnabled)
            mode = tripped ? UpsMode.OVERLOAD : UpsMode.OFF;
        else if (energy <= 0)
            mode = UpsMode.DEPLETED;
        else
            mode = mainsGood ? UpsMode.ONLINE : UpsMode.BATTERY;

        inverter.setVoltage(outputEnabled && energy > 0 ? Grid.nominal() : 0);
        updateCharger(mainsGood, capacity - energy, efficiency);
        tickAlarm();
        updateBlockState();
        setChanged();
    }

    /** Draw enough from the mains to cover the load plus charging, sized for the voltage we see. */
    private void updateCharger(boolean mainsGood, double missing, double efficiency) {
        double resistance = IDLE_RESISTANCE;
        if (mainsGood) {
            double charge = Math.min(Config.UPS_CHARGE_RATE.get(), missing / Grid.TICK);
            double wanted = (charge + outputPower) / efficiency;
            if (wanted > 1) {
                double max = (Config.UPS_CHARGE_RATE.get() + Config.UPS_MAX_OUTPUT.get()) / efficiency;
                resistance = Math.max(inputVoltage * inputVoltage / wanted, Grid.resistanceFor(max) * 0.5);
            }
        }
        if (Math.abs(resistance - chargerResistance) / chargerResistance < 0.02)
            return;
        chargerResistance = resistance;
        charger.setResistance(resistance);
    }

    private void tickAlarm() {
        if (mode != UpsMode.BATTERY && mode != UpsMode.OVERLOAD) {
            beepTicks = 0;
            return;
        }
        if (beepTicks-- > 0)
            return;
        beepTicks = BEEP_INTERVAL;
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.7f, mode == UpsMode.OVERLOAD ? 0.5f : 1.8f);
    }

    private void updateBlockState() {
        var state = getBlockState();
        if (!(state.getBlock() instanceof UpsBlock))
            return;
        int bars = energy <= 0 ? 0 : Math.min(4, 1 + (int) (getChargeFraction() * 4));
        var next = state.setValue(UpsBlock.MODE, mode).setValue(UpsBlock.CHARGE, bars);
        if (next != state)
            level.setBlock(worldPosition, next, 3);
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level != null && !level.isClientSide)
            sendData();
    }

    @Override
    public boolean isNoisy() {
        return false;
    }

    public void setOutputEnabled(boolean enabled) {
        outputEnabled = enabled;
        if (enabled) {
            tripped = false;
            overloadTicks = 0;
        }
        setChanged();
    }

    public boolean isOutputEnabled() {
        return outputEnabled;
    }

    public double getEnergy() {
        return energy;
    }

    public double getChargeFraction() {
        return energy / capacityJoules();
    }

    public UpsMode getMode() {
        return mode;
    }

    public double getInputVoltage() {
        return inputVoltage;
    }

    public double getInputPower() {
        return inputPower;
    }

    public double getOutputVoltage() {
        return outputVoltage;
    }

    public double getOutputPower() {
        return outputPower;
    }

    /** Seconds of runtime left at the present load, or -1 when idle. */
    public double getRuntimeSeconds() {
        return outputPower < 1 ? -1 : energy / outputPower;
    }

    private UpsPeripheral peripheral;

    public UpsPeripheral peripheral() {
        if (peripheral == null)
            peripheral = new UpsPeripheral(this);
        return peripheral;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putDouble("Energy", energy);
        tag.putBoolean("OutputEnabled", outputEnabled);
        tag.putBoolean("Tripped", tripped);
        tag.putString("Mode", mode.getSerializedName());
        tag.putDouble("InputVoltage", inputVoltage);
        tag.putDouble("InputPower", inputPower);
        tag.putDouble("OutputVoltage", outputVoltage);
        tag.putDouble("OutputPower", outputPower);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        energy = tag.getDouble("Energy");
        outputEnabled = !tag.contains("OutputEnabled") || tag.getBoolean("OutputEnabled");
        tripped = tag.getBoolean("Tripped");
        mode = UpsMode.byName(tag.getString("Mode"));
        inputVoltage = tag.getDouble("InputVoltage");
        inputPower = tag.getDouble("InputPower");
        outputVoltage = tag.getDouble("OutputVoltage");
        outputPower = tag.getDouble("OutputPower");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Goggles.title(tooltip, "ups");
        Goggles.line(tooltip, "status", Component.translatable("mws.ups_mode." + mode.getSerializedName()), mode.color);
        Goggles.line(tooltip, "charge", String.format("%.1f%% (%.0f / %.0f Wh)", getChargeFraction() * 100, energy / 3600, Config.UPS_CAPACITY.get()), ChatFormatting.AQUA);
        Goggles.line(tooltip, "input", String.format("%.1f V, %.0f W", inputVoltage, inputPower),
                Grid.within(inputVoltage, Config.UPS_INPUT_TOLERANCE.get()) ? ChatFormatting.GREEN : ChatFormatting.RED);
        Goggles.line(tooltip, "output", String.format("%.1f V, %.0f W", outputVoltage, outputPower), ChatFormatting.GREEN);
        double runtime = getRuntimeSeconds();
        if (runtime >= 0)
            Goggles.line(tooltip, "runtime", String.format("%d:%02d", (int) runtime / 60, (int) runtime % 60), ChatFormatting.WHITE);
        return true;
    }
}
