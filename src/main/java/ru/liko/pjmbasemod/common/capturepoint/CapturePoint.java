package ru.liko.pjmbasemod.common.capturepoint;

import java.util.List;

/**
 * Иммутабельный снапшот точки захвата для синхронизации и отображения.
 * Геометрия (id/displayName/dimension/vertices) задаётся OP через редактор,
 * runtime-состояние (ownerTeamId/captureTeamId/progressPercent/contested)
 * обновляется серверным {@link CapturePointManager} каждый тик.
 *
 * @param id              уникальный идентификатор точки (напр. {@code cp_north})
 * @param displayName     отображаемое имя (напр. «Северная точка»)
 * @param dimension       id измерения (напр. {@code minecraft:overworld})
 * @param vertices        вершины полигона в блочных координатах X/Z
 * @param ownerTeamId     текущий владелец ({@code ""} = нейтрально)
 * @param ownerColor      RGB цвет владельца (для карты; 0x9B9B9B = нейтрально)
 * @param captureTeamId   команда, захватывающая/нейтрализующая точку ({@code ""} = нет)
 * @param progressPercent 0–100: при NEUTRALIZING — остаток контроля владельца,
 *                        при CAPTURING — прогресс захвата
 * @param contested       true, если внутри точки игроки 2+ команд одновременно
 */
public record CapturePoint(
        String id,
        String displayName,
        String dimension,
        List<Vertex> vertices,
        String ownerTeamId,
        int ownerColor,
        String captureTeamId,
        int progressPercent,
        boolean contested
) {

    /** Вершина полигона в блочных координатах (X/Z, Y игнорируется). */
    public record Vertex(int x, int z) {}

    /**
     * Тест «точка внутри полигона» (ray-casting). Работает с целочисленными
     * блочными координатами. Полигон должен иметь ≥ 3 вершин.
     *
     * @param polygon вершины полигона
     * @param x       блочная координата X игрока
     * @param z       блочная координата Z игрока
     * @return true, если точка (x, z) внутри полигона
     */
    public static boolean contains(List<Vertex> polygon, int x, int z) {
        if (polygon == null || polygon.size() < 3) return false;
        boolean inside = false;
        int n = polygon.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Vertex vi = polygon.get(i);
            Vertex vj = polygon.get(j);
            if ((vi.z() > z) != (vj.z() > z)
                    && x < (double) (vj.x() - vi.x()) * (z - vi.z()) / (vj.z() - vi.z()) + vi.x()) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** Центроид полигона (для позиционирования лейбла на карте). */
    public static Vertex centroid(List<Vertex> polygon) {
        if (polygon == null || polygon.isEmpty()) return new Vertex(0, 0);
        long sumX = 0, sumZ = 0;
        for (Vertex v : polygon) {
            sumX += v.x();
            sumZ += v.z();
        }
        return new Vertex((int) (sumX / polygon.size()), (int) (sumZ / polygon.size()));
    }
}
