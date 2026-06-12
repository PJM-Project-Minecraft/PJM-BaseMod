package ru.liko.pjmbasemod.client.radio;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import ru.liko.pjmbasemod.common.init.PjmSounds;

public class RadioStaticSoundInstance extends AbstractSoundInstance {

    private boolean stopped = false;

    public RadioStaticSoundInstance() {
        super(PjmSounds.RADIO_BACKGROUND.get(), SoundSource.MASTER, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.35f;
        this.pitch = 1.0f;
        this.relative = true;
    }

    public void stopSound() {
        this.stopped = true;
        this.looping = false;
    }

    public boolean isStopped() {
        return stopped;
    }
}
