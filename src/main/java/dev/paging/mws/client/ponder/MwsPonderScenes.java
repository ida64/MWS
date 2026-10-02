package dev.paging.mws.client.ponder;

import dev.paging.mws.chiller.ChillerBlockEntity;
import dev.paging.mws.rack.ServerRackBlockEntity;
import dev.paging.mws.registry.MwsFluids;
import dev.paging.mws.registry.MwsItems;
import dev.paging.mws.ups.UpsBlockEntity;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.patryk3211.powergrid.ponder.base.PowerGridSceneBuilder;

/**
 * Every scene runs the real machines on Power Grid's ponder circuit simulation: racks really
 * boot, the UPS really switches over, the chiller really cools. The scene text has to line up
 * with what the simulation does, so keep the idle times longer than the machine timings
 * (a rack takes {@code bootTicks} = 100 ticks to come online).
 *
 * <p>Scene text lives in en_us.json as mws.ponder.&lt;scene&gt;.text_N, numbered in order of showText.
 */
public final class MwsPonderScenes {
    private static final int BOOT = 110;

    private MwsPonderScenes() {
    }

    /** Common start: base plate, the live-state redraw hook, and a short pause. */
    private static PowerGridSceneBuilder begin(SceneBuilder builder, String id, String title) {
        var scene = new PowerGridSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 5);
        scene.addInstruction(ponder -> ponder.addElement(new RedrawOnChange()));
        scene.showBasePlate();
        scene.idle(10);
        return scene;
    }

    private static void feed(PowerGridSceneBuilder scene, BlockPos source, BlockPos machine, int live, int neutral) {
        scene.electric().connect(source, 0, machine, live);
        scene.electric().connect(source, 1, machine, neutral);
    }

    private static void insertBlade(PowerGridSceneBuilder scene, SceneBuildingUtil util, BlockPos rack) {
        scene.overlay().showControls(util.vector().blockSurface(rack, Direction.NORTH), Pointing.DOWN, 30)
                .rightClick().withItem(new ItemStack(MwsItems.SERVER_BLADE.get()));
        scene.idle(7);
        scene.world().modifyBlockEntity(rack, ServerRackBlockEntity.class, ServerRackBlockEntity::insertBlade);
    }

    public static void rackBasics(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = begin(builder, "server_rack_basics", "Running a Server");
        BlockPos rack = util.grid().at(2, 1, 2);
        BlockPos source = util.grid().at(2, 1, 4);

        scene.world().showSection(util.select().position(rack), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("Server Racks are 240 V machines, powered from a Create: Power Grid network")
                .pointAt(util.vector().topOf(rack))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.rotateCameraY(180);
        scene.idle(20);
        scene.world().showSection(util.select().position(source), Direction.DOWN);
        scene.idle(10);
        feed(scene, source, rack, 0, 1);
        scene.electric().setSource(source, 240);
        scene.electric().tickForever();
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Wire the supply into Feed A: the red and blue inlets on the back")
                .pointAt(util.vector().blockSurface(rack, Direction.SOUTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
        scene.rotateCameraY(-180);
        scene.idle(20);

        insertBlade(scene, util, rack);
        scene.overlay().showText(70)
                .text("Right-click with Server Blades to fill it, up to four per rack")
                .pointAt(util.vector().blockSurface(rack, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);
        scene.overlay().showText(70)
                .text("With power within 5% of 240 V, the rack boots and comes online")
                .pointAt(util.vector().blockSurface(rack, Direction.NORTH))
                .placeNearTarget();
        scene.idle(BOOT - 80 + 40);

        scene.electric().setSource(source, 180);
        scene.overlay().showText(80)
                .colored(PonderPalette.RED)
                .text("If the voltage drifts more than 15% while it runs, the server crashes and may lose data")
                .pointAt(util.vector().blockSurface(rack, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
        scene.electric().setSource(source, 240);
        scene.overlay().showText(70)
                .text("Once the power is clean again, it reboots by itself")
                .pointAt(util.vector().blockSurface(rack, Direction.NORTH))
                .placeNearTarget();
        scene.idle(BOOT);

        scene.overlay().showText(100)
                .text("Every watt a server draws turns into heat. One blade cools itself, two run throttled, and more need a Chiller")
                .pointAt(util.vector().topOf(rack))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);
        scene.overlay().showText(80)
                .text("Engineer's Goggles show a rack's temperature, its power, and why it won't boot")
                .pointAt(util.vector().topOf(rack))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showControls(util.vector().blockSurface(rack, Direction.NORTH), Pointing.DOWN, 30)
                .rightClick().whileSneaking();
        scene.idle(7);
        scene.world().modifyBlockEntity(rack, ServerRackBlockEntity.class, ServerRackBlockEntity::removeBlade);
        scene.overlay().showText(70)
                .text("Sneak and right-click with an empty hand to pull a blade back out")
                .pointAt(util.vector().blockSurface(rack, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);
        scene.markAsFinished();
    }

    public static void cabinet(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = begin(builder, "server_rack_cabinet", "Cabinets and Redundant Power");
        BlockPos base = util.grid().at(2, 1, 2);
        BlockPos top = util.grid().at(2, 3, 2);
        BlockPos sourceA = util.grid().at(1, 1, 4);
        BlockPos sourceB = util.grid().at(3, 1, 4);

        scene.world().showSection(util.select().position(base), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(base.above()), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(top), Direction.DOWN);
        scene.idle(15);
        for (int y = 1; y <= 3; ++y)
            scene.world().modifyBlockEntity(util.grid().at(2, y, 2), ServerRackBlockEntity.class, ServerRackBlockEntity::insertBlade);
        scene.overlay().showText(80)
                .text("Stack racks into a cabinet. The whole stack runs from the inlets on the bottom rack")
                .pointAt(util.vector().blockSurface(top, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.rotateCameraY(180);
        scene.idle(20);
        scene.world().showSection(util.select().position(sourceA), Direction.DOWN);
        scene.world().showSection(util.select().position(sourceB), Direction.DOWN);
        scene.idle(10);
        feed(scene, sourceA, base, 0, 1);
        scene.idle(10);
        feed(scene, sourceB, base, 2, 3);
        scene.electric().setSource(sourceA, 240);
        scene.electric().setSource(sourceB, 240);
        scene.electric().tickForever();
        scene.overlay().showText(90)
                .text("The base has two inlets. Feed A is red and blue, Feed B is orange and cyan")
                .pointAt(util.vector().blockSurface(base, Direction.SOUTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
        scene.rotateCameraY(-180);
        scene.idle(20);

        scene.overlay().showText(70)
                .text("The cabinet splits its load between both feeds")
                .pointAt(util.vector().blockSurface(base, Direction.NORTH))
                .placeNearTarget();
        scene.idle(BOOT - 40);

        scene.electric().setSource(sourceA, 0);
        scene.overlay().showText(90)
                .colored(PonderPalette.GREEN)
                .text("If one feed fails, the other carries the whole cabinet and every server stays online")
                .pointAt(util.vector().blockSurface(top, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(80)
                .text("A single feed works too. Two separate supplies, or a UPS on one of them, keep the cabinet up through outages")
                .pointAt(util.vector().blockSurface(base, Direction.NORTH))
                .placeNearTarget();
        scene.idle(90);
        scene.overlay().showText(80)
                .text("The cabinet keeps its stored data in the base rack, and computers can reach it through any rack")
                .pointAt(util.vector().blockSurface(base, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    public static void ups(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = begin(builder, "ups", "Riding Through Blackouts");
        BlockPos ups = util.grid().at(1, 1, 2);
        BlockPos rack = util.grid().at(3, 1, 2);
        BlockPos source = util.grid().at(1, 1, 4);

        scene.world().showSection(util.select().position(ups), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(rack), Direction.DOWN);
        scene.idle(15);
        // Start it half charged so the blackout below has something to run on.
        scene.world().modifyBlockEntityNBT(util.select().position(ups), UpsBlockEntity.class,
                tag -> tag.putDouble("Energy", 1.8e6));
        scene.world().modifyBlockEntity(rack, ServerRackBlockEntity.class, ServerRackBlockEntity::insertBlade);
        scene.overlay().showText(70)
                .text("A UPS sits between the mains and your servers")
                .pointAt(util.vector().topOf(ups))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.rotateCameraY(180);
        scene.idle(20);
        scene.world().showSection(util.select().position(source), Direction.DOWN);
        scene.idle(10);
        feed(scene, source, ups, 0, 1);
        scene.electric().setSource(source, 240);
        scene.electric().tickForever();
        scene.overlay().showText(70)
                .text("Mains goes into the input on the left of its back panel...")
                .pointAt(util.vector().blockSurface(ups, Direction.SOUTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);
        scene.electric().connect(ups, 2, rack, 0);
        scene.electric().connect(ups, 3, rack, 1);
        scene.overlay().showText(70)
                .text("...and the servers plug into the output on the right")
                .pointAt(util.vector().blockSurface(rack, Direction.SOUTH))
                .placeNearTarget();
        scene.idle(80);
        scene.rotateCameraY(-180);
        scene.idle(20);

        scene.overlay().showText(80)
                .text("While the mains is healthy, the UPS passes it through and charges its battery")
                .pointAt(util.vector().blockSurface(ups, Direction.NORTH))
                .placeNearTarget();
        scene.idle(BOOT - 30);

        scene.electric().setSource(source, 0);
        scene.overlay().showText(90)
                .colored(PonderPalette.GREEN)
                .text("When the mains fails, it switches to battery instantly. The servers never notice")
                .pointAt(util.vector().blockSurface(rack, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(80)
                .text("A comparator reads its charge. Right-click with an empty hand to switch the output on or off")
                .pointAt(util.vector().topOf(ups))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
        scene.electric().setSource(source, 240);
        scene.idle(20);
        scene.markAsFinished();
    }

    public static void chiller(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = begin(builder, "chiller", "Cooling Servers");
        BlockPos rack = util.grid().at(1, 1, 2);
        BlockPos chiller = util.grid().at(3, 1, 2);
        BlockPos source = util.grid().at(2, 1, 4);

        scene.world().showSection(util.select().position(rack), Direction.DOWN);
        scene.world().showSection(util.select().position(source), Direction.DOWN);
        scene.idle(10);
        for (int i = 0; i < ServerRackBlockEntity.MAX_BLADES; ++i)
            scene.world().modifyBlockEntity(rack, ServerRackBlockEntity.class, ServerRackBlockEntity::insertBlade);
        feed(scene, source, rack, 0, 1);
        scene.electric().setSource(source, 240);
        scene.electric().tickForever();
        scene.overlay().showText(80)
                .text("A full rack makes far more heat than it can shed on its own")
                .pointAt(util.vector().topOf(rack))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(util.select().position(chiller), Direction.DOWN);
        scene.idle(10);
        feed(scene, source, chiller, 0, 1);
        scene.overlay().showText(80)
                .text("A Chiller pulls heat out of every rack within 6 blocks. It runs on 240 V too")
                .pointAt(util.vector().topOf(chiller))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().modifyBlockEntity(chiller, ChillerBlockEntity.class,
                be -> be.getFluidHandler().fill(new FluidStack(Fluids.WATER, 8000), IFluidHandler.FluidAction.EXECUTE));
        scene.overlay().showText(80)
                .text("It evaporates water to do it, so pipe water in from any side")
                .pointAt(util.vector().blockSurface(chiller, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);
        scene.overlay().showText(80)
                .text("Once a rack runs warmer than 30 °C, the chiller starts working")
                .pointAt(util.vector().blockSurface(chiller, Direction.NORTH))
                .placeNearTarget();
        scene.idle(90);

        scene.world().modifyBlockEntity(chiller, ChillerBlockEntity.class,
                be -> be.getSludge().fill(new FluidStack(MwsFluids.SLUDGE.get(), be.getSludge().getCapacity()), IFluidHandler.FluidAction.EXECUTE));
        scene.overlay().showText(90)
                .colored(PonderPalette.RED)
                .text("Some of that water comes back as sludge. Pipe it away, or the chiller dumps it on the floor")
                .pointAt(util.vector().topOf(chiller))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
        scene.markAsFinished();
    }

    public static void waterIntake(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = begin(builder, "water_intake", "Pumping Water");
        BlockPos intake = util.grid().at(3, 1, 1);
        BlockPos source = util.grid().at(4, 1, 3);

        scene.world().showSection(util.select().fromTo(0, 1, 0, 2, 1, 2), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(intake), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("The Water Intake pulls water out of whatever lake, river or moat it touches")
                .pointAt(util.vector().topOf(intake))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.world().showSection(util.select().position(source), Direction.DOWN);
        scene.idle(10);
        feed(scene, source, intake, 0, 1);
        scene.electric().setSource(source, 240);
        scene.electric().tickForever();
        scene.overlay().showText(90)
                .text("When powered, it takes the nearest source block every second, reaching up to 16 blocks through connected water")
                .pointAt(util.vector().topOf(1, 1, 1))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(80)
                .text("Pump water out of it with Create pipes and send it on to a Chiller")
                .pointAt(util.vector().blockSurface(intake, Direction.NORTH))
                .placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }
}
