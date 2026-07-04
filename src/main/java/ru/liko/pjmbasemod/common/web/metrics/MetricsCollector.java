package ru.liko.pjmbasemod.common.web.metrics;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.web.WebSnapshots;
import ru.liko.pjmbasemod.common.web.WebState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сбор метрик на server thread: длительность тика сервера и каждого уровня
 * замеряется nanoTime в Pre/Post-событиях, раз в секунду (20 тиков) фиксируется
 * {@link MetricsSample} в {@link WebState}. Каждые 40 тиков перестраиваются
 * снапшоты игроков/entity ({@link WebSnapshots}). При выключенной панели — ноль работы.
 */
@EventBusSubscriber(modid = Pjmbasemod.MODID)
public final class MetricsCollector {

    private static final int SAMPLE_EVERY_TICKS = 20;
    private static final int SNAPSHOT_EVERY_TICKS = 40;

    private static long tickStartNanos;
    private static long accumulatedTickNanos;
    private static int tickCount;
    // Все три структуры трогает только server thread.
    private static final Map<String, Long> levelStartNanos = new HashMap<>();
    private static final Map<String, Long> levelAccumNanos = new HashMap<>();

    private MetricsCollector() {}

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        if (!Config.isWebEnabled()) return;
        tickStartNanos = System.nanoTime();
    }

    @SubscribeEvent
    public static void onLevelTickPre(LevelTickEvent.Pre event) {
        if (!Config.isWebEnabled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        levelStartNanos.put(level.dimension().location().toString(), System.nanoTime());
    }

    @SubscribeEvent
    public static void onLevelTickPost(LevelTickEvent.Post event) {
        if (!Config.isWebEnabled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        String dim = level.dimension().location().toString();
        Long start = levelStartNanos.remove(dim);
        if (start != null) levelAccumNanos.merge(dim, System.nanoTime() - start, Long::sum);
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (!Config.isWebEnabled() || tickStartNanos == 0L) return;
        accumulatedTickNanos += System.nanoTime() - tickStartNanos;
        tickCount++;
        // Сброс до переполнения int; 2_000_000_000 кратно 40 — обе кадансы (20/40) не сбиваются.
        if (tickCount >= 2_000_000_000) tickCount = 0;

        MinecraftServer server = event.getServer();
        if (tickCount % SNAPSHOT_EVERY_TICKS == 0) {
            WebSnapshots.rebuild(server);
        }
        if (tickCount % SAMPLE_EVERY_TICKS != 0) return;

        double avgMspt = accumulatedTickNanos / 1_000_000.0 / SAMPLE_EVERY_TICKS;
        accumulatedTickNanos = 0L;
        double tps = avgMspt <= 50.0 ? 20.0 : 1000.0 / avgMspt;

        List<MetricsSample.DimSample> dims = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            String dim = level.dimension().location().toString();
            long nanos = levelAccumNanos.getOrDefault(dim, 0L);
            dims.add(new MetricsSample.DimSample(dim,
                    round2(nanos / 1_000_000.0 / SAMPLE_EVERY_TICKS),
                    WebState.entityCount(dim),
                    level.getChunkSource().getLoadedChunksCount()));
        }
        levelAccumNanos.clear();

        Runtime rt = Runtime.getRuntime();
        WebState.addSample(new MetricsSample(System.currentTimeMillis(),
                round2(avgMspt), round2(tps),
                rt.totalMemory() - rt.freeMemory(), rt.maxMemory(),
                server.getPlayerList().getPlayerCount(), List.copyOf(dims)));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
