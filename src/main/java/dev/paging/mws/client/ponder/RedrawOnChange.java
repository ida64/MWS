package dev.paging.mws.client.ponder;

import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.PonderElementBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * MWS machines show what they are doing by changing their own block states, and the intake
 * removes water blocks. Ponder only redraws after its own world instructions, so watch the
 * scene and redraw whenever a block changes underneath it.
 */
final class RedrawOnChange extends PonderElementBase {
    private final Map<BlockPos, BlockState> last = new HashMap<>();

    @Override
    public void tick(PonderScene scene) {
        var world = scene.getWorld();
        var bounds = scene.getBounds();
        boolean changed = false;
        for (var pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            var state = world.getBlockState(pos);
            var previous = last.put(pos.immutable(), state);
            if (previous != null && previous != state)
                changed = true;
        }
        if (changed)
            scene.forEach(WorldSectionElement.class, WorldSectionElement::queueRedraw);
    }

    @Override
    public void reset(PonderScene scene) {
        last.clear();
    }
}
