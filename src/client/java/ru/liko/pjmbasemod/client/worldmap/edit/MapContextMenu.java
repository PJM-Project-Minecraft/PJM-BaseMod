package ru.liko.pjmbasemod.client.worldmap.edit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;

/**
 * Лёгкое контекст-меню для карты (ПКМ по точке захвата в режиме редактора). Поддерживает
 * одноуровневые подменю (владелец/порядок) через стек с пунктом «назад».
 */
public final class MapContextMenu {

    public record Entry(String label, Runnable action, List<Entry> submenu) {
        public static Entry leaf(String label, Runnable action) {
            return new Entry(label, action, null);
        }

        public static Entry sub(String label, List<Entry> children) {
            return new Entry(label, null, children);
        }
    }

    private static final String BACK_LABEL = "‹ Назад";
    private static final int PAD = 4;
    private static final int ROW_H = 12;
    // Amber-Tactical (PjmGuiUtils): стеклянная тёмная панель + приглушённое золото
    private static final int BG = 0xF00E1319;
    private static final int BORDER = 0x80B98A46;
    private static final int HOVER = PjmGuiUtils.SCREEN_SELECT;

    private boolean visible;
    private int x, y;
    private List<Entry> current = List.of();
    private final Deque<List<Entry>> stack = new ArrayDeque<>();

    public boolean visible() {
        return visible;
    }

    public void open(int x, int y, List<Entry> entries) {
        this.x = x;
        this.y = y;
        this.current = entries;
        this.stack.clear();
        this.visible = !entries.isEmpty();
    }

    public void close() {
        visible = false;
        stack.clear();
        current = List.of();
    }

    /** @return true если клик был внутри меню (обработан); клик снаружи закрывает меню. */
    public boolean mouseClicked(double mx, double my, Font font) {
        if (!visible) return false;
        int w = width(font);
        int h = height();
        if (mx < x || mx > x + w || my < y || my > y + h) {
            close();
            return false;
        }
        boolean hasBack = !stack.isEmpty();
        int total = current.size() + (hasBack ? 1 : 0);
        int row = (int) ((my - y - PAD) / ROW_H);
        if (row < 0 || row >= total) return true;
        if (hasBack && row == 0) {
            current = stack.pop();
            return true;
        }
        Entry e = current.get(hasBack ? row - 1 : row);
        if (e.submenu() != null) {
            stack.push(current);
            current = e.submenu();
            return true;
        }
        Runnable a = e.action();
        close();
        if (a != null) a.run();
        return true;
    }

    public void render(GuiGraphics gg, Font font, double mx, double my) {
        if (!visible) return;
        // Z+400 (уровень тултипов): текст подписей маркеров батчится и флашится позже
        // заливок, без подъёма он «просвечивал» бы сквозь меню.
        gg.pose().pushPose();
        gg.pose().translate(0, 0, 400);
        boolean hasBack = !stack.isEmpty();
        int total = current.size() + (hasBack ? 1 : 0);
        int w = width(font);
        int h = height();
        gg.fill(x, y, x + w, y + h, BG);
        gg.fill(x, y, x + w, y + 1, BORDER);
        gg.fill(x, y + h - 1, x + w, y + h, BORDER);
        gg.fill(x, y, x + 1, y + h, BORDER);
        gg.fill(x + w - 1, y, x + w, y + h, BORDER);
        int hoverRow = (mx >= x && mx <= x + w && my >= y && my <= y + h) ? (int) ((my - y - PAD) / ROW_H) : -1;
        for (int i = 0; i < total; i++) {
            int ry = y + PAD + i * ROW_H;
            if (i == hoverRow) gg.fill(x + 1, ry - 1, x + w - 1, ry + ROW_H - 1, HOVER);
            String label;
            if (hasBack && i == 0) {
                label = BACK_LABEL;
            } else {
                Entry e = current.get(hasBack ? i - 1 : i);
                label = e.submenu() != null ? e.label() + "  ▸" : e.label();
            }
            gg.drawString(font, label, x + PAD, ry, PjmGuiUtils.TEXT_PRIMARY);
        }
        gg.pose().popPose();
    }

    private int width(Font font) {
        int max = 60;
        if (!stack.isEmpty()) max = Math.max(max, font.width(BACK_LABEL) + PAD * 2 + 4);
        for (Entry e : current) {
            String l = e.submenu() != null ? e.label() + "  ▸" : e.label();
            max = Math.max(max, font.width(l) + PAD * 2 + 4);
        }
        return max;
    }

    private int height() {
        int total = current.size() + (stack.isEmpty() ? 0 : 1);
        return total * ROW_H + PAD * 2;
    }
}
