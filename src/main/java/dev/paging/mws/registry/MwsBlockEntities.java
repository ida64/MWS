package dev.paging.mws.registry;

import dev.paging.mws.Mws;
import dev.paging.mws.chiller.ChillerBlockEntity;
import dev.paging.mws.intake.WaterIntakeBlockEntity;
import dev.paging.mws.rack.ServerRackBlockEntity;
import dev.paging.mws.ups.UpsBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class MwsBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Mws.MODID);

    @FunctionalInterface
    private interface Factory<T extends BlockEntity> {
        T create(BlockEntityType<?> type, BlockPos pos, BlockState state);
    }

    @SuppressWarnings("DataFlowIssue")
    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> register(String name, Factory<T> factory, Supplier<? extends Block> block) {
        var holder = new Object() {
            DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> value;
        };
        holder.value = BLOCK_ENTITIES.register(name,
                () -> BlockEntityType.Builder.<T>of((pos, state) -> factory.create(holder.value.get(), pos, state), block.get()).build(null));
        return holder.value;
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServerRackBlockEntity>> SERVER_RACK =
            register("server_rack", ServerRackBlockEntity::new, MwsBlocks.SERVER_RACK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UpsBlockEntity>> UPS =
            register("ups", UpsBlockEntity::new, MwsBlocks.UPS);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChillerBlockEntity>> CHILLER =
            register("chiller", ChillerBlockEntity::new, MwsBlocks.CHILLER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterIntakeBlockEntity>> WATER_INTAKE =
            register("water_intake", WaterIntakeBlockEntity::new, MwsBlocks.WATER_INTAKE);

    private MwsBlockEntities() {
    }
}
