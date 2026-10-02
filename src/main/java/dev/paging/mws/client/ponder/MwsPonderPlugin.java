package dev.paging.mws.client.ponder;

import dev.paging.mws.Mws;
import dev.paging.mws.registry.MwsBlocks;
import dev.paging.mws.registry.MwsItems;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/** Ponder scenes (hold W over an item) for every MWS machine. Registered from client setup. */
public class MwsPonderPlugin implements PonderPlugin {
    public static final ResourceLocation DATA_CENTER = ResourceLocation.fromNamespaceAndPath(Mws.MODID, "data_center");

    @Override
    public String getModId() {
        return Mws.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(MwsBlocks.SERVER_RACK.getId(), MwsItems.SERVER_BLADE.getId())
                .addStoryBoard("server_rack/basics", MwsPonderScenes::rackBasics, DATA_CENTER)
                .addStoryBoard("server_rack/cabinet", MwsPonderScenes::cabinet, DATA_CENTER);
        helper.forComponents(MwsBlocks.UPS.getId())
                .addStoryBoard("ups", MwsPonderScenes::ups, DATA_CENTER);
        helper.forComponents(MwsBlocks.CHILLER.getId(), MwsItems.SLUDGE_BUCKET.getId())
                .addStoryBoard("chiller", MwsPonderScenes::chiller, DATA_CENTER);
        helper.forComponents(MwsBlocks.WATER_INTAKE.getId())
                .addStoryBoard("water_intake", MwsPonderScenes::waterIntake, DATA_CENTER);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(DATA_CENTER)
                .addToIndex()
                .item(MwsBlocks.SERVER_RACK.get(), true, false)
                .title("Data Center")
                .description("Servers, and everything it takes to keep them running")
                .register();
        helper.addToTag(DATA_CENTER)
                .add(MwsBlocks.SERVER_RACK.getId())
                .add(MwsBlocks.UPS.getId())
                .add(MwsBlocks.CHILLER.getId())
                .add(MwsBlocks.WATER_INTAKE.getId());
    }
}
