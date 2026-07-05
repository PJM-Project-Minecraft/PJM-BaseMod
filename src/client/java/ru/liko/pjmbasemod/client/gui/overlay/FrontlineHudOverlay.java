package ru.liko.pjmbasemod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.client.frontline.ClientFrontlineState;
import ru.liko.pjmbasemod.common.network.packet.FrontlineHudPacket;

public final class FrontlineHudOverlay {

    /** Целевая высота обычной плашки. */
    private static final int HEIGHT_IDLE = 22;
    /** Целевая высота расширенной плашки (с прогресс-баром). */
    private static final int HEIGHT_PROGRESS = 38;

    /** Состояние плавной анимации (Dynamic Island-стиль: морфинг размеров). */
    private static float animHeight = HEIGHT_IDLE;
    private static float animWidthFactor = 1.0f;
    private static float animPulse = 0f;
    private static long lastFrameTime = System.currentTimeMillis();
    /** Момент последней смены состояния для spring-подъёма. */
    private static boolean lastHasProgress = false;
    private static float springVel = 0f;
    private static float springOffset = 0f;

    public static final LayeredDraw.Layer OVERLAY = (graphics, deltaTracker) -> render(graphics, deltaTracker);

    private FrontlineHudOverlay() {}

    private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!Config.isFrontlineHudEnabled() || mc.player == null || mc.options.hideGui || mc.screen != null) return;

        FrontlineHudPacket hud = ClientFrontlineState.hud();
        ChunkPos clientChunk = mc.player.chunkPosition();
        int sectorX = hud == null ? Math.floorDiv(clientChunk.x, 3) : hud.sectorX();
        int sectorZ = hud == null ? Math.floorDiv(clientChunk.z, 3) : hud.sectorZ();

        Font font = mc.font;
        boolean hasProgress = hud != null && (hud.progressPercent() > 0 || hud.secondsRemaining() > 0);

        // --- Плавная анимация размеров (spring-модель как у Dynamic Island) ---
        long now = System.currentTimeMillis();
        float dt = (now - lastFrameTime) / 1000.0f;
        if (dt > 0.1f) dt = 0.1f;
        if (dt <= 0f) dt = 0.016f;
        lastFrameTime = now;

        float targetHeight = hasProgress ? HEIGHT_PROGRESS : HEIGHT_IDLE;
        // Easing к целевой высоте (критически демпфированный spring).
        float heightDiff = targetHeight - animHeight;
        springVel += heightDiff * 90.0f * dt;
        springVel *= Mth.clamp(1.0f - 8.0f * dt, 0.0f, 1.0f);
        animHeight += springVel * dt;
        // Лёгкое «перелетание» (overshoot) для Dynamic-Island-ощущения.
        if (hasProgress != lastHasProgress) {
            springOffset = hasProgress ? -3.0f : 0f;
            lastHasProgress = hasProgress;
        }
        springOffset += (0f - springOffset) * Mth.clamp(10.0f * dt, 0.0f, 1.0f);

        int height = Math.max(HEIGHT_IDLE, Math.round(animHeight + springOffset));

        // --- Пульсация фона/блюра при активном захвате ---
        boolean captureActive = hud != null && hud.captureActive() && hasProgress;
        if (captureActive) {
            animPulse += dt;
        } else {
            animPulse = 0f;
        }
        // Плавная синусоидальная волна амплитудой 0..1.
        float pulse = captureActive ? (0.5f + 0.5f * (float) Math.sin(animPulse * 2.2f * Math.PI)) : 0f;

        int baseWidth = 280;
        // Лёгкое расширение плашки при захвате.
        int width = baseWidth;
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int x = cx - width / 2;
        int y = 5; // Top center margin
        
        int accent = accentColor(hud);
        int ownerColor = hud == null ? accent : withAlpha(hud.ownerColor(), 0xFF);

        graphics.pose().pushPose();
        RenderSystem.enableBlend();
        try {
            // Blur-фон под баром; радиус блюра пульсирует при захвате (через alpha-подложку).
            graphics.enableScissor(x, y, x + width, y + height);
            mc.gameRenderer.processBlurEffect(deltaTracker.getGameTimeDeltaPartialTick(false));
            mc.getMainRenderTarget().bindWrite(false);
            graphics.disableScissor();

            // Полупрозрачный тёмный фон; alpha «дышит» при активном захвате.
            int bgBase = 0xA8;
            int bgAlpha = captureActive
                    ? Math.min(0xFF, Math.round(bgBase + 0x30 * pulse))
                    : bgBase;
            int colorBg = (bgAlpha << 24) | 0x0E1014;
            graphics.fill(x, y, x + width, y + height, colorBg);

            // Пульсирующая мягкая подложка акцентным цветом при захвате.
            if (captureActive) {
                int pulseAlpha = Math.round(0x44 * pulse);
                graphics.fill(x, y, x + width, y + height, (pulseAlpha << 24) | (accent & 0x00FFFFFF));
            }
            
            // Top colored indicator line
            int lineAlpha = captureActive ? Math.min(0xFF, 0xFF - Math.round(0x33 * pulse)) : 0xFF;
            graphics.fill(x, y, x + width, y + 2, withAlpha(ownerColor, lineAlpha));
            
            if (hud == null) {
                graphics.drawCenteredString(font, "СЕКТОР: " + sectorX + ", " + sectorZ, cx, y + 8, 0xFFDDDDDD);
                return;
            }

            if (hasProgress) {
                String captureName = hud.captureName().toUpperCase();
                String ownerText = hud.inRegion() ? hud.ownerName().toUpperCase() : "ВНЕ ЗОНЫ";
                
                // Left: Sector. Right: Owner
                graphics.drawString(font, "СЕКТОР " + sectorX + "," + sectorZ, x + 8, y + 8, 0xFFAAAAAA, false);
                graphics.drawString(font, ownerText, x + width - 8 - font.width(ownerText), y + 8, ownerColor, false);
                
                // Center: Capture Point Name — мигает при активном захвате.
                int nameColor = hud.captureActive()
                        ? withAlpha(0xFFFFFF, Math.round(0xFF - 0x33 * pulse))
                        : 0xFFFFCC00;
                graphics.drawCenteredString(font, captureName, cx, y + 8, nameColor);
                
                // Progress Bar
                int barW = 200;
                int barH = 6;
                int barX = cx - barW / 2;
                // Плавно поднимаем бар по мере роста высоты плашки.
                int barY = y + Math.round(height - 14);
                
                // Bar Background
                graphics.fill(barX, barY, barX + barW, barY + barH, 0x66000000);
                
                // Bar Fill
                int fillW = hud.progressPercent() <= 0 ? 0 : Math.max(1, barW * hud.progressPercent() / 100);
                int fillAlpha = captureActive ? Math.round(0xEE + 0x11 * pulse) : 0xEE;
                graphics.fill(barX, barY, barX + fillW, barY + barH, withAlpha(accent, Math.min(0xFF, fillAlpha)));

                // Glow при захвате — мягкая подсветка под заливкой.
                if (captureActive) {
                    int glowAlpha = Math.round(0x22 * pulse);
                    graphics.fill(barX, barY - 2, barX + fillW, barY, (glowAlpha << 24) | (accent & 0x00FFFFFF));
                }
                
                // Percent text
                String pctText = hud.progressPercent() + "%";
                graphics.pose().pushPose();
                float scale = 0.6f;
                graphics.pose().translate(barX + barW / 2.0f - (font.width(pctText) * scale) / 2.0f, barY + 1.5f, 0);
                graphics.pose().scale(scale, scale, 1.0f);
                graphics.drawString(font, pctText, 0, 0, 0xFFFFFFFF, false);
                graphics.pose().popPose();

                // Time remaining
                if (hud.secondsRemaining() > 0) {
                    String timeText = hud.secondsRemaining() + "С";
                    graphics.drawString(font, timeText, barX + barW + 6, barY, 0xFFFFCC00, false);
                }

            } else {
                String ownerText = hud.inRegion() ? hud.ownerName().toUpperCase() : "ВНЕ ЗОНЫ";
                graphics.drawString(font, "СЕКТОР " + sectorX + "," + sectorZ, x + 8, y + 7, 0xFFDDDDDD, false);
                graphics.drawString(font, ownerText, x + width - 8 - font.width(ownerText), y + 7, ownerColor, false);
                
                String status = hud.status().toUpperCase();
                if (!status.isEmpty()) {
                    graphics.drawCenteredString(font, status, cx, y + 7, 0xFFFFFFFF);
                }
            }
        } finally {
            RenderSystem.disableBlend();
            graphics.pose().popPose();
        }
    }

    private static int accentColor(FrontlineHudPacket hud) {
        if (hud == null) return 0xFFD8B15F;
        if (hud.progressPercent() > 0 || hud.secondsRemaining() > 0) return withAlpha(hud.captureColor(), 0xFF);
        return withAlpha(hud.ownerColor(), 0xFF);
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }
}
