package dev.paging.mws.rack;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.paging.mws.Config;
import dev.paging.mws.client.FanSounds;
import dev.paging.mws.compat.cc.ServerRackPeripheral;
import dev.paging.mws.power.Goggles;
import dev.paging.mws.power.Grid;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One server chassis with up to four blades. Racks stacked vertically form a cabinet: the bottom
 * rack (the base) owns two independent power feeds, A and B, sized for the whole cabinet's load
 * and shared between whichever feeds are healthy, so losing one feed never takes a server down.
 * The base also holds the cabinet's object store, served to ComputerCraft while any server is up.
 *
 * <p>Every rack still turns its share of the power into heat and runs its own boot / crash /
 * thermal state machine.
 */
public class ServerRackBlockEntity extends ElectricBlockEntity implements IHaveGoggleInformation {
    public static final int MAX_BLADES = 4;
    public static final int MAX_CABINET_HEIGHT = 32;
    private static final int MELT_INTERVAL = 100;
    private static final double IDLE_RESISTANCE = 1.0E6;

    // Assigned in buildCircuit, which Power Grid calls from inside the super constructor:
    // these must not have field initializers.
    private ElectricWire feedA;
    private ElectricWire feedB;
    private double resistanceA;
    private double resistanceB;

    // ---- This unit ----
    private int blades;
    private RackStatus status = RackStatus.OFF;
    private double temperature = Grid.AMBIENT;
    private int bootTicks;
    private int meltTicks;
    private long uptimeTicks;
    private int dirtyShutdowns;
    /** Watts this unit wants at nominal voltage. */
    private double requested;
    /** Watts this unit actually got (its share of the cabinet's draw). */
    private double power;

    // ---- Cabinet (authoritative on the base, mirrored on members for goggles) ----
    private final Feed a = new Feed();
    private final Feed b = new Feed();
    private int units = 1;
    private int onlineUnits;
    private double cabinetPower;
    private double cabinetRequested;
    private long cabinetCapacity;
    private long usedBytes;
    private long requests;
    private final Map<String, String> objects = new LinkedHashMap<>();

    private boolean wasBase = true;
    private BlockPos lastBase;
    private ServerRackPeripheral peripheral;

    /** One power inlet as seen at the cabinet base. */
    public static final class Feed {
        public double voltage;
        public double power;

        public boolean ok() {
            return Grid.runnable(voltage);
        }

        void copy(Feed other) {
            voltage = other.voltage;
            power = other.power;
        }
    }

