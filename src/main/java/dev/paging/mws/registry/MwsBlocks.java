package dev.paging.mws.registry;

import dev.paging.mws.Mws;
import dev.paging.mws.chiller.ChillerBlock;
import dev.paging.mws.intake.WaterIntakeBlock;
import dev.paging.mws.rack.RackStatus;
import dev.paging.mws.rack.ServerRackBlock;
import dev.paging.mws.sludge.SludgeBlock;
import dev.paging.mws.ups.UpsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MwsBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mws.MODID);

    private static BlockBehaviour.Properties machine(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(3.5f, 6f)
                .sound(SoundType.NETHERITE_BLOCK)
                .requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<ServerRackBlock> SERVER_RACK = BLOCKS.register("server_rack",
            () -> new ServerRackBlock(machine(MapColor.COLOR_BLACK)
                    .lightLevel(state -> state.getValue(ServerRackBlock.STATUS) == RackStatus.ONLINE ? 4 : 0)));
    public static final DeferredBlock<UpsBlock> UPS = BLOCKS.register("ups",
            () -> new UpsBlock(machine(MapColor.COLOR_GRAY)));
    public static final DeferredBlock<ChillerBlock> CHILLER = BLOCKS.register("chiller",
            () -> new ChillerBlock(machine(MapColor.METAL)));
    public static final DeferredBlock<WaterIntakeBlock> WATER_INTAKE = BLOCKS.register("water_intake",
            () -> new WaterIntakeBlock(machine(MapColor.COLOR_CYAN)));

    public static final DeferredBlock<SludgeBlock> SLUDGE = BLOCKS.register("sludge",
            () -> new SludgeBlock(MwsFluids.SLUDGE.get(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .replaceable()
                    .noCollission()
                    .strength(100f)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .sound(SoundType.EMPTY)));

    private MwsBlocks() {
    }
}
