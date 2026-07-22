package ru.liko.pjmbasemod.client.missile;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.liko.pjmbasemod.common.entity.StrategicMissileEntity;
import ru.liko.pjmbasemod.common.init.PjmSounds;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.List;

/**
 * Запускает двигатель и последовательные far→medium→close слои для отслеживаемых ракет.
 * Дополнительно считает per-missile коэффициент окклюзии (нет прямой видимости — звук
 * приглушается), который слои читают через {@link #muffle(UUID)}, и играет одноразовый
 * woosh в момент, когда ракета проносится мимо слушателя.
 */
public final class MissileSoundController {

    private static final double FLYBY_TRIGGER_DISTANCE = 60.0;
    private static final float MUFFLED_FACTOR = 0.3f;
    private static final float MUFFLE_SMOOTHING = 0.12f;

    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private MissileSoundController() {}

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (StrategicMissileEntity missile : mc.level.getEntitiesOfClass(
                StrategicMissileEntity.class, mc.player.getBoundingBox().inflate(600.0))) {
            if (missile.isRemoved()) continue;
            seen.add(missile.getUUID());
            Track track = TRACKS.computeIfAbsent(missile.getUUID(), ignored -> start(mc, missile));
            track.update(mc, missile);
        }
        TRACKS.entrySet().removeIf(entry -> {
            if (seen.contains(entry.getKey())) return false;
            entry.getValue().stop();
            return true;
        });
    }

    public static void reset() {
        TRACKS.values().forEach(Track::stop);
        TRACKS.clear();
    }

    /** Коэффициент приглушения [0..1] для слоёв этой ракеты (1 — прямая видимость). */
    static float muffle(UUID missileId) {
        Track track = TRACKS.get(missileId);
        return track == null ? 1.0f : track.muffle;
    }

    private static Track start(Minecraft mc, StrategicMissileEntity missile) {
        SoundEvent engineEvent = missile.isBallistic()
                ? PjmSounds.MISSILE_ENGINE_BALLISTIC.get()
                : PjmSounds.MISSILE_ENGINE_CRUISE.get();
        MissileEngineSoundInstance engine = new MissileEngineSoundInstance(missile, engineEvent);
        mc.getSoundManager().play(engine);
        MissileLayerSoundInstance far = new MissileLayerSoundInstance(
                PjmSounds.MISSILE_FAR.get(), missile, MissileLayerSoundInstance.Band.FAR);
        MissileLayerSoundInstance medium = new MissileLayerSoundInstance(
                PjmSounds.MISSILE_MEDIUM.get(), missile, MissileLayerSoundInstance.Band.MEDIUM);
        MissileLayerSoundInstance close = new MissileLayerSoundInstance(
                PjmSounds.MISSILE_CLOSE.get(), missile, MissileLayerSoundInstance.Band.CLOSE);
        mc.getSoundManager().play(far);
        mc.getSoundManager().play(medium);
        mc.getSoundManager().play(close);
        return new Track(engine, List.of(far, medium, close));
    }

    private static final class Track {
        private final MissileEngineSoundInstance engine;
        private final List<MissileLayerSoundInstance> layers;
        private float muffle = 1.0f;
        private double minDistance = Double.MAX_VALUE;
        private boolean flybyPlayed;

        private Track(MissileEngineSoundInstance engine, List<MissileLayerSoundInstance> layers) {
            this.engine = engine;
            this.layers = layers;
        }

        private void update(Minecraft mc, StrategicMissileEntity missile) {
            Vec3 eye = mc.player.getEyePosition();
            Vec3 pos = missile.position();
            boolean visible = mc.level.clip(new ClipContext(eye, pos,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player))
                    .getType() == HitResult.Type.MISS;
            float target = visible ? 1.0f : MUFFLED_FACTOR;
            muffle += (target - muffle) * MUFFLE_SMOOTHING;

            double distance = mc.player.distanceTo(missile);
            minDistance = Math.min(minDistance, distance);
            if (!flybyPlayed && minDistance <= FLYBY_TRIGGER_DISTANCE
                    && distance > minDistance + 4.0) {
                flybyPlayed = true;
                // Громкость по точке максимального сближения: в упор 1.0, на границе триггера ~0.4.
                float volume = 1.0f - (float) (Math.max(0.0, minDistance - 10.0)
                        / (FLYBY_TRIGGER_DISTANCE - 10.0)) * 0.6f;
                mc.level.playLocalSound(pos.x, pos.y, pos.z, PjmSounds.MISSILE_FLYBY.get(),
                        SoundSource.HOSTILE, volume * muffle,
                        0.95f + mc.level.random.nextFloat() * 0.1f, false);
            }
        }

        private void stop() {
            engine.stopSound();
            layers.forEach(MissileLayerSoundInstance::stopSound);
        }
    }
}
