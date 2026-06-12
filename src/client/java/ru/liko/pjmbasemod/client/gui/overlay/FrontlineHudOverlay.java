package ru.liko.pjmbasemod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.world.level.ChunkPos;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.client.frontline.ClientFrontlineState;
import ru.liko.pjmbasemod.common.network.packet.FrontlineHudPacket;

public final class FrontlineHudOverlay {

    public static final LayeredDraw.Layer OVERLAY = (graphics, deltaTracker) -> render(graphics);

    private FrontlineHudOverlay() {}

    private static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!Config.isFrontlineHudEnabled() || mc.player == null || mc.options.hideGui || mc.screen != null) return;

        FrontlineHudPacket hud = ClientFrontlineState.hud();
        ChunkPos clientChunk = mc.player.chunkPosition();
        int sectorX = hud == null ? Math.floorDiv(clientChunk.x, 3) : hud.sectorX();
        int sectorZ = hud == null ? Math.floorDiv(clientChunk.z, 3) : hud.sectorZ();

        Font font = mc.font;
        int width = 280;
        boolean hasProgress = hud != null && (hud.progressPercent() > 0 || hud.secondsRemaining() > 0);
        int height = hasProgress ? 38 : 22;
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int x = cx - width / 2;
        int y = 5; // Top center margin
        
        int accent = accentColor(hud);
        int ownerColor = hud == null ? accent : withAlpha(hud.ownerColor(), 0xFF);

        graphics.pose().pushPose();
        RenderSystem.enableBlend();
        try {
            // Main dark bar (Squad style)
            graphics.fill(x, y, x + width, y + height, 0x99000000);
            
            // Top colored indicator line
            graphics.fill(x, y, x + width, y + 2, ownerColor);
            
            // Subtle bottom gradient line
            graphics.fillGradient(x, y + height - 1, cx, y + height, 0x00FFFFFF, 0x55FFFFFF);
            graphics.fillGradient(cx, y + height - 1, x + width, y + height, 0x55FFFFFF, 0x00FFFFFF);

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
                
                // Center: Capture Point Name
                graphics.drawCenteredString(font, captureName, cx, y + 8, hud.captureActive() ? 0xFFFFFFFF : 0xFFFFCC00);
                
                // Progress Bar
                int barW = 200;
                int barH = 6;
                int barX = cx - barW / 2;
                int barY = y + 24;
                
                // Bar Background
                graphics.fill(barX, barY, barX + barW, barY + barH, 0x66000000);
                
                // Bar Fill
                int fillW = hud.progressPercent() <= 0 ? 0 : Math.max(1, barW * hud.progressPercent() / 100);
                graphics.fill(barX, barY, barX + fillW, barY + barH, withAlpha(accent, 0xEE));
                
                // Bar Outline
                graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY, 0x33FFFFFF);
                graphics.fill(barX - 1, barY + barH, barX + barW + 1, barY + barH + 1, 0x33FFFFFF);
                graphics.fill(barX - 1, barY, barX, barY + barH, 0x33FFFFFF);
                graphics.fill(barX + barW, barY, barX + barW + 1, barY + barH, 0x33FFFFFF);
                
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
