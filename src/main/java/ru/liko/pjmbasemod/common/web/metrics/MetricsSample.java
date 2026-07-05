package ru.liko.pjmbasemod.common.web.metrics;

import java.util.List;

/**
 * Один сэмпл метрик (раз в секунду). Сериализуется Gson-ом как есть в API/WebSocket —
 * имена полей являются контрактом фронтенда (web/src/api.ts).
 *
 * @param t        эпоха, мс
 * @param mspt     средняя длительность тика за секунду, мс
 * @param tps      фактический TPS (≤ 20)
 * @param heapUsed занятая куча, байт
 * @param heapMax  максимум кучи, байт
 * @param online   игроков онлайн
 * @param dims     разбивка по дименшенам
 */
public record MetricsSample(long t, double mspt, double tps, long heapUsed, long heapMax,
                            int online, List<DimSample> dims) {

    /** @param dim id дименшена ({@code minecraft:overworld}), mspt — мс на тик уровня. */
    public record DimSample(String dim, double mspt, int entities, int chunks) {}
}
