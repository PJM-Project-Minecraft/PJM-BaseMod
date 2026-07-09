package ru.liko.pjmbasemod.client.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;
import ru.liko.pjmbasemod.client.capturepoint.ClientCapturePointState;
import ru.liko.pjmbasemod.common.network.packet.CapturePointHudPacket;

/**
 * HUD точки захвата: плашка по центру сверху с именем точки, состоянием
 * (NEUTRALIZE/CAPTURE/SECURED) и полосой прогресса. Показывается, когда
 * локальный игрок стоит внутри точки захвата.
 */
public final class CapturePointHudOverlay {

    private static final int BAR_HEIGHT_IDLE = 20;
    private static final int BAR_HEIGHT_PROGRESS = 32;
    private static final int BAR_WIDTH = 180;
    private static final int BAR_Y = 8;

    public static final LayeredDraw.Layer OVERLAY = (graphics, deltaTracker) -> render(graphics);

    private static float animHeight = BAR_HEIGHT_IDLE;
    private static long lastFrameTime = System.currentTimeMillis();

    private CapturePointHudOverlay() {}

    private static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

        CapturePointHudPacket hud = ClientCapturePointState.hud();
        if (hud == null) {
            // Плавно схлопываемся
            long now = System.currentTimeMillis();
            float dt = Math.min(0.1f, (now - lastFrameTime) / 1000f);
            lastFrameTime = now;
            animHeight = Math.max(BAR_HEIGHT_IDLE, animHeight - 60f * dt);
            if (animHeight > BAR_HEIGHT_IDLE + 0.5f) {
                drawBar(graphics, mc.font, null, (int) animHeight);
            }
            return;
        }

        boolean hasProgress = hud.progressPercent() > 0 || hud.capturing() || hud.neutralizing();
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrameTime) / 1000f);
        lastFrameTime = now;
        float target = hasProgress ? BAR_HEIGHT_PROGRESS : BAR_HEIGHT_IDLE;
        animHeight += (target - animHeight) * Math.min(1f, 12f * dt);

        drawBar(graphics, mc.font, hud, (int) animHeight);
    }

    private static void drawBar(GuiGraphics g, Font font, CapturePointHudPacket hud, int height) {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int x = (screenWidth - BAR_WIDTH) / 2;
        int y = BAR_Y;

        // Фон плашки
        g.fill(x, y, x + BAR_WIDTH, y + height, 0xE0101014);
        // Рамка
        g.fill(x, y, x + BAR_WIDTH, y + 1, 0xFF353540);
        g.fill(x, y + height - 1, x + BAR_WIDTH, y + height, 0xFF353540);
        g.fill(x, y, x + 1, y + height, 0xFF353540);
        g.fill(x + BAR_WIDTH - 1, y, x + BAR_WIDTH, y + height, 0xFF353540);

        if (hud == null) return;

        // Имя точки + владелец
        String ownerText = hud.ownerTeamName().isEmpty() ? "нейтрально" : hud.ownerTeamName();
        String line1 = hud.pointName() + " · " + ownerText;
        g.drawCenteredString(font, line1, screenWidth / 2, y + 5, 0xFFE8E8E8);

        if (height >= BAR_HEIGHT_PROGRESS - 2) {
            // Состояние
            String state;
            int stateColor;
            if (hud.neutralizing()) {
                state = "Нейтрализация: " + hud.captureTeamName();
                stateColor = hud.captureColor() == 0 ? 0xFFC13D : hud.captureColor();
            } else if (hud.capturing()) {
                state = "Захват: " + hud.captureTeamName();
                stateColor = hud.captureColor() == 0 ? 0xFFC13D : hud.captureColor();
            } else {
                state = "Под контролем";
                stateColor = hud.ownerColor() == 0 ? 0x4A90E2 : hud.ownerColor();
            }
            g.drawCenteredString(font, state, screenWidth / 2, y + 16, stateColor);

            // Полоса прогресса
            int barX = x + 10;
            int barY = y + height - 7;
            int barW = BAR_WIDTH - 20;
            int barH = 4;
            g.fill(barX, barY, barX + barW, barY + barH, 0xFF222229);
            int fillW = (int) (barW * Mth.clamp(hud.progressPercent() / 100f, 0f, 1f));
            int fillColor = hud.neutralizing() ? 0xFFE74C3C : (hud.capturing() ? 0xFF2ECC71 : stateColor);
            if (fillW > 0) g.fill(barX, barY, barX + fillW, barY + barH, fillColor);

            // Процент
            g.drawCenteredString(font, hud.progressPercent() + "%", screenWidth / 2, y + height - 16, 0xFFB8B8B8);
        }
    }
}
