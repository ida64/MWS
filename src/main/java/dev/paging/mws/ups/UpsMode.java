package dev.paging.mws.ups;

import net.minecraft.ChatFormatting;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum UpsMode implements StringRepresentable {
    /** Output switched off by hand or by a computer. */
    OFF(ChatFormatting.GRAY),
    /** Mains is good: the load runs from the inverter while the charger tops up the battery. */
    ONLINE(ChatFormatting.GREEN),
    /** Mains is gone or dirty: running from the battery, beeping. */
    BATTERY(ChatFormatting.GOLD),
    /** Battery flat, output dead. */
    DEPLETED(ChatFormatting.RED),
    /** Overload protection tripped; output off until reset. */
    OVERLOAD(ChatFormatting.DARK_RED);

    public final ChatFormatting color;

    UpsMode(ChatFormatting color) {
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static UpsMode byName(String name) {
        for (var mode : values())
            if (mode.getSerializedName().equals(name))
                return mode;
        return OFF;
    }
}
