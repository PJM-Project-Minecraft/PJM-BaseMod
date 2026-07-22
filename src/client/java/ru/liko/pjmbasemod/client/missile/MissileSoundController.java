package ru.liko.pjmbasemod.client.missile;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import ru.liko.pjmbasemod.common.entity.StrategicMissileEntity;
import ru.liko.pjmbasemod.common.init.PjmSounds;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.List;

/** Запускает двигатель и последовательные far→medium→close слои для отслеживаемых ракет. */
public final class MissileSoundController {

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
            TRACKS.computeIfAbsent(missile.getUUID(), ignored -> start(mc, missile));
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

        private Track(MissileEngineSoundInstance engine, List<MissileLayerSoundInstance> layers) {
            this.engine = engine;
            this.layers = layers;
        }

        private void stop() {
            engine.stopSound();
            layers.forEach(MissileLayerSoundInstance::stopSound);
        }
    }
}
