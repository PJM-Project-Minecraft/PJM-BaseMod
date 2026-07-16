package ru.liko.pjmbasemod.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import ru.liko.pjmbasemod.client.gui.PjmChatTime;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.client.gui.PjmUiSounds;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.SubmitReportPacket;
import ru.liko.pjmbasemod.common.report.ReportCategory;
import ru.liko.pjmbasemod.common.report.ReportMessage;
import ru.liko.pjmbasemod.common.report.ReportThread;

import java.util.ArrayList;
import java.util.List;

/**
 * Переписка игрока с администрацией в стиле мессенджера: пузыри сообщений
 * (свои — справа, ответы админа — слева), прокрутка, поле ввода снизу.
 * Если активного обращения нет — сверху выбор категории для первого сообщения.
 */
public class ReportScreen extends PjmBaseScreen {

    private static final int GUI_WIDTH = 340;
    private static final int GUI_HEIGHT = 260;
    private static final int HEADER_H = 26;
    private static final int INPUT_H = 20;
    private static final int PAD = 10;

    private ReportThread thread;
    private ReportCategory category = ReportCategory.CHEATER;
    private EditBox input;
    private int scroll;                 // смещение прокрутки вниз, в пикселях
    private int contentHeight;          // высота всей переписки (для клампа)
    private boolean stickToBottom = true;
    private final List<Clickable> buttons = new ArrayList<>();

    public ReportScreen(ReportThread thread) {
        super(Component.translatable("gui.pjmbasemod.report.title"), GUI_WIDTH, GUI_HEIGHT);
        this.thread = thread == null ? ReportThread.NONE : thread;
    }

    public static void open(ReportThread thread) {
        Minecraft.getInstance().setScreen(new ReportScreen(thread));
    }

    public void updateThread(ReportThread next) {
        this.thread = next == null ? ReportThread.NONE : next;
        stickToBottom = true;
    }

    /** Показывать ли выбор категории (нет активного открытого обращения → это новое). */
    private boolean pickingCategory() {
        return !thread.exists() || !thread.open();
    }

    @Override
    protected void init() {
        super.init();
        String prev = input != null ? input.getValue() : "";
        input = new EditBox(this.font, 0, 0, 200, 16,
                Component.translatable("gui.pjmbasemod.report.chat.input"));
        input.setMaxLength(512);
        input.setBordered(false);
        input.setTextColor(PjmGuiUtils.TEXT_PRIMARY);
        input.setValue(prev);
        addWidget(input);
        setInitialFocus(input);
    }

    // ---------------------------------------------------------------- рендер

    @Override
    protected void renderScaled(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        buttons.clear();
        int left = guiLeft();
        int top = guiTop();

        PjmGuiUtils.drawScreenPanel(g, left, top, GUI_WIDTH, GUI_HEIGHT, 0, HEADER_H);
        String title = Component.translatable("gui.pjmbasemod.report.title").getString();
        g.drawString(this.font, "§l" + title, left + 12, top + 9, PjmGuiUtils.TEXT_GOLD, false);
        if (thread.exists()) {
            String sub = "#" + thread.id() + " · " + Component.translatable(thread.category().langKey()).getString()
                    + " · " + Component.translatable(thread.open()
                        ? "gui.pjmbasemod.report.admin.open" : "gui.pjmbasemod.report.admin.closed").getString();
            g.drawString(this.font, "§7" + sub, left + GUI_WIDTH - this.font.width(sub) - 12, top + 10,
                    PjmGuiUtils.TEXT_MUTED, false);
        }

        int contentTop = top + HEADER_H;
        int catRowH = 0;
        if (pickingCategory()) {
            catRowH = renderCategoryRow(g, left + PAD, contentTop + 6, mouseX, mouseY);
        }

        int msgTop = contentTop + 6 + catRowH;
        int msgBottom = top + GUI_HEIGHT - INPUT_H - 12;
        renderMessages(g, left + PAD, msgTop, GUI_WIDTH - PAD * 2, msgBottom, mouseX, mouseY);

        renderInput(g, left + PAD, top + GUI_HEIGHT - INPUT_H - 8, GUI_WIDTH - PAD * 2, mouseX, mouseY);
    }

