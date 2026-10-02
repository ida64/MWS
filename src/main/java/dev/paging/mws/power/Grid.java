package dev.paging.mws.power;

import dev.paging.mws.Config;

/**
 * The 240V server power standard. All MWS machines are resistive loads (or sources) sized
 * against the nominal voltage, so a machine wired to the wrong voltage really does draw
 * the wrong power.
 */
public final class Grid {
    /** Seconds per game tick. */
    public static final double TICK = 0.05;
    /** Room temperature in degrees Celsius. */
    public static final double AMBIENT = 25.0;

    private Grid() {
    }

    public static double nominal() {
        return Config.NOMINAL_VOLTAGE.get();
    }

    public static boolean within(double voltage, double tolerance) {
        double nominal = nominal();
        return Math.abs(voltage - nominal) <= nominal * tolerance;
    }

    /** Clean enough to start a server. */
    public static boolean bootable(double voltage) {
        return within(voltage, Config.BOOT_TOLERANCE.get());
    }

    /** Clean enough to keep a running machine alive. */
    public static boolean runnable(double voltage) {
        return within(voltage, Config.RUN_TOLERANCE.get());
    }

    /** Resistance that draws {@code watts} when fed with nominal voltage. */
    public static double resistanceFor(double watts) {
        double nominal = nominal();
        return nominal * nominal / Math.max(watts, 0.01);
    }
}
