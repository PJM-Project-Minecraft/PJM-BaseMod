package ru.liko.pjmbasemod.client.worldmap.overlay;

import java.util.List;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
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

    /** Маяк рации (иконки Xaero): циан-кольцо + расходящийся пинг; серый с отсчётом на перезарядке. */
    private static void drawRadioMarker(GuiGraphics gg, Font font, int sx, int sy, String name, int cooldown) {
        int rgb = cooldown > 0 ? 0x9AA0A6 : 0x4CE05A;
        if (cooldown == 0) {
            float t = (Util.getMillis() % 1600L) / 1600f;
            blitSprite(gg, PING, sx, sy, 12 + t * 18, PING_TW, PING_TH, rgb, (1f - t) * 0.7f);
        }
        blitSprite(gg, BEACON, sx, sy, 14, BEACON_TW, BEACON_TH, rgb, 1f);
        drawLabelPill(gg, font, sx, sy + 8, name, rgb);
        if (cooldown > 0) {
            String cd = cooldown + "s";
            int cw = font.width(cd);
            gg.fill(sx - cw / 2 - 2, sy - 20, sx + cw / 2 + 2, sy - 9, 0xC00A0A12);
            gg.drawCenteredString(font, cd, sx, sy - 18, 0xFFFF7777);
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
            drawPointIcon(gg, cx, cy, rgb, contested ? pulse : -1f, width, height);
            drawLabelPill(gg, font, cx, cy - 30, cp.displayName(), rgb);
            if (!cp.captureTeamId().isEmpty() && cp.progressPercent() > 0) {
                drawProgressBar(gg, cx, cy + 4, cp.progressPercent(), rgb);
            }
        }
    }

    private static final ResourceLocation POINT_ICON =
            ResourceLocation.fromNamespaceAndPath("pjmbasemod", "textures/gui/map/point.png");
    private static final int ICON_W = 15, ICON_H = 18, ICON_TW = 165, ICON_TH = 196;

    /** Маркер-знамя точки (иконка Xaero, тинт по владельцу), остриём в центроид; контест — пинг-вспышка. */
    private static void drawPointIcon(GuiGraphics gg, int cx, int cy, int rgb, float contested, int w, int h) {
        int c = rgb & 0xFFFFFF;
        if (contested >= 0f) {
            float t = (Util.getMillis() % 1400L) / 1400f;
            blitSprite(gg, PING, cx, cy - ICON_H / 2.0, 10 + t * 16, PING_TW, PING_TH, c, (1f - t) * 0.6f);
        }
        gg.setColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, 1f);
        gg.blit(POINT_ICON, cx - ICON_W / 2, cy - ICON_H, ICON_W, ICON_H, 0f, 0f, ICON_TW, ICON_TH, ICON_TW, ICON_TH);
        gg.setColor(1f, 1f, 1f, 1f);
    }

    private static final ResourceLocation BEACON =
            ResourceLocation.fromNamespaceAndPath("pjmbasemod", "textures/gui/map/beacon.png");
    private static final ResourceLocation PING =
            ResourceLocation.fromNamespaceAndPath("pjmbasemod", "textures/gui/map/ping.png");
    private static final int BEACON_TW = 114, BEACON_TH = 106, PING_TW = 107, PING_TH = 105;

    /** Спрайт из атласа: центрированно, с тинтом и масштабом. */
    private static void blitSprite(GuiGraphics gg, ResourceLocation rl, double cx, double cy,
                                   double size, int tw, int th, int rgb, float alpha) {
        int c = rgb & 0xFFFFFF;
        gg.setColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, alpha);
        int s = (int) Math.round(size);
        gg.blit(rl, (int) Math.round(cx - size / 2), (int) Math.round(cy - size / 2), s, s, 0f, 0f, tw, th, tw, th);
        gg.setColor(1f, 1f, 1f, 1f);
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
