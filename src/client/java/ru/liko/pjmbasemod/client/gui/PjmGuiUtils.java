package ru.liko.pjmbasemod.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;
import ru.liko.pjmbasemod.Pjmbasemod;

/**
 * Общие UI-константы и утилиты для всех экранов и оверлеев мода.
 * Стиль: Amber-Tactical — полупрозрачные тёмные панели + янтарный акцент.
 */
public final class PjmGuiUtils {

    // -------------------------------------------------------------------------
    // Amber-Tactical палитра
    // -------------------------------------------------------------------------

    // Базовый тон — тёмный сине-стальной («штабной планшет»), а не чистый чёрный:
    // мягче контраст, теплее янтарный акцент лёг сверху.
    /** Фон панели (81% непрозрачности). */
    public static final int SCREEN_BG          = 0xCE12161C;
    /** Хедер панели (87%). */
    public static final int SCREEN_HEADER      = 0xE00D1015;
    /** Сайдбар (80%). */
    public static final int SCREEN_SIDEBAR     = 0xCC0F131A;
    /** Граница панели — янтарный. */
    public static final int SCREEN_BORDER      = 0x66FFB020;
    /** Скрим поверх игрового мира. */
    public static final int SCREEN_SCRIM       = 0xCC05070A;

    /** Фон строки. */
    public static final int SCREEN_ROW         = 0xAA1B2129;
    /** Фон строки при наведении. */
    public static final int SCREEN_ROW_HOVER   = 0xBB26313D;
    /** Фон заблокированной строки. */
    public static final int SCREEN_ROW_LOCKED  = 0x99101319;
    /** Выбранная строка — янтарный тинт. */
    public static final int SCREEN_SELECT      = 0x55FFB020;

    // Акцентный янтарный
    public static final int ACCENT             = 0xFFFFB020;
    public static final int ACCENT_DIM         = 0x77FFB020;

    // Цвета текста — тёплый офф-вайт вместо чистого белого (мягче контраст)
    public static final int TEXT_PRIMARY       = 0xFFF2ECE0;
    public static final int TEXT_DIM           = 0xFFC7CDD6;
    public static final int TEXT_MUTED         = 0xFF7F8794;
    public static final int TEXT_LABEL         = 0xFF9AA4B0;
    /** Янтарный текст (ценники, статусы). */
    public static final int TEXT_GOLD          = 0xFFFFB020;

    // Кнопки — насыщенные семантические цвета (зелёный/красный/янтарный/серый)
    public static final int BTN_GREEN          = 0xFF2F7D4F;
    public static final int BTN_GREEN_HOVER    = 0xFF3EA063;
    public static final int BTN_RED            = 0xFF9E3B32;
    public static final int BTN_RED_HOVER      = 0xFFC24A3E;
    /** Вторичное действие (сдать, сохранить). */
    public static final int BTN_AMBER          = 0xFF4A3A16;
    public static final int BTN_AMBER_HOVER    = 0xFF6B531F;
    public static final int BTN_DISABLED       = 0xFF2A2F36;

    // -------------------------------------------------------------------------
    // Обратная совместимость (legacy aliases)
    // -------------------------------------------------------------------------
    /** @deprecated Используй {@link #SCREEN_BG}. */
    @Deprecated public static final int COLOR_BACKGROUND    = SCREEN_BG;
    /** @deprecated Используй {@link #SCREEN_BG}. */
    @Deprecated public static final int COLOR_PANEL         = SCREEN_BG;
    /** @deprecated Используй {@link #SCREEN_BORDER}. */
    @Deprecated public static final int COLOR_PANEL_BORDER  = SCREEN_BORDER;
    /** @deprecated Используй {@link #ACCENT}. */
    @Deprecated public static final int COLOR_ORANGE_ACCENT = ACCENT;
    /** @deprecated */
    @Deprecated public static final int COLOR_BLUE_ACCENT   = 0xFF3498DB;
    /** @deprecated */
    @Deprecated public static final int COLOR_RED_ACCENT    = 0xFFE74C3C;
    /** @deprecated */
    @Deprecated public static final int COLOR_GREEN_ACCENT  = 0xFF2ECC71;
    /** @deprecated Используй {@link #TEXT_PRIMARY}. */
    @Deprecated public static final int COLOR_WHITE         = TEXT_PRIMARY;
    /** @deprecated Используй {@link #TEXT_LABEL}. */
    @Deprecated public static final int COLOR_GRAY          = TEXT_LABEL;

    private PjmGuiUtils() {}

    /** Строка версии для футеров меню: реальная версия мода из метаданных NeoForge. */
    public static String versionLabel() {
        String v = ModList.get().getModContainerById(Pjmbasemod.MODID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("?");
        return "ver. " + Pjmbasemod.MODID.toUpperCase() + " " + v;
    }

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
     * Рисует тонкую горизонтальную янтарную линию (2 px).
     * Используется как акцент-разделитель под хедером.
     */
    public static void drawAccentLine(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 2, ACCENT_DIM);
    }

    /**
     * Рисует стандартную панель мода (фон + рамка + хедер с акцент-линией + сайдбар).
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
            // Янтарная акцент-линия под хедером
            drawAccentLine(g, x, y + headerHeight - 2, w);
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
        g.fill(x, y, x + trackW, y + height, 0x33111111);
        int thumbH = Math.max(12, height * visible / total);
        int maxScroll = total - visible;
        int thumbY = y + (height - thumbH) * scroll / Math.max(1, maxScroll);
        g.fill(x, thumbY, x + trackW, thumbY + thumbH, ACCENT_DIM);
    }

    // -------------------------------------------------------------------------
    // Legacy-методы (совместимость)
    // -------------------------------------------------------------------------

    /** @deprecated Используй {@link #drawScreenPanel}. */
    @Deprecated
    public static void drawDarkPanel(GuiGraphics gg, int x, int y, int w, int h) {
        gg.fill(x, y, x + w, y + h, SCREEN_BG);
        gg.fill(x, y, x + w, y + 1, SCREEN_BORDER);
        gg.fill(x, y + h - 1, x + w, y + h, SCREEN_BORDER);
        gg.fill(x, y, x + 1, y + h, SCREEN_BORDER);
        gg.fill(x + w - 1, y, x + w, y + h, SCREEN_BORDER);
    }

    /** @deprecated Используй {@link #drawScreenPanel}. */
    @Deprecated
    public static void drawRoundedPanel(GuiGraphics gg, int x, int y, int w, int h, int color) {
        gg.fill(x + 1, y, x + w - 1, y + h, color);
        gg.fill(x, y + 1, x + w, y + h - 1, color);
    }
}
