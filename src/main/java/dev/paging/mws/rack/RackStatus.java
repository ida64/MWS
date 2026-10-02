package dev.paging.mws.rack;

import net.minecraft.ChatFormatting;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum RackStatus implements StringRepresentable {
    /** No blades, or no usable power. */
    OFF(ChatFormatting.GRAY),
    /** Clean 240V present, POST in progress. */
    BOOTING(ChatFormatting.GOLD),
    /** Serving requests. */
    ONLINE(ChatFormatting.GREEN),
    /** Crashed from bad power, or blades fried. */
    FAULT(ChatFormatting.RED),
    /** Emergency thermal shutdown; waits to cool down before booting. */
    OVERHEATED(ChatFormatting.DARK_RED);

    public final ChatFormatting color;

    RackStatus(ChatFormatting color) {
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RackStatus byName(String name) {
        for (var status : values())
            if (status.getSerializedName().equals(name))
                return status;
        return OFF;
    }
}