    private int renderCategoryRow(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        ReportCategory[] cats = ReportCategory.values();
        int gap = 4;
        int bw = (GUI_WIDTH - PAD * 2 - gap * (cats.length - 1)) / cats.length;
        for (int i = 0; i < cats.length; i++) {
            ReportCategory c = cats[i];
            int bx = x + i * (bw + gap);
            boolean sel = c == category;
            boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= y && mouseY < y + 16;
            int bg = sel ? PjmGuiUtils.SCREEN_SELECT : hover ? PjmGuiUtils.SCREEN_ROW_HOVER : PjmGuiUtils.SCREEN_ROW;
            g.fill(bx, y, bx + bw, y + 16, bg);
            PjmGuiUtils.drawBorder(g, bx, y, bw, 16, sel ? PjmGuiUtils.ACCENT : PjmGuiUtils.SCREEN_BORDER);
            String label = PjmGuiUtils.ellipsize(this.font, Component.translatable(c.langKey()).getString(), bw - 6);
            g.drawCenteredString(this.font, label, bx + bw / 2, y + 4, sel ? 0xFFFFFFFF : PjmGuiUtils.TEXT_DIM);
            buttons.add(new Clickable(bx, y, bw, 16, () -> category = c));
        }
        return 16 + 8;
    }

    private void renderMessages(GuiGraphics g, int x, int top, int width, int bottom, int mouseX, int mouseY) {
        int areaH = bottom - top;
        PjmGuiUtils.drawDarkPanel(g, x, top, width, areaH);

        int innerX = x + 6;
        int innerW = width - 12;
        int bubbleMaxW = (int) (innerW * 0.72f);

        // 1. Разложить сообщения в строки, посчитать полную высоту.
        List<Line> lines = new ArrayList<>();
        int cy = 6;
        for (ReportMessage m : thread.messages()) {
            List<FormattedCharSequence> wrapped = this.font.split(Component.literal(m.text()), bubbleMaxW - 10);
            int textW = 0;
            for (FormattedCharSequence l : wrapped) textW = Math.max(textW, widthOf(l));
            String time = PjmChatTime.clock(m.time());
            textW = Math.max(textW, this.font.width(time));
            int bubbleW = Math.min(bubbleMaxW, textW + 10);
            int bubbleH = wrapped.size() * 10 + 8 + 8;   // +строка времени
            int bx = m.fromAdmin() ? innerX : innerX + innerW - bubbleW;
            lines.add(new Line(m, bx, cy, bubbleW, bubbleH, wrapped));
            cy += bubbleH + 6;
        }
        contentHeight = cy;

        // 2. Кламп прокрутки; прилипание к низу при обновлении.
        int maxScroll = Math.max(0, contentHeight - areaH);
        if (stickToBottom) {
            scroll = maxScroll;
            stickToBottom = false;
        }
        scroll = Mth.clamp(scroll, 0, maxScroll);

        if (thread.messages().isEmpty()) {
            g.drawCenteredString(this.font, "§7" + Component.translatable("gui.pjmbasemod.report.chat.empty").getString(),
                    x + width / 2, top + areaH / 2 - 4, PjmGuiUtils.TEXT_MUTED);
        }

        // 3. Отрисовать с культингом по вертикали (без scissor).
        int originY = top - scroll;
        for (Line ln : lines) {
            int by = originY + ln.y;
            if (by + ln.h < top || by > bottom) continue;         // целиком вне области
            boolean admin = ln.msg.fromAdmin();
            int bg = admin ? PjmGuiUtils.SCREEN_ROW : PjmGuiUtils.ACCENT_DIM;
            // фон-пузырь, обрезанный по границам области
            int clipTop = Math.max(by, top);
            int clipBot = Math.min(by + ln.h, bottom);
            if (clipBot > clipTop) {
                g.fill(ln.x, clipTop, ln.x + ln.w, clipBot, bg);
                PjmGuiUtils.drawBorder(g, ln.x, clipTop, ln.w, clipBot - clipTop, PjmGuiUtils.SCREEN_BORDER);
            }
            int ty = by + 4;
            for (FormattedCharSequence l : ln.text) {
                if (ty >= top - 8 && ty <= bottom) {
                    g.drawString(this.font, l, ln.x + 5, ty, PjmGuiUtils.TEXT_PRIMARY, false);
                }
                ty += 10;
            }
            // Метка времени — правый нижний угол пузыря, приглушённо.
            String time = PjmChatTime.clock(ln.msg.time());
            if (ty >= top - 8 && ty <= bottom) {
                g.drawString(this.font, "§8" + time, ln.x + ln.w - this.font.width(time) - 5, ty, PjmGuiUtils.TEXT_MUTED, false);
            }
        }

        PjmGuiUtils.drawScrollbar(g, x + width - 3, top, areaH,
                Math.max(1, contentHeight), areaH, maxScroll == 0 ? 0 : scroll * areaH / Math.max(1, contentHeight));
    }

