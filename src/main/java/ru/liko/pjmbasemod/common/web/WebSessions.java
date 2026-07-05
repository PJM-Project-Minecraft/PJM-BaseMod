package ru.liko.pjmbasemod.common.web;

import javax.annotation.Nullable;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Сессии веб-панели: токен (256 бит, hex) → игрок. Хранятся только в памяти —
 * рестарт сервера сбрасывает все сессии (осознанное решение из спеки).
 * TTL фиксированный от момента создания.
 */
public final class WebSessions {

    public record Session(String token, UUID playerId, String playerName, long expiresAtMs) {}

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new HashMap<>();

    public synchronized Session create(UUID playerId, String playerName, long nowMs, long ttlMs) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        Session session = new Session(token, playerId, playerName, nowMs + ttlMs);
        sessions.put(token, session);
        return session;
    }

    /** @return сессия или null (неизвестный токен / истекла — истёкшая удаляется). */
    @Nullable
    public synchronized Session get(@Nullable String token, long nowMs) {
        if (token == null) return null;
        Session session = sessions.get(token);
        if (session == null) return null;
        if (session.expiresAtMs() <= nowMs) {
            sessions.remove(token);
            return null;
        }
        return session;
    }

    /** Завершает все сессии игрока. @return сколько снято. */
    public synchronized int revokeAll(UUID playerId) {
        int before = sessions.size();
        sessions.values().removeIf(s -> s.playerId().equals(playerId));
        return before - sessions.size();
    }

    public synchronized void clear() {
        sessions.clear();
    }
}
