package dev.paging.mws.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue FAN_VOLUME = BUILDER
            .comment("Volume of server rack and chiller fans. 0 mutes them, 1 is the default, up to 2.")
            .defineInRange("fanVolume", 1.0, 0.0, 2.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
