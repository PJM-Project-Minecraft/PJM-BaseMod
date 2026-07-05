package ru.liko.pjmbasemod.common.web;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Троттлинг по ключу (IP) со скользящим окном: не более {@code maxAttempts}
 * за {@code windowMs}. Время передаётся снаружи — класс чистый и тестируемый.
 * Используется для защиты /api/auth/* от перебора кодов входа.
 */
public final class RateLimiter {

    private final int maxAttempts;
    private final long windowMs;
    private final Map<String, Deque<Long>> hits = new HashMap<>();

    public RateLimiter(int maxAttempts, long windowMs) {
        this.maxAttempts = maxAttempts;
        this.windowMs = windowMs;
    }

    /** @return true, если попытка разрешена (и учтена). */
    public synchronized boolean allow(String key, long nowMs) {
        // Попутная эвикция: убираем полностью протухшие ключи, чтобы карта не росла бесконечно.
        hits.values().removeIf(queue -> {
            while (!queue.isEmpty() && queue.peekFirst() <= nowMs - windowMs) queue.pollFirst();
            return queue.isEmpty();
        });
        Deque<Long> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        if (queue.size() >= maxAttempts) return false;
        queue.addLast(nowMs);
        return true;
    }
}
