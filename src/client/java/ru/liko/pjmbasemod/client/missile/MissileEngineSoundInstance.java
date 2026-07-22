package ru.liko.pjmbasemod.client.missile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import ru.liko.pjmbasemod.common.entity.StrategicMissileEntity;

/** Зацикленный mono-двигатель, привязанный к движущейся сущности ракеты. */
final class MissileEngineSoundInstance extends AbstractTickableSoundInstance {

    private final StrategicMissileEntity missile;

    MissileEngineSoundInstance(StrategicMissileEntity missile, SoundEvent sound) {
        super(sound, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.missile = missile;
        this.looping = true;
        this.delay = 0;
        this.relative = false;
        this.attenuation = Attenuation.NONE;
        this.pitch = missile.isBallistic() ? 0.92f : 1.0f;
        updatePosition();
        this.volume = 0.0f;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (missile.isRemoved()) {
            stop();
            return;
        }
        updatePosition();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            volume = 0.0f;
            return;
        }
        double distance = mc.player.distanceTo(missile);
        float fade = 1.0f - smoothstep(180.0, 280.0, distance);
        // Тихое ядро двигателя; основную перспективу дают три crossfade-слоя.
        volume = 0.30f * fade * MissileSoundController.muffle(missile.getUUID());
    }

    void stopSound() { stop(); }

    private void updatePosition() {
        x = missile.getX();
        y = missile.getY();
        z = missile.getZ();
    }

    private static float smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return (float) (t * t * (3.0 - 2.0 * t));
    }
}
