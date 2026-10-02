package dev.paging.mws.client;

import dev.paging.mws.chiller.ChillerBlockEntity;
import dev.paging.mws.chiller.ChillerStatus;
import dev.paging.mws.rack.ServerRackBlockEntity;
import dev.paging.mws.registry.MwsSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Client-side fan loops. Called from block entity client ticks only. */
public final class FanSounds {
    private static final Map<BlockEntity, Fan> PLAYING = new WeakHashMap<>();

    private FanSounds() {
    }

    public static void tick(ServerRackBlockEntity rack) {
        play(rack, rack::fanSpeed, 0.75f, 0.6f, 0.12f);
    }

    public static void tick(ChillerBlockEntity chiller) {
        // A chiller has one big slow fan: lower and louder, spinning with its cooling load.
        play(chiller, () -> chiller.getStatus() == ChillerStatus.COOLING ? 0.5f + 0.5f * (float) Math.min(1, chiller.getCoolingLoad() / 4000) : 0f,
                0.5f, 0.3f, 0.2f);
    }

    private static void play(BlockEntity be, Supplier<Float> speed, float pitch, float pitchRange, float volume) {
        // Machines in Ponder scenes tick too, but they are not in the world the player hears.
        if (be.getLevel() != Minecraft.getInstance().level)
            return;
        var fan = PLAYING.get(be);
        if (fan != null && !fan.isStopped())
            return;
        if (speed.get() <= 0 || ClientConfig.FAN_VOLUME.get() <= 0)
            return;
        fan = new Fan(be, speed, pitch, pitchRange, volume);
        PLAYING.put(be, fan);
        Minecraft.getInstance().getSoundManager().play(fan);
    }

    private static final class Fan extends AbstractTickableSoundInstance {
        private final BlockEntity be;
        private final Supplier<Float> speed;
        private final float basePitch;
        private final float pitchRange;
        private final float maxVolume;
        private float current;

        Fan(BlockEntity be, Supplier<Float> speed, float basePitch, float pitchRange, float maxVolume) {
            super(MwsSounds.RACK_FAN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.be = be;
            this.speed = speed;
            this.basePitch = basePitch;
            this.pitchRange = pitchRange;
            this.maxVolume = maxVolume;
            var center = be.getBlockPos().getCenter();
            x = center.x;
            y = center.y;
            z = center.z;
            looping = true;
            delay = 0;
            volume = 0.01f;
            pitch = basePitch;
            attenuation = Attenuation.LINEAR;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (be.isRemoved() || be.getLevel() != Minecraft.getInstance().level) {
                stop();
                return;
            }
            // Fans spin up and down over a couple of seconds rather than snapping.
            float target = Math.max(0, speed.get());
            current += (target - current) * 0.04f;
            if (target == 0 && current < 0.02f) {
                stop();
                return;
            }
            volume = Math.max(0.001f, maxVolume * current * ClientConfig.FAN_VOLUME.get().floatValue());
            pitch = basePitch + pitchRange * current;
        }
    }
}
