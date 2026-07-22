package ru.liko.pjmbasemod.client.missile;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.liko.pjmbasemod.common.init.PjmSounds;
import ru.liko.pjmbasemod.common.network.packet.MissileAudioSyncPacket;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Звук ракет по виртуальным трекам из {@link MissileAudioSyncPacket}, а не по сущности:
 * сервер шлёт позиции всем в звуковом радиусе, поэтому ракету слышно и вне прогруза.
 * На каждый трек — двигатель + far/medium/close слои, per-track окклюзия (нет прямой
 * видимости — приглушение) и одноразовый flyby-woosh в точке максимального сближения.
 */
public final class MissileSoundController {

    private static final double FLYBY_TRIGGER_DISTANCE = 60.0;
    private static final float MUFFLED_FACTOR = 0.3f;
    private static final float MUFFLE_SMOOTHING = 0.12f;
    private static final int STALE_TICKS = 60;

    private static final Map<UUID, RemoteMissile> TRACKS = new HashMap<>();
    private static int clientTicks;

    private MissileSoundController() {}

    public static void handleSync(MissileAudioSyncPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        RemoteMissile track = TRACKS.get(packet.missileId());
        if (!packet.active()) {
            if (track != null) {
                track.stop();
                TRACKS.remove(packet.missileId());
            }
            return;
        }
        Vec3 position = new Vec3(packet.x(), packet.y(), packet.z());
        if (track == null) {
            track = new RemoteMissile(packet.ballistic(), position);
            TRACKS.put(packet.missileId(), track);
            track.startSounds(mc);
        }
        track.target = position;
        track.lastPacketTick = clientTicks;
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        clientTicks++;
        Iterator<RemoteMissile> iterator = TRACKS.values().iterator();
        while (iterator.hasNext()) {
            RemoteMissile track = iterator.next();
            if (clientTicks - track.lastPacketTick > STALE_TICKS) {
                track.stop();
                iterator.remove();
                continue;
            }
            track.tick(mc);
        }
    }

    public static void reset() {
        TRACKS.values().forEach(RemoteMissile::stop);
        TRACKS.clear();
    }

    /** Виртуальная ракета: сглаженная позиция, окклюзия и flyby-детект. */
    static final class RemoteMissile {
        final boolean ballistic;
        Vec3 position;
        Vec3 target;
        float muffle = 1.0f;
        boolean stopped;
        int lastPacketTick;

        private double minDistance = Double.MAX_VALUE;
        private boolean flybyPlayed;

        private RemoteMissile(boolean ballistic, Vec3 position) {
            this.ballistic = ballistic;
            this.position = position;
            this.target = position;
        }

        private void startSounds(Minecraft mc) {
            SoundEvent engineEvent = ballistic
                    ? PjmSounds.MISSILE_ENGINE_BALLISTIC.get()
                    : PjmSounds.MISSILE_ENGINE_CRUISE.get();
            MissileEngineSoundInstance engine = new MissileEngineSoundInstance(this, engineEvent);
            MissileLayerSoundInstance far = new MissileLayerSoundInstance(
                    PjmSounds.MISSILE_FAR.get(), this, MissileLayerSoundInstance.Band.FAR);
            MissileLayerSoundInstance medium = new MissileLayerSoundInstance(
                    PjmSounds.MISSILE_MEDIUM.get(), this, MissileLayerSoundInstance.Band.MEDIUM);
            MissileLayerSoundInstance close = new MissileLayerSoundInstance(
                    PjmSounds.MISSILE_CLOSE.get(), this, MissileLayerSoundInstance.Band.CLOSE);
            mc.getSoundManager().play(engine);
            mc.getSoundManager().play(far);
            mc.getSoundManager().play(medium);
            mc.getSoundManager().play(close);
        }

        private void tick(Minecraft mc) {
            // Пакеты приходят раз в 2 тика — интерполяция убирает ступеньки позиции.
            position = position.lerp(target, 0.4);

            Vec3 eye = mc.player.getEyePosition();
            boolean visible = mc.level.clip(new ClipContext(eye, position,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player))
                    .getType() == HitResult.Type.MISS;
            float muffleTarget = visible ? 1.0f : MUFFLED_FACTOR;
            muffle += (muffleTarget - muffle) * MUFFLE_SMOOTHING;

            double distance = eye.distanceTo(position);
            minDistance = Math.min(minDistance, distance);
            if (!flybyPlayed && minDistance <= FLYBY_TRIGGER_DISTANCE
                    && distance > minDistance + 4.0) {
                flybyPlayed = true;
                // Громкость по точке максимального сближения: в упор 1.0, на границе триггера ~0.4.
                float volume = 1.0f - (float) (Math.max(0.0, minDistance - 10.0)
                        / (FLYBY_TRIGGER_DISTANCE - 10.0)) * 0.6f;
                mc.level.playLocalSound(position.x, position.y, position.z,
                        PjmSounds.MISSILE_FLYBY.get(), SoundSource.HOSTILE, volume * muffle,
                        0.95f + mc.level.random.nextFloat() * 0.1f, false);
            }
        }

        private void stop() {
            stopped = true;
        }
    }
}
