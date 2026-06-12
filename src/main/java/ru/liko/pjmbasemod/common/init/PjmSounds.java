package ru.liko.pjmbasemod.common.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.pjmbasemod.Pjmbasemod;

public final class PjmSounds {

    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, Pjmbasemod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> UI_MENU_PRESS         = reg("ui.menu.press");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_MENU_SHARED        = reg("ui.menu.shared");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_MOUSE_CLICK        = reg("ui.mouse.click");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_CLASS_CHANGE       = reg("ui.class.change");
    public static final DeferredHolder<SoundEvent, SoundEvent> MENU_LOADING          = reg("menu.loading");
    public static final DeferredHolder<SoundEvent, SoundEvent> MENU_MUSIC            = reg("menu.music");
    public static final DeferredHolder<SoundEvent, SoundEvent> MENU_JOIN             = reg("menu.join");
    public static final DeferredHolder<SoundEvent, SoundEvent> MENU_PROMOTED         = reg("menu.promoted");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_START           = reg("radio.start");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_END             = reg("radio.end");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_BACKGROUND      = reg("radio.background");

    private PjmSounds() {}

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
