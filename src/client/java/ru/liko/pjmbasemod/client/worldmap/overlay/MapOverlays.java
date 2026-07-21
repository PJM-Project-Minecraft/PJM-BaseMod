package ru.liko.pjmbasemod.client.worldmap.overlay;

import java.util.List;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import ru.liko.pjmbasemod.client.basezone.ClientBaseZoneState;
import ru.liko.pjmbasemod.client.capturepoint.ClientCapturePointState;
import ru.liko.pjmbasemod.client.radiospawn.ClientRadioCarrierState;
import ru.liko.pjmbasemod.client.worldmap.gui.MapRenderer;
import ru.liko.pjmbasemod.common.basezone.BaseZoneView;
import ru.liko.pjmbasemod.common.capturepoint.CapturePoint;
import ru.liko.pjmbasemod.common.network.packet.RadioSpawnListPacket;

/**
 * Тактические оверлеи поверх карты: зоны баз (AABB команд) и точки захвата (полигоны).
 * Всё в мировых координатах через трансформ камеры {@link MapRenderer}. Данные — клиентские
 * зеркала {@link ClientBaseZoneState} / {@link ClientCapturePointState}.
 */
public final class MapOverlays {

    private static final int NEUTRAL_COLOR = 0x9B9B9B;
    private static final int CONTESTED_COLOR = 0xE0A020;

    private MapOverlays() {}

    public static void render(GuiGraphics gg, Font font, double camX, double camZ,
                              double scale, int width, int height, String dim, String skipCaptureId) {
        drawBaseZones(gg, font, camX, camZ, scale, width, height, dim);
        drawCapturePoints(gg, font, camX, camZ, scale, width, height, dim, skipCaptureId);
    }

    /** Носители рации своей команды (точки десанта). Данные уже отфильтрованы сервером по измерению. */
    public static void drawRadioCarriers(GuiGraphics gg, Font font, double camX, double camZ,
                                         double scale, int width, int height) {
        for (RadioSpawnListPacket.Entry e : ClientRadioCarrierState.carriers()) {
            int sx = (int) MapRenderer.worldToScreenX(e.pos().getX() + 0.5, camX, scale, width);
            int sy = (int) MapRenderer.worldToScreenY(e.pos().getZ() + 0.5, camZ, scale, height);
            if (sx < -24 || sx > width + 24 || sy < -32 || sy > height + 24) continue;
            drawRadioMarker(gg, font, sx, sy, e.owner(), e.cooldownSeconds());
        }
    }

    /** Иконка рации: антенна с сигнал-волнами + корпус + ник; серая с отсчётом на перезарядке. */
    private static void drawRadioMarker(GuiGraphics gg, Font font, int sx, int sy, String name, int cooldown) {
        int col = cooldown > 0 ? 0xFF9AA0A6 : 0xFF4CE05A;
        int dark = 0xFF0C120B;
        // радар-пинг активной рации (расходящийся ромб)
        if (cooldown == 0) {
            float ping = (Util.getMillis() % 1600L) / 1600f;
            int pr = (int) (7 + ping * 12);
            int pc = (((int) ((1f - ping) * 0x55)) << 24) | (col & 0xFFFFFF);
            ringDiamond(gg, sx, sy, pr, pc);
        }
        // антенна + наконечник
        gg.fill(sx - 1, sy - 13, sx + 1, sy - 3, dark);
        gg.fill(sx, sy - 13, sx + 1, sy - 3, 0xFFF0F0F0);
        gg.fill(sx - 1, sy - 15, sx + 2, sy - 13, col);
        // сигнал-волны по бокам
        gg.fill(sx - 4, sy - 15, sx - 3, sy - 10, col);
        gg.fill(sx + 3, sy - 15, sx + 4, sy - 10, col);
        gg.fill(sx - 6, sy - 17, sx - 5, sy - 8, col);
        gg.fill(sx + 5, sy - 17, sx + 6, sy - 8, col);
        // корпус рации
        gg.fill(sx - 5, sy - 3, sx + 6, sy + 5, dark);
        gg.fill(sx - 4, sy - 2, sx + 5, sy + 4, col);
        gg.fill(sx - 2, sy - 1, sx + 2, sy + 3, dark);
        // ник
        int tw = font.width(name);
        gg.fill(sx - tw / 2 - 2, sy + 7, sx + tw / 2 + 2, sy + 17, 0x99000000);
        gg.drawCenteredString(font, name, sx, sy + 8, cooldown > 0 ? 0xFFCFCFCF : 0xFFCDEFD2);
        // отсчёт перезарядки
        if (cooldown > 0) {
            String cd = cooldown + "s";
            int cw = font.width(cd);
            gg.fill(sx - cw / 2 - 2, sy - 28, sx + cw / 2 + 2, sy - 18, 0x99000000);
            gg.drawCenteredString(font, cd, sx, sy - 27, 0xFFFF7777);
        }
    }

