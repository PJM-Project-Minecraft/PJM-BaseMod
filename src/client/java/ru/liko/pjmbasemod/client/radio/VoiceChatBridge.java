package ru.liko.pjmbasemod.client.radio;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@OnlyIn(Dist.CLIENT)
public final class VoiceChatBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String CLIENT_MANAGER_CLASS = "de.maxhenkel.voicechat.voice.client.ClientManager";
    private static final String PTT_KEY_HANDLER_CLASS = "de.maxhenkel.voicechat.voice.client.PTTKeyHandler";

    private static Object pttKeyHandlerInstance = null;
    private static Field pttKeyDownField = null;
    private static boolean bridgeAvailable = false;
    private static boolean initAttempted = false;

    private static boolean autoEngaged = false;

    private VoiceChatBridge() {
    }

    private static void ensureInit() {
        if (initAttempted) return;
        initAttempted = true;
        try {
            Class<?> clientManagerClass = Class.forName(CLIENT_MANAGER_CLASS);
            Method instanceMethod = clientManagerClass.getDeclaredMethod("instance");
            instanceMethod.setAccessible(true);
            Object clientManager = instanceMethod.invoke(null);

            Field pttHandlerField = clientManagerClass.getDeclaredField("pttKeyHandler");
            pttHandlerField.setAccessible(true);
            pttKeyHandlerInstance = pttHandlerField.get(clientManager);

            Class<?> pttHandlerClass = Class.forName(PTT_KEY_HANDLER_CLASS);
            pttKeyDownField = pttHandlerClass.getDeclaredField("pttKeyDown");
            pttKeyDownField.setAccessible(true);

            bridgeAvailable = true;
            LOGGER.info("[PJM Radio] VoiceChatBridge: ready — radio key auto-activates SVC PTT");
        } catch (ClassNotFoundException e) {
            LOGGER.warn("[PJM Radio] VoiceChatBridge: SVC classes not found ({}), user must hold SVC PTT manually",
                    e.getMessage());
        } catch (Exception e) {
            LOGGER.warn("[PJM Radio] VoiceChatBridge: init failed ({}), user must hold SVC PTT manually",
                    e.getMessage());
        }
    }

    public static void tick(boolean radioPttDown) {
        ensureInit();
        if (!bridgeAvailable) return;
        try {
            if (radioPttDown) {
                boolean currentlyDown = (boolean) pttKeyDownField.get(pttKeyHandlerInstance);
                if (!currentlyDown) {
                    pttKeyDownField.set(pttKeyHandlerInstance, true);
                }
                autoEngaged = true;
            } else if (autoEngaged) {
                pttKeyDownField.set(pttKeyHandlerInstance, false);
                autoEngaged = false;
            }
        } catch (Exception e) {
            LOGGER.debug("[PJM Radio] VoiceChatBridge tick error: {}", e.getMessage());
            bridgeAvailable = false;
        }
    }

    public static void enforceIfActive() {
        if (!autoEngaged || !bridgeAvailable) return;
        try {
            pttKeyDownField.set(pttKeyHandlerInstance, true);
        } catch (Exception e) {
            LOGGER.debug("[PJM Radio] VoiceChatBridge enforce error: {}", e.getMessage());
        }
    }

    public static void reset() {
        if (autoEngaged && bridgeAvailable) {
            try {
                pttKeyDownField.set(pttKeyHandlerInstance, false);
            } catch (Exception ignored) {
            }
        }
        autoEngaged = false;
    }
}
