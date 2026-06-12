package ru.liko.pjmbasemod.common.frontline.bluemap;

import net.minecraft.server.MinecraftServer;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.lang.reflect.Method;
import java.util.Map;

public final class FrontlineBlueMapService {

    private static final boolean PRESENT;
    private static final Method ON_SERVER_STARTED;
    private static final Method ON_SERVER_TICK;
    private static final Method ON_SERVER_STOPPING;
    private static final Method REQUEST_SYNC;
    private static final Method FORCE_SYNC_NOW;
    private static final Method STATUS;

    static {
        boolean present = false;
        Method onServerStarted = null;
        Method onServerTick = null;
        Method onServerStopping = null;
        Method requestSync = null;
        Method forceSyncNow = null;
        Method status = null;
        try {
            Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
            Class<?> runtimeClass = Class.forName("ru.liko.pjmbasemod.common.frontline.bluemap.FrontlineBlueMapRuntime");
            onServerStarted = runtimeClass.getMethod("onServerStarted", MinecraftServer.class);
            onServerTick = runtimeClass.getMethod("onServerTick", MinecraftServer.class);
            onServerStopping = runtimeClass.getMethod("onServerStopping");
            requestSync = runtimeClass.getMethod("requestSync", String.class);
            forceSyncNow = runtimeClass.getMethod("forceSyncNow", MinecraftServer.class, String.class);
            status = runtimeClass.getMethod("status");
            present = true;
        } catch (Throwable t) {
            Pjmbasemod.LOGGER.info("[FRONTLINE][BlueMap] API not found, integration disabled ({})", t.getClass().getSimpleName());
        }
        PRESENT = present;
        ON_SERVER_STARTED = onServerStarted;
        ON_SERVER_TICK = onServerTick;
        ON_SERVER_STOPPING = onServerStopping;
        REQUEST_SYNC = requestSync;
        FORCE_SYNC_NOW = forceSyncNow;
        STATUS = status;
    }

    private FrontlineBlueMapService() {}

    public static void onServerStarted(MinecraftServer server) {
        invokeVoid(ON_SERVER_STARTED, server);
    }

    public static void onServerTick(MinecraftServer server) {
        invokeVoid(ON_SERVER_TICK, server);
    }

    public static void onServerStopping() {
        invokeVoid(ON_SERVER_STOPPING);
    }

    public static void requestSync(String reason) {
        invokeVoid(REQUEST_SYNC, reason);
    }

    public static boolean forceSyncNow(MinecraftServer server, String reason) {
        if (!PRESENT || FORCE_SYNC_NOW == null) return false;
        try {
            Object result = FORCE_SYNC_NOW.invoke(null, server, reason);
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            Pjmbasemod.LOGGER.warn("[FRONTLINE][BlueMap] forceSyncNow failed", t);
            return false;
        }
    }

    public static StatusSnapshot status() {
        if (!PRESENT || STATUS == null) return StatusSnapshot.unavailable();
        try {
            Object result = STATUS.invoke(null);
            if (result instanceof StatusSnapshot snapshot) return snapshot;
        } catch (Throwable t) {
            Pjmbasemod.LOGGER.warn("[FRONTLINE][BlueMap] status failed", t);
        }
        return StatusSnapshot.unavailable();
    }

    private static void invokeVoid(Method method, Object... args) {
        if (!PRESENT || method == null) return;
        try {
            method.invoke(null, args);
        } catch (Throwable t) {
            Pjmbasemod.LOGGER.warn("[FRONTLINE][BlueMap] bridge call failed: {}", method.getName(), t);
        }
    }

    public record StatusSnapshot(
            boolean enabledByConfig,
            boolean apiPresent,
            String blueMapVersion,
            boolean syncRequested,
            boolean hasPendingSnapshot,
            int debounceTicksLeft,
            String lastReason,
            long lastSuccessfulSyncAtMs,
            Map<String, String> dimensionMapping
    ) {
        public static StatusSnapshot unavailable() {
            return new StatusSnapshot(Config.isFrontlineBlueMapEnabled(), false, "", false, false, 0, "unavailable", 0L, Map.of());
        }
    }
}
