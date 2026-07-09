package ru.liko.pjmbasemod.common.capturepoint;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты геометрии точек захвата: point-in-polygon (ray-casting) и центроид.
 * Чистая логика без зависимостей от Minecraft — проверяет корректность
 * {@link CapturePoint#contains} и {@link CapturePoint#centroid}.
 */
class CapturePointGeometryTest {

    /** Квадрат 0..10 по X и Z. */
    private static List<CapturePoint.Vertex> square() {
        return List.of(
                new CapturePoint.Vertex(0, 0),
                new CapturePoint.Vertex(10, 0),
                new CapturePoint.Vertex(10, 10),
                new CapturePoint.Vertex(0, 10)
        );
    }

    @Test
    void containsPointInsideSquare() {
        assertTrue(CapturePoint.contains(square(), 5, 5));
        assertTrue(CapturePoint.contains(square(), 1, 1));
        assertTrue(CapturePoint.contains(square(), 9, 9));
    }

    @Test
    void doesNotContainPointOutsideSquare() {
        assertFalse(CapturePoint.contains(square(), -1, 5));
        assertFalse(CapturePoint.contains(square(), 11, 5));
        assertFalse(CapturePoint.contains(square(), 5, -1));
        assertFalse(CapturePoint.contains(square(), 5, 11));
        assertFalse(CapturePoint.contains(square(), -5, -5));
    }

    @Test
    void containsHandlesTriangle() {
        // Треугольник (0,0)-(10,0)-(5,10)
        List<CapturePoint.Vertex> tri = List.of(
                new CapturePoint.Vertex(0, 0),
                new CapturePoint.Vertex(10, 0),
                new CapturePoint.Vertex(5, 10)
        );
        assertTrue(CapturePoint.contains(tri, 5, 3));   // центр тяжести
        assertTrue(CapturePoint.contains(tri, 5, 1));    // у основания
        assertTrue(CapturePoint.contains(tri, 5, 9));    // у вершины, на оси симметрии — внутри
        assertFalse(CapturePoint.contains(tri, 8, 8));   // за правой гипотенузой
        assertFalse(CapturePoint.contains(tri, 2, 8));   // за левой гипотенузой
        assertFalse(CapturePoint.contains(tri, -1, 0));  // слева от основания
        assertFalse(CapturePoint.contains(tri, 11, 0));  // справа от основания
    }

    @Test
    void containsRejectsDegeneratePolygon() {
        // Меньше 3 вершин — не полигон
        assertFalse(CapturePoint.contains(List.of(), 5, 5));
        assertFalse(CapturePoint.contains(List.of(new CapturePoint.Vertex(0, 0)), 5, 5));
        assertFalse(CapturePoint.contains(
                List.of(new CapturePoint.Vertex(0, 0), new CapturePoint.Vertex(1, 1)), 5, 5));
    }

    @Test
    void centroidOfSquareIsCenter() {
        CapturePoint.Vertex c = CapturePoint.centroid(square());
        assertEquals(5, c.x());
        assertEquals(5, c.z());
    }

    @Test
    void centroidOfTriangleIsAverage() {
        List<CapturePoint.Vertex> tri = List.of(
                new CapturePoint.Vertex(0, 0),
                new CapturePoint.Vertex(30, 0),
                new CapturePoint.Vertex(15, 30)
        );
        CapturePoint.Vertex c = CapturePoint.centroid(tri);
        assertEquals(15, c.x());  // (0+30+15)/3 = 15
        assertEquals(10, c.z());  // (0+0+30)/3 = 10
    }

    @Test
    void centroidOfEmptyIsOrigin() {
        CapturePoint.Vertex c = CapturePoint.centroid(List.of());
        assertEquals(0, c.x());
        assertEquals(0, c.z());
    }

    @Test
    void containsConcavePolygon() {
        // L-образный (вогнутый) полигон: внешние углы (0,0)-(10,0)-(10,4)-(4,4)-(4,10)-(0,10)
        List<CapturePoint.Vertex> lShape = List.of(
                new CapturePoint.Vertex(0, 0),
                new CapturePoint.Vertex(10, 0),
                new CapturePoint.Vertex(10, 4),
                new CapturePoint.Vertex(4, 4),
                new CapturePoint.Vertex(4, 10),
                new CapturePoint.Vertex(0, 10)
        );
        // Внутри «ножки» L
        assertTrue(CapturePoint.contains(lShape, 2, 2));
        assertTrue(CapturePoint.contains(lShape, 8, 2));
        // Внутри «полки» L (верхняя часть)
        assertTrue(CapturePoint.contains(lShape, 2, 8));
        // В вырезе (вогнутая часть) — снаружи
        assertFalse(CapturePoint.contains(lShape, 7, 7));
        assertFalse(CapturePoint.contains(lShape, 8, 8));
    }
}
