package ru.liko.pjmbasemod.common.web.metrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Скользящее окно профайлера entity: копит наносекунды тика по entity,
 * по вызову {@link #flush} отдаёт агрегированный отчёт (топ entity, по типам,
 * «горячие» чанки) и очищается. Чистый класс без MC-зависимостей — вся привязка
 * к событиям в {@code EntityProfiler}. Пишет только server thread, flush
 * дергается им же — synchronized на случай чтения из HTTP-потока.
 */
public final class ProfilerWindow {

    public record EntityTiming(String uuid, String type, String name, String dim,
                               double x, double y, double z, long ticks, long totalNanos) {}

    public record TypeTiming(String type, int count, long totalNanos) {}

    public record ChunkTiming(String dim, int chunkX, int chunkZ, long totalNanos) {}

    public record Report(long windowMs, long totalNanos, List<EntityTiming> topEntities,
                         List<TypeTiming> byType, List<ChunkTiming> hotChunks) {

        public static Report empty() {
            return new Report(0, 0, List.of(), List.of(), List.of());
        }
    }

    /** Мутабельный аккумулятор одного entity внутри окна. */
    private static final class Acc {
        final String type;
        final String name;
        String dim;
        double x, y, z;
        long ticks;
        long totalNanos;

        Acc(String type, String name) {
            this.type = type;
            this.name = name;
        }
    }

    private final Map<String, Acc> byEntity = new HashMap<>();

    public synchronized void record(String uuid, String type, String name, String dim,
                                    double x, double y, double z, long nanos) {
        Acc acc = byEntity.computeIfAbsent(uuid, k -> new Acc(type, name));
        acc.dim = dim;
        acc.x = x;
        acc.y = y;
        acc.z = z;
        acc.ticks++;
        acc.totalNanos += nanos;
    }

    /** Собирает отчёт и очищает окно. */
    public synchronized Report flush(long windowMs, int topLimit) {
        long totalNanos = 0;
        List<EntityTiming> entities = new ArrayList<>(byEntity.size());
        Map<String, long[]> typeNanos = new HashMap<>();      // type → [totalNanos]
        Map<String, Set<String>> typeEntities = new HashMap<>(); // type → uuids
        Map<String, long[]> chunkNanos = new HashMap<>();     // "dim|cx|cz" → [totalNanos]

        for (Map.Entry<String, Acc> e : byEntity.entrySet()) {
            Acc a = e.getValue();
            totalNanos += a.totalNanos;
            entities.add(new EntityTiming(e.getKey(), a.type, a.name, a.dim,
                    a.x, a.y, a.z, a.ticks, a.totalNanos));
            typeNanos.computeIfAbsent(a.type, k -> new long[1])[0] += a.totalNanos;
            typeEntities.computeIfAbsent(a.type, k -> new HashSet<>()).add(e.getKey());
            String chunkKey = a.dim + "|" + ((int) Math.floor(a.x) >> 4) + "|" + ((int) Math.floor(a.z) >> 4);
            chunkNanos.computeIfAbsent(chunkKey, k -> new long[1])[0] += a.totalNanos;
        }
        byEntity.clear();

        entities.sort(Comparator.comparingLong(EntityTiming::totalNanos).reversed());
        List<EntityTiming> top = List.copyOf(entities.subList(0, Math.min(topLimit, entities.size())));

        List<TypeTiming> byType = new ArrayList<>();
        for (Map.Entry<String, long[]> e : typeNanos.entrySet()) {
            byType.add(new TypeTiming(e.getKey(), typeEntities.get(e.getKey()).size(), e.getValue()[0]));
        }
        byType.sort(Comparator.comparingLong(TypeTiming::totalNanos).reversed());

        List<ChunkTiming> chunks = new ArrayList<>();
        for (Map.Entry<String, long[]> e : chunkNanos.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            chunks.add(new ChunkTiming(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), e.getValue()[0]));
        }
        chunks.sort(Comparator.comparingLong(ChunkTiming::totalNanos).reversed());

        return new Report(windowMs, totalNanos, top, List.copyOf(byType), List.copyOf(chunks));
    }
}
