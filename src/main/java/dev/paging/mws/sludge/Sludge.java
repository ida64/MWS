package dev.paging.mws.sludge;

import dev.paging.mws.registry.MwsFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayDeque;
import java.util.HashSet;

public final class Sludge {
    private Sludge() {
    }

    private static boolean isSludgeSource(FluidState fluid) {
        return fluid.isSource() && fluid.getType().isSame(MwsFluids.SLUDGE.get());
    }

    /**
     * Dump one bucket of sludge as a source block in the nearest free spot around {@code origin},
     * spreading across existing sludge so dumps pile up into a growing pool.
     *
     * @return whether there was room
     */
    public static boolean spill(Level level, BlockPos origin, int maxDistance) {
        var queue = new ArrayDeque<BlockPos>();
        var seen = new HashSet<BlockPos>();
        queue.add(origin);
        seen.add(origin);
        while (!queue.isEmpty()) {
            var pos = queue.poll();
            var state = level.getBlockState(pos);
            boolean sludge = isSludgeSource(state.getFluidState());
            if (!pos.equals(origin) && !sludge && (state.isAir() || state.canBeReplaced(MwsFluids.SLUDGE.get()))) {
                level.setBlock(pos, MwsFluids.SLUDGE.get().defaultFluidState().createLegacyBlock(), 3);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 0.6f);
                return true;
            }
            if (!pos.equals(origin) && !sludge)
                continue;
            // Prefer flowing downhill, then sideways, then piling up.
            for (var next : new BlockPos[]{pos.below(), pos.north(), pos.south(), pos.east(), pos.west(), pos.above()}) {
                if (next.distManhattan(origin) <= maxDistance && level.isLoaded(next) && seen.add(next))
                    queue.add(next);
            }
        }
        return false;
    }
}
