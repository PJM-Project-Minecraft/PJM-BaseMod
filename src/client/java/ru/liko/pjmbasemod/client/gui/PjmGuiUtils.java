package ru.liko.pjmbasemod.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Общие UI-константы и утилиты для всех экранов и оверлеев мода.
 */
public final class PjmGuiUtils {

    // -------------------------------------------------------------------------
    // Старые константы (оставлены для совместимости с оверлеями)
    // -------------------------------------------------------------------------
    public static final int COLOR_BACKGROUND   = 0xFF000000;
    public static final int COLOR_PANEL        = 0xFF121212;
    public static final int COLOR_PANEL_BORDER = 0xFF2B2B2B;
    public static final int COLOR_ORANGE_ACCENT = 0xFFE67E22;
    public static final int COLOR_BLUE_ACCENT  = 0xFF3498DB;
    public static final int COLOR_RED_ACCENT   = 0xFFE74C3C;
    public static final int COLOR_GREEN_ACCENT = 0xFF2ECC71;
    public static final int COLOR_WHITE        = 0xFFFFFFFF;
    public static final int COLOR_GRAY         = 0xFFB0B0B0;

    // -------------------------------------------------------------------------
    // Палитра экранов (Warehouse / Garage / Faction*)
    // -------------------------------------------------------------------------
    /** Фон панели (почти непрозрачный тёмный). */
    public static final int SCREEN_BG          = 0xF216161A;
    /** Граница панели. */
    public static final int SCREEN_BORDER      = 0xFF353540;
    /** Фон хедера. */
    public static final int SCREEN_HEADER      = 0xFF1F1F26;
    /** Фон сайдбара. */
    public static final int SCREEN_SIDEBAR     = 0xFF1A1A20;
    /** Фон строки по умолчанию. */
    public static final int SCREEN_ROW         = 0xFF222229;
    /** Фон строки при наведении. */
    public static final int SCREEN_ROW_HOVER   = 0xFF2A2A33;
    /** Фон заблокированной строки. */
    public static final int SCREEN_ROW_LOCKED  = 0xFF1D1D22;
    /** Выделенная строка (синяя). */
    public static final int SCREEN_SELECT      = 0xFF35506E;
    /** Затемняющий скрим поверх игрового мира. */
    public static final int SCREEN_SCRIM       = 0xCC050507;

    // Цвета текста
    public static final int TEXT_PRIMARY   = 0xFFE8E8E8;
    public static final int TEXT_DIM       = 0xFFB8B8B8;
    public static final int TEXT_MUTED     = 0xFF777777;
    public static final int TEXT_LABEL     = 0xFF9AA0A6;
    public static final int TEXT_GOLD      = 0xFFD8B15F;

    private PjmGuiUtils() {}

    // -------------------------------------------------------------------------
    // Цветовые утилиты
    // -------------------------------------------------------------------------

    /** Задаёт альфа-канал цвета. */
    public static int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    /** Линейная интерполяция двух ARGB-цветов. */
    public static int lerpColor(int from, int to, float t) {
        int a = lerpChannel(from, to, t, 24);
        int r = lerpChannel(from, to, t, 16);
        int g = lerpChannel(from, to, t, 8);
        int b = lerpChannel(from, to, t, 0);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, float t, int shift) {
        int f = (from >> shift) & 0xFF;
        int v = (to   >> shift) & 0xFF;
        return Mth.clamp(Math.round(f + (v - f) * t), 0, 255);
    }

    // -------------------------------------------------------------------------
    // Рисование
    // -------------------------------------------------------------------------

    /**
     * Рисует внешнюю рамку толщиной 1px вокруг прямоугольника (x,y,w,h).
     */
    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y,         color); // top
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, color); // bottom
        g.fill(x - 1, y,     x,          y + h,     color); // left
        g.fill(x + w, y,     x + w + 1,  y + h,     color); // right
    }

    /**
     * Рисует стандартную тёмную панель мода (фон + рамка + хедер + сайдбар).
     * Вызывать до рисования содержимого.
     *
     * @param sidebarWidth ширина левой боковой панели (0 — без сайдбара)
     * @param headerHeight высота верхней полоски (0 — без хедера)
     */
    public static void drawScreenPanel(GuiGraphics g,
                                       int x, int y, int w, int h,
                                       int sidebarWidth, int headerHeight) {
        g.fill(x, y, x + w, y + h, SCREEN_BG);
        drawBorder(g, x, y, w, h, SCREEN_BORDER);
        if (headerHeight > 0) {
            g.fill(x, y, x + w, y + headerHeight, SCREEN_HEADER);
        }
        if (sidebarWidth > 0) {
            int sidebarTop = y + headerHeight;
            g.fill(x, sidebarTop, x + sidebarWidth, y + h, SCREEN_SIDEBAR);
        }
    }

    /**
     * Рисует иконку с билинейной фильтрацией (без пикселизации при масштабировании).
     */
    public static void drawSmoothIcon(GuiGraphics g, ResourceLocation icon,
                                      int x, int y, int w, int h, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderTexture(0, icon);
        RenderSystem.texParameter(3553, 10241, 9729); // GL_TEXTURE_MIN_FILTER = GL_LINEAR
        RenderSystem.texParameter(3553, 10240, 9729); // GL_TEXTURE_MAG_FILTER = GL_LINEAR
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(icon, x, y, 0, 0, w, h, w, h);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** Удобная перегрузка без явной альфы (alpha=1.0). */
    public static void drawSmoothIcon(GuiGraphics g, ResourceLocation icon, int x, int y, int w, int h) {
        drawSmoothIcon(g, icon, x, y, w, h, 1.0f);
    }

    /**
     * Обрезает строку с «...» если она не влезает в maxWidth пикселей.
     */
    public static String ellipsize(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String suffix = "...";
        int suffixWidth = font.width(suffix);
        String result = text;
        while (!result.isEmpty() && font.width(result) + suffixWidth > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? suffix : result + suffix;
    }

    /**
     * Рисует вертикальный скроллбар.
     *
     * @param total   общее число элементов
     * @param visible видимых элементов
     * @param scroll  текущая позиция прокрутки
     */
    public static void drawScrollbar(GuiGraphics g, int x, int y, int height, int total, int visible, int scroll) {
        if (total <= visible) return;
        int trackW = 3;
        g.fill(x, y, x + trackW, y + height, 0xFF15151A);
        int thumbH = Math.max(12, height * visible / total);
        int maxScroll = total - visible;
        int thumbY = y + (height - thumbH) * scroll / Math.max(1, maxScroll);
        g.fill(x, thumbY, x + trackW, thumbY + thumbH, 0xFF454552);
    }

    // -------------------------------------------------------------------------
    // Старые методы (совместимость)
    // -------------------------------------------------------------------------

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

