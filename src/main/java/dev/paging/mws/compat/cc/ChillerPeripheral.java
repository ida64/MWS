package dev.paging.mws.compat.cc;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.paging.mws.chiller.ChillerBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** {@code mws_chiller}: cooling load and tank levels. */
public class ChillerPeripheral implements IPeripheral {
    private final ChillerBlockEntity chiller;

    public ChillerPeripheral(ChillerBlockEntity chiller) {
        this.chiller = chiller;
    }

    @Override
    public String getType() {
        return "mws_chiller";
    }

    @Override
    public Object getTarget() {
        return chiller;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof ChillerPeripheral that && that.chiller == chiller;
    }

    @LuaFunction(mainThread = true)
    public final String getStatus() {
        return chiller.getStatus().id();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getInfo() {
        var info = new HashMap<String, Object>();
        info.put("status", chiller.getStatus().id());
        info.put("voltage", chiller.getVoltage());
        info.put("power", chiller.getPower());
        info.put("coolingLoad", chiller.getCoolingLoad());
        info.put("racks", chiller.getRackCount());
        info.put("water", chiller.getWater().getFluidAmount());
        info.put("waterCapacity", chiller.getWater().getCapacity());
        info.put("sludge", chiller.getSludge().getFluidAmount());
        info.put("sludgeCapacity", chiller.getSludge().getCapacity());
        return info;
    }

    @LuaFunction(mainThread = true)
    public final int getWater() {
        return chiller.getWater().getFluidAmount();
    }

    @LuaFunction(mainThread = true)
    public final int getSludge() {
        return chiller.getSludge().getFluidAmount();
    }

    @LuaFunction(mainThread = true)
    public final double getCoolingLoad() {
        return chiller.getCoolingLoad();
    }
}
