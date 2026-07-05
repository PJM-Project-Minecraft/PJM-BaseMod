package ru.liko.pjmbasemod.common.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JsonMapper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.NotNull;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.web.api.WebApiRoutes;
import ru.liko.pjmbasemod.common.web.api.WebSocketHub;
import ru.liko.pjmbasemod.common.web.metrics.EntityProfiler;

import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Жизненный цикл встроенного Javalin-сервера веб-панели. Стартует на
 * ServerStartedEvent (если web.enabled), гасится на ServerStoppingEvent.
 * Любая ошибка старта логируется и НЕ роняет игровой сервер.
 * TLS не встроен — в проде панель ставится за reverse proxy (см. docs/WEBPANEL.md).
 */
@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class WebPanelService {

    /** Общий Gson панели: сериализация DTO в API и WebSocket. */
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    @Nullable
    private static Javalin app;

    private WebPanelService() {}

    /**
     * Базовый URL панели для ссылки входа: {@code web.publicUrl}, если задан,
     * иначе {@code http://<IP сервера>:<web.port>}. IP берётся по цепочке:
     * конкретный {@code web.bindAddress} (не 0.0.0.0/127.0.0.1) → server-ip
     * (server.properties) → автоопределение по исходящему интерфейсу машины.
     */
    public static String panelBaseUrl(MinecraftServer server) {
        String publicUrl = Config.getWebPublicUrl();
        if (!publicUrl.isBlank()) return publicUrl.replaceAll("/+$", "");
        String host = Config.getWebBindAddress();
        if (isWildcardOrLoopback(host)) host = server.getLocalIp();
        if (host == null || host.isBlank()) host = detectLocalAddress();
        return "http://" + host + ":" + Config.getWebPort();
    }

    /** true для пустого адреса, 0.0.0.0/[::] и loopback — такие не годятся для внешней ссылки. */
    private static boolean isWildcardOrLoopback(String host) {
        if (host == null || host.isBlank()) return true;
        return host.equals("0.0.0.0") || host.equals("::") || host.equals("[::]")
                || host.equals("127.0.0.1") || host.equalsIgnoreCase("localhost");
    }

    /** IP исходящего интерфейса: UDP-connect не шлёт пакетов, но выбирает локальный адрес маршрута. */
    private static String detectLocalAddress() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("1.1.1.1"), 53);
            InetAddress local = socket.getLocalAddress();
            if (local != null && !local.isAnyLocalAddress()) return local.getHostAddress();
        } catch (Exception ignored) {
        }
        return "localhost";
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Config.isWebEnabled()) return;
        WebState.init(Config.getWebHistoryMinutes() * 60);
        try {
            Javalin javalin = Javalin.create(config -> {
                config.showJavalinBanner = false;
                config.jsonMapper(gsonMapper());
                config.staticFiles.add(sf -> {
                    sf.hostedPath = "/";
                    sf.directory = "/web";
                    sf.location = Location.CLASSPATH;
                });
                config.spaRoot.addFile("/", "/web/index.html", Location.CLASSPATH);
            });
            WebApiRoutes.register(javalin, event.getServer());
            WebSocketHub.register(javalin);
            javalin.start(Config.getWebBindAddress(), Config.getWebPort());
            WebSocketHub.startBroadcast();
            app = javalin;
            Pjmbasemod.LOGGER.info("[WebPanel] панель запущена на {}:{}",
                    Config.getWebBindAddress(), Config.getWebPort());
        } catch (Exception e) {
            Pjmbasemod.LOGGER.error("[WebPanel] не удалось запустить веб-панель — панель отключена", e);
            app = null;
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        WebSocketHub.stopBroadcast();
        if (app != null) {
            try {
                app.stop();
            } catch (Exception e) {
                Pjmbasemod.LOGGER.warn("[WebPanel] ошибка остановки веб-сервера", e);
            }
            app = null;
        }
        EntityProfiler.setActive(false);
        WebAuthService.clearAll();
    }

    private static JsonMapper gsonMapper() {
        return new JsonMapper() {
            @NotNull
            @Override
            public String toJsonString(@NotNull Object obj, @NotNull Type type) {
                return GSON.toJson(obj, type);
            }

            @NotNull
            @Override
            public <T> T fromJsonString(@NotNull String json, @NotNull Type targetType) {
                return GSON.fromJson(json, targetType);
            }
        };
    }
}
