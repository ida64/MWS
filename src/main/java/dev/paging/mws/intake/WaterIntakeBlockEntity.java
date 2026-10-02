package dev.paging.mws.intake;

import dev.paging.mws.Config;
import dev.paging.mws.power.Goggles;
import dev.paging.mws.power.Grid;
import dev.paging.mws.power.MachineBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;

/**
 * Electric water intake. Every few seconds it reaches through connected water and takes the
 * nearest source block it can find: ponds, rivers, a neighbour's moat, the waterlogged
 * stairs of their porch. Pipes (with a Create pump) pull the water out of its buffer tank.
 */
public class WaterIntakeBlockEntity extends MachineBlockEntity {
    private static final int MAX_SEARCH = 4096;

    public enum Status {
        NO_POWER(ChatFormatting.RED), PUMPING(ChatFormatting.AQUA), FULL(ChatFormatting.GRAY), DRY(ChatFormatting.GOLD);

        public final ChatFormatting color;

        Status(ChatFormatting color) {
            this.color = color;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final FluidTank tank;
    private final IFluidHandler output;
    private int cooldown;
    private long stolen;
    private Status status = Status.NO_POWER;

    public WaterIntakeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        tank = new FluidTank(Config.INTAKE_TANK.get(), stack -> stack.is(Fluids.WATER)) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
        output = new DrainOnly(tank);
    }

    @Override
    protected double idlePower() {
        return 10;
    }

    @Override
    protected void serverTick() {
        if (!Grid.runnable(voltage)) {
            status = Status.NO_POWER;
        } else if (tank.getSpace() < 1000) {
            status = Status.FULL;
        } else if (--cooldown <= 0) {
            cooldown = Config.INTAKE_INTERVAL.get();
            status = steal() ? Status.PUMPING : Status.DRY;
        }
        setLoadPower(status == Status.PUMPING ? Config.INTAKE_POWER.get() : idlePower());

        var state = getBlockState();
        if (state.getBlock() instanceof WaterIntakeBlock) {
            boolean active = status == Status.PUMPING;
            if (state.getValue(WaterIntakeBlock.ACTIVE) != active)
                level.setBlock(worldPosition, state.setValue(WaterIntakeBlock.ACTIVE, active), 3);
        }
    }

    private boolean steal() {
        var source = findSource();
        if (source == null)
            return false;
        var state = level.getBlockState(source);
        if (!(state.getBlock() instanceof BucketPickup pickup))
            return false;
        var taken = pickup.pickupBlock(null, level, source, state);
        if (!taken.is(Items.WATER_BUCKET))
            return false;
        tank.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        ++stolen;
        level.playSound(null, source, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.6f, 0.8f);
        if (level instanceof ServerLevel server) {
            var c = source.getCenter();
            server.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.05);
        }
        return true;
    }

    /** Breadth-first through connected water, so the closest source goes first. */
    @Nullable
    private BlockPos findSource() {
        int range = Config.INTAKE_RANGE.get();
        var queue = new ArrayDeque<BlockPos>();
        var seen = new HashSet<BlockPos>();
        for (var side : Direction.values()) {
            var start = worldPosition.relative(side);
            seen.add(start);
            queue.add(start);
        }
        while (!queue.isEmpty() && seen.size() < MAX_SEARCH) {
            var pos = queue.poll();
            if (!level.isLoaded(pos))
                continue;
            var fluid = level.getFluidState(pos);
            if (!fluid.is(FluidTags.WATER))
                continue;
            if (fluid.isSource() && level.getBlockState(pos).getBlock() instanceof BucketPickup)
                return pos;
            for (var side : Direction.values()) {
                var next = pos.relative(side);
                if (next.distManhattan(worldPosition) <= range && seen.add(next))
                    queue.add(next);
            }
        }
        return null;
    }

    public IFluidHandler getFluidHandler() {
        return output;
    }

    public FluidTank getTank() {
        return tank;
    }

    public Status getStatus() {
        return status;
    }

    public long getStolen() {
        return stolen;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putLong("Stolen", stolen);
        tag.putInt("Status", status.ordinal());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        stolen = tag.getLong("Stolen");
        status = Status.values()[Math.floorMod(tag.getInt("Status"), Status.values().length)];
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        Goggles.title(tooltip, "water_intake");
        Goggles.line(tooltip, "status", Component.translatable("mws.intake_status." + status.id()), status.color);
        electricalLines(tooltip);
        Goggles.line(tooltip, "water", String.format("%,d / %,d mB", tank.getFluidAmount(), tank.getCapacity()), ChatFormatting.BLUE);
        Goggles.line(tooltip, "stolen", String.format("%,d", stolen), ChatFormatting.AQUA);
        return true;
    }

    /** Pipes may take water out; nothing goes back in. */
    private record DrainOnly(FluidTank tank) implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            return tank.getFluid();
        }

        @Override
        public int getTankCapacity(int index) {
            return tank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return tank.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return tank.drain(maxDrain, action);
        }
    }
}
