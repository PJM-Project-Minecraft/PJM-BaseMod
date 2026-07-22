package ru.liko.pjmbasemod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;

public class NotificationOverlay {

    public static class Notification {
        public final Component title;
        public final Component subtitle;
        public final ResourceLocation icon;
        public final int accentColor;
        public final long durationMs;

        public Notification(Component title, Component subtitle, ResourceLocation icon, int accentColor, long durationMs) {
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            this.accentColor = accentColor;
            this.durationMs = durationMs;
        }
    }

    private static final Queue<Notification> queue = new LinkedList<>();
    private static Notification currentNotification = null;
    private static long startTime = 0;

    private static float currentAnimProgress = 0f;
    private static long lastFrameTime = System.currentTimeMillis();

    public static void show(Component title, Component subtitle, ResourceLocation icon, int accentColor, long durationMs) {
        queue.offer(new Notification(title, subtitle, icon, accentColor, durationMs));
    }

    public static final LayeredDraw.Layer OVERLAY = (graphics, deltaTracker) -> {
        Minecraft mc = Minecraft.getInstance();
        long time = System.currentTimeMillis();
        float dt = (time - lastFrameTime) / 1000.0f;
        if (dt > 0.1f) dt = 0.1f;
        lastFrameTime = time;

        if (currentNotification == null && !queue.isEmpty()) {
            currentNotification = queue.poll();
            startTime = time;
            currentAnimProgress = 0f;
        }

        if (currentNotification == null) {
            return;
        }

        long elapsed = time - startTime;

        boolean shouldShow = (mc.screen == null) && !mc.options.hideGui;

        float targetProgress = 0f;
        if (shouldShow && startTime != 0 && elapsed < currentNotification.durationMs) {
            targetProgress = 1.0f;
        }

        // Smooth slide-in / slide-out
        currentAnimProgress += (targetProgress - currentAnimProgress) * 14.0f * dt;
        currentAnimProgress = Mth.clamp(currentAnimProgress, 0.0f, 1.0f);

        if (currentAnimProgress <= 0.01f && targetProgress == 0f) {
            currentNotification = null;
            return;
        }

        if (currentAnimProgress <= 0.01f) {
            return;
        }

        int sw = mc.getWindow().getGuiScaledWidth();
        int alphaInt = (int) (currentAnimProgress * 255);
        if (alphaInt > 255) alphaInt = 255;
        if (alphaInt < 0) alphaInt = 0;

        String titleStr = currentNotification.title.getString().toUpperCase(Locale.ROOT);
        String subtitleStr = currentNotification.subtitle.getString().toUpperCase(Locale.ROOT);

        int titleWidth = (int) (mc.font.width(titleStr) * 0.8f);
        int subtitleWidth = mc.font.width(subtitleStr);
        int maxTextWidth = Math.max(titleWidth, subtitleWidth);

        int textOffsetX = currentNotification.icon != null ? 36 : 12;
        int width = Math.max(180, textOffsetX + maxTextWidth + 24);
        int height = 36;

        // Полупрозрачный тёмный фон (как в TAB/верхнем баре) поверх blur.
        int bgAlpha = (int)(0xA8 * currentAnimProgress);
        int colorBg = (bgAlpha << 24) | 0x0E1014;

        int accentBase = currentNotification.accentColor & 0x00FFFFFF;
        int colorAccent = (alphaInt << 24) | accentBase;

        int colorTextTitle = (alphaInt << 24) | accentBase;
        int colorTextSub = (alphaInt << 24) | 0xEEEEEE;

        // Выезд сверху по центру экрана (под верхним баром).
        int targetX = (sw - width) / 2;
        int targetY = 40;
        int x = targetX;
        int y = targetY - (int)((height + 20) * (1.0f - currentAnimProgress));

        // Масштаб от разрешения вокруг верх-центра. Scissor ниже наследует pose (transformMaxBounds).
        ru.liko.pjmbasemod.client.gui.PjmGuiUtils.pushHudScale(graphics, sw / 2f, 0);
        RenderSystem.enableBlend();

        try {
            // Blur-фон под плашкой (scissor + processBlurEffect), как в TAB.
            graphics.enableScissor(x, y, x + width, y + height);
            mc.gameRenderer.processBlurEffect(deltaTracker.getGameTimeDeltaPartialTick(false));
            mc.getMainRenderTarget().bindWrite(false);
            graphics.disableScissor();

            // Полупрозрачный фон без рамок.
            graphics.fill(x, y, x + width, y + height, colorBg);

            // Тонкая цветная полоска-акцент слева.
            graphics.fill(x, y, x + 2, y + height, colorAccent);

            // Icon
            if (currentNotification.icon != null) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, currentAnimProgress);
                graphics.blit(currentNotification.icon, x + 10, y + 10, 0, 0, 16, 16, 16, 16);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            // Title
            graphics.pose().pushPose();
            graphics.pose().translate(x + textOffsetX, y + 6, 0);
            graphics.pose().scale(0.8f, 0.8f, 1.0f);
            graphics.drawString(mc.font, titleStr, 0, 0, colorTextTitle, false);
            graphics.pose().popPose();

            // Subtitle
            graphics.drawString(mc.font, subtitleStr, x + textOffsetX, y + 18, colorTextSub, false);

        } finally {
            RenderSystem.disableBlend();
            graphics.pose().popPose();
        }
    };
}
