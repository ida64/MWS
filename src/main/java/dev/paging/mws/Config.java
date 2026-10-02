package dev.paging.mws;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Electrical standard shared by every MWS machine").push("grid");
    }

    public static final ModConfigSpec.DoubleValue NOMINAL_VOLTAGE = BUILDER
            .comment("Standard server input voltage. Every MWS machine is rated for this.")
            .defineInRange("nominalVoltage", 240.0, 1.0, 100000.0);
    public static final ModConfigSpec.DoubleValue BOOT_TOLERANCE = BUILDER
            .comment("A server only starts booting when its input is within this fraction of nominal (0.05 = 228-252V).")
            .defineInRange("bootTolerance", 0.05, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue RUN_TOLERANCE = BUILDER
            .comment("A running machine crashes when its input leaves this fraction of nominal (0.15 = 204-276V).")
            .defineInRange("runTolerance", 0.15, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue SURGE_VOLTAGE = BUILDER
            .comment("Any voltage at or above this destroys the blades in a server rack.")
            .defineInRange("surgeVoltage", 300.0, 1.0, 100000.0);

    static {
        BUILDER.pop().push("serverRack");
    }

    public static final ModConfigSpec.DoubleValue BLADE_POWER = BUILDER
            .comment("Power drawn by each running server blade, in watts.")
            .defineInRange("bladePower", 500.0, 1.0, 1000000.0);
    public static final ModConfigSpec.DoubleValue RACK_STANDBY_POWER = BUILDER
            .comment("Power drawn by a rack that is not running (fans, management controller), in watts.")
            .defineInRange("standbyPower", 25.0, 0.1, 1000000.0);
    public static final ModConfigSpec.IntValue BOOT_TICKS = BUILDER
            .comment("How long a rack needs clean power before it comes online, in ticks.")
            .defineInRange("bootTicks", 100, 1, 72000);
    public static final ModConfigSpec.DoubleValue RACK_THERMAL_MASS = BUILDER
            .comment("Heat needed to raise a rack by one degree, in joules per kelvin.")
            .defineInRange("thermalMass", 4000.0, 1.0, 1.0E9);
    public static final ModConfigSpec.DoubleValue RACK_PASSIVE_COOLING = BUILDER
            .comment("Heat a rack sheds to the room on its own, in watts per kelvin above ambient. Enough for one blade; two throttle, three need a chiller.")
            .defineInRange("passiveCooling", 12.0, 0.0, 1.0E6);
    public static final ModConfigSpec.DoubleValue THROTTLE_TEMPERATURE = BUILDER
            .comment("Above this temperature (C) the rack throttles its blades.")
            .defineInRange("throttleTemperature", 70.0, -273.0, 10000.0);
    public static final ModConfigSpec.DoubleValue SHUTDOWN_TEMPERATURE = BUILDER
            .comment("At this temperature (C) the rack performs an emergency thermal shutdown.")
            .defineInRange("shutdownTemperature", 85.0, -273.0, 10000.0);
    public static final ModConfigSpec.DoubleValue RESTART_TEMPERATURE = BUILDER
            .comment("A rack refuses to boot until it has cooled below this temperature (C).")
            .defineInRange("restartTemperature", 45.0, -273.0, 10000.0);
    public static final ModConfigSpec.DoubleValue MELTDOWN_TEMPERATURE = BUILDER
            .comment("Above this temperature (C) blades start melting, one every five seconds.")
            .defineInRange("meltdownTemperature", 110.0, -273.0, 10000.0);
    public static final ModConfigSpec.IntValue STORAGE_PER_BLADE = BUILDER
            .comment("Bytes of object storage each blade contributes to the rack's bucket.")
            .defineInRange("storagePerBlade", 65536, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue CORRUPTION_CHANCE = BUILDER
            .comment("Chance that an unclean shutdown (power loss while online) corrupts a stored object.")
            .defineInRange("corruptionChance", 0.25, 0.0, 1.0);

    static {
        BUILDER.pop().push("ups");
    }

    public static final ModConfigSpec.DoubleValue UPS_CAPACITY = BUILDER
            .comment("Battery capacity of a UPS, in watt-hours.")
            .defineInRange("capacity", 1000.0, 1.0, 1.0E9);
    public static final ModConfigSpec.DoubleValue UPS_MAX_OUTPUT = BUILDER
            .comment("Output power a UPS can supply before its overload protection trips, in watts.")
            .defineInRange("maxOutput", 4000.0, 1.0, 1.0E9);
    public static final ModConfigSpec.DoubleValue UPS_CHARGE_RATE = BUILDER
            .comment("Maximum battery charging power, in watts.")
            .defineInRange("chargeRate", 1000.0, 1.0, 1.0E9);
    public static final ModConfigSpec.DoubleValue UPS_EFFICIENCY = BUILDER
            .comment("Fraction of input energy that makes it into the battery and out of the inverter.")
            .defineInRange("efficiency", 0.9, 0.01, 1.0);
    public static final ModConfigSpec.DoubleValue UPS_INPUT_TOLERANCE = BUILDER
            .comment("Input voltage window, as a fraction of nominal, outside which the UPS runs on battery.")
            .defineInRange("inputTolerance", 0.2, 0.0, 1.0);

    static {
        BUILDER.pop().push("chiller");
    }

    public static final ModConfigSpec.DoubleValue CHILLER_CAPACITY = BUILDER
            .comment("Heat a chiller can pull out of nearby racks, in watts.")
            .defineInRange("coolingCapacity", 4000.0, 1.0, 1.0E9);
    public static final ModConfigSpec.DoubleValue CHILLER_POWER = BUILDER
            .comment("Electrical power of the chiller compressor at full load, in watts.")
            .defineInRange("compressorPower", 1200.0, 1.0, 1.0E9);
    public static final ModConfigSpec.IntValue CHILLER_RANGE = BUILDER
            .comment("Racks within this many blocks (cube) of a chiller are cooled by it.")
            .defineInRange("range", 6, 1, 32);
    public static final ModConfigSpec.DoubleValue CHILLER_TARGET = BUILDER
            .comment("Temperature (C) the chiller tries to hold racks at.")
            .defineInRange("targetTemperature", 30.0, -273.0, 10000.0);
    public static final ModConfigSpec.DoubleValue JOULES_PER_MB = BUILDER
            .comment("Heat removed per millibucket of water evaporated. Lower means thirstier chillers.")
            .defineInRange("joulesPerMb", 200.0, 0.001, 1.0E9);
    public static final ModConfigSpec.DoubleValue SLUDGE_RATIO = BUILDER
            .comment("Millibuckets of sludge produced per millibucket of water consumed.")
            .defineInRange("sludgeRatio", 0.25, 0.0, 100.0);
    public static final ModConfigSpec.IntValue CHILLER_TANK = BUILDER
            .comment("Capacity of the chiller's water and sludge tanks, in millibuckets. A full sludge tank vents into the world.")
            .defineInRange("tankCapacity", 8000, 1000, Integer.MAX_VALUE);

    static {
        BUILDER.pop().push("waterIntake");
    }

    public static final ModConfigSpec.DoubleValue INTAKE_POWER = BUILDER
            .comment("Electrical power of the intake pump while pumping, in watts.")
            .defineInRange("pumpPower", 400.0, 1.0, 1.0E9);
    public static final ModConfigSpec.IntValue INTAKE_INTERVAL = BUILDER
            .comment("Ticks between each water source block the intake steals.")
            .defineInRange("interval", 20, 1, 72000);
    public static final ModConfigSpec.IntValue INTAKE_RANGE = BUILDER
            .comment("How far (in blocks, through connected water) the intake will reach to steal a source block.")
            .defineInRange("range", 16, 1, 64);
    public static final ModConfigSpec.IntValue INTAKE_TANK = BUILDER
            .comment("Capacity of the intake's buffer tank, in millibuckets.")
            .defineInRange("tankCapacity", 8000, 1000, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
