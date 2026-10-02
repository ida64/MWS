package dev.paging.mws.test;

import dan200.computercraft.api.lua.LuaException;
import dev.paging.mws.Mws;
import dev.paging.mws.chiller.ChillerBlockEntity;
import dev.paging.mws.chiller.ChillerStatus;
import dev.paging.mws.intake.WaterIntakeBlockEntity;
import dev.paging.mws.rack.RackSection;
import dev.paging.mws.rack.RackStatus;
import dev.paging.mws.rack.ServerRackBlock;
import dev.paging.mws.rack.ServerRackBlockEntity;
import dev.paging.mws.registry.MwsBlocks;
import dev.paging.mws.registry.MwsFluids;
import dev.paging.mws.ups.UpsBlockEntity;
import dev.paging.mws.ups.UpsMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;

/**
 * End-to-end tests against the real Power Grid solver: every machine is wired with hanging
 * wires to Power Grid's creative voltage source.
 */
@GameTestHolder(Mws.MODID)
@PrefixGameTestTemplate(false)
public class MwsGameTests {
    private static final String FLOOR = "floor";
    private static final BlockPos SOURCE = new BlockPos(1, 1, 1);
    private static final BlockPos SOURCE_2 = new BlockPos(1, 1, 7);
    private static final BlockPos RACK = new BlockPos(4, 1, 1);
    private static final BlockPos UPS = new BlockPos(1, 1, 4);
    private static final BlockPos CHILLER = new BlockPos(4, 1, 7);
    private static final int SETTLE = 5;

