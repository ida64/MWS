package dev.paging.mws.sludge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Datacenter cooling-tower blowdown: warm, concentrated, and full of everything the water
 * treatment was supposed to catch. It never evaporates, sponges won't touch it, and it slowly
 * kills whatever it sits next to.
 */
public class SludgeBlock extends LiquidBlock {
    public SludgeBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (!level.isClientSide && entity instanceof LivingEntity living && level.getGameTime() % 20 == 0) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        var target = pos.relative(Direction.getRandom(random));
        var victim = level.getBlockState(target);
        if (victim.is(Blocks.GRASS_BLOCK) || victim.is(Blocks.MYCELIUM) || victim.is(Blocks.PODZOL) || victim.is(Blocks.FARMLAND)) {
            level.setBlockAndUpdate(target, Blocks.COARSE_DIRT.defaultBlockState());
        } else if (victim.is(BlockTags.LEAVES) || victim.getBlock() instanceof BushBlock) {
            level.destroyBlock(target, false);
        }
    }
}
