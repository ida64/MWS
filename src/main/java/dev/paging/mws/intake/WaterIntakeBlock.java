package dev.paging.mws.intake;

import dev.paging.mws.power.MachineBlock;
import dev.paging.mws.registry.MwsBlockEntities;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class WaterIntakeBlock extends MachineBlock<WaterIntakeBlockEntity> {
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;

    public WaterIntakeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    public Class<WaterIntakeBlockEntity> getBlockEntityClass() {
        return WaterIntakeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WaterIntakeBlockEntity> getBlockEntityType() {
        return MwsBlockEntities.WATER_INTAKE.get();
    }
}
