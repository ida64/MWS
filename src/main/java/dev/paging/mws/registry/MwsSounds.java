package dev.paging.mws.registry;

import dev.paging.mws.Mws;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MwsSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Mws.MODID);

    /** Seamless loop of server fans; pitch and volume follow fan speed. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RACK_FAN = SOUNDS.register("rack_fan",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Mws.MODID, "rack_fan")));

    private MwsSounds() {
    }
}
