package dev.paging.mws.registry;

import dev.paging.mws.Mws;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MwsCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Mws.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mws"))
            .icon(() -> MwsBlocks.SERVER_RACK.asItem().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(MwsBlocks.SERVER_RACK);
                output.accept(MwsItems.SERVER_BLADE);
                output.accept(MwsBlocks.UPS);
                output.accept(MwsBlocks.CHILLER);
                output.accept(MwsBlocks.WATER_INTAKE);
                output.accept(MwsItems.SLUDGE_BUCKET);
            })
            .build());

    private MwsCreativeTabs() {
    }
}
