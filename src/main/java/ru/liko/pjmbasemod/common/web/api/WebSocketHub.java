package ru.liko.pjmbasemod.common.web.api;

import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.web.WebAuthService;
import ru.liko.pjmbasemod.common.web.WebPanelService;
import ru.liko.pjmbasemod.common.web.WebState;
import ru.liko.pjmbasemod.common.web.metrics.EntityProfiler;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Пуш живых данных в подключённые браузеры: раз в секунду кадр "tick"
 * (текущий сэмпл + игроки), каждый второй кадр дополняется entity-статистикой
 * и отчётом профайлера. Работает на собственном daemon-потоке; читает только
 * volatile-снапшоты WebState. Браузер по WS ничего не шлёт (кроме ping).
 */
public final class WebSocketHub {

    private static final String SESSION_COOKIE = "pjm_session";
    private static final Set<WsContext> CLIENTS = ConcurrentHashMap.newKeySet();

    @Nullable
    private static ScheduledExecutorService executor;
    private static long frames;

    private WebSocketHub() {}

    public static void register(Javalin app) {
        app.ws("/ws/live", ws -> {
            ws.onConnect(ctx -> {
                if (WebAuthService.session(ctx.cookie(SESSION_COOKIE)) == null) {
                    ctx.closeSession(4001, "unauthorized");
                    return;
                }
                CLIENTS.add(ctx);
            });
            ws.onClose(ctx -> CLIENTS.remove(ctx));
            ws.onError(ctx -> CLIENTS.remove(ctx));
            ws.onMessage(ctx -> { /* ping от клиента — игнорируем */ });
        });
    }

    public static void startBroadcast() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "pjm-webpanel-broadcast");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(WebSocketHub::broadcast, 1, 1, TimeUnit.SECONDS);
    }

    public static void stopBroadcast() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        CLIENTS.clear();
    }

    private static void broadcast() {
        try {
            if (CLIENTS.isEmpty()) return;
            frames++;
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", "tick");
            frame.put("sample", WebState.current());
            frame.put("players", WebState.players());
            if (frames % 2 == 0) {
                frame.put("entityCounts", WebState.entityCountsByCategory());
                frame.put("profilerActive", EntityProfiler.isActive());
                frame.put("profiler", WebState.profilerReport());
            }
            String json = WebPanelService.GSON.toJson(frame);
            for (WsContext ctx : CLIENTS) {
                try {
                    ctx.send(json);
                } catch (Exception e) {
                    CLIENTS.remove(ctx);
                }
            }
        } catch (Exception e) {
            // Поток вещания не должен умирать из-за разовой ошибки сериализации.
            Pjmbasemod.LOGGER.debug("[WebPanel] ошибка broadcast", e);
        }
    }
}