    public ServerRackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(4);
        if (resistanceA <= 0)
            resistanceA = IDLE_RESISTANCE;
        if (resistanceB <= 0)
            resistanceB = IDLE_RESISTANCE;
        feedA = builder.connect((float) resistanceA, builder.terminalNode(0), builder.terminalNode(1));
        feedB = builder.connect((float) resistanceB, builder.terminalNode(2), builder.terminalNode(3));
    }

    // ---------------------------------------------------------------- Cabinet structure ----

    public boolean isBase() {
        var state = getBlockState();
        return !(state.getBlock() instanceof ServerRackBlock) || ServerRackBlock.isBase(state);
    }

    /** The bottom rack of this cabinet (possibly this one). */
    public ServerRackBlockEntity base() {
        var pos = worldPosition;
        ServerRackBlockEntity base = this;
        for (int i = 0; i < MAX_CABINET_HEIGHT; ++i) {
            pos = pos.below();
            if (!(level.getBlockEntity(pos) instanceof ServerRackBlockEntity below))
                break;
            base = below;
        }
        return base;
    }

    /** Every rack in this cabinet, bottom first. Call on the base. */
    public List<ServerRackBlockEntity> members() {
        var members = new ArrayList<ServerRackBlockEntity>();
        var pos = worldPosition;
        for (int i = 0; i < MAX_CABINET_HEIGHT && level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack; ++i) {
            members.add(rack);
            pos = pos.above();
        }
        return members;
    }

    /** This rack just had another stacked under it: its inlets are gone, so are the wires on them. */
    private void onDemoted(ServerRackBlockEntity newBase) {
        if (electricBehaviour != null) {
            for (var wire : GlobalElectricNetworks.getWorldNetworks(level).findConnectedWires(electricBehaviour)) {
                if (wire.owner != null)
                    wire.owner.kill();
                else
                    wire.remove();
            }
        }
        handOffStorage(newBase);
        setResistance(IDLE_RESISTANCE, IDLE_RESISTANCE);
    }

    /** Move this cabinet's stored objects into another rack, which becomes (or already is) the base. */
    public void handOffStorage(ServerRackBlockEntity target) {
        if (target == this || objects.isEmpty())
            return;
        objects.forEach(target.objects::putIfAbsent);
        target.recountBytes();
        target.requests += requests;
        objects.clear();
        recountBytes();
        target.setChanged();
        setChanged();
    }

    // ------------------------------------------------------------------------- Ticking ----

    @Override
    public void electricalTick() {
        if (feedA == null || feedB == null)
            return;
        applyPower(feedA);
        applyPower(feedB);

        var base = base();
        boolean isBase = base == this;
        if (wasBase && !isBase)
            onDemoted(base);
        wasBase = isBase;
        if (!base.worldPosition.equals(lastBase)) {
            // The peripheral handed to computers is the cabinet's; recompute it after any restack.
            lastBase = base.worldPosition;
            level.invalidateCapabilities(worldPosition);
        }

        if (isBase) {
            a.voltage = Math.abs(feedA.potentialDifference());
            a.power = Math.abs(feedA.power());
            b.voltage = Math.abs(feedB.potentialDifference());
            b.power = Math.abs(feedB.power());
        } else if (!objects.isEmpty()) {
            handOffStorage(base);
        }

        tickUnit(base);
        if (isBase)
            tickCabinet();
        else
            mirror(base);

        updateBlockState();
        setChanged();
    }

    private void tickUnit(ServerRackBlockEntity base) {
        // Each unit gets its share of what the cabinet actually drew, and all of it becomes heat.
        power = base.cabinetRequested > 0 ? requested * base.cabinetPower / base.cabinetRequested : 0;
        double leak = Config.RACK_PASSIVE_COOLING.get() * (temperature - Grid.AMBIENT);
        temperature += (power - leak) * Grid.TICK / Config.RACK_THERMAL_MASS.get();

        double surge = Config.SURGE_VOLTAGE.get();
        if ((base.a.voltage >= surge || base.b.voltage >= surge) && blades > 0)
            fry(base);
        tickMeltdown();

        // Dual PSUs: the server lives as long as either feed is healthy.
        boolean powered = base.a.ok() || base.b.ok();
        boolean bootable = Grid.bootable(base.a.voltage) || Grid.bootable(base.b.voltage);
        switch (status) {
            case OFF, FAULT -> {
                if (blades > 0 && bootable && temperature < Config.RESTART_TEMPERATURE.get())
                    setStatus(RackStatus.BOOTING);
            }
            case BOOTING -> {
                if (blades == 0 || !powered)
                    setStatus(RackStatus.OFF);
                else if (++bootTicks >= Config.BOOT_TICKS.get())
                    setStatus(RackStatus.ONLINE);
            }
            case ONLINE -> {
                if (blades == 0)
                    setStatus(RackStatus.OFF);
                else if (!powered)
                    crash(base);
                else if (temperature >= Config.SHUTDOWN_TEMPERATURE.get())
                    setStatus(RackStatus.OVERHEATED);
                else
                    ++uptimeTicks;
            }
            case OVERHEATED -> {
                if (temperature < Config.RESTART_TEMPERATURE.get())
                    setStatus(RackStatus.OFF);
            }
        }
        requested = Config.RACK_STANDBY_POWER.get() + blades * Config.BLADE_POWER.get() * loadFactor();
    }

    /** Base only: total up the cabinet and size both feeds for it. */
    private void tickCabinet() {
        var members = members();
        units = members.size();
        onlineUnits = 0;
        long capacity = 0;
        double total = 0;
        for (var member : members) {
            total += member.requested;
            if (member.status == RackStatus.ONLINE) {
                ++onlineUnits;
                capacity += member.capacityBytes();
            }
        }
        cabinetRequested = total;
        cabinetPower = a.power + b.power;
        cabinetCapacity = capacity;
        if (members.size() != lastMembers) {
            // Computers attach to every rack; tell them the cabinet changed.
            lastMembers = members.size();
            for (var member : members)
                level.invalidateCapabilities(member.worldPosition);
        }

        // Share the load between healthy feeds; with neither healthy, draw from whatever is live.
        boolean liveA = a.ok() || (!b.ok() && a.voltage > 1);
        boolean liveB = b.ok() || (!a.ok() && b.voltage > 1);
        int live = (liveA ? 1 : 0) + (liveB ? 1 : 0);
        double share = live == 0 ? 0 : total / live;
        setResistance(liveA ? Grid.resistanceFor(share) : IDLE_RESISTANCE, liveB ? Grid.resistanceFor(share) : IDLE_RESISTANCE);
    }

    private int lastMembers;

    private void setResistance(double ra, double rb) {
        if (Math.abs(ra - resistanceA) / resistanceA > 0.01) {
            resistanceA = ra;
            feedA.setResistance(ra);
        }
        if (Math.abs(rb - resistanceB) / resistanceB > 0.01) {
            resistanceB = rb;
            feedB.setResistance(rb);
        }
    }

    /** Members copy the cabinet's numbers so their goggles show them. */
    private void mirror(ServerRackBlockEntity base) {
        a.copy(base.a);
        b.copy(base.b);
        units = base.units;
        onlineUnits = base.onlineUnits;
        cabinetPower = base.cabinetPower;
        cabinetCapacity = base.cabinetCapacity;
        usedBytes = base.usedBytes;
    }

    private double loadFactor() {
        return switch (status) {
            case ONLINE -> isThrottled() ? 0.6 : 1.0;
            case BOOTING -> 0.5;
            default -> 0.0;
        };
    }

    public boolean isThrottled() {
        return status == RackStatus.ONLINE && temperature >= Config.THROTTLE_TEMPERATURE.get();
    }

    private void setStatus(RackStatus next) {
        if (next == RackStatus.BOOTING)
            bootTicks = 0;
        if (next != RackStatus.ONLINE)
            uptimeTicks = 0;
        status = next;
    }

    /** Both feeds went bad under a running server: an unclean shutdown that may eat cabinet data. */
    private void crash(ServerRackBlockEntity base) {
        setStatus(RackStatus.FAULT);
        ++dirtyShutdowns;
        if (!base.objects.isEmpty() && level.random.nextDouble() < Config.CORRUPTION_CHANCE.get()) {
            var keys = new ArrayList<>(base.objects.keySet());
            base.deleteObject(keys.get(level.random.nextInt(keys.size())));
        }
        level.playSound(null, worldPosition, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.8f, 0.6f);
    }

    /** Overvoltage lets the magic smoke out of every blade, and takes the cabinet's data with it. */
    private void fry(ServerRackBlockEntity base) {
        blades = 0;
        base.objects.clear();
        base.recountBytes();
        setStatus(RackStatus.FAULT);
        var center = worldPosition.getCenter();
        level.explode(null, center.x, center.y, center.z, 1.0f, Level.ExplosionInteraction.NONE);
        if (level instanceof ServerLevel server)
            server.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 0.6, center.z, 20, 0.3, 0.3, 0.3, 0.02);
    }

    private void tickMeltdown() {
        if (blades == 0 || temperature < Config.MELTDOWN_TEMPERATURE.get()) {
            meltTicks = 0;
            return;
        }
        if (++meltTicks < MELT_INTERVAL)
            return;
        meltTicks = 0;
        --blades;
        var center = worldPosition.getCenter();
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 0.5f);
        if (level instanceof ServerLevel server)
            server.sendParticles(ParticleTypes.SMOKE, center.x, center.y + 0.6, center.z, 12, 0.3, 0.2, 0.3, 0.01);
    }

    private void updateBlockState() {
        var state = getBlockState();
        if (!(state.getBlock() instanceof ServerRackBlock))
            return;
        var next = state.setValue(ServerRackBlock.STATUS, status).setValue(ServerRackBlock.BLADES, blades);
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

    /** Client only (Power Grid strips this on dedicated servers): keep the fan loop running. */
    @Override
    public void tickAudio() {
        FanSounds.tick(this);
    }

    /** How hard this unit's fans are spinning, 0 (stopped) to 1 (flat out). */
    public float fanSpeed() {
        if (!a.ok() && !b.ok())
            return 0;
        return switch (status) {
            case BOOTING, OVERHEATED -> 1f;        // POST and thermal emergencies run fans at 100%
            case ONLINE -> (float) Math.clamp(0.45 + (temperature - Config.CHILLER_TARGET.get()) / 50.0, 0.45, 1.0);
            default -> 0.25f;
        };
    }

    // -------------------------------------------------------------------- Interaction ----

    /** Remove heat (joules) from this unit; used by chillers. */
    public void removeHeat(double joules) {
        temperature -= joules / Config.RACK_THERMAL_MASS.get();
    }

    /** Heat (joules) that must be removed to bring this unit down to {@code target} degrees. */
    public double heatAbove(double target) {
        return Math.max(0, (temperature - target) * Config.RACK_THERMAL_MASS.get());
    }

    public boolean insertBlade() {
        if (blades >= MAX_BLADES)
            return false;
        ++blades;
        updateBlockState();
        notifyUpdate();
        return true;
    }

    public boolean removeBlade() {
        if (blades <= 0)
            return false;
        --blades;
        updateBlockState();
        notifyUpdate();
        return true;
    }

    // ------------------------------------------- Cabinet object storage (call on the base) ----

    public long capacityBytes() {
        return (long) blades * Config.STORAGE_PER_BLADE.get();
    }

    /** Storage is up while at least one server in the cabinet is. */
    public boolean isServing() {
        return onlineUnits > 0;
    }

    public long cabinetCapacityBytes() {
        return cabinetCapacity;
    }

    private static long sizeOf(String key, String value) {
        return key.getBytes(StandardCharsets.UTF_8).length + value.getBytes(StandardCharsets.UTF_8).length;
    }

    private void recountBytes() {
        usedBytes = 0;
        objects.forEach((key, value) -> usedBytes += sizeOf(key, value));
    }

    /** @return false when the object would not fit in the cabinet's online capacity. */
    public boolean putObject(String key, String value) {
        ++requests;
        var old = objects.get(key);
        long next = usedBytes + sizeOf(key, value) - (old == null ? 0 : sizeOf(key, old));
        if (next > cabinetCapacity)
            return false;
        objects.put(key, value);
        usedBytes = next;
        setChanged();
        return true;
    }

    public String getObject(String key) {
        ++requests;
        return objects.get(key);
    }

    public boolean deleteObject(String key) {
        ++requests;
        var old = objects.remove(key);
        if (old == null)
            return false;
        usedBytes -= sizeOf(key, old);
        setChanged();
        return true;
    }

    public List<String> listObjects() {
        ++requests;
        return new ArrayList<>(objects.keySet());
    }

    public int objectCount() {
        return objects.size();
    }

    // ----------------------------------------------------------------------- Accessors ----

    public int getBlades() {
        return blades;
    }

    public RackStatus getStatus() {
        return status;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getPower() {
        return power;
    }

    public double getUptimeSeconds() {
        return uptimeTicks * Grid.TICK;
    }

    public int getDirtyShutdowns() {
        return dirtyShutdowns;
    }

    public long getRequests() {
        return requests;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public Feed feedA() {
        return a;
    }

    public Feed feedB() {
        return b;
    }

    public boolean isRedundant() {
        return a.ok() && b.ok();
    }

    public int getUnits() {
        return units;
    }

    public int getOnlineUnits() {
        return onlineUnits;
    }

    public double getCabinetPower() {
        return cabinetPower;
    }

    public double getBootProgress() {
        return status == RackStatus.BOOTING ? (double) bootTicks / Config.BOOT_TICKS.get() : status == RackStatus.ONLINE ? 1 : 0;
    }

    /** The cabinet's peripheral, the same object whichever rack a computer touches. */
    public ServerRackPeripheral peripheral() {
        var base = level == null ? this : base();
        if (base.peripheral == null)
            base.peripheral = new ServerRackPeripheral(base);
        return base.peripheral;
    }

    // ------------------------------------------------------------------------- Saving ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Blades", blades);
        tag.putString("Status", status.getSerializedName());
        tag.putDouble("Temperature", temperature);
        tag.putInt("BootTicks", bootTicks);
        tag.putLong("Uptime", uptimeTicks);
        tag.putInt("DirtyShutdowns", dirtyShutdowns);
        tag.putDouble("Power", power);
        tag.putDouble("FeedAVoltage", a.voltage);
        tag.putDouble("FeedAPower", a.power);
        tag.putDouble("FeedBVoltage", b.voltage);
        tag.putDouble("FeedBPower", b.power);
        tag.putInt("Units", units);
        tag.putInt("OnlineUnits", onlineUnits);
        tag.putDouble("CabinetPower", cabinetPower);
        tag.putLong("CabinetCapacity", cabinetCapacity);
        tag.putLong("UsedBytes", usedBytes);
        tag.putLong("Requests", requests);
        if (!clientPacket) {
            var list = new ListTag();
            objects.forEach((key, value) -> {
                var entry = new CompoundTag();
                entry.putString("Key", key);
                entry.putString("Value", value);
                list.add(entry);
            });
            tag.put("Objects", list);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        blades = tag.getInt("Blades");
        status = RackStatus.byName(tag.getString("Status"));
        temperature = tag.contains("Temperature") ? tag.getDouble("Temperature") : Grid.AMBIENT;
        bootTicks = tag.getInt("BootTicks");
        uptimeTicks = tag.getLong("Uptime");
        dirtyShutdowns = tag.getInt("DirtyShutdowns");
        power = tag.getDouble("Power");
        a.voltage = tag.getDouble("FeedAVoltage");
        a.power = tag.getDouble("FeedAPower");
        b.voltage = tag.getDouble("FeedBVoltage");
        b.power = tag.getDouble("FeedBPower");
        units = Math.max(1, tag.getInt("Units"));
        onlineUnits = tag.getInt("OnlineUnits");
        cabinetPower = tag.getDouble("CabinetPower");
        cabinetCapacity = tag.getLong("CabinetCapacity");
        usedBytes = tag.getLong("UsedBytes");
        requests = tag.getLong("Requests");
        if (!clientPacket) {
            objects.clear();
            for (var element : tag.getList("Objects", Tag.TAG_COMPOUND)) {
                var entry = (CompoundTag) element;
                objects.put(entry.getString("Key"), entry.getString("Value"));
            }
        }
    }

    // ------------------------------------------------------------------------ Goggles ----

    private static Component feedLine(Feed feed) {
        if (feed.voltage < 1)
            return Component.translatable("mws.goggles.feed_dead");
        return Component.literal(String.format("%.1f V, %.0f W", feed.voltage, feed.power));
    }

    /** Why a stopped rack is not booting, in the order tickUnit checks; null if it is running or about to boot. */
    private @Nullable Component bootBlocker() {
        if (status == RackStatus.BOOTING || status == RackStatus.ONLINE)
            return null;
        if (blades == 0)
            return Component.translatable("mws.boot_blocked.no_blades");
        double restart = Config.RESTART_TEMPERATURE.get();
        if (temperature >= restart)
            return Component.translatable("mws.boot_blocked.too_hot", String.format("%.0f", restart));
        if (Grid.bootable(a.voltage) || Grid.bootable(b.voltage))
            return null;
        double voltage = Math.abs(a.voltage - Grid.nominal()) <= Math.abs(b.voltage - Grid.nominal()) ? a.voltage : b.voltage;
        if (voltage < 1)
            return Component.translatable("mws.boot_blocked.no_power");
        double window = Grid.nominal() * Config.BOOT_TOLERANCE.get();
        return Component.translatable("mws.boot_blocked.voltage", String.format("%.0f", voltage),
                String.format("%.0f", Grid.nominal() - window), String.format("%.0f", Grid.nominal() + window));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Goggles.title(tooltip, "server_rack");
        Goggles.line(tooltip, "status", Component.translatable("mws.rack_status." + status.getSerializedName()), status.color);
        var blocked = bootBlocker();
        if (blocked != null)
            Goggles.line(tooltip, "boot_blocked", blocked, ChatFormatting.GOLD);
        if (status == RackStatus.BOOTING)
            Goggles.line(tooltip, "boot", String.format("%.0f%%", getBootProgress() * 100), ChatFormatting.GOLD);
        ChatFormatting tempColor = temperature >= Config.SHUTDOWN_TEMPERATURE.get() ? ChatFormatting.RED
                : temperature >= Config.THROTTLE_TEMPERATURE.get() ? ChatFormatting.GOLD : ChatFormatting.AQUA;
        Goggles.line(tooltip, "temperature", isThrottled()
                ? Component.translatable("mws.goggles.throttled", String.format("%.1f", temperature))
                : String.format("%.1f °C", temperature), tempColor);
        Goggles.line(tooltip, "blades", blades + " / " + MAX_BLADES, ChatFormatting.WHITE);
        Goggles.line(tooltip, "power", String.format("%.0f W", power), ChatFormatting.AQUA);
        if (dirtyShutdowns > 0)
            Goggles.line(tooltip, "dirty_shutdowns", dirtyShutdowns, ChatFormatting.RED);

        tooltip.add(Component.literal(""));
        Goggles.line(tooltip, "cabinet", Component.translatable("mws.goggles.cabinet_units", onlineUnits, units), ChatFormatting.WHITE);
        Goggles.line(tooltip, "feed_a", feedLine(a), a.ok() ? ChatFormatting.GREEN : ChatFormatting.RED);
        Goggles.line(tooltip, "feed_b", feedLine(b), b.ok() ? ChatFormatting.GREEN : ChatFormatting.RED);
        String redundancy = isRedundant() ? "redundant" : (a.ok() || b.ok()) ? "degraded" : "down";
        Goggles.line(tooltip, "redundancy", Component.translatable("mws.redundancy." + redundancy),
                isRedundant() ? ChatFormatting.GREEN : (a.ok() || b.ok()) ? ChatFormatting.GOLD : ChatFormatting.RED);
        Goggles.line(tooltip, "storage", String.format("%,d / %,d B", usedBytes, cabinetCapacity), ChatFormatting.WHITE);
        return true;
    }
}
