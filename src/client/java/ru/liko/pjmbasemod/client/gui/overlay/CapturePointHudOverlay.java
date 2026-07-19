package ru.liko.pjmbasemod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
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
 *
 * Стиль мода: размытый blur-фон под плашкой (scissor + processBlurEffect),
 * полупрозрачная тёмная подложка без рамок, тонкая цветная полоска-акцент
 * слева — как в NotificationOverlay / TacticalTabOverlay.
 */
public final class CapturePointHudOverlay {

    private static final int BAR_HEIGHT_IDLE = 16;
    private static final int BAR_HEIGHT_PROGRESS = 30;
    private static final int BAR_WIDTH = 160;
    private static final int BAR_Y = 76;

    private static final int NEUTRAL_ACCENT = 0xFF9AA0A6;
    private static final int TITLE_COLOR = 0xFFDDDDDD;
    private static final int SUB_COLOR = 0xFFE8E8E8;
    private static final int PERCENT_COLOR = 0xFFB8B8B8;

    public static final LayeredDraw.Layer OVERLAY = (graphics, deltaTracker) -> render(graphics, deltaTracker);

    private static float animHeight = BAR_HEIGHT_IDLE;
    private static float animAlpha = 0f;
    private static long lastFrameTime = System.currentTimeMillis();

    private CapturePointHudOverlay() {}

    private static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrameTime) / 1000f);
        lastFrameTime = now;

        if (mc.player == null || mc.options.hideGui || mc.screen != null) {
            fadeOut(dt);
            return;
        }

        CapturePointHudPacket hud = ClientCapturePointState.hud();

        if (hud == null) {
            fadeOut(dt);
            if (animAlpha > 0.01f) {
                drawBar(g, mc.font, null, (int) animHeight, animAlpha, deltaTracker);
            }
            return;
        }

        boolean hasProgress = hud.progressPercent() > 0 || hud.capturing() || hud.neutralizing() || hud.locked();
        float targetHeight = hasProgress ? BAR_HEIGHT_PROGRESS : BAR_HEIGHT_IDLE;
        animHeight += (targetHeight - animHeight) * Math.min(1f, 12f * dt);
        animAlpha += (1f - animAlpha) * Math.min(1f, 12f * dt);

        drawBar(g, mc.font, hud, (int) animHeight, animAlpha, deltaTracker);
    }

    private static void fadeOut(float dt) {
        animHeight = Math.max(BAR_HEIGHT_IDLE, animHeight - 60f * dt);
        animAlpha = Math.max(0f, animAlpha - 6f * dt);
    }

    private static void drawBar(GuiGraphics g, Font font, CapturePointHudPacket hud, int height,
                                float alpha, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int x = (screenWidth - BAR_WIDTH) / 2;
        int y = BAR_Y;
        int right = x + BAR_WIDTH;
        int bottom = y + height;

        int accent = accentColor(hud);
        int a = (int) Mth.clamp(alpha * 255f, 0f, 255f);

        int bg = withAlpha(0x0E1014, (int)(0xA8 * alpha));
        int accentCol = withAlpha(accent, a);

        g.pose().pushPose();
        try {
            RenderSystem.disableDepthTest();

            // Blur только под плашкой, остальной HUD остаётся чётким.
            g.enableScissor(x, y, right, bottom);
            mc.gameRenderer.processBlurEffect(deltaTracker.getGameTimeDeltaPartialTick(false));
            mc.getMainRenderTarget().bindWrite(false);
            g.disableScissor();

            RenderSystem.enableBlend();

            // Полупрозрачный фон без рамок + тонкая цветная полоска-акцент слева.
            g.fill(x, y, right, bottom, bg);
            g.fill(x, y, x + 2, bottom, accentCol);

            if (hud == null) return;

            // Имя точки + владелец
            String ownerText = hud.ownerTeamName().isEmpty() ? "нейтрально" : hud.ownerTeamName();
            String line1 = hud.pointName() + " · " + ownerText;
            g.drawCenteredString(font, line1, screenWidth / 2, y + 3, withAlpha(TITLE_COLOR, a));

            if (height >= BAR_HEIGHT_PROGRESS - 2) {
                // Состояние — цвет из scoreboard team (ownerColor/captureColor из пакета)
                String state;
                int stateBase;
                if (hud.locked() && !hud.neutralizing() && !hud.capturing()) {
                    // Цепной захват: точка закрыта для команды игрока.
                    state = "Сначала захватите соседнюю точку";
                    stateBase = NEUTRAL_ACCENT;
                } else if (hud.neutralizing()) {
                    state = "Нейтрализация: " + hud.captureTeamName();
                    stateBase = hud.captureColor() == 0 ? NEUTRAL_ACCENT : hud.captureColor();
                } else if (hud.capturing()) {
                    state = "Захват: " + hud.captureTeamName();
                    stateBase = hud.captureColor() == 0 ? NEUTRAL_ACCENT : hud.captureColor();
                } else {
                    state = "Под контролем";
                    stateBase = hud.ownerColor() == 0 ? NEUTRAL_ACCENT : hud.ownerColor();
                }
                g.drawCenteredString(font, state, screenWidth / 2, y + 13, withAlpha(stateBase, a));

                // Процент справа от состояния
                String pct = hud.progressPercent() + "%";
                g.drawString(font, pct, right - 6 - font.width(pct), y + 13, withAlpha(PERCENT_COLOR, a), false);

                // Полоса прогресса (плоская, без контура)
                int barX = x + 12;
                int barY = y + height - 6;
                int barW = BAR_WIDTH - 24;
                int barH = 3;
                g.fill(barX, barY, barX + barW, barY + barH, withAlpha(0x222229, a));
                int fillW = (int) (barW * Mth.clamp(hud.progressPercent() / 100f, 0f, 1f));
                if (fillW > 0) g.fill(barX, barY, barX + fillW, barY + barH, withAlpha(accent, a));
            }
        } finally {
            RenderSystem.disableBlend();
            g.pose().popPose();
        }
    }

    private static int accentColor(CapturePointHudPacket hud) {
        if (hud == null) return NEUTRAL_ACCENT;
        if (hud.neutralizing() || hud.capturing()) {
            return hud.captureColor() == 0 ? NEUTRAL_ACCENT : hud.captureColor();
        }
        if (!hud.ownerTeamName().isEmpty()) {
            return hud.ownerColor() == 0 ? NEUTRAL_ACCENT : hud.ownerColor();
        }
        return NEUTRAL_ACCENT;
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }
}
