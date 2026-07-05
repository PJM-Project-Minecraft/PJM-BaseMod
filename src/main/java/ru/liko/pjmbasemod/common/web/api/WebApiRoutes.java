package ru.liko.pjmbasemod.common.web.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.HandlerType;
import io.javalin.http.SameSite;
import io.javalin.http.UnauthorizedResponse;
import net.minecraft.server.MinecraftServer;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.common.moderation.DurationParser;
import ru.liko.pjmbasemod.common.web.WebActions;
import ru.liko.pjmbasemod.common.web.WebAuthService;
import ru.liko.pjmbasemod.common.web.WebDtos;
import ru.liko.pjmbasemod.common.web.WebSessions;
import ru.liko.pjmbasemod.common.web.WebState;
import ru.liko.pjmbasemod.common.web.metrics.EntityProfiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Все REST-эндпоинты панели. Инварианты:
 * - /api/* (кроме /api/auth/*) требует валидную сессию → иначе 401;
 * - POST требует заголовок X-Requested-With: PJMPanel (анти-CSRF в пару к SameSite=Strict);
 * - игровое состояние НЕ трогается из HTTP-потоков: чтение — WebState, запись — WebActions
 *   (server.execute + ожидание future с таймаутом 5с → 504).
 */
public final class WebApiRoutes {

    private static final String SESSION_COOKIE = "pjm_session";
    private static final long ACTION_TIMEOUT_SECONDS = 5;

    private WebApiRoutes() {}

