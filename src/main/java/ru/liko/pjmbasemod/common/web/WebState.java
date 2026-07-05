package ru.liko.pjmbasemod.common.web;

import ru.liko.pjmbasemod.common.web.metrics.MetricsHistory;
import ru.liko.pjmbasemod.common.web.metrics.MetricsSample;
import ru.liko.pjmbasemod.common.web.metrics.ProfilerWindow;

import java.util.List;
import java.util.Map;

/**
 * Единственная точка обмена данными между server thread и HTTP/WS-потоками панели.
 * Server thread пишет иммутабельные снапшоты в volatile-поля; веб-потоки только читают.
 * Никакого доступа к игровому состоянию из веб-потоков — это инвариант всей подсистемы.
 */
public final class WebState {

    private WebState() {}

    /** Оба счётчика публикуются одной volatile-записью — читатель всегда видит согласованную пару. */
    private record EntityCounts(Map<String, Integer> byDim, Map<String, Integer> byCategory) {}

    private static volatile MetricsHistory history = new MetricsHistory(7200);
    private static volatile MetricsSample current;
    private static volatile List<WebDtos.PlayerDto> players = List.of();
    private static volatile List<WebDtos.EntityDto> entities = List.of();
    private static volatile EntityCounts entityCounts = new EntityCounts(Map.of(), Map.of());
    private static volatile ProfilerWindow.Report profilerReport = ProfilerWindow.Report.empty();

    /** Вызывается на старте сервера: сброс состояния, ёмкость истории из конфига. */
    public static void init(int historySamples) {
        history = new MetricsHistory(historySamples);
        current = null;
        players = List.of();
        entities = List.of();
        entityCounts = new EntityCounts(Map.of(), Map.of());
        profilerReport = ProfilerWindow.Report.empty();
    }

    public static void addSample(MetricsSample sample) {
        current = sample;
        history.add(sample);
    }

    public static MetricsSample current()                 { return current; }
    public static MetricsHistory history()                { return history; }
    public static List<WebDtos.PlayerDto> players()       { return players; }
    public static List<WebDtos.EntityDto> entities()      { return entities; }
    public static ProfilerWindow.Report profilerReport()  { return profilerReport; }
    public static Map<String, Integer> entityCountsByCategory() { return entityCounts.byCategory(); }

    public static int entityCount(String dim) {
        return entityCounts.byDim().getOrDefault(dim, 0);
    }

    public static void setPlayers(List<WebDtos.PlayerDto> value)   { players = value; }
    public static void setEntities(List<WebDtos.EntityDto> value)  { entities = value; }
    public static void setProfilerReport(ProfilerWindow.Report r)  { profilerReport = r; }

    public static void setEntityCounts(Map<String, Integer> byDim, Map<String, Integer> byCategory) {
        entityCounts = new EntityCounts(byDim, byCategory);
    }
}
