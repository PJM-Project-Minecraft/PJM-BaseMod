package ru.liko.pjmbasemod.client.missile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Движущийся loop-слой виртуального трека; три экземпляра смешиваются по расстоянию. */
final class MissileLayerSoundInstance extends AbstractTickableSoundInstance {

    enum Band { FAR, MEDIUM, CLOSE }

    private final MissileSoundController.RemoteMissile track;
    private final Band band;

    MissileLayerSoundInstance(SoundEvent sound, MissileSoundController.RemoteMissile track, Band band) {
        super(sound, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.track = track;
        this.band = band;
        this.looping = true;
        this.delay = 0;
        this.relative = false;
        this.attenuation = Attenuation.NONE;
        this.pitch = track.ballistic ? 0.9f : 1.0f;
        this.volume = 0.0f;
        updatePosition();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (track.stopped) {
            stop();
            return;
        }
        updatePosition();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            volume = 0.0f;
            return;
        }
        double distance = mc.player.getEyePosition().distanceTo(track.position);
        float base = switch (band) {
            case CLOSE -> 0.95f * (1.0f - smoothstep(36.0, 82.0, distance));
            case MEDIUM -> 0.82f * smoothstep(28.0, 62.0, distance)
                    * (1.0f - smoothstep(125.0, 185.0, distance));
            case FAR -> 0.68f * smoothstep(90.0, 145.0, distance)
                    * (1.0f - smoothstep(450.0, 700.0, distance));
        };
        volume = base * track.muffle;
    }

    private void updatePosition() {
        this.x = track.position.x;
        this.y = track.position.y;
        this.z = track.position.z;
    }

    private static float smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return (float) (t * t * (3.0 - 2.0 * t));
    }
}
