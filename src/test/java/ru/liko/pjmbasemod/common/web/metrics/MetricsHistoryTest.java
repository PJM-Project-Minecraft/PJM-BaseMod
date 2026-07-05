package ru.liko.pjmbasemod.common.web.metrics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsHistoryTest {

    private static MetricsSample sample(long t) {
        return new MetricsSample(t, 10.0, 20.0, 100, 1000, 5, List.of());
    }

    @Test
    void keepsInsertionOrderOldestFirst() {
        MetricsHistory history = new MetricsHistory(10);
        history.add(sample(1));
        history.add(sample(2));
        history.add(sample(3));
        List<MetricsSample> snap = history.snapshot();
        assertEquals(3, snap.size());
        assertEquals(1, snap.get(0).t());
        assertEquals(3, snap.get(2).t());
    }

    @Test
    void evictsOldestWhenFull() {
        MetricsHistory history = new MetricsHistory(2);
        history.add(sample(1));
        history.add(sample(2));
        history.add(sample(3));
        List<MetricsSample> snap = history.snapshot();
        assertEquals(2, snap.size());
        assertEquals(2, snap.get(0).t());
        assertEquals(3, snap.get(1).t());
    }
}
