package ru.liko.pjmbasemod.client.radio;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import ru.liko.pjmbasemod.common.init.PjmSounds;

/**
 * Зацикленный «шум эфира» рации.
 * <p>
 * Реализован как {@link AbstractTickableSoundInstance}, а не обычный
 * {@code AbstractSoundInstance}, намеренно: движок звука каждый тик опрашивает
 * tickable-звуки и глушит любой, у которого {@link #isStopped()} вернул {@code true}.
 * Это устраняет гонку play→stop — если {@code SoundManager.stop(...)} вызвали
 * раньше, чем звук успел получить канал (короткая передача / лаг загрузки OGG),
 * обычный looping-звук зацикливался бы навсегда без ссылки для остановки. Здесь же
 * флаг {@code stopped} гарантирует, что следующий тик после {@link #stopSound()}
 * убьёт звук в любом случае.
 */
public class RadioStaticSoundInstance extends AbstractTickableSoundInstance {

    public RadioStaticSoundInstance() {
        super(PjmSounds.RADIO_BACKGROUND.get(), SoundSource.MASTER, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.35f;
        this.pitch = 1.0f;
        this.relative = true;
    }

    @Override
    public void tick() {
        // Ничего не делаем: звук продолжает зацикливаться, пока не выставлен stopped.
    }

    /** Помечает звук на остановку; движок заглушит его на ближайшем тике. */
    public void stopSound() {
        this.stop();
    }
}