    private static void drawBaseZones(GuiGraphics gg, Font font, double camX, double camZ,
                                      double scale, int width, int height, String dim) {
        for (BaseZoneView z : ClientBaseZoneState.zones()) {
            if (!z.dimension().equals(dim)) continue;
            double sx0 = MapRenderer.worldToScreenX(z.minX(), camX, scale, width);
            double sy0 = MapRenderer.worldToScreenY(z.minZ(), camZ, scale, height);
            double sx1 = MapRenderer.worldToScreenX(z.maxX() + 1, camX, scale, width);
            double sy1 = MapRenderer.worldToScreenY(z.maxZ() + 1, camZ, scale, height);
            int x0 = (int) Math.min(sx0, sx1), y0 = (int) Math.min(sy0, sy1);
            int x1 = (int) Math.max(sx0, sx1), y1 = (int) Math.max(sy0, sy1);
            if (x1 < 0 || y1 < 0 || x0 > width || y0 > height) continue;

            int rgb = z.ownerColor() & 0xFFFFFF;
            gg.fill(x0, y0, x1, y1, (0x30 << 24) | rgb);      // полупрозрачная заливка территории
            int border = 0xFF000000 | rgb;
            gg.fill(x0, y0, x1, y0 + 2, border);
            gg.fill(x0, y1 - 2, x1, y1, border);
            gg.fill(x0, y0, x0 + 2, y1, border);
            gg.fill(x1 - 2, y0, x1, y1, border);
            gg.drawCenteredString(font, z.displayName(), (x0 + x1) / 2, (y0 + y1) / 2 - 4, 0xFFFFFFFF);
        }
    }

