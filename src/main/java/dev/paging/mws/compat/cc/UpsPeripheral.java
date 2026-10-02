package dev.paging.mws.compat.cc;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.paging.mws.ups.UpsBlockEntity;
import dev.paging.mws.ups.UpsMode;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** {@code mws_ups}: battery telemetry and output control, for graceful-shutdown scripts. */
public class UpsPeripheral implements IPeripheral {
    private final UpsBlockEntity ups;

    public UpsPeripheral(UpsBlockEntity ups) {
        this.ups = ups;
    }

    @Override
    public String getType() {
        return "mws_ups";
    }

    @Override
    public Object getTarget() {
        return ups;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof UpsPeripheral that && that.ups == ups;
    }

    @LuaFunction(mainThread = true)
    public final String getMode() {
        return ups.getMode().getSerializedName();
    }

    @LuaFunction(mainThread = true)
    public final boolean isOnBattery() {
        return ups.getMode() == UpsMode.BATTERY;
    }

    @LuaFunction(mainThread = true)
    public final double getCharge() {
        return ups.getChargeFraction();
    }

    @LuaFunction(mainThread = true)
    public final double getEnergy() {
        return ups.getEnergy();
    }

    @LuaFunction(mainThread = true)
    public final double getCapacity() {
        return UpsBlockEntity.capacityJoules();
    }

    @LuaFunction(mainThread = true)
    public final double getRuntime() {
        return ups.getRuntimeSeconds();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getInfo() {
        var info = new HashMap<String, Object>();
        info.put("mode", ups.getMode().getSerializedName());
        info.put("charge", ups.getChargeFraction());
        info.put("energy", ups.getEnergy());
        info.put("capacity", UpsBlockEntity.capacityJoules());
        info.put("inputVoltage", ups.getInputVoltage());
        info.put("inputPower", ups.getInputPower());
        info.put("outputVoltage", ups.getOutputVoltage());
        info.put("outputPower", ups.getOutputPower());
        info.put("outputEnabled", ups.isOutputEnabled());
        info.put("runtime", ups.getRuntimeSeconds());
        return info;
    }

    @LuaFunction(mainThread = true)
    public final boolean isOutputEnabled() {
        return ups.isOutputEnabled();
    }

    /** Switch the output; enabling also resets a tripped overload. */
    @LuaFunction(mainThread = true)
    public final void setOutputEnabled(boolean enabled) {
        ups.setOutputEnabled(enabled);
    }
}
