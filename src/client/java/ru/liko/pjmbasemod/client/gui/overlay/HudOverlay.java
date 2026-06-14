package ru.liko.pjmbasemod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.client.config.ClientHudConfig;

@EventBusSubscriber(modid = Pjmbasemod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class HudOverlay {

    private static final int COMPASS_WIDTH = 340;
    private static final int COMPASS_HEIGHT = 22;
    private static final int COMPASS_BOTTOM_MARGIN = 0; // Вплотную к краю
    private static final float PIXELS_PER_DEGREE = 2.5f;
    // Азимутная система (север=0), согласованная с yaw = getViewYRot + 180 и числом азимута.
    private static final CompassMark[] COMPASS_MARKS = {
            new CompassMark("N", 0, true),
            new CompassMark("NE", 45, false),
            new CompassMark("E", 90, true),
            new CompassMark("SE", 135, false),
            new CompassMark("S", 180, true),
            new CompassMark("SW", 225, false),
            new CompassMark("W", 270, true),
            new CompassMark("NW", 315, false)
    };

    private static boolean inZone = false;

    public static final LayeredDraw.Layer COMPASS_OVERLAY = (g, deltaTracker) -> {
        renderCompass(g, deltaTracker.getGameTimeDeltaPartialTick(false));
    };

    private static void renderCompass(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int compassWidth = Math.min(COMPASS_WIDTH, width - 40);
        if (compassWidth < 120) {
            return;
        }

        int left = (width - compassWidth) / 2;
        int right = left + compassWidth;
        int centerX = width / 2;
        int bottom = height - COMPASS_BOTTOM_MARGIN;
        int top = bottom - COMPASS_HEIGHT;
        
        // Offset by 180 to align 'N' to the north (0 in MC is South).
        float yaw = Mth.positiveModulo(mc.player.getViewYRot(partialTick) + 180.0f, 360.0f);

        graphics.pose().pushPose();
        RenderSystem.enableBlend();
        try {
            // Draw custom horizontal gradients via strips
            drawHorizontalFade(graphics, left, right, top, bottom, 0x000000, 0x77); // Background
            drawHorizontalFade(graphics, left, right, top, top + 1, 0xFFFFFF, 0x44); // Top border line

            graphics.enableScissor(left, top - 20, right, bottom + 10);
            try {
                renderDegreeTicks(graphics, mc.font, yaw, centerX, top, left, right, compassWidth);
                renderCompassMarks(graphics, mc.font, yaw, centerX, top, left, right, compassWidth);
            } finally {
                graphics.disableScissor();
            }
            
            // Center marker
            renderCenterMarker(graphics, centerX, top, bottom);
            
            // Exact heading numbers
            renderHeading(graphics, mc.font, yaw, centerX, top);
        } finally {
            RenderSystem.disableBlend();
            graphics.pose().popPose();
        }
    }

    private static void drawHorizontalFade(GuiGraphics graphics, int left, int right, int top, int bottom, int baseColor, int maxAlpha) {
        int cx = (left + right) / 2;
        int halfWidth = cx - left;
        int strips = 20; // smooth gradient
        float step = halfWidth / (float) strips;
        for (int i = 0; i < strips; i++) {
            int x1 = Math.round(cx - step * (i + 1));
            int x2 = Math.round(cx - step * i);
            int x3 = Math.round(cx + step * i);
            int x4 = Math.round(cx + step * (i + 1));
            
            float progress = (float) i / strips;
            // Smooth fade out: 1.0 at center, 0.0 at edge (progress goes from 0 to 1)
            float alphaF = Mth.clamp(1.3f - 1.3f * progress, 0.0f, 1.0f);
            int currentAlpha = (int) (maxAlpha * alphaF);
            if (currentAlpha <= 0) continue;
            
            int c = (baseColor & 0x00FFFFFF) | (currentAlpha << 24);
            graphics.fill(x1, top, x2, bottom, c); // Left side
            graphics.fill(x3, top, x4, bottom, c); // Right side
        }
    }

    private static void renderDegreeTicks(GuiGraphics graphics, Font font, float yaw, int centerX, int top, int left, int right, int compassWidth) {
        float halfWidth = compassWidth / 2.0f;
        for (int degree = 0; degree < 360; degree += 15) {
            if (degree % 45 == 0) continue; // Skip N, E, S, W and NE, SE, SW, NW

            float delta = Mth.wrapDegrees(degree - yaw);
            int x = Math.round(centerX + delta * PIXELS_PER_DEGREE);
            if (x < left || x > right) continue;

            float dist = Math.abs(x - centerX) / halfWidth;
            float alphaF = Mth.clamp(1.3f - 1.3f * dist, 0.0f, 1.0f);
            if (alphaF <= 0.05f) continue;
            
            int alpha = (int)(alphaF * 255) << 24;
            int tickColor = (0x77FFFFFF & 0x00FFFFFF) | ((int)(alphaF * 0x77) << 24);

            // Short tick
            graphics.fill(x, top + 1, x + 1, top + 5, tickColor);
            
            if (degree % 15 == 0) {
                String text = String.valueOf(degree);
                int tw = font.width(text);
                float scale = 0.55f;
                int textColor = (0xFFAAAAAA & 0x00FFFFFF) | alpha;

                graphics.pose().pushPose();
                graphics.pose().translate(x - (tw * scale) / 2f, top + 7, 0);
                graphics.pose().scale(scale, scale, 1f);
                graphics.drawString(font, text, 0, 0, textColor, true);
                graphics.pose().popPose();
            }
        }
    }

    private static void renderCompassMarks(GuiGraphics graphics, Font font, float yaw, int centerX, int top, int left, int right, int compassWidth) {
        float halfWidth = compassWidth / 2.0f;
        for (CompassMark mark : COMPASS_MARKS) {
            float delta = Mth.wrapDegrees(mark.degrees - yaw);
            int x = Math.round(centerX + delta * PIXELS_PER_DEGREE);
            if (x + 15 < left || x - 15 > right) continue;

            float dist = Math.abs(x - centerX) / halfWidth;
            float alphaF = Mth.clamp(1.3f - 1.3f * dist, 0.0f, 1.0f);
            if (alphaF <= 0.05f) continue;

            int alpha = (int)(alphaF * 255) << 24;

            // Long tick
            int baseTickColor = mark.cardinal ? 0xBBFFFFFF : 0x88FFFFFF;
            int tickColor = (baseTickColor & 0x00FFFFFF) | ((int)(alphaF * ((baseTickColor >> 24) & 0xFF)) << 24);
            graphics.fill(x, top + 1, x + 1, top + 7, tickColor);
            
            int tw = font.width(mark.label);
            int color = mark.cardinal ? 0xFFFFFFFF : 0xFFCCCCCC;
            
            // Highlight North
            if (mark.degrees == 0) {
                color = 0xFFFFCC00; // Yellowish/Orange for North
            }
            
            color = (color & 0x00FFFFFF) | alpha;
            
            float scale = mark.cardinal ? 0.9f : 0.7f;
            graphics.pose().pushPose();
            graphics.pose().translate(x - (tw * scale) / 2f, top + 9, 0);
            graphics.pose().scale(scale, scale, 1f);
            graphics.drawString(font, mark.label, 0, 0, color, true);
            graphics.pose().popPose();
        }
    }

    private static void renderCenterMarker(GuiGraphics graphics, int centerX, int top, int bottom) {
        // Simple distinct vertical line for current heading
        graphics.fill(centerX, top, centerX + 1, bottom - 3, 0xDDFFAA00);
    }

    private static void renderHeading(GuiGraphics graphics, Font font, float yaw, int centerX, int top) {
        int heading = Math.round(yaw) % 360;
        if (heading < 0) heading += 360;
        String text = String.format("%03d", heading);
        int textWidth = font.width(text);
        
        int boxTop = top - 13;
        int boxBottom = top - 1;
        
        // Small tactical box above center
        graphics.fill(centerX - textWidth / 2 - 3, boxTop, centerX + textWidth / 2 + 3, boxBottom, 0xAA000000);
        graphics.fill(centerX - textWidth / 2 - 3, boxBottom, centerX + textWidth / 2 + 3, boxBottom + 1, 0x88FFAA00);
        
        graphics.drawString(font, text, centerX - textWidth / 2, boxTop + 2, 0xFFFFFFFF, false);
    }

    private record CompassMark(String label, int degrees, boolean cardinal) {}

    public static void setInZoneStatus(boolean status) {
        inZone = status;
    }

    public static boolean isInZone() {
        return inZone;
    }

    public static void setTeamBalance(String team1Name, int team1Balance, String team2Name, int team2Balance) {
        // no-op after team system removal
    }

    public static void reset() {
        inZone = false;
    }

    public static void setSafeZoneStatus(boolean inOwn, boolean enemyBlockPulse) {
        // no-op
    }

    @SubscribeEvent
    public static void onRenderFood(RenderGuiLayerEvent.Pre e) {
        if (ClientHudConfig.disableHunger() && e.getName().equals(VanillaGuiLayers.FOOD_LEVEL)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderArmor(RenderGuiLayerEvent.Pre e) {
        if (ClientHudConfig.hideArmorBar() && e.getName().equals(VanillaGuiLayers.ARMOR_LEVEL)) {
            e.setCanceled(true);
        }
    }
}
