package dev.paging.mws;

import com.mojang.logging.LogUtils;
import dev.paging.mws.client.ClientConfig;
import dev.paging.mws.registry.MwsBlockEntities;
import dev.paging.mws.registry.MwsBlocks;
import dev.paging.mws.registry.MwsCreativeTabs;
import dev.paging.mws.registry.MwsFluids;
import dev.paging.mws.registry.MwsItems;
import dev.paging.mws.registry.MwsSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Minecraft Web Services: 240V server racks for Create: Power Grid, with UPS batteries,
 * water-hungry chillers, the sludge they leave behind, and ComputerCraft peripherals for all of it.
 */
@Mod(Mws.MODID)
public class Mws {
    public static final String MODID = "mws";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Mws(IEventBus modEventBus, ModContainer modContainer) {
        MwsSounds.SOUNDS.register(modEventBus);
        MwsFluids.FLUID_TYPES.register(modEventBus);
        MwsFluids.FLUIDS.register(modEventBus);
        MwsBlocks.BLOCKS.register(modEventBus);
        MwsItems.ITEMS.register(modEventBus);
        MwsBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        MwsCreativeTabs.TABS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }
}
