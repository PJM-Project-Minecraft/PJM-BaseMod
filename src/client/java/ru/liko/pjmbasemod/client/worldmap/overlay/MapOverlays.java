package ru.liko.pjmbasemod.client.worldmap.overlay;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import ru.liko.pjmbasemod.client.basezone.ClientBaseZoneState;
import ru.liko.pjmbasemod.client.capturepoint.ClientCapturePointState;
import ru.liko.pjmbasemod.client.worldmap.gui.MapRenderer;
import ru.liko.pjmbasemod.common.basezone.BaseZoneView;
import ru.liko.pjmbasemod.common.capturepoint.CapturePoint;

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
                              double scale, int width, int height, String dim) {
        drawBaseZones(gg, font, camX, camZ, scale, width, height, dim);
        drawCapturePoints(gg, font, camX, camZ, scale, width, height, dim);
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
                                          double scale, int width, int height, String dim) {
        for (CapturePoint cp : ClientCapturePointState.points()) {
            if (!cp.dimension().equals(dim)) continue;
            List<CapturePoint.Vertex> vs = cp.vertices();
            if (vs.size() < 2) continue;

            int rgb = (cp.contested() ? CONTESTED_COLOR
                    : cp.ownerTeamId().isEmpty() ? NEUTRAL_COLOR : cp.ownerColor()) & 0xFFFFFF;
            int argb = 0xFF000000 | rgb;
            for (int i = 0; i < vs.size(); i++) {
                CapturePoint.Vertex a = vs.get(i);
                CapturePoint.Vertex b = vs.get((i + 1) % vs.size());
                double ax = MapRenderer.worldToScreenX(a.x() + 0.5, camX, scale, width);
                double ay = MapRenderer.worldToScreenY(a.z() + 0.5, camZ, scale, height);
                double bx = MapRenderer.worldToScreenX(b.x() + 0.5, camX, scale, width);
                double by = MapRenderer.worldToScreenY(b.z() + 0.5, camZ, scale, height);
                line(gg, ax, ay, bx, by, 2f, argb);
            }

            CapturePoint.Vertex c = CapturePoint.centroid(vs);
            int lx = (int) MapRenderer.worldToScreenX(c.x() + 0.5, camX, scale, width);
            int ly = (int) MapRenderer.worldToScreenY(c.z() + 0.5, camZ, scale, height);
            String label = cp.captureTeamId().isEmpty()
                    ? cp.displayName()
                    : cp.displayName() + " " + cp.progressPercent() + "%";
            gg.drawCenteredString(font, label, lx, ly - 4, 0xFFFFFFFF);
        }
    }

    /** Отрезок произвольного угла — повёрнутый тонкий прямоугольник через PoseStack. */
    private static void line(GuiGraphics gg, double x1, double y1, double x2, double y2, float thickness, int argb) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5) return;
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(x1, y1, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        int half = Math.max(1, Math.round(thickness / 2f));
        gg.fill(0, -half, (int) Math.round(len), half, argb);
        pose.popPose();
    }
}
