package ru.liko.pjmbasemod.common.web.metrics;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Кольцевой буфер истории метрик для графиков панели. Ёмкость = historyMinutes × 60.
 * Пишет server thread, читают HTTP-потоки — все методы synchronized, snapshot()
 * возвращает иммутабельную копию.
 */
public final class MetricsHistory {

    private final int capacity;
    private final ArrayDeque<MetricsSample> samples;

    public MetricsHistory(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.samples = new ArrayDeque<>(this.capacity);
    }

    public synchronized void add(MetricsSample sample) {
        if (samples.size() >= capacity) samples.pollFirst();
        samples.addLast(sample);
    }

    /** Старые → новые. */
    public synchronized List<MetricsSample> snapshot() {
        return List.copyOf(samples);
    }
}
