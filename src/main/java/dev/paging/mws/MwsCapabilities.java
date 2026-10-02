package dev.paging.mws;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import dev.paging.mws.registry.MwsBlockEntities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@EventBusSubscriber(modid = Mws.MODID)
public final class MwsCapabilities {
    private MwsCapabilities() {
    }

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        // Create pipes: water in / sludge out of the chiller, water out of the intake.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, MwsBlockEntities.CHILLER.get(), (be, side) -> be.getFluidHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, MwsBlockEntities.WATER_INTAKE.get(), (be, side) -> be.getFluidHandler());

        // CC: Tweaked peripherals. Every rack in a cabinet hands out the cabinet base's peripheral.
        event.registerBlockEntity(PeripheralCapability.get(), MwsBlockEntities.SERVER_RACK.get(), (be, side) -> be.peripheral());
        event.registerBlockEntity(PeripheralCapability.get(), MwsBlockEntities.UPS.get(), (be, side) -> be.peripheral());
        event.registerBlockEntity(PeripheralCapability.get(), MwsBlockEntities.CHILLER.get(), (be, side) -> be.peripheral());
    }
}
