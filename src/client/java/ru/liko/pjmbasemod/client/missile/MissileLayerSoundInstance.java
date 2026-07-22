package ru.liko.pjmbasemod.client.missile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import ru.liko.pjmbasemod.common.entity.StrategicMissileEntity;

/** Движущийся loop-слой; три экземпляра плавно смешиваются по расстоянию до слушателя. */
final class MissileLayerSoundInstance extends AbstractTickableSoundInstance {

    enum Band { FAR, MEDIUM, CLOSE }

    private final StrategicMissileEntity missile;
    private final Band band;

    MissileLayerSoundInstance(SoundEvent sound, StrategicMissileEntity missile, Band band) {
        super(sound, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.missile = missile;
        this.band = band;
        this.looping = true;
        this.delay = 0;
        this.relative = false;
        this.attenuation = Attenuation.NONE;
        this.pitch = missile.isBallistic() ? 0.9f : 1.0f;
        this.volume = 0.0f;
        updatePosition();
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
        volume = switch (band) {
            case CLOSE -> 0.95f * (1.0f - smoothstep(36.0, 82.0, distance));
            case MEDIUM -> 0.82f * smoothstep(28.0, 62.0, distance)
                    * (1.0f - smoothstep(125.0, 185.0, distance));
            case FAR -> 0.68f * smoothstep(90.0, 145.0, distance)
                    * (1.0f - smoothstep(250.0, 340.0, distance));
        };
    }

    void stopSound() { stop(); }

    private void updatePosition() {
        this.x = missile.getX();
        this.y = missile.getY();
        this.z = missile.getZ();
    }

    private static float smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return (float) (t * t * (3.0 - 2.0 * t));
    }
}
