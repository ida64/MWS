package dev.paging.mws.power;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Formatting for Create engineer's goggles overlays. */
public final class Goggles {
    private Goggles() {
    }

    public static void title(List<Component> tooltip, String blockId) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.mws." + blockId)));
    }

    public static void line(List<Component> tooltip, String key, Object value, ChatFormatting color) {
        tooltip.add(Component.literal("    ")
                .append(Component.translatable("mws.goggles." + key).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" "))
                .append(value instanceof Component component ? component.copy().withStyle(color) : Component.literal(String.valueOf(value)).withStyle(color)));
    }
}
