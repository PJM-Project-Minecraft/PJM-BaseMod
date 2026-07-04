package ru.liko.pjmbasemod.common.web;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WebSessionsTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void createdSessionIsRetrievableByToken() {
        WebSessions sessions = new WebSessions();
        WebSessions.Session s = sessions.create(ID, "Liko", 0, 1_000);
        assertEquals(64, s.token().length()); // 32 байта в hex
        WebSessions.Session got = sessions.get(s.token(), 500);
        assertNotNull(got);
        assertEquals("Liko", got.playerName());
    }

    @Test
    void expiredSessionIsRemoved() {
        WebSessions sessions = new WebSessions();
        WebSessions.Session s = sessions.create(ID, "Liko", 0, 1_000);
        assertNull(sessions.get(s.token(), 1_001));
    }

    @Test
    void revokeAllRemovesOnlyThatPlayer() {
        WebSessions sessions = new WebSessions();
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000003");
        WebSessions.Session a1 = sessions.create(ID, "Liko", 0, 10_000);
        WebSessions.Session a2 = sessions.create(ID, "Liko", 0, 10_000);
        WebSessions.Session b = sessions.create(other, "Bob", 0, 10_000);
        assertEquals(2, sessions.revokeAll(ID));
        assertNull(sessions.get(a1.token(), 1));
        assertNull(sessions.get(a2.token(), 1));
        assertNotNull(sessions.get(b.token(), 1));
    }

    @Test
    void clearRemovesEverything() {
        WebSessions sessions = new WebSessions();
        WebSessions.Session s = sessions.create(ID, "Liko", 0, 10_000);
        sessions.clear();
        assertNull(sessions.get(s.token(), 1));
    }
}
