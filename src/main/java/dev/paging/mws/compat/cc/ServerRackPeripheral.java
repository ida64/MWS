package dev.paging.mws.compat.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.paging.mws.rack.RackStatus;
import dev.paging.mws.rack.ServerRackBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code mws_server_rack}: one peripheral per cabinet (stack of racks), reachable from any rack in
 * it. Health telemetry for both power feeds and every server, plus the cabinet's object store,
 * which is reachable while at least one server is online.
 */
public class ServerRackPeripheral implements IPeripheral {
    private final ServerRackBlockEntity base;

    public ServerRackPeripheral(ServerRackBlockEntity base) {
        this.base = base;
    }

    @Override
    public String getType() {
        return "mws_server_rack";
    }

    @Override
    public Object getTarget() {
        return base;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof ServerRackPeripheral that && that.base == base;
    }

    private void requireServing() throws LuaException {
        if (!base.isServing())
            throw new LuaException("503 Service Unavailable (no server in this cabinet is online)");
    }

    private static Map<String, Object> feed(ServerRackBlockEntity.Feed feed) {
        var info = new HashMap<String, Object>();
        info.put("voltage", feed.voltage);
        info.put("power", feed.power);
        info.put("ok", feed.ok());
        return info;
    }

    // ---- Health ----

    /** "online" (every server with blades is up), "degraded" (some are), "booting", or "offline". */
    @LuaFunction(mainThread = true)
    public final String getStatus() {
        int bladed = 0, online = 0, booting = 0;
        for (var rack : base.members()) {
            if (rack.getBlades() == 0)
                continue;
            ++bladed;
            if (rack.getStatus() == RackStatus.ONLINE)
                ++online;
            else if (rack.getStatus() == RackStatus.BOOTING)
                ++booting;
        }
        if (bladed > 0 && online == bladed)
            return "online";
        if (online > 0)
            return "degraded";
        return booting > 0 ? "booting" : "offline";
    }

    @LuaFunction(mainThread = true)
    public final boolean isOnline() {
        return base.isServing();
    }

    /** Both feeds healthy. */
    @LuaFunction(mainThread = true)
    public final boolean isRedundant() {
        return base.isRedundant();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getFeeds() {
        return Map.of("a", feed(base.feedA()), "b", feed(base.feedB()));
    }

    /** Every server in the cabinet, bottom first. */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getServers() {
        var servers = new ArrayList<Map<String, Object>>();
        for (var rack : base.members()) {
            var info = new HashMap<String, Object>();
            info.put("status", rack.getStatus().getSerializedName());
            info.put("temperature", rack.getTemperature());
            info.put("throttled", rack.isThrottled());
            info.put("blades", rack.getBlades());
            info.put("power", rack.getPower());
            info.put("uptime", rack.getUptimeSeconds());
            info.put("bootProgress", rack.getBootProgress());
            info.put("dirtyShutdowns", rack.getDirtyShutdowns());
            servers.add(info);
        }
        return servers;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getInfo() {
        var info = new HashMap<String, Object>();
        info.put("status", getStatus());
        info.put("units", base.getUnits());
        info.put("onlineUnits", base.getOnlineUnits());
        info.put("blades", getBladeCount());
        info.put("power", base.getCabinetPower());
        info.put("temperature", getTemperature());
        info.put("redundant", base.isRedundant());
        info.put("feeds", getFeeds());
        info.put("requests", base.getRequests());
        info.put("usedBytes", base.getUsedBytes());
        info.put("capacityBytes", base.cabinetCapacityBytes());
        return info;
    }

    /** Highest healthy feed voltage (what the servers are running on). */
    @LuaFunction(mainThread = true)
    public final double getVoltage() {
        var a = base.feedA();
        var b = base.feedB();
        if (a.ok() != b.ok())
            return a.ok() ? a.voltage : b.voltage;
        return Math.max(a.voltage, b.voltage);
    }

    @LuaFunction(mainThread = true)
    public final double getPower() {
        return base.getCabinetPower();
    }

    /** Hottest server in the cabinet. */
    @LuaFunction(mainThread = true)
    public final double getTemperature() {
        double max = Double.NEGATIVE_INFINITY;
        for (var rack : base.members())
            max = Math.max(max, rack.getTemperature());
        return max;
    }

    @LuaFunction(mainThread = true)
    public final int getBladeCount() {
        int total = 0;
        for (var rack : base.members())
            total += rack.getBlades();
        return total;
    }

    // ---- Object storage ----

    @LuaFunction(mainThread = true)
    public final void put(String key, String value) throws LuaException {
        requireServing();
        if (!base.putObject(key, value))
            throw new LuaException("507 Insufficient Storage");
    }

    @LuaFunction(mainThread = true)
    public final @Nullable String get(String key) throws LuaException {
        requireServing();
        return base.getObject(key);
    }

    @LuaFunction(mainThread = true)
    public final boolean delete(String key) throws LuaException {
        requireServing();
        return base.deleteObject(key);
    }

    @LuaFunction(mainThread = true)
    public final List<String> list() throws LuaException {
        requireServing();
        return base.listObjects();
    }

    @LuaFunction(mainThread = true)
    public final long getUsedBytes() {
        return base.getUsedBytes();
    }

    /** Capacity contributed by the servers that are online right now. */
    @LuaFunction(mainThread = true)
    public final long getCapacityBytes() {
        return base.cabinetCapacityBytes();
    }
}