    private static void drawCapturePoints(GuiGraphics gg, Font font, double camX, double camZ,
                                          double scale, int width, int height, String dim, String skipCaptureId) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(Util.getMillis() / 380.0);
        for (CapturePoint cp : ClientCapturePointState.points()) {
            if (!cp.dimension().equals(dim)) continue;
            if (skipCaptureId != null && cp.id().equals(skipCaptureId)) continue; // рисует редактор
            List<CapturePoint.Vertex> vs = cp.vertices();
            int n = vs.size();
            if (n < 2) continue;

            boolean contested = cp.contested();
            boolean owned = !cp.ownerTeamId().isEmpty();
            int rgb = (contested ? CONTESTED_COLOR : owned ? cp.ownerColor() : NEUTRAL_COLOR) & 0xFFFFFF;

            double[] xs = new double[n];
            double[] ys = new double[n];
            for (int i = 0; i < n; i++) {
                xs[i] = MapRenderer.worldToScreenX(vs.get(i).x() + 0.5, camX, scale, width);
                ys[i] = MapRenderer.worldToScreenY(vs.get(i).z() + 0.5, camZ, scale, height);
            }

            // Заливка территории (контест пульсирует), затем контур.
            if (n >= 3) {
                int fillA = contested ? (int) (0x2E + pulse * 0x3C) : (owned ? 0x2A : 0x1C);
                MapRenderer.fillPolygon(gg, xs, ys, n, (fillA << 24) | rgb, width, height);
            }
            float th = contested ? 2f + pulse * 1.6f : 2f;
            int outline = 0xFF000000 | rgb;
            for (int i = 0; i < n; i++) {
                MapRenderer.line(gg, xs[i], ys[i], xs[(i + 1) % n], ys[(i + 1) % n], th, outline);
            }

            // Маркер + подпись + прогресс в центроиде.
            CapturePoint.Vertex c = CapturePoint.centroid(vs);
            int cx = (int) MapRenderer.worldToScreenX(c.x() + 0.5, camX, scale, width);
            int cy = (int) MapRenderer.worldToScreenY(c.z() + 0.5, camZ, scale, height);
            drawDiamond(gg, cx, cy, rgb, contested ? pulse : -1f, width, height);
            drawLabelPill(gg, font, cx, cy - 17, cp.displayName(), rgb);
            if (!cp.captureTeamId().isEmpty() && cp.progressPercent() > 0) {
                drawProgressBar(gg, cx, cy + 7, cp.progressPercent(), rgb);
            }
        }
    }

    /** Ромб-маркер точки; при pulse≥0 — расходящийся радар-пинг (контест). */
    private static void drawDiamond(GuiGraphics gg, int cx, int cy, int rgb, float pulse, int w, int h) {
        if (pulse >= 0f) {
            int pr = (int) (5 + pulse * 8);
            int pc = (((int) ((1f - pulse) * 0x66)) << 24) | (rgb & 0xFFFFFF);
            ringDiamond(gg, cx, cy, pr, pc);
        }
        double[] bx = {cx, cx + 5.0, cx, cx - 5.0};
        double[] by = {cy - 5.0, cy, cy + 5.0, cy};
        MapRenderer.fillPolygon(gg, bx, by, 4, 0xFF0A0A0A, w, h);
        double[] fx = {cx, cx + 4.0, cx, cx - 4.0};
        double[] fy = {cy - 4.0, cy, cy + 4.0, cy};
        MapRenderer.fillPolygon(gg, fx, fy, 4, 0xFF000000 | (rgb & 0xFFFFFF), w, h);
        gg.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
    }

    private static void ringDiamond(GuiGraphics gg, int cx, int cy, int r, int argb) {
        MapRenderer.line(gg, cx, cy - r, cx + r, cy, 1.5f, argb);
        MapRenderer.line(gg, cx + r, cy, cx, cy + r, 1.5f, argb);
        MapRenderer.line(gg, cx, cy + r, cx - r, cy, 1.5f, argb);
        MapRenderer.line(gg, cx - r, cy, cx, cy - r, 1.5f, argb);
    }

    private static void drawLabelPill(GuiGraphics gg, Font font, int cx, int topY, String text, int rgb) {
        int tw = font.width(text);
        int x0 = cx - tw / 2 - 4;
        int x1 = cx + tw / 2 + 4;
        gg.fill(x0, topY, x1, topY + 11, 0xC00A0A12);
        gg.fill(x0, topY, x1, topY + 1, 0xFF000000 | (rgb & 0xFFFFFF));
        gg.drawCenteredString(font, text, cx, topY + 2, 0xFFFFFFFF);
    }

    private static void drawProgressBar(GuiGraphics gg, int cx, int y, int pct, int rgb) {
        int bw = 34;
        int x0 = cx - bw / 2;
        gg.fill(x0 - 1, y - 1, x0 + bw + 1, y + 4, 0xC00A0A12);
        gg.fill(x0, y, x0 + bw, y + 3, 0xFF2A2A2E);
        int fillW = Math.max(1, bw * Math.min(100, pct) / 100);
        gg.fill(x0, y, x0 + fillW, y + 3, 0xFF000000 | (rgb & 0xFFFFFF));
    }
}
