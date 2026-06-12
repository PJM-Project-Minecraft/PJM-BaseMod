package ru.liko.pjmbasemod.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import ru.liko.pjmbasemod.common.init.PjmSounds;

/** Обёртки над UI-звуками: клики, наведение, смена класса. */
public final class PjmUiSounds {

    private PjmUiSounds() {}

    public static void playPress(SoundManager sm)       { play(sm, PjmSounds.UI_MENU_PRESS); }
    public static void playShared(SoundManager sm)      { play(sm, PjmSounds.UI_MENU_SHARED); }
    public static void playClick(SoundManager sm)       { play(sm, PjmSounds.UI_MOUSE_CLICK); }
    public static void playHover(SoundManager sm)       { play(sm, PjmSounds.UI_MENU_SHARED); }
    public static void playClassChange(SoundManager sm) { play(sm, PjmSounds.UI_CLASS_CHANGE); }
    public static void playPromoted(SoundManager sm)    { play(sm, PjmSounds.MENU_PROMOTED); }

    public static void playPress()       { playOnClient(PjmSounds.UI_MENU_PRESS); }
    public static void playShared()      { playOnClient(PjmSounds.UI_MENU_SHARED); }
    public static void playClick()       { playOnClient(PjmSounds.UI_MOUSE_CLICK); }
    public static void playHover()       { playOnClient(PjmSounds.UI_MENU_SHARED); }
    public static void playClassChange() { playOnClient(PjmSounds.UI_CLASS_CHANGE); }
    public static void playPromoted()    { playOnClient(PjmSounds.MENU_PROMOTED); }

    private static void play(SoundManager sm, DeferredHolder<SoundEvent, SoundEvent> holder) {
        if (sm == null || holder == null || !holder.isBound()) return;
        sm.play(SimpleSoundInstance.forUI(holder.get(), 1.0f));
    }

    private static void playOnClient(DeferredHolder<SoundEvent, SoundEvent> holder) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) play(mc.getSoundManager(), holder);
    }
}
