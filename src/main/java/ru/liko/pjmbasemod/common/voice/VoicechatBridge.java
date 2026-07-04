package ru.liko.pjmbasemod.common.voice;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

public final class VoicechatBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final boolean PRESENT;
    private static final Method GET_INSTANCE;
    private static final Method CAN_USE_TEAM_RADIO;
    private static final Method ON_PLAYER_START_RADIO;
    private static final Method ON_PLAYER_STOP_RADIO;
    private static final Method ON_SERVER_STOP;
    private static final Method SET_MUTED;
    private static final Method IS_MUTED;

    static {
        boolean present = false;
        Method get = null;
        Method canUseTeamRadio = null;
        Method startRadio = null;
        Method stopRadio = null;
        Method serverStop = null;
        Method setMuted = null;
        Method isMuted = null;
        try {
            Class.forName("de.maxhenkel.voicechat.api.VoicechatPlugin");
            Class<?> pluginCls = Class.forName("ru.liko.pjmbasemod.common.voice.PjmVoiceChatPlugin");
            get = pluginCls.getMethod("get");
            canUseTeamRadio = pluginCls.getMethod("canUseTeamRadio", ServerPlayer.class);
            startRadio = pluginCls.getMethod("onPlayerStartRadio", ServerPlayer.class);
            stopRadio = pluginCls.getMethod("onPlayerStopRadio", ServerPlayer.class);
            serverStop = pluginCls.getMethod("onServerStop");
            setMuted = pluginCls.getMethod("setMuted", UUID.class, boolean.class);
            isMuted = pluginCls.getMethod("isMuted", UUID.class);
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
        SET_MUTED = setMuted;
        IS_MUTED = isMuted;
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

    /** Наложить/снять войс-мут модерации. No-op, если войс-мод отсутствует. */
    public static void setVoiceMuted(UUID playerId, boolean muted) {
        if (!PRESENT || playerId == null) return;
        try {
            Object plugin = GET_INSTANCE.invoke(null);
            if (plugin != null) {
                SET_MUTED.invoke(plugin, playerId, muted);
            }
        } catch (Throwable t) {
            LOGGER.warn("VoicechatBridge.setVoiceMuted failed", t);
        }
    }

    public static boolean isVoiceMuted(UUID playerId) {
        if (!PRESENT || playerId == null) return false;
        try {
            Object plugin = GET_INSTANCE.invoke(null);
            if (plugin != null) {
                return Boolean.TRUE.equals(IS_MUTED.invoke(plugin, playerId));
            }
        } catch (Throwable t) {
            LOGGER.debug("VoicechatBridge.isVoiceMuted failed: {}", t.getMessage());
        }
        return false;
    }
}
