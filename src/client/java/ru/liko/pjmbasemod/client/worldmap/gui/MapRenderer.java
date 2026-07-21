package ru.liko.pjmbasemod.client.worldmap.gui;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.GuiGraphics;
import ru.liko.pjmbasemod.client.worldmap.WorldMapEngine;
import ru.liko.pjmbasemod.client.worldmap.data.MapConstants;
import ru.liko.pjmbasemod.client.worldmap.data.Region;

/**
 * Отрисовка тайлов регионов с трансформом камеры. Камера задаётся мировой точкой в центре
 * экрана (camX,camZ) и масштабом scale (пикселей на блок). Текстура региона — 512 текселей =
 * 512 блоков, поэтому pose масштабируется на scale.
 */
public final class MapRenderer {

    private MapRenderer() {}

    public static void render(GuiGraphics gg, WorldMapEngine engine,
                              double camX, double camZ, double scale, int width, int height) {
        double halfW = width / 2.0;
        double halfH = height / 2.0;
        int side = MapConstants.REGION_BLOCKS;
        for (Region region : engine.regionsInView(camX, camZ, scale, width, height)) {
            double sx = (region.worldMinX() - camX) * scale + halfW;
            double sy = (region.worldMinZ() - camZ) * scale + halfH;

            PoseStack pose = gg.pose();
            pose.pushPose();
            pose.translate(sx, sy, 0);
            pose.scale((float) scale, (float) scale, 1f);
            gg.blit(region.gpu.location(), 0, 0, 0, 0, side, side, side, side);
            pose.popPose();
        }
    }

    // Преобразования мир↔экран (для маркеров/оверлеев).
    public static double worldToScreenX(double worldX, double camX, double scale, int width) {
        return (worldX - camX) * scale + width / 2.0;
    }

    public static double worldToScreenY(double worldZ, double camZ, double scale, int height) {
        return (worldZ - camZ) * scale + height / 2.0;
    }

    public static double screenToWorldX(double screenX, double camX, double scale, int width) {
        return (screenX - width / 2.0) / scale + camX;
    }

    public static double screenToWorldZ(double screenY, double camZ, double scale, int height) {
        return (screenY - height / 2.0) / scale + camZ;
    }

    /** Отрезок произвольного угла — повёрнутый тонкий прямоугольник через PoseStack. */
    public static void line(GuiGraphics gg, double x1, double y1, double x2, double y2, float thickness, int argb) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5) return;
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        PoseStack pose = gg.pose();
        pose.pushPose();
        pose.translate(x1, y1, 0);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));
        int half = Math.max(1, Math.round(thickness / 2f));
        gg.fill(0, -half, (int) Math.round(len), half, argb);
        pose.popPose();
    }
}
