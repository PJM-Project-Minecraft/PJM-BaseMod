package ru.liko.pjmbasemod.common.voice;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.lang.reflect.Method;

public final class VoicechatBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final boolean PRESENT;
    private static final Method GET_INSTANCE;
    private static final Method CAN_USE_TEAM_RADIO;
    private static final Method ON_PLAYER_START_RADIO;
    private static final Method ON_PLAYER_STOP_RADIO;
    private static final Method ON_SERVER_STOP;

    static {
        boolean present = false;
        Method get = null;
        Method canUseTeamRadio = null;
        Method startRadio = null;
        Method stopRadio = null;
        Method serverStop = null;
        try {
            Class.forName("de.maxhenkel.voicechat.api.VoicechatPlugin");
            Class<?> pluginCls = Class.forName("ru.liko.pjmbasemod.common.voice.PjmVoiceChatPlugin");
            get = pluginCls.getMethod("get");
            canUseTeamRadio = pluginCls.getMethod("canUseTeamRadio", ServerPlayer.class);
            startRadio = pluginCls.getMethod("onPlayerStartRadio", ServerPlayer.class);
            stopRadio = pluginCls.getMethod("onPlayerStopRadio", ServerPlayer.class);
            serverStop = pluginCls.getMethod("onServerStop");
            present = true;
        } catch (Throwable t) {
            LOGGER.info("VoicechatBridge: voicechat api not available, radio integration disabled ({})",
                    t.getClass().getSimpleName());
        }
        PRESENT = present;
        GET_INSTANCE = get;
        CAN_USE_TEAM_RADIO = canUseTeamRadio;
        ON_PLAYER_START_RADIO = startRadio;
        ON_PLAYER_STOP_RADIO = stopRadio;
        ON_SERVER_STOP = serverStop;
    }

    private VoicechatBridge() {}

    public static boolean isPresent() {
        return PRESENT;
    }

    public static boolean canUseTeamRadio(ServerPlayer player) {
        if (!PRESENT || player == null) return false;
        try {
            Object plugin = GET_INSTANCE.invoke(null);
            if (plugin == null) return false;
            return Boolean.TRUE.equals(CAN_USE_TEAM_RADIO.invoke(plugin, player));
        } catch (Throwable t) {
            LOGGER.debug("VoicechatBridge.canUseTeamRadio failed: {}", t.getMessage());
            return false;
        }
    }

    public static void onPlayerStartRadio(ServerPlayer player) {
        if (!PRESENT || player == null) return;
        try {
            Object plugin = GET_INSTANCE.invoke(null);
            if (plugin != null) {
                ON_PLAYER_START_RADIO.invoke(plugin, player);
            }
        } catch (Throwable t) {
            LOGGER.warn("VoicechatBridge.onPlayerStartRadio failed", t);
        }
    }

    public static void onPlayerStopRadio(ServerPlayer player) {
        if (!PRESENT || player == null) return;
        try {
            Object plugin = GET_INSTANCE.invoke(null);
            if (plugin != null) {
                ON_PLAYER_STOP_RADIO.invoke(plugin, player);
            }
        } catch (Throwable t) {
            LOGGER.warn("VoicechatBridge.onPlayerStopRadio failed", t);
        }
    }

    public static void onServerStop() {
        if (!PRESENT) return;
        try {
            Object plugin = GET_INSTANCE.invoke(null);
            if (plugin != null) {
                ON_SERVER_STOP.invoke(plugin);
            }
        } catch (Throwable t) {
            LOGGER.warn("VoicechatBridge.onServerStop failed", t);
        }
    }
}
