package ru.liko.pjmbasemod.common.web.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfilerWindowTest {

    @Test
    void aggregatesSameEntityAndKeepsLastCoords() {
        ProfilerWindow window = new ProfilerWindow();
        window.record("u1", "minecraft:zombie", "Zombie", "minecraft:overworld", 0, 64, 0, 1_000);
        window.record("u1", "minecraft:zombie", "Zombie", "minecraft:overworld", 10, 64, 20, 2_000);
        ProfilerWindow.Report report = window.flush(30_000, 10);
        assertEquals(1, report.topEntities().size());
        ProfilerWindow.EntityTiming e = report.topEntities().get(0);
        assertEquals(2, e.ticks());
        assertEquals(3_000, e.totalNanos());
        assertEquals(10, e.x());
        assertEquals(20, e.z());
        assertEquals(3_000, report.totalNanos());
    }

    @Test
    void topIsSortedDescAndLimited() {
        ProfilerWindow window = new ProfilerWindow();
        window.record("light", "minecraft:cow", "Cow", "minecraft:overworld", 0, 0, 0, 100);
        window.record("heavy", "minecraft:zombie", "Zombie", "minecraft:overworld", 0, 0, 0, 9_000);
        window.record("mid", "minecraft:pig", "Pig", "minecraft:overworld", 0, 0, 0, 500);
        ProfilerWindow.Report report = window.flush(30_000, 2);
        assertEquals(2, report.topEntities().size());
        assertEquals("heavy", report.topEntities().get(0).uuid());
        assertEquals("mid", report.topEntities().get(1).uuid());
    }

    @Test
    void byTypeCountsDistinctEntities() {
        ProfilerWindow window = new ProfilerWindow();
        window.record("z1", "minecraft:zombie", "Zombie", "minecraft:overworld", 0, 0, 0, 100);
        window.record("z2", "minecraft:zombie", "Zombie", "minecraft:overworld", 0, 0, 0, 200);
        window.record("z1", "minecraft:zombie", "Zombie", "minecraft:overworld", 0, 0, 0, 50);
        ProfilerWindow.Report report = window.flush(30_000, 10);
        assertEquals(1, report.byType().size());
        ProfilerWindow.TypeTiming type = report.byType().get(0);
        assertEquals(2, type.count());
        assertEquals(350, type.totalNanos());
    }

    @Test
    void hotChunksGroupByChunkCoords() {
        ProfilerWindow window = new ProfilerWindow();
        // (5, 5) и (12, 8) — один чанк (0, 0); (20, 0) — чанк (1, 0).
        window.record("a", "t", "n", "minecraft:overworld", 5, 0, 5, 100);
        window.record("b", "t", "n", "minecraft:overworld", 12, 0, 8, 200);
        window.record("c", "t", "n", "minecraft:overworld", 20, 0, 0, 50);
        ProfilerWindow.Report report = window.flush(30_000, 10);
        assertEquals(2, report.hotChunks().size());
        assertEquals(300, report.hotChunks().get(0).totalNanos());
        assertEquals(0, report.hotChunks().get(0).chunkX());
    }

    @Test
    void flushClearsWindow() {
        ProfilerWindow window = new ProfilerWindow();
        window.record("u", "t", "n", "d", 0, 0, 0, 100);
        window.flush(30_000, 10);
        assertTrue(window.flush(30_000, 10).topEntities().isEmpty());
    }
}
