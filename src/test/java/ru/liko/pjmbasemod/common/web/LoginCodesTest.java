package ru.liko.pjmbasemod.common.web;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoginCodesTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void issueThenConsumeReturnsLogin() {
        LoginCodes codes = new LoginCodes(300_000);
        String code = codes.issue(ID, "Liko", 1_000);
        assertNotNull(code);
        assertEquals(8, code.length());
        LoginCodes.PendingLogin login = codes.consume(code, 2_000);
        assertNotNull(login);
        assertEquals(ID, login.playerId());
        assertEquals("Liko", login.playerName());
    }

    @Test
    void codeIsSingleUse() {
        LoginCodes codes = new LoginCodes(300_000);
        String code = codes.issue(ID, "Liko", 0);
        assertNotNull(codes.consume(code, 1));
        assertNull(codes.consume(code, 2));
    }

    @Test
    void expiredCodeIsRejected() {
        LoginCodes codes = new LoginCodes(300_000);
        String code = codes.issue(ID, "Liko", 0);
        assertNull(codes.consume(code, 300_001));
    }

    @Test
    void consumeIsCaseInsensitiveAndNullSafe() {
        LoginCodes codes = new LoginCodes(300_000);
        String code = codes.issue(ID, "Liko", 0);
        assertNotNull(codes.consume(code.toLowerCase(java.util.Locale.ROOT), 1));
        assertNull(codes.consume(null, 1));
    }

    @Test
    void codeExpiresExactlyAtTtlBoundary() {
        LoginCodes codes = new LoginCodes(300_000);
        String code = codes.issue(ID, "Liko", 0);
        // Граница включительно: expiresAtMs <= nowMs → код истёк ровно в момент TTL.
        assertNull(codes.consume(code, 300_000));
    }
}