    private static void placeSource(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("powergrid:creative_voltage_source")));
    }

    private static CreativeSourceBlockEntity source(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos);
    }

    private static void connect(GameTestHelper helper, BlockPos a, int terminalA, BlockPos b, int terminalB) {
        var level = helper.getLevel();
        var wire = HangingWireEntity.create(level,
                new BlockWireEndpoint(helper.absolutePos(a), terminalA),
                new BlockWireEndpoint(helper.absolutePos(b), terminalB),
                new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("powergrid:wire")), 4), null);
        if (!level.addFreshEntity(wire))
            helper.fail("Could not spawn wire " + a + " -> " + b);
    }

    /** Wire a creative source's terminals 0/1 to the given machine terminals and set its voltage. */
    private static void feed(GameTestHelper helper, BlockPos src, BlockPos machine, int live, int neutral, float volts) {
        connect(helper, src, 0, machine, live);
        connect(helper, src, 1, machine, neutral);
        source(helper, src).setValue(volts);
    }

    private static ServerRackBlockEntity rack(GameTestHelper helper, int blades) {
        helper.setBlock(RACK, MwsBlocks.SERVER_RACK.get());
        ServerRackBlockEntity rack = helper.getBlockEntity(RACK);
        for (int i = 0; i < blades; ++i)
            rack.insertBlade();
        return rack;
    }

    @GameTest(template = FLOOR, timeoutTicks = 400)
    public static void rackBootsOnCleanPowerAndServesObjects(GameTestHelper helper) {
        placeSource(helper, SOURCE);
        var rack = rack(helper, 2);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    feed(helper, SOURCE, RACK, 0, 1, 240f);
                    try {
                        rack.peripheral().put("before", "boot");
                        helper.fail("Offline rack accepted a write");
                    } catch (LuaException expected) {
                        helper.assertTrue(expected.getMessage().startsWith("503"), "Expected 503, got " + expected.getMessage());
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(rack.getStatus() == RackStatus.ONLINE, "Rack is " + rack.getStatus()))
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(Math.abs(rack.feedA().voltage - 240) < 5, "Voltage " + rack.feedA().voltage);
                    helper.assertTrue(Math.abs(rack.getPower() - 1025) < 60, "Power " + rack.getPower());
                    helper.assertBlockProperty(RACK, ServerRackBlock.STATUS, RackStatus.ONLINE);
                    try {
                        rack.peripheral().put("hello", "world");
                        helper.assertTrue("world".equals(rack.peripheral().get("hello")), "Object did not round-trip");
                        helper.assertTrue(rack.getUsedBytes() == 10, "Used bytes " + rack.getUsedBytes());
                    } catch (LuaException e) {
                        helper.fail("Online rack rejected request: " + e.getMessage());
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 400)
    public static void rackRefusesToBootOnWrongVoltage(GameTestHelper helper) {
        placeSource(helper, SOURCE);
        var rack = rack(helper, 1);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> feed(helper, SOURCE, RACK, 0, 1, 120f))
                .thenIdle(150)
                .thenExecute(() -> helper.assertTrue(rack.getStatus() == RackStatus.OFF, "Rack booted on 120V: " + rack.getStatus()))
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 400)
    public static void brownoutCrashesRack(GameTestHelper helper) {
        placeSource(helper, SOURCE);
        var rack = rack(helper, 1);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> feed(helper, SOURCE, RACK, 0, 1, 240f))
                .thenWaitUntil(() -> helper.assertTrue(rack.getStatus() == RackStatus.ONLINE, "Rack is " + rack.getStatus()))
                .thenExecute(() -> source(helper, SOURCE).setValue(150f))
                .thenWaitUntil(() -> helper.assertTrue(rack.getStatus() == RackStatus.FAULT, "Rack is " + rack.getStatus()))
                .thenExecute(() -> helper.assertTrue(rack.getDirtyShutdowns() == 1, "Dirty shutdowns " + rack.getDirtyShutdowns()))
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void surgeFriesBlades(GameTestHelper helper) {
        placeSource(helper, SOURCE);
        var rack = rack(helper, 3);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> feed(helper, SOURCE, RACK, 0, 1, 400f))
                .thenWaitUntil(() -> helper.assertTrue(rack.getBlades() == 0, "Blades survived: " + rack.getBlades()))
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 800)
    public static void upsCarriesRackThroughBlackout(GameTestHelper helper) {
        placeSource(helper, SOURCE);
        helper.setBlock(UPS, MwsBlocks.UPS.get());
        var rack = rack(helper, 1);
        UpsBlockEntity ups = helper.getBlockEntity(UPS);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    feed(helper, SOURCE, UPS, 0, 1, 240f);
                    connect(helper, UPS, 2, RACK, 0);
                    connect(helper, UPS, 3, RACK, 1);
                })
                .thenWaitUntil(() -> helper.assertTrue(rack.getStatus() == RackStatus.ONLINE, "Rack is " + rack.getStatus() + ", UPS " + ups.getMode()))
                .thenExecute(() -> helper.assertTrue(ups.getMode() == UpsMode.ONLINE, "UPS is " + ups.getMode()))
                .thenExecute(() -> source(helper, SOURCE).setValue(0f))
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(ups.getMode() == UpsMode.BATTERY, "UPS is " + ups.getMode());
                    helper.assertTrue(rack.getStatus() == RackStatus.ONLINE, "Rack dropped: " + rack.getStatus());
                    helper.assertTrue(rack.getDirtyShutdowns() == 0, "Rack crashed during transfer");
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 1200)
    public static void chillerCoolsRackAndMakesSludge(GameTestHelper helper) {
        placeSource(helper, SOURCE);
        placeSource(helper, SOURCE_2);
        var rack = rack(helper, 4);
        helper.setBlock(CHILLER, MwsBlocks.CHILLER.get());
        ChillerBlockEntity chiller = helper.getBlockEntity(CHILLER);
        chiller.getFluidHandler().fill(new FluidStack(Fluids.WATER, 8000), IFluidHandler.FluidAction.EXECUTE);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    feed(helper, SOURCE, RACK, 0, 1, 240f);
                    feed(helper, SOURCE_2, CHILLER, 0, 1, 240f);
                })
                .thenWaitUntil(() -> helper.assertTrue(chiller.getStatus() == ChillerStatus.COOLING, "Chiller is " + chiller.getStatus() + ", rack at " + rack.getTemperature()))
                .thenIdle(400)
                .thenExecute(() -> {
                    helper.assertTrue(rack.getStatus() == RackStatus.ONLINE, "Rack is " + rack.getStatus());
                    helper.assertTrue(rack.getTemperature() < 32, "Rack temperature " + rack.getTemperature());
                    helper.assertTrue(chiller.getWater().getFluidAmount() < 8000, "No water used");
                    helper.assertTrue(chiller.getSludge().getFluidAmount() > 0, "No sludge produced");
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void fullChillerVentsSludge(GameTestHelper helper) {
        placeSource(helper, SOURCE_2);
        helper.setBlock(CHILLER, MwsBlocks.CHILLER.get());
        ChillerBlockEntity chiller = helper.getBlockEntity(CHILLER);
        int capacity = chiller.getSludge().getCapacity();
        chiller.getSludge().fill(new FluidStack(MwsFluids.SLUDGE.get(), capacity), IFluidHandler.FluidAction.EXECUTE);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> feed(helper, SOURCE_2, CHILLER, 0, 1, 240f))
                .thenWaitUntil(() -> helper.assertTrue(chiller.getSludge().getFluidAmount() < capacity, "Sludge never vented"))
                .thenExecute(() -> {
                    boolean found = false;
                    for (var pos : BlockPos.betweenClosed(CHILLER.offset(-3, -1, -3), CHILLER.offset(3, 2, 3)))
                        found |= helper.getBlockState(pos).is(MwsBlocks.SLUDGE.get());
                    helper.assertTrue(found, "No sludge block in the world");
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 300)
    public static void intakeStealsWaterSources(GameTestHelper helper) {
        placeSource(helper, SOURCE);
        var intake = new BlockPos(4, 1, 4);
        helper.setBlock(intake, MwsBlocks.WATER_INTAKE.get());
        // A two-block pond in a stone basin.
        for (int x = 4; x <= 8; ++x)
            for (int z = 3; z <= 5; ++z)
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        helper.setBlock(intake, MwsBlocks.WATER_INTAKE.get());
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.WATER);
        helper.setBlock(new BlockPos(6, 1, 4), Blocks.WATER);
        WaterIntakeBlockEntity be = helper.getBlockEntity(intake);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> feed(helper, SOURCE, intake, 0, 1, 240f))
                .thenWaitUntil(() -> helper.assertTrue(be.getTank().getFluidAmount() == 2000, "Tank has " + be.getTank().getFluidAmount()))
                .thenExecute(() -> {
                    for (int x = 5; x <= 6; ++x) {
                        var fluid = helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(x, 1, 4)));
                        helper.assertTrue(!fluid.isSource(), "Water source left at x=" + x);
                    }
                })
                .thenSucceed();
    }

    // ---- Dual feeds ----

    @GameTest(template = FLOOR, timeoutTicks = 400)
    public static void dualFeedsShareLoadAndSurviveLosingOne(GameTestHelper helper) {
        placeSource(helper, SOURCE);
        placeSource(helper, SOURCE_2);
        var rack = rack(helper, 4);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    feed(helper, SOURCE, RACK, 0, 1, 240f);
                    feed(helper, SOURCE_2, RACK, 2, 3, 240f);
                })
                .thenWaitUntil(() -> helper.assertTrue(rack.getStatus() == RackStatus.ONLINE, "Rack is " + rack.getStatus()))
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(rack.isRedundant(), "Not redundant with two good feeds");
                    double pa = rack.feedA().power, pb = rack.feedB().power;
                    helper.assertTrue(Math.abs(pa - pb) < 0.1 * (pa + pb), "Load not shared: A=" + pa + " B=" + pb);
                    source(helper, SOURCE).setValue(0f);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(rack.getStatus() == RackStatus.ONLINE, "Rack dropped on losing feed A: " + rack.getStatus());
                    helper.assertTrue(rack.getDirtyShutdowns() == 0, "Rack crashed on losing feed A");
                    helper.assertTrue(!rack.isRedundant(), "Still claims redundancy");
                    helper.assertTrue(rack.feedB().power > 1800, "Feed B did not pick up the load: " + rack.feedB().power);
                })
                .thenSucceed();
    }

    // ---- Cabinets ----

    @GameTest(template = FLOOR, timeoutTicks = 400)
    public static void stackedRacksRunFromOneInlet(GameTestHelper helper) {
        placeSource(helper, SOURCE);
        var bottom = rack(helper, 2);
        helper.setBlock(RACK.above(), MwsBlocks.SERVER_RACK.get());
        helper.setBlock(RACK.above(2), MwsBlocks.SERVER_RACK.get());
        ServerRackBlockEntity middle = helper.getBlockEntity(RACK.above());
        ServerRackBlockEntity top = helper.getBlockEntity(RACK.above(2));
        middle.insertBlade();
        top.insertBlade();
        top.insertBlade();
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertBlockProperty(RACK, ServerRackBlock.SECTION, RackSection.BOTTOM);
                    helper.assertBlockProperty(RACK.above(), ServerRackBlock.SECTION, RackSection.MIDDLE);
                    helper.assertBlockProperty(RACK.above(2), ServerRackBlock.SECTION, RackSection.TOP);
                    feed(helper, SOURCE, RACK, 0, 1, 240f);
                })
                .thenWaitUntil(() -> helper.assertTrue(top.getStatus() == RackStatus.ONLINE && middle.getStatus() == RackStatus.ONLINE
                        && bottom.getStatus() == RackStatus.ONLINE, "Cabinet not up: " + bottom.getStatus() + "/" + middle.getStatus() + "/" + top.getStatus()))
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(bottom.getUnits() == 3, "Units " + bottom.getUnits());
                    helper.assertTrue(Math.abs(bottom.getCabinetPower() - 2575) < 150, "Cabinet power " + bottom.getCabinetPower());
                    helper.assertTrue(top.getPower() > 900, "Top rack got no power share: " + top.getPower());
                    helper.assertTrue(bottom.cabinetCapacityBytes() == 5L * 65536, "Capacity " + bottom.cabinetCapacityBytes());
                    // Any rack in the cabinet answers with the same peripheral.
                    helper.assertTrue(top.peripheral().equals(bottom.peripheral()), "Different peripherals per rack");
                    try {
                        top.peripheral().put("shared", "yes");
                        helper.assertTrue("yes".equals(middle.peripheral().get("shared")), "Storage not shared");
                    } catch (LuaException e) {
                        helper.fail(e.getMessage());
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void stackingUnderAWiredRackDropsItsWires(GameTestHelper helper) {
        placeSource(helper, SOURCE);
        var upper = RACK.above();
        helper.setBlock(upper, MwsBlocks.SERVER_RACK.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> feed(helper, SOURCE, upper, 0, 1, 240f))
                .thenExecuteAfter(5, () -> helper.assertTrue(wires(helper) == 2, "Expected 2 wires, found " + wires(helper)))
                .thenExecute(() -> helper.setBlock(RACK, MwsBlocks.SERVER_RACK.get()))
                .thenExecuteAfter(5, () -> {
                    helper.assertBlockProperty(upper, ServerRackBlock.SECTION, RackSection.TOP);
                    helper.assertTrue(wires(helper) == 0, "Wires still attached to a rack with no inlets: " + wires(helper));
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 400)
    public static void breakingCabinetBaseKeepsData(GameTestHelper helper) {
        placeSource(helper, SOURCE);
        var bottom = rack(helper, 1);
        helper.setBlock(RACK.above(), MwsBlocks.SERVER_RACK.get());
        ServerRackBlockEntity upper = helper.getBlockEntity(RACK.above());
        upper.insertBlade();
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> feed(helper, SOURCE, RACK, 0, 1, 240f))
                .thenWaitUntil(() -> helper.assertTrue(bottom.isServing(), "Cabinet never came online"))
                .thenExecute(() -> {
                    try {
                        upper.peripheral().put("precious", "data");
                    } catch (LuaException e) {
                        helper.fail(e.getMessage());
                    }
                    helper.setBlock(RACK, Blocks.AIR);
                    // Checked straight away: a tick later the now-unpowered server crashes, which may
                    // (by design) corrupt an object.
                    helper.assertTrue(upper.objectCount() == 1, "Data not handed to the rack above");
                    helper.assertBlockProperty(RACK.above(), ServerRackBlock.SECTION, RackSection.SINGLE);
                })
                .thenSucceed();
    }

    // ---- Ponder schematics (client assets, but plain structure NBT) ----

    private static StructureTemplate ponderSchematic(GameTestHelper helper, String path) {
        try (var in = MwsGameTests.class.getResourceAsStream("/assets/mws/ponder/" + path + ".nbt")) {
            helper.assertTrue(in != null, "Missing ponder schematic " + path);
            return helper.getLevel().getStructureManager().readStructure(NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int count(StructureTemplate template, Block block) {
        return template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).size();
    }

    /** The scenes drive these blocks by position, so a schematic that loads differently breaks them silently. */
    @GameTest(template = FLOOR)
    public static void ponderSchematicsLoad(GameTestHelper helper) {
        var source = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("powergrid:creative_voltage_source"));

        var basics = ponderSchematic(helper, "server_rack/basics");
        helper.assertTrue(count(basics, MwsBlocks.SERVER_RACK.get()) == 1 && count(basics, source) == 1, "basics layout");

        var cabinet = ponderSchematic(helper, "server_rack/cabinet");
        var racks = cabinet.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), MwsBlocks.SERVER_RACK.get());
        helper.assertTrue(racks.size() == 3 && count(cabinet, source) == 2, "cabinet layout");
        for (var rack : racks) {
            var expected = rack.pos().getY() == 1 ? RackSection.BOTTOM : rack.pos().getY() == 3 ? RackSection.TOP : RackSection.MIDDLE;
            helper.assertTrue(rack.state().getValue(ServerRackBlock.SECTION) == expected, "rack at " + rack.pos() + " is " + rack.state());
        }

        var ups = ponderSchematic(helper, "ups");
        helper.assertTrue(count(ups, MwsBlocks.UPS.get()) == 1 && count(ups, MwsBlocks.SERVER_RACK.get()) == 1, "ups layout");

        var chiller = ponderSchematic(helper, "chiller");
        helper.assertTrue(count(chiller, MwsBlocks.CHILLER.get()) == 1 && count(chiller, MwsBlocks.SERVER_RACK.get()) == 1, "chiller layout");

        var intake = ponderSchematic(helper, "water_intake");
        helper.assertTrue(count(intake, MwsBlocks.WATER_INTAKE.get()) == 1 && count(intake, Blocks.WATER) == 9, "intake layout");
        helper.succeed();
    }

    private static int wires(GameTestHelper helper) {
        return helper.getLevel().getEntities((net.minecraft.world.entity.Entity) null, helper.getBounds(), e -> e instanceof HangingWireEntity).size();
    }
}
