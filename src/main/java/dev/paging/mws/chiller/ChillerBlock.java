package dev.paging.mws.chiller;

import dev.paging.mws.power.MachineBlock;
import dev.paging.mws.registry.MwsBlockEntities;
import dev.paging.mws.registry.MwsFluids;
import dev.paging.mws.sludge.Sludge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class ChillerBlock extends MachineBlock<ChillerBlockEntity> {
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;

    public ChillerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    /** Breaking a chiller dumps whatever sludge it was holding onto the floor. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        int buckets = 0;
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            var chiller = getBlockEntity(level, pos);
            if (chiller != null)
                buckets = chiller.getSludge().getFluidAmount() / 1000;
        }
        super.onRemove(state, level, pos, newState, moved);
        if (buckets > 0 && newState.isAir()) {
            level.setBlock(pos, MwsFluids.SLUDGE.get().defaultFluidState().createLegacyBlock(), 3);
            for (int i = 1; i < buckets; ++i)
                Sludge.spill(level, pos, 4);
        }
    }

    @Override
    public Class<ChillerBlockEntity> getBlockEntityClass() {
        return ChillerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ChillerBlockEntity> getBlockEntityType() {
        return MwsBlockEntities.CHILLER.get();
    }
}
