package ru.liko.pjmbasemod.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import ru.liko.pjmbasemod.Pjmbasemod;

@EventBusSubscriber(modid = Pjmbasemod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ModKeyBindings {

    public static final String CATEGORY = "key.categories." + Pjmbasemod.MODID;

    public static final KeyMapping OPEN_RADIAL_MENU = new KeyMapping(
            "key.pjmbasemod.radial_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping RADIAL_MENU = OPEN_RADIAL_MENU;

    public static final KeyMapping CYCLE_CHAT_MODE = new KeyMapping(
            "key.pjmbasemod.chat_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);

    public static final KeyMapping COMMAND_RADIO = new KeyMapping(
            "key.pjmbasemod.command_radio", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    public static final KeyMapping OPEN_MODERATION = new KeyMapping(
            "key.pjmbasemod.moderation", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);

    private ModKeyBindings() {}

    @SubscribeEvent
    public static void onRegister(RegisterKeyMappingsEvent event) {
        event.register(OPEN_RADIAL_MENU);
        event.register(CYCLE_CHAT_MODE);
        event.register(COMMAND_RADIO);
        event.register(OPEN_MODERATION);
    }
}
