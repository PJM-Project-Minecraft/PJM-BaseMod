package ru.liko.pjmbasemod.client.gui.widget;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import ru.liko.pjmbasemod.client.gui.PjmUiSounds;

/** Кнопка со звуком интерфейса ({@link PjmUiSounds}). */
public final class PjmUiButton extends Button {

    public PjmUiButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        PjmUiSounds.playPress(soundManager);
    }
}
