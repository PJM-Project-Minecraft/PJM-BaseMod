package ru.liko.pjmbasemod.common.web;

import net.minecraft.server.level.ServerPlayer;
import ru.liko.pjmbasemod.Config;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Фасад авторизации веб-панели: одноразовые коды (/pjm web login) → сессии.
 * Троттлинг обмена кодов — 5 попыток в минуту с одного IP.
 * Всё состояние в памяти, рестарт сервера сбрасывает сессии.
 */
public final class WebAuthService {

    private static final long CODE_TTL_MS = 5 * 60 * 1000L;
    private static final LoginCodes CODES = new LoginCodes(CODE_TTL_MS);
    private static final WebSessions SESSIONS = new WebSessions();
    private static final RateLimiter AUTH_LIMITER = new RateLimiter(5, 60_000);

    private WebAuthService() {}

    public static String issueCode(ServerPlayer player) {
        return CODES.issue(player.getUUID(), player.getGameProfile().getName(), System.currentTimeMillis());
    }

    /** Обмен кода на сессию. null — неверный код, истёк или превышен лимит попыток. */
    @Nullable
    public static WebSessions.Session exchange(@Nullable String code, @Nullable String ip) {
        long now = System.currentTimeMillis();
        if (!AUTH_LIMITER.allow(ip == null || ip.isBlank() ? "unknown" : ip, now)) return null;
        LoginCodes.PendingLogin login = CODES.consume(code, now);
        if (login == null) return null;
        long ttlMs = Config.getWebSessionTtlMinutes() * 60_000L;
        return SESSIONS.create(login.playerId(), login.playerName(), now, ttlMs);
    }

    @Nullable
    public static WebSessions.Session session(@Nullable String token) {
        return SESSIONS.get(token, System.currentTimeMillis());
    }

    public static int revokeAllFor(UUID playerId) {
        return SESSIONS.revokeAll(playerId);
    }

    public static void clearAll() {
        SESSIONS.clear();
    }
}
