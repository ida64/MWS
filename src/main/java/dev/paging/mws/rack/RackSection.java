package dev.paging.mws.rack;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** Where a rack sits in a vertical cabinet. The base (single or bottom) holds the power inlets. */
public enum RackSection implements StringRepresentable {
    SINGLE, BOTTOM, MIDDLE, TOP;

    public boolean isBase() {
        return this == SINGLE || this == BOTTOM;
    }

    public static RackSection of(boolean rackBelow, boolean rackAbove) {
        if (rackBelow)
            return rackAbove ? MIDDLE : TOP;
        return rackAbove ? BOTTOM : SINGLE;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
