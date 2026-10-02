package dev.paging.mws.chiller;

import net.minecraft.ChatFormatting;

import java.util.Locale;

public enum ChillerStatus {
    NO_POWER(ChatFormatting.RED),
    IDLE(ChatFormatting.GRAY),
    COOLING(ChatFormatting.AQUA),
    NO_WATER(ChatFormatting.GOLD),
    /** Sludge tank full and nowhere left to dump it. */
    JAMMED(ChatFormatting.DARK_RED);

    public final ChatFormatting color;

    ChillerStatus(ChatFormatting color) {
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ChillerStatus byId(String id) {
        for (var status : values())
            if (status.id().equals(id))
                return status;
        return NO_POWER;
    }
}
