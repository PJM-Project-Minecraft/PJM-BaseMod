package ru.liko.pjmbasemod.client.missile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Зацикленный mono-двигатель, привязанный к виртуальному треку ракеты. */
final class MissileEngineSoundInstance extends AbstractTickableSoundInstance {

    private final MissileSoundController.RemoteMissile track;

    MissileEngineSoundInstance(MissileSoundController.RemoteMissile track, SoundEvent sound) {
        super(sound, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.track = track;
        this.looping = true;
        this.delay = 0;
        this.relative = false;
        this.attenuation = Attenuation.NONE;
        this.pitch = track.ballistic ? 0.92f : 1.0f;
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
        float fade = 1.0f - smoothstep(180.0, 280.0, distance);
        // Тихое ядро двигателя; основную перспективу дают три crossfade-слоя.
        volume = 0.30f * fade * track.muffle;
    }

    private void updatePosition() {
        x = track.position.x;
        y = track.position.y;
        z = track.position.z;
    }

    private static float smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return (float) (t * t * (3.0 - 2.0 * t));
    }
}