    private void renderInput(GuiGraphics g, int x, int y, int width, int mouseX, int mouseY) {
        boolean closed = thread.exists() && !thread.open();
        int sendW = 54;
        int boxW = width - sendW - 6;

        g.fill(x, y, x + boxW, y + INPUT_H, PjmGuiUtils.SCREEN_ROW);
        PjmGuiUtils.drawBorder(g, x, y, boxW, INPUT_H, input.isFocused() ? PjmGuiUtils.ACCENT_DIM : PjmGuiUtils.SCREEN_BORDER);
        input.setX(x + 5);
        input.setY(y + 6);
        input.setWidth(boxW - 10);
        input.render(g, mouseX, mouseY, 0);

        int sx = x + boxW + 6;
        boolean enabled = !input.getValue().isBlank();
        boolean hover = enabled && mouseX >= sx && mouseX < sx + sendW && mouseY >= y && mouseY < y + INPUT_H;
        int bg = !enabled ? PjmGuiUtils.BTN_DISABLED : hover ? PjmGuiUtils.SCREEN_ROW_HOVER : PjmGuiUtils.SCREEN_SELECT;
        g.fill(sx, y, sx + sendW, y + INPUT_H, bg);
        PjmGuiUtils.drawBorder(g, sx, y, sendW, INPUT_H, PjmGuiUtils.SCREEN_BORDER);
        g.drawCenteredString(this.font, "§a" + Component.translatable("gui.pjmbasemod.report.send").getString(),
                sx + sendW / 2, y + 6, enabled ? 0xFFFFFFFF : 0xFF555555);
        if (enabled) buttons.add(new Clickable(sx, y, sendW, INPUT_H, this::send));

        if (closed) {
            g.drawString(this.font, "§8" + Component.translatable("gui.pjmbasemod.report.chat.closed").getString(),
                    x, y - 11, PjmGuiUtils.TEXT_MUTED, false);
        }
    }

    private void send() {
        String text = input.getValue().strip();
        if (text.isEmpty()) return;
        ReportCategory cat = pickingCategory() ? category : thread.category();
        PjmNetworking.sendToServer(new SubmitReportPacket(cat, text));
        input.setValue("");
        stickToBottom = true;
    }

    // ---------------------------------------------------------------- ввод

    @Override
    protected boolean mouseClickedScaled(int mouseX, int mouseY, int button) {
        for (Clickable c : buttons) {
            if (c.contains(mouseX, mouseY)) {
                c.action().run();
                PjmUiSounds.playClick();
                return true;
            }
        }
        return super.mouseClickedScaled(mouseX, mouseY, button);
    }

    @Override
    protected boolean mouseScrolledScaled(int mouseX, int mouseY, double deltaX, double deltaY) {
        scroll -= (int) (Math.signum(deltaY) * 16);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter — отправить (когда фокус в поле ввода).
        if (input != null && input.isFocused() && (keyCode == 257 || keyCode == 335)) {
            send();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int widthOf(FormattedCharSequence seq) {
        return this.font.width(seq);
    }

    private record Line(ReportMessage msg, int x, int y, int w, int h, List<FormattedCharSequence> text) {}

    private record Clickable(int x, int y, int w, int h, Runnable action) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
}
