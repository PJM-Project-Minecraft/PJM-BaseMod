package ru.liko.pjmbasemod.common.web;

import javax.annotation.Nullable;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Одноразовые коды входа в веб-панель. Выдаются командой {@code /pjm web login},
 * обмениваются на сессию в {@code POST /api/auth/exchange}. Код живёт {@code ttlMs}
 * и сгорает при первом использовании. Алфавит без неоднозначных символов (0/O, 1/I).
 */
public final class LoginCodes {

    /** Кому был выдан код. */
    public record PendingLogin(UUID playerId, String playerName) {}

    private record Entry(PendingLogin login, long expiresAtMs) {}

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();
    private final long ttlMs;
    private final Map<String, Entry> codes = new HashMap<>();

    public LoginCodes(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    public synchronized String issue(UUID playerId, String playerName, long nowMs) {
        codes.values().removeIf(e -> e.expiresAtMs() <= nowMs);
        String code;
        do {
            code = randomCode();
        } while (codes.containsKey(code));
        codes.put(code, new Entry(new PendingLogin(playerId, playerName), nowMs + ttlMs));
        return code;
    }

    /** @return данные входа или null (нет кода / истёк / уже использован). Код сгорает. Граница TTL включительно: при {@code nowMs == expiresAtMs} код считается истёкшим. */
    @Nullable
    public synchronized PendingLogin consume(@Nullable String code, long nowMs) {
        if (code == null || code.isBlank()) return null;
        Entry entry = codes.remove(code.trim().toUpperCase(Locale.ROOT));
        if (entry == null || entry.expiresAtMs() <= nowMs) return null;
        return entry.login();
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
