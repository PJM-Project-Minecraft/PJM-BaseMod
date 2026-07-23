package ru.liko.pjmbasemod.client.worldmap.overlay;

import java.util.List;
import java.util.Map;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import ru.liko.pjmbasemod.client.basezone.ClientBaseZoneState;
import ru.liko.pjmbasemod.client.missile.ClientMissileState;
import ru.liko.pjmbasemod.client.capturepoint.ClientCapturePointState;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.client.mapmarker.ClientMapMarkerState;
import ru.liko.pjmbasemod.client.radiospawn.ClientRadioCarrierState;
import ru.liko.pjmbasemod.client.worldmap.MapSettings;
import ru.liko.pjmbasemod.client.worldmap.gui.MapRenderer;
import ru.liko.pjmbasemod.common.basezone.BaseZoneView;
import ru.liko.pjmbasemod.common.capturepoint.CapturePoint;
import ru.liko.pjmbasemod.common.network.packet.MapMarkerSyncPacket;
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
        drawMissileImpacts(gg, camX, camZ, scale, width, height, dim);
        drawMissileTracks(gg, camX, camZ, scale, width, height, dim);
        drawMissileAlerts(gg, font, camX, camZ, scale, width, height, dim);
        drawCapturePoints(gg, font, camX, camZ, scale, width, height, dim, skipCaptureId);
    }

    private static final int TRACK_COLOR = 0xE6A640;

    /**
     * Стрелки живых ракет своей команды: позиция и курс, апдейт с сервера раз в 10 тиков.
     * Приватность гарантирует сервер — чужим командам трек просто не приходит.
     */
    private static void drawMissileTracks(GuiGraphics gg, double camX, double camZ,
                                          double scale, int width, int height, String dim) {
        for (ClientMissileState.Track track : ClientMissileState.tracks()) {
            if (!track.dimension().equals(dim)) continue;
            double sx = MapRenderer.worldToScreenX(track.x(), camX, scale, width);
            double sy = MapRenderer.worldToScreenY(track.z(), camZ, scale, height);
            if (sx < -24 || sx > width + 24 || sy < -24 || sy > height + 24) continue;

            // Курс из yaw сущности: направление полёта в мировых (x, z) = (-sin, cos).
            double a = Math.toRadians(track.yaw());
            double dx = -Math.sin(a), dz = Math.cos(a);
            double px = -dz, pz = dx;
            double[] xs = {sx + dx * 10, sx - dx * 6 + px * 5, sx - dx * 3, sx - dx * 6 - px * 5};
            double[] ys = {sy + dz * 10, sy - dz * 6 + pz * 5, sy - dz * 3, sy - dz * 6 - pz * 5};
            MapRenderer.fillPolygon(gg, xs, ys, 4, 0xCC000000 | TRACK_COLOR, width, height);
            for (int i = 0; i < 4; i++) {
                MapRenderer.line(gg, xs[i], ys[i], xs[(i + 1) % 4], ys[(i + 1) % 4],
                        1.5f, 0xFF000000 | TRACK_COLOR);
            }
        }
    }

    /**
     * Зоны предупреждения о летящих ракетах: пульсирующий круг зоны поражения.
     * Своей команде — янтарный с названием ракеты (появляется мгновенно при пуске),
     * остальным — красный без названия (сервер шлёт с задержкой ~8.5 с).
     */
    private static void drawMissileAlerts(GuiGraphics gg, Font font, double camX, double camZ,
                                          double scale, int width, int height, String dim) {
        long now = Util.getMillis();
        float pulse = 0.6f + 0.4f * (float) Math.sin(now / 220.0);
        for (ClientMissileState.StrikeAlert alert : ClientMissileState.alerts()) {
            if (!alert.dimension().equals(dim)) continue;
            int sx = (int) MapRenderer.worldToScreenX(alert.x(), camX, scale, width);
            int sy = (int) MapRenderer.worldToScreenY(alert.z(), camZ, scale, height);
            double rPx = alert.radius() * 2.0 * scale;
            if (sx + rPx < 0 || sx - rPx > width || sy + rPx < 0 || sy - rPx > height) continue;

            int rgb = (alert.ownTeam() ? CONTESTED_COLOR : 0xE04A3D) & 0xFFFFFF;
            double[] xs = new double[IMPACT_SEGMENTS];
            double[] ys = new double[IMPACT_SEGMENTS];
            for (int i = 0; i < IMPACT_SEGMENTS; i++) {
                double a = Math.PI * 2.0 * i / IMPACT_SEGMENTS;
                xs[i] = sx + Math.cos(a) * rPx;
                ys[i] = sy + Math.sin(a) * rPx;
            }
            int fillA = (int) (0x28 + 0x20 * pulse);
            MapRenderer.fillPolygon(gg, xs, ys, IMPACT_SEGMENTS, (fillA << 24) | rgb, width, height);
            int outline = 0xFF000000 | rgb;
            for (int i = 0; i < IMPACT_SEGMENTS; i++) {
                MapRenderer.line(gg, xs[i], ys[i],
                        xs[(i + 1) % IMPACT_SEGMENTS], ys[(i + 1) % IMPACT_SEGMENTS],
                        1.5f + pulse * 1.5f, outline);
            }
            float t = (now % 1400L) / 1400f;
            blitSprite(gg, PING, sx, sy, 16 + t * 26, PING_TW, PING_TH, rgb, (1f - t) * 0.85f);

            String label = alert.ownTeam() && !alert.missileName().isBlank()
                    ? alert.missileName()
                    : net.minecraft.network.chat.Component.translatable(
                            "gui.pjmbasemod.missile.warning.title").getString();
            drawLabelPill(gg, font, sx, sy - 18, label, rgb);
        }
    }

    // ── поражения ракет ──

    private static final int IMPACT_COLOR = 0xE04A3D;
    private static final int IMPACT_SHOT_DOWN_COLOR = 0x9AA0A6;
    private static final int IMPACT_SEGMENTS = 32;
    private static final long IMPACT_FRESH_MS = 60_000L;

    /**
     * Отметки поражения ракет: круг реальной досягаемости урона (у SBW ~2× радиуса взрыва)
     * с крестом в эпицентре; свежий удар (первая минута) пульсирует пингом, отметка плавно
     * гаснет к концу {@link ClientMissileState#IMPACT_TTL_MS}. Сбитые ракеты — серым.
     */
    private static void drawMissileImpacts(GuiGraphics gg, double camX, double camZ,
                                           double scale, int width, int height, String dim) {
        long now = Util.getMillis();
        for (ClientMissileState.Impact impact : ClientMissileState.impacts()) {
            if (!impact.dimension().equals(dim)) continue;
            int sx = (int) MapRenderer.worldToScreenX(impact.x(), camX, scale, width);
            int sy = (int) MapRenderer.worldToScreenY(impact.z(), camZ, scale, height);
            double rPx = impact.radius() * 2.0 * scale;
            if (sx + rPx < 0 || sx - rPx > width || sy + rPx < 0 || sy - rPx > height) continue;

            long age = now - impact.timeMs();
            float fade = 1f - Mth.clamp((float) age / ClientMissileState.IMPACT_TTL_MS, 0f, 1f);
            float alpha = 0.35f + 0.65f * fade;
            int rgb = (impact.shotDown() ? IMPACT_SHOT_DOWN_COLOR : IMPACT_COLOR) & 0xFFFFFF;

            double[] xs = new double[IMPACT_SEGMENTS];
            double[] ys = new double[IMPACT_SEGMENTS];
            for (int i = 0; i < IMPACT_SEGMENTS; i++) {
                double a = Math.PI * 2.0 * i / IMPACT_SEGMENTS;
                xs[i] = sx + Math.cos(a) * rPx;
                ys[i] = sy + Math.sin(a) * rPx;
            }
            int fillA = (int) (0x38 * alpha);
            MapRenderer.fillPolygon(gg, xs, ys, IMPACT_SEGMENTS, (fillA << 24) | rgb, width, height);
            int lineA = (int) (0xE0 * alpha);
            int outline = (lineA << 24) | rgb;
            for (int i = 0; i < IMPACT_SEGMENTS; i++) {
                MapRenderer.line(gg, xs[i], ys[i],
                        xs[(i + 1) % IMPACT_SEGMENTS], ys[(i + 1) % IMPACT_SEGMENTS], 1.6f, outline);
            }

            // Крест эпицентра фиксированного экранного размера.
            int cross = 5;
            MapRenderer.line(gg, sx - cross, sy - cross, sx + cross, sy + cross, 2f, outline);
            MapRenderer.line(gg, sx - cross, sy + cross, sx + cross, sy - cross, 2f, outline);

            if (!impact.shotDown() && age < IMPACT_FRESH_MS) {
                float t = (now % 1400L) / 1400f;
                blitSprite(gg, PING, sx, sy, 14 + t * 22, PING_TW, PING_TH, rgb, (1f - t) * 0.8f);
            }
        }
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

    /** Маяк рации: диск с тёмной обводкой и точкой + расходящийся пинг; серый с отсчётом на перезарядке. */
    public static void drawRadioMarker(GuiGraphics gg, Font font, int sx, int sy, String name, int cooldown) {
        int rgb = cooldown > 0 ? 0x9AA0A6 : 0x4CE05A;
        if (cooldown == 0) {
            float t = (Util.getMillis() % 1600L) / 1600f;
            blitSprite(gg, PING, sx, sy, 12 + t * 18, PING_TW, PING_TH, rgb, (1f - t) * 0.7f);
        }
        drawDisc(gg, sx, sy, 6.0, 0xD00A0A12);
        drawDisc(gg, sx, sy, 4.5, 0xFF000000 | (rgb & 0xFFFFFF));
        gg.fill(sx - 1, sy - 1, sx + 1, sy + 1, 0xE00A0A12);
        drawLabelPill(gg, font, sx, sy + 8, name, rgb);
        if (cooldown > 0) {
            String cd = cooldown + "s";
            int cw = font.width(cd);
            gg.fill(sx - cw / 2 - 2, sy - 20, sx + cw / 2 + 2, sy - 9, PjmGuiUtils.SCREEN_HEADER);
            gg.drawCenteredString(font, cd, sx, sy - 18, 0xFFFF7777);
        }
    }

    // ── тактические метки команды (иконки NATO-стиля, видны только своей команде) ──

    private static final int MARKER_SIZE = 18, MARKER_TEX = 64;
    private static final Map<String, ResourceLocation> MARKER_ICONS = Map.of(
            "arrow", markerRl("marker_arrow"),
            "infantry", markerRl("marker_infantry"),
            "vehicle", markerRl("marker_vehicle"),
            "danger", markerRl("marker_danger"));
    private static final ResourceLocation ARROWHEAD = markerRl("marker_arrowhead");
    private static final int ARROW_RGB = PjmGuiUtils.ACCENT & 0xFFFFFF;

    private static ResourceLocation markerRl(String name) {
        return ResourceLocation.fromNamespaceAndPath("pjmbasemod", "textures/gui/map/" + name + ".png");
    }

    /** Метки своей команды: фиксированный экранный размер, ник автора — пилюлей под меткой при зуме.
     *  Стрелки — Squad-style (линия от якоря до конца + наконечник). Командирские метки подсвечены. */
    public static void drawTacticalMarkers(GuiGraphics gg, Font font, double camX, double camZ,
                                           double scale, int width, int height, String dim) {
        int half = MARKER_SIZE / 2;
        for (MapMarkerSyncPacket.Entry m : ClientMapMarkerState.markers()) {
            if (!m.dimension().equals(dim)) continue;
            int sx = (int) MapRenderer.worldToScreenX(m.x() + 0.5, camX, scale, width);
            int sy = (int) MapRenderer.worldToScreenY(m.z() + 0.5, camZ, scale, height);

            if ("arrow".equals(m.type()) && m.directional()) {
                int ex = (int) MapRenderer.worldToScreenX(m.x2() + 0.5, camX, scale, width);
                int ey = (int) MapRenderer.worldToScreenY(m.z2() + 0.5, camZ, scale, height);
                int pad = 40;
                if ((sx < -pad || sx > width + pad || sy < -pad || sy > height + pad)
                        && (ex < -pad || ex > width + pad || ey < -pad || ey > height + pad)) continue;
                if (m.commander()) drawCommanderRing(gg, sx, sy);
                drawArrowShape(gg, sx, sy, ex, ey, scale, ARROW_RGB, 1f);
                if (scale >= 2.0) {
                    drawLabelPill(gg, font, sx, sy + 6, ownerLabel(m), ARROW_RGB);
                }
                continue;
            }

            ResourceLocation icon = MARKER_ICONS.get(m.type());
            if (icon == null) continue;
            if (sx < -MARKER_SIZE || sx > width + MARKER_SIZE
                    || sy < -MARKER_SIZE || sy > height + MARKER_SIZE) continue;
            if (m.commander()) drawCommanderRing(gg, sx, sy);
            gg.blit(icon, sx - half, sy - half, MARKER_SIZE, MARKER_SIZE,
                    0f, 0f, MARKER_TEX, MARKER_TEX, MARKER_TEX, MARKER_TEX);
            if (scale >= 2.0) {
                drawLabelPill(gg, font, sx, sy + half + 2, ownerLabel(m), ARROW_RGB);
            }
        }
    }

    private static String ownerLabel(MapMarkerSyncPacket.Entry m) {
        return m.commander() ? "★ " + m.owner() : m.owner();
    }

    /** Подсветка командирской метки: пульсирующее янтарное кольцо под иконкой. */
    private static void drawCommanderRing(GuiGraphics gg, int sx, int sy) {
        float pulse = 0.75f + 0.25f * (float) Math.sin(Util.getMillis() / 300.0);
        blitSprite(gg, PING, sx, sy, 26, PING_TW, PING_TH, ARROW_RGB, pulse);
    }

    /**
     * Squad-style стрелка в экранных координатах: тёмная подложка + янтарная линия,
     * наконечник повёрнут по направлению. Используется и для превью при установке.
     */
    public static void drawArrowShape(GuiGraphics gg, double x0, double y0, double x1, double y1,
                                      double scale, int rgb, float alpha) {
        float th = (float) Math.min(9.0, Math.max(3.0, scale * 1.2));
        int headSize = (int) Math.min(36.0, Math.max(16.0, scale * 5.0));
        // укорачиваем линию, чтобы она не торчала из наконечника
        double dx = x1 - x0, dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        double tx = x1 - dx / len * headSize * 0.35;
        double ty = y1 - dy / len * headSize * 0.35;
        int a = Math.round(alpha * 255);
        MapRenderer.line(gg, x0, y0, tx, ty, th + 3f, ((a * 230 / 255) << 24) | 0x0A0A12);
        MapRenderer.line(gg, x0, y0, tx, ty, th, (a << 24) | (rgb & 0xFFFFFF));
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx)) + 90f; // текстура смотрит вверх
        gg.pose().pushPose();
        gg.pose().translate(x1, y1, 0);
        gg.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));
        gg.setColor(1f, 1f, 1f, alpha);
        gg.blit(ARROWHEAD, -headSize / 2, -headSize / 2, headSize, headSize,
                0f, 0f, MARKER_TEX, MARKER_TEX, MARKER_TEX, MARKER_TEX);
        gg.setColor(1f, 1f, 1f, 1f);
        gg.pose().popPose();
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
        // Цепочка захвата (граф links): игрокам — при sequential, OP — всегда в режиме редактора.
        if (ClientCapturePointState.sequential()
                || ru.liko.pjmbasemod.client.worldmap.edit.CapturePointEditor.get().enabled()) {
            drawChainLinks(gg, camX, camZ, scale, width, height, dim);
        }
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

    /** Рёбра графа цепного захвата: линия центроид—центроид, каждое ребро один раз. */
    private static void drawChainLinks(GuiGraphics gg, double camX, double camZ,
                                       double scale, int width, int height, String dim) {
        List<CapturePoint> points = ClientCapturePointState.points();
        for (CapturePoint cp : points) {
            if (!cp.dimension().equals(dim) || cp.links().isEmpty() || cp.vertices().isEmpty()) continue;
            CapturePoint.Vertex a = CapturePoint.centroid(cp.vertices());
            for (String linkId : cp.links()) {
                if (cp.id().compareTo(linkId) >= 0) continue; // рёбра симметричны — рисуем с меньшего id
                CapturePoint other = null;
                for (CapturePoint c : points) {
                    if (c.id().equals(linkId)) { other = c; break; }
                }
                if (other == null || !other.dimension().equals(dim) || other.vertices().isEmpty()) continue;
                CapturePoint.Vertex b = CapturePoint.centroid(other.vertices());
                double ax = MapRenderer.worldToScreenX(a.x() + 0.5, camX, scale, width);
                double ay = MapRenderer.worldToScreenY(a.z() + 0.5, camZ, scale, height);
                double bx = MapRenderer.worldToScreenX(b.x() + 0.5, camX, scale, width);
                double by = MapRenderer.worldToScreenY(b.z() + 0.5, camZ, scale, height);
                MapRenderer.line(gg, ax, ay, bx, by, 3f, 0x60000000);
                MapRenderer.line(gg, ax, ay, bx, by, 1.5f, 0xA0FFFFFF);
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

    private static final ResourceLocation PING =
            ResourceLocation.fromNamespaceAndPath("pjmbasemod", "textures/gui/map/ping.png");
    private static final int PING_TW = 107, PING_TH = 105;

    /** Диск фиксированного экранного размера (16 сегментов) — маркер без текстуры. */
    private static void drawDisc(GuiGraphics gg, int cx, int cy, double r, int argb) {
        int n = 16;
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2.0 * i / n;
            xs[i] = cx + Math.cos(a) * r;
            ys[i] = cy + Math.sin(a) * r;
        }
        MapRenderer.fillPolygon(gg, xs, ys, n, argb, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /** Спрайт из атласа: центрированно, с тинтом и масштабом. */
    private static void blitSprite(GuiGraphics gg, ResourceLocation rl, double cx, double cy,
                                   double size, int tw, int th, int rgb, float alpha) {
        int c = rgb & 0xFFFFFF;
        gg.setColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, alpha);
        int s = (int) Math.round(size);
        gg.blit(rl, (int) Math.round(cx - size / 2), (int) Math.round(cy - size / 2), s, s, 0f, 0f, tw, th, tw, th);
        gg.setColor(1f, 1f, 1f, 1f);
    }

    /** Подпись-пилюля; масштабируется настройкой {@link MapSettings#labelScale()} вокруг своего якоря. */
    private static void drawLabelPill(GuiGraphics gg, Font font, int cx, int topY, String text, int rgb) {
        float s = MapSettings.get().labelScale();
        int tw = font.width(text);
        int x0 = -tw / 2 - 4;
        int x1 = tw / 2 + 4;
        gg.pose().pushPose();
        gg.pose().translate(cx, topY, 0);
        gg.pose().scale(s, s, 1f);
        gg.fill(x0, 0, x1, 11, PjmGuiUtils.SCREEN_HEADER);
        gg.fill(x0, 0, x1, 1, 0xFF000000 | (rgb & 0xFFFFFF));
        gg.drawCenteredString(font, text, 0, 2, PjmGuiUtils.TEXT_PRIMARY);
        gg.pose().popPose();
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
