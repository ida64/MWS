package dev.paging.mws.power;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import org.patryk3211.powergrid.electricity.deviceconnector.IAcceptConnector;

/**
 * Full-cube horizontal machine with its power terminals on the back panel. It also accepts
 * Power Grid's power cords and device connectors, which bind to terminals 0 and 1.
 */
public abstract class MachineBlock<T extends BlockEntity> extends HorizontalElectricBlock implements IBE<T>, IAcceptConnector {
    /** Live and neutral on the back panel, in the north-facing orientation. */
    public static final TerminalBoundingBox[] POWER_TERMINALS = {
            new TerminalBoundingBox(IDecoratedTerminal.POSITIVE, 3, 11, 0, 6, 14, 1).withColor(IDecoratedTerminal.RED),
            new TerminalBoundingBox(IDecoratedTerminal.NEGATIVE, 10, 11, 0, 13, 14, 1).withColor(IDecoratedTerminal.BLUE)
    };

    public MachineBlock(Properties properties) {
        this(properties, POWER_TERMINALS);
    }

    public MachineBlock(Properties properties, TerminalBoundingBox[] terminals) {
        super(properties);
        if (terminals != null)
            setTerminalCollection(horizontalNorthTerminals(this, terminals, Shapes.block()));
    }

    /** Rotate north-facing terminals to match the block's facing. */
    public static TerminalBoundingBox[] rotated(TerminalBoundingBox[] terminals, BlockState state) {
        return BlockStateTerminalCollection.each(terminals, terminal -> switch (state.getValue(HORIZONTAL_FACING)) {
            case SOUTH -> terminal.rotateAroundY(180);
            case EAST -> terminal.rotateAroundY(90);
            case WEST -> terminal.rotateAroundY(-90);
            default -> terminal;
        });
    }

    /** Server equipment takes any wire type, not just light wire. Only wire items ever ask. */
    @Override
    public boolean accepts(ItemStack wireStack) {
        return true;
    }
}
