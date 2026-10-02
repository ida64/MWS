package dev.paging.mws.ups;

import dev.paging.mws.power.MachineBlock;
import dev.paging.mws.registry.MwsBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;

public class UpsBlock extends MachineBlock<UpsBlockEntity> {
    public static final EnumProperty<UpsMode> MODE = EnumProperty.create("mode", UpsMode.class);
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);

    /** Mains input on the left of the back panel (terminals 0/1, also where cords plug in), output on the right. */
    private static final TerminalBoundingBox[] TERMINALS = {
            new TerminalBoundingBox(Component.translatable("mws.terminal.input_live"), 2, 11, 0, 4, 13, 1).withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.translatable("mws.terminal.input_neutral"), 5, 11, 0, 7, 13, 1).withColor(IDecoratedTerminal.BLUE),
            new TerminalBoundingBox(Component.translatable("mws.terminal.output_live"), 9, 11, 0, 11, 13, 1).withColor(0xFF8C00),
            new TerminalBoundingBox(Component.translatable("mws.terminal.output_neutral"), 12, 11, 0, 14, 13, 1).withColor(0x00B4B4)
    };

    public UpsBlock(Properties properties) {
        super(properties, TERMINALS);
        registerDefaultState(defaultBlockState().setValue(MODE, UpsMode.OFF).setValue(CHARGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODE, CHARGE);
    }

    /** Empty hand toggles the output (and resets a tripped overload). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        var ups = getBlockEntity(level, pos);
        if (ups == null)
            return InteractionResult.PASS;
        ups.setOutputEnabled(!ups.isOutputEnabled());
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5f, ups.isOutputEnabled() ? 1.2f : 0.8f);
        player.displayClientMessage(Component.translatable(ups.isOutputEnabled() ? "mws.message.ups_on" : "mws.message.ups_off"), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        var ups = getBlockEntity(level, pos);
        return ups == null ? 0 : (int) Math.round(ups.getChargeFraction() * 15);
    }

    @Override
    public Class<UpsBlockEntity> getBlockEntityClass() {
        return UpsBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends UpsBlockEntity> getBlockEntityType() {
        return MwsBlockEntities.UPS.get();
    }
}
