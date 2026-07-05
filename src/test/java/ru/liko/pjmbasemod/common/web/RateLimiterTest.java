package ru.liko.pjmbasemod.common.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void allowsUpToMaxAttemptsWithinWindow() {
        RateLimiter limiter = new RateLimiter(3, 60_000);
        assertTrue(limiter.allow("1.2.3.4", 1_000));
        assertTrue(limiter.allow("1.2.3.4", 2_000));
        assertTrue(limiter.allow("1.2.3.4", 3_000));
        assertFalse(limiter.allow("1.2.3.4", 4_000));
    }

    @Test
    void windowSlides() {
        RateLimiter limiter = new RateLimiter(2, 10_000);
        assertTrue(limiter.allow("k", 0));
        assertTrue(limiter.allow("k", 1_000));
        assertFalse(limiter.allow("k", 2_000));
        // Первая попытка (t=0) вышла из окна к t=10_001.
        assertTrue(limiter.allow("k", 10_001));
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(1, 60_000);
        assertTrue(limiter.allow("a", 0));
        assertTrue(limiter.allow("b", 0));
        assertFalse(limiter.allow("a", 1));
    }

    @Test
    void staleKeysDoNotBlockAfterWindow() {
        RateLimiter limiter = new RateLimiter(1, 10_000);
        assertTrue(limiter.allow("a", 0));
        assertFalse(limiter.allow("a", 1));
        // Спустя окно ключ снова доступен (и внутренняя карта не копит мусор).
        assertTrue(limiter.allow("a", 10_001));
    }
}
