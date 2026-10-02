package dev.paging.mws.client;

import dev.paging.mws.Mws;
import dev.paging.mws.client.ponder.MwsPonderPlugin;
import dev.paging.mws.registry.MwsFluids;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = Mws.MODID, value = Dist.CLIENT)
public final class MwsClient {
    /** Murky brown-green, mostly opaque. */
    private static final int SLUDGE_TINT = 0xF04A4F1C;

    private MwsClient() {
    }

    @SubscribeEvent
    static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(MwsFluids.SLUDGE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(MwsFluids.FLOWING_SLUDGE.get(), RenderType.translucent());
        });
        PonderIndex.addPlugin(new MwsPonderPlugin());
    }

    @SubscribeEvent
    static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(waterTinted(SLUDGE_TINT), MwsFluids.SLUDGE_TYPE.get());
    }

    /** Vanilla water textures with our own colour. */
    private static IClientFluidTypeExtensions waterTinted(int tint) {
        return new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return ResourceLocation.withDefaultNamespace("block/water_still");
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return ResourceLocation.withDefaultNamespace("block/water_flow");
            }

            @Override
            public ResourceLocation getOverlayTexture() {
                return ResourceLocation.withDefaultNamespace("block/water_overlay");
            }

            @Override
            public int getTintColor() {
                return tint;
            }
        };
    }
}
