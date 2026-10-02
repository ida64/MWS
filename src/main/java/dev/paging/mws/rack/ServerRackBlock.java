package dev.paging.mws.rack;

import dev.paging.mws.power.MachineBlock;
import dev.paging.mws.registry.MwsBlockEntities;
import dev.paging.mws.registry.MwsItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;

/**
 * A server chassis. Racks stacked on top of each other form one cabinet: the bottom rack carries
 * the cabinet's two power inlets (Feed A and Feed B) and powers everything above it.
 */
public class ServerRackBlock extends MachineBlock<ServerRackBlockEntity> {
    public static final EnumProperty<RackStatus> STATUS = EnumProperty.create("status", RackStatus.class);
    public static final IntegerProperty BLADES = IntegerProperty.create("blades", 0, ServerRackBlockEntity.MAX_BLADES);
    public static final EnumProperty<RackSection> SECTION = EnumProperty.create("section", RackSection.class);

    /** Dual inlets on the back panel of the cabinet base. Terminals 0/1 (Feed A) are also where cords plug in. */
    private static final TerminalBoundingBox[] FEED_TERMINALS = {
            new TerminalBoundingBox(Component.translatable("mws.terminal.feed_a_live"), 2, 11, 0, 4, 13, 1).withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(Component.translatable("mws.terminal.feed_a_neutral"), 5, 11, 0, 7, 13, 1).withColor(IDecoratedTerminal.BLUE),
            new TerminalBoundingBox(Component.translatable("mws.terminal.feed_b_live"), 9, 11, 0, 11, 13, 1).withColor(0xFF8C00),
            new TerminalBoundingBox(Component.translatable("mws.terminal.feed_b_neutral"), 12, 11, 0, 14, 13, 1).withColor(0x00B4B4)
    };
    private static final TerminalBoundingBox[] NO_TERMINALS = new TerminalBoundingBox[FEED_TERMINALS.length];

    public ServerRackBlock(Properties properties) {
        super(properties, null);
        registerDefaultState(defaultBlockState()
                .setValue(STATUS, RackStatus.OFF)
                .setValue(BLADES, 0)
                .setValue(SECTION, RackSection.SINGLE));
        // Only the cabinet base has inlets; the slots stay (as nulls) so every state has four terminals.
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStatesExcept(state -> state.getValue(SECTION).isBase() ? rotated(FEED_TERMINALS, state) : NO_TERMINALS, STATUS, BLADES)
                .withShapeMapper(state -> Shapes.block())
                .build());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STATUS, BLADES, SECTION);
    }

    public static boolean isBase(BlockState state) {
        return state.getValue(SECTION).isBase();
    }

    private boolean isRack(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(this);
    }

    /** Stacking onto a cabinet lines the new rack up with it. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = super.getStateForPlacement(context);
        if (state == null)
            return null;
        var level = context.getLevel();
        var pos = context.getClickedPos();
        for (var neighbor : new BlockPos[]{pos.below(), pos.above()}) {
            var other = level.getBlockState(neighbor);
            if (other.is(this)) {
                state = state.setValue(HORIZONTAL_FACING, other.getValue(HORIZONTAL_FACING));
                break;
            }
        }
        return state.setValue(SECTION, RackSection.of(isRack(level, pos.below()), isRack(level, pos.above())));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction.getAxis() == Direction.Axis.Y)
            state = state.setValue(SECTION, RackSection.of(isRack(level, pos.below()), isRack(level, pos.above())));
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /** Device connectors and cords only fit the inlet panel on the cabinet base. */
    @Override
    public boolean canConnect(LevelReader world, BlockPos pos, BlockState state, Direction side) {
        return isBase(state) && super.canConnect(world, pos, state, side);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(MwsItems.SERVER_BLADE.get()))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;
        var rack = getBlockEntity(level, pos);
        if (rack == null || !rack.insertBlade())
            return ItemInteractionResult.FAIL;
        if (!player.getAbilities().instabuild)
            stack.shrink(1);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.6f);
        return ItemInteractionResult.SUCCESS;
    }

    /** Sneak with an empty hand to pull a blade out. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        var rack = getBlockEntity(level, pos);
        if (rack == null || !rack.removeBlade())
            return InteractionResult.PASS;
        player.getInventory().placeItemBackInInventory(new ItemStack(MwsItems.SERVER_BLADE.get()));
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.6f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            var rack = getBlockEntity(level, pos);
            if (rack != null) {
                if (rack.getBlades() > 0)
                    popResource(level, pos, new ItemStack(MwsItems.SERVER_BLADE.get(), rack.getBlades()));
                // The cabinet's data lives in its base; the rack above inherits it.
                if (isBase(state) && level.getBlockEntity(pos.above()) instanceof ServerRackBlockEntity above)
                    rack.handOffStorage(above);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public Class<ServerRackBlockEntity> getBlockEntityClass() {
        return ServerRackBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ServerRackBlockEntity> getBlockEntityType() {
        return MwsBlockEntities.SERVER_RACK.get();
    }
}
