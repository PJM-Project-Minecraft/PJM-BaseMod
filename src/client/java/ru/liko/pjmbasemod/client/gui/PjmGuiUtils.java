package ru.liko.pjmbasemod.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Shared UI constants and utility methods used across PJM screens and overlays.
 */
public final class PjmGuiUtils {

    public static final int COLOR_BACKGROUND = 0xFF000000;
    public static final int COLOR_PANEL = 0xFF121212;
    public static final int COLOR_PANEL_BORDER = 0xFF2B2B2B;
    public static final int COLOR_ORANGE_ACCENT = 0xFFE67E22;
    public static final int COLOR_BLUE_ACCENT = 0xFF3498DB;
    public static final int COLOR_RED_ACCENT = 0xFFE74C3C;
    public static final int COLOR_GREEN_ACCENT = 0xFF2ECC71;
    public static final int COLOR_WHITE = 0xFFFFFFFF;
    public static final int COLOR_GRAY = 0xFFB0B0B0;

    private PjmGuiUtils() {
    }

    public static int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    public static void drawDarkPanel(GuiGraphics gg, int x, int y, int w, int h) {
        gg.fill(x, y, x + w, y + h, COLOR_PANEL);
        gg.fill(x, y, x + w, y + 1, COLOR_PANEL_BORDER);
        gg.fill(x, y + h - 1, x + w, y + h, COLOR_PANEL_BORDER);
        gg.fill(x, y, x + 1, y + h, COLOR_PANEL_BORDER);
        gg.fill(x + w - 1, y, x + w, y + h, COLOR_PANEL_BORDER);
    }

    public static void drawRoundedPanel(GuiGraphics gg, int x, int y, int w, int h, int color) {
        gg.fill(x + 1, y, x + w - 1, y + h, color);
        gg.fill(x, y + 1, x + w, y + h - 1, color);
    }
}