    public static void register(Javalin app, MinecraftServer server) {
        app.before("/api/*", ctx -> {
            if (ctx.path().startsWith("/api/auth/")) return;
            WebSessions.Session session = WebAuthService.session(ctx.cookie(SESSION_COOKIE));
            if (session == null) throw new UnauthorizedResponse("no_session");
            ctx.attribute("session", session);
        });
        app.before("/api/*", ctx -> {
            if (ctx.method() == HandlerType.POST && !"PJMPanel".equals(ctx.header("X-Requested-With"))) {
                throw new UnauthorizedResponse("csrf");
            }
        });

        // ---- auth ----
        app.post("/api/auth/exchange", ctx -> {
            WebDtos.ExchangeRequest req = ctx.bodyAsClass(WebDtos.ExchangeRequest.class);
            WebSessions.Session session = WebAuthService.exchange(req == null ? null : req.code(), ctx.ip());
            if (session == null) {
                ctx.status(401).json(Map.of("ok", false, "error", "bad_code"));
                return;
            }
            Cookie cookie = new Cookie(SESSION_COOKIE, session.token());
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setSameSite(SameSite.STRICT);
            cookie.setMaxAge(Config.getWebSessionTtlMinutes() * 60);
            ctx.cookie(cookie);
            ctx.json(Map.of("ok", true, "name", session.playerName()));
        });
        app.post("/api/auth/logout", ctx -> {
            WebSessions.Session session = WebAuthService.session(ctx.cookie(SESSION_COOKIE));
            if (session != null) WebAuthService.revokeAllFor(session.playerId());
            ctx.removeCookie(SESSION_COOKIE);
            ctx.json(Map.of("ok", true));
        });

        // ---- чтение (только WebState) ----
        app.get("/api/overview", ctx -> ctx.json(Map.of(
                "current", nullable(WebState.current()),
                "history", WebState.history().snapshot(),
                "entityCounts", WebState.entityCountsByCategory(),
                "profilerActive", EntityProfiler.isActive(),
                "profilerAllowed", Config.isWebProfilerAllowed())));

        app.get("/api/players", ctx -> ctx.json(WebState.players()));

        app.get("/api/entities", ctx -> {
            String dim = ctx.queryParam("dim");
            String type = ctx.queryParam("type");
            int limit = parseInt(ctx.queryParam("limit"), 2000);
            List<WebDtos.EntityDto> all = WebState.entities();
            List<WebDtos.EntityDto> filtered = new ArrayList<>();
            for (WebDtos.EntityDto e : all) {
                if (dim != null && !dim.isBlank() && !e.dim().equals(dim)) continue;
                if (type != null && !type.isBlank() && !e.type().equals(type)) continue;
                filtered.add(e);
                if (filtered.size() >= limit) break;
            }
            ctx.json(Map.of("total", all.size(), "entities", filtered));
        });

        app.get("/api/profiler", ctx -> ctx.json(Map.of(
                "allowed", Config.isWebProfilerAllowed(),
                "active", EntityProfiler.isActive(),
                "report", WebState.profilerReport())));

        app.post("/api/profiler/toggle", ctx -> {
            if (!Config.isWebProfilerAllowed()) {
                ctx.status(403).json(Map.of("ok", false, "error", "profiler_disabled"));
                return;
            }
            EntityProfiler.setActive(!EntityProfiler.isActive());
            ctx.json(Map.of("ok", true, "active", EntityProfiler.isActive()));
        });

        // ---- модерация ----
        app.get("/api/moderation/history", ctx -> {
            UUID target = parseUuid(ctx.queryParam("player"));
            if (target == null) {
                ctx.status(400).json(Map.of("ok", false, "error", "bad_uuid"));
                return;
            }
            ctx.json(await(WebActions.moderationHistory(server, target), ctx));
        });

        // ---- действия ----
        app.post("/api/actions/kick", ctx -> {
            WebDtos.KickRequest req = ctx.bodyAsClass(WebDtos.KickRequest.class);
            UUID target = parseUuid(req.uuid());
            if (target == null) {
                badRequest(ctx, "bad_uuid");
                return;
            }
            WebSessions.Session session = session(ctx);
            respond(ctx, WebActions.kick(server, target, orDash(req.reason()),
                    session.playerId(), session.playerName()));
        });

        app.post("/api/actions/punish", ctx -> {
            WebDtos.PunishRequest req = ctx.bodyAsClass(WebDtos.PunishRequest.class);
            UUID target = parseUuid(req.uuid());
            if (target == null || req.name() == null || req.type() == null) {
                badRequest(ctx, "bad_request");
                return;
            }
            long durationMs = DurationParser.PERMANENT;
            if (req.duration() != null && !req.duration().isBlank()) {
                durationMs = DurationParser.parseToMillis(req.duration());
                if (durationMs == DurationParser.INVALID) {
                    badRequest(ctx, "bad_duration");
                    return;
                }
            }
            WebSessions.Session session = session(ctx);
            respond(ctx, WebActions.punish(server, target, req.name(), req.type(), durationMs,
                    orDash(req.reason()), session.playerId(), session.playerName()));
        });

        app.post("/api/actions/pardon", ctx -> {
            WebDtos.PardonRequest req = ctx.bodyAsClass(WebDtos.PardonRequest.class);
            UUID target = parseUuid(req.uuid());
            if (target == null || req.type() == null) {
                badRequest(ctx, "bad_request");
                return;
            }
            WebSessions.Session session = session(ctx);
            respond(ctx, WebActions.pardon(server, target, req.name() == null ? "unknown" : req.name(),
                    req.type(), session.playerId(), session.playerName()));
        });

        app.post("/api/actions/teleport", ctx -> {
            WebDtos.TeleportRequest req = ctx.bodyAsClass(WebDtos.TeleportRequest.class);
            UUID target = parseUuid(req.uuid());
            if (target == null) {
                badRequest(ctx, "bad_uuid");
                return;
            }
            WebSessions.Session session = session(ctx);
            respond(ctx, WebActions.teleport(server, target, parseUuid(req.toPlayer()),
                    req.x(), req.y(), req.z(), req.dim(), session.playerName()));
        });

        app.post("/api/actions/entities/remove", ctx -> {
            WebDtos.RemoveEntitiesRequest req = ctx.bodyAsClass(WebDtos.RemoveEntitiesRequest.class);
            if (req.uuids() == null || req.uuids().isEmpty()) {
                badRequest(ctx, "empty_list");
                return;
            }
            respond(ctx, WebActions.removeEntities(server, req.uuids(), session(ctx).playerName()));
        });

        app.post("/api/actions/entities/remove-bulk", ctx -> {
            WebDtos.BulkRemoveRequest req = ctx.bodyAsClass(WebDtos.BulkRemoveRequest.class);
            if (req.dim() == null || req.dim().isBlank()) {
                badRequest(ctx, "bad_dimension");
                return;
            }
            respond(ctx, WebActions.removeEntitiesBulk(server, req.type(), req.dim(),
                    req.x(), req.z(), req.radius(), session(ctx).playerName()));
        });

        app.exception(Exception.class, (e, ctx) ->
                ctx.status(500).json(Map.of("ok", false, "error", "internal_error")));
    }

    // ---------------------------------------------------------------- утилиты

    private static WebSessions.Session session(Context ctx) {
        WebSessions.Session session = ctx.attribute("session");
        if (session == null) throw new UnauthorizedResponse("no_session");
        return session;
    }

    /** Ждёт результат действия с таймаутом; таймаут → 504. */
    private static void respond(Context ctx, CompletableFuture<WebActions.ActionResult> future) {
        try {
            WebActions.ActionResult result = future.get(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (result.ok()) {
                ctx.json(Map.of("ok", true, "message", result.message()));
            } else {
                ctx.status(404).json(Map.of("ok", false, "error", result.message()));
            }
        } catch (TimeoutException e) {
            ctx.status(504).json(Map.of("ok", false, "error", "server_busy"));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("ok", false, "error", "internal_error"));
        }
    }

    private static <T> T await(CompletableFuture<T> future, Context ctx) {
        try {
            return future.get(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("server_busy", e);
        }
    }

    private static void badRequest(Context ctx, String error) {
        ctx.status(400).json(Map.of("ok", false, "error", error));
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    /** Map.of не принимает null-значения — заворачиваем current(). */
    private static Object nullable(Object value) {
        return value == null ? Map.of() : value;
    }
}
