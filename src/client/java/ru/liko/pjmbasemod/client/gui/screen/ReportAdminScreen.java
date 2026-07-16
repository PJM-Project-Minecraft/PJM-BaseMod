package ru.liko.pjmbasemod.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import ru.liko.pjmbasemod.client.gui.PjmChatTime;
import ru.liko.pjmbasemod.common.report.ReportMessage;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.client.gui.PjmUiSounds;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.ReportActionPacket;
import ru.liko.pjmbasemod.common.report.ReportSnapshot;
import ru.liko.pjmbasemod.common.report.ReportSnapshot.Entry;

import java.util.ArrayList;
import java.util.List;

/** Админский экран жалоб: список обращений + детали и кнопки (телепорт / ответить / закрыть). */
public class ReportAdminScreen extends PjmBaseScreen {

    private static final int GUI_WIDTH = 480;
    private static final int GUI_HEIGHT = 300;
    private static final int HEADER_H = 26;
    private static final int LIST_W = 200;
    private static final int ROW_H = 24;

    private ReportSnapshot snapshot;
    private int selected = -1;
    private int selectedId = -1;
    private int scroll;                 // прокрутка списка обращений
    private int detailScroll;           // прокрутка переписки в детали
    private boolean detailStick = true; // прилипание переписки к низу
    private int detailAreaX, detailAreaY, detailAreaW, detailAreaH, detailContentH;

    private EditBox replyBox;
    private final List<Clickable> buttons = new ArrayList<>();

    public ReportAdminScreen(ReportSnapshot snapshot) {
        super(Component.translatable("gui.pjmbasemod.report.admin.title"), GUI_WIDTH, GUI_HEIGHT);
        this.snapshot = snapshot == null ? new ReportSnapshot(List.of()) : snapshot;
    }

    public static void open(ReportSnapshot snapshot) {
        Minecraft.getInstance().setScreen(new ReportAdminScreen(snapshot));
    }

    public void updateSnapshot(ReportSnapshot next) {
        // Прилипнуть к низу при новом сообщении только если админ уже был внизу (не мешаем читать историю).
        if (detailScroll >= Math.max(0, detailContentH - detailAreaH)) detailStick = true;
        this.snapshot = next == null ? new ReportSnapshot(List.of()) : next;
        selected = -1;
        if (selectedId >= 0) {
            for (int i = 0; i < snapshot.reports().size(); i++) {
                if (snapshot.reports().get(i).id() == selectedId) {
                    selected = i;
                    break;
                }
            }
        }
        clampScroll();
    }

    @Override
    protected void init() {
        super.init();
        String prev = replyBox != null ? replyBox.getValue() : "";
        replyBox = new EditBox(this.font, 0, 0, 200, 16,
                Component.translatable("gui.pjmbasemod.report.admin.reply.placeholder"));
        replyBox.setMaxLength(256);
        replyBox.setBordered(false);
        replyBox.setTextColor(PjmGuiUtils.TEXT_PRIMARY);
        replyBox.setValue(prev);
        addWidget(replyBox);
    }

    @Override
    protected void renderScaled(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        buttons.clear();
        int left = guiLeft();
        int top = guiTop();

        PjmGuiUtils.drawScreenPanel(g, left, top, GUI_WIDTH, GUI_HEIGHT, 0, HEADER_H);
        g.drawString(this.font, "§l" + Component.translatable("gui.pjmbasemod.report.admin.title").getString(),
                left + 12, top + 9, PjmGuiUtils.TEXT_GOLD, false);

        renderList(g, left + 8, top + HEADER_H + 6, mouseX, mouseY);
        renderDetail(g, left + LIST_W + 20, top + HEADER_H + 6, mouseX, mouseY);
    }

    private void renderList(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int h = GUI_HEIGHT - HEADER_H - 14;
        PjmGuiUtils.drawDarkPanel(g, x, y, LIST_W, h);
        int visible = h / ROW_H;
        List<Entry> reports = snapshot.reports();
        clampScroll();
        for (int i = 0; i < visible && i + scroll < reports.size(); i++) {
            int idx = i + scroll;
            Entry e = reports.get(idx);
            int ry = y + i * ROW_H;
            boolean hover = mouseX >= x && mouseX < x + LIST_W && mouseY >= ry && mouseY < ry + ROW_H;
            int bg = idx == selected ? PjmGuiUtils.SCREEN_SELECT : hover ? PjmGuiUtils.SCREEN_ROW_HOVER : PjmGuiUtils.SCREEN_ROW;
            g.fill(x + 2, ry + 1, x + LIST_W - 2, ry + ROW_H - 1, bg);
            String head = "§7#" + e.id() + " §f" + PjmGuiUtils.ellipsize(this.font, e.reporterName(), LIST_W - 70);
            g.drawString(this.font, head, x + 8, ry + 3, PjmGuiUtils.TEXT_PRIMARY, false);
            String cat = "§8" + Component.translatable(e.category().langKey()).getString();
            g.drawString(this.font, cat, x + 8, ry + 13, PjmGuiUtils.TEXT_DIM, false);
            String status = e.open() ? "§a●" : "§8✓";
            g.drawString(this.font, status, x + LIST_W - 14, ry + 8, PjmGuiUtils.TEXT_DIM, false);
        }
        PjmGuiUtils.drawScrollbar(g, x + LIST_W - 3, y, h, reports.size(), visible, scroll);
    }

    private void renderDetail(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int w = GUI_WIDTH - LIST_W - 28;
        int h = GUI_HEIGHT - HEADER_H - 14;
        PjmGuiUtils.drawDarkPanel(g, x, y, w, h);
        Entry sel = selectedEntry();
        if (sel == null) {
            g.drawCenteredString(this.font, "§7" + Component.translatable("gui.pjmbasemod.report.admin.select").getString(),
                    x + w / 2, y + h / 2 - 4, PjmGuiUtils.TEXT_MUTED);
            return;
        }
        int px = x + 12;
        int py = y + 10;
        g.drawString(this.font, "§l#" + sel.id() + " " + sel.reporterName(), px, py, PjmGuiUtils.TEXT_PRIMARY, false);
        py += 14;
        String catLine = "§7" + Component.translatable("gui.pjmbasemod.report.category").getString() + " §f"
                + Component.translatable(sel.category().langKey()).getString();
        g.drawString(this.font, catLine, px, py, PjmGuiUtils.TEXT_DIM, false);
        py += 12;
        String state = sel.open()
                ? "§a" + Component.translatable("gui.pjmbasemod.report.admin.open").getString()
                : "§8" + Component.translatable("gui.pjmbasemod.report.admin.closed").getString();
        String online = sel.reporterOnline()
                ? "§a" + Component.translatable("gui.pjmbasemod.report.admin.player_online").getString()
                : "§8" + Component.translatable("gui.pjmbasemod.report.admin.player_offline").getString();
        g.drawString(this.font, state + "   " + online, px, py, PjmGuiUtils.TEXT_DIM, false);
        py += 16;

        // Переписка (пузыри, привязка к низу — самые новые видны).
        int replyY = y + h - 74;
        renderThread(g, px, py, w - 24, replyY - 6 - py, sel.messages());

        // Поле ответа.
        g.drawString(this.font, "§7" + Component.translatable("gui.pjmbasemod.report.admin.reply").getString(),
                px, replyY, PjmGuiUtils.TEXT_LABEL, false);
        int boxY = replyY + 10;
        g.fill(px, boxY, px + w - 24, boxY + 16, PjmGuiUtils.SCREEN_ROW);
        PjmGuiUtils.drawBorder(g, px, boxY, w - 24, 16,
                replyBox.isFocused() ? PjmGuiUtils.ACCENT_DIM : PjmGuiUtils.SCREEN_BORDER);
        replyBox.setX(px + 5);
        replyBox.setY(boxY + 4);
        replyBox.setWidth(w - 34);
        replyBox.render(g, mouseX, mouseY, 0);

        // Кнопки.
        int by = boxY + 24;
        int bw = (w - 24 - 12) / 3;
        int id = sel.id();
        addButton(g, px, by, bw, "§b" + Component.translatable("gui.pjmbasemod.report.admin.btn.teleport").getString(),
                mouseX, mouseY, sel.reporterOnline(), () -> send(id, "teleport", ""));
        addButton(g, px + bw + 6, by, bw, "§e" + Component.translatable("gui.pjmbasemod.report.admin.btn.reply").getString(),
                mouseX, mouseY, !replyBox.getValue().isBlank(),
                () -> { send(id, "reply", replyBox.getValue().strip()); replyBox.setValue(""); });
        addButton(g, px + (bw + 6) * 2, by, bw, "§a" + Component.translatable("gui.pjmbasemod.report.admin.btn.close").getString(),
                mouseX, mouseY, sel.open(), () -> send(id, "close", ""));
    }

    /**
     * Переписка пузырями с прокруткой (колесо над областью детали). По умолчанию
     * привязана к низу. Свои ответы (админ) — справа/акцент, игрок — слева/тёмный.
     */
    private void renderThread(GuiGraphics g, int x, int top, int width, int areaH, List<ReportMessage> messages) {
        // Запомнить геометрию для обработчика колеса.
        detailAreaX = x; detailAreaY = top; detailAreaW = width; detailAreaH = areaH;

        if (messages.isEmpty()) {
            detailContentH = 0;
            g.drawCenteredString(this.font, "§8" + Component.translatable("gui.pjmbasemod.report.chat.empty").getString(),
                    x + width / 2, top + areaH / 2 - 4, PjmGuiUtils.TEXT_MUTED);
            return;
        }
        int bubbleMaxW = (int) (width * 0.75f);
        List<AdminLine> lines = new ArrayList<>();
        int cy = 0;
        for (ReportMessage m : messages) {
            List<FormattedCharSequence> wrapped = this.font.split(Component.literal(m.text()), bubbleMaxW - 10);
            int textW = 0;
            for (FormattedCharSequence l : wrapped) textW = Math.max(textW, this.font.width(l));
            String time = PjmChatTime.clock(m.time());
            textW = Math.max(textW, this.font.width(time));
            int bw = Math.min(bubbleMaxW, textW + 10);
            int bh = wrapped.size() * 10 + 8 + 8;   // +строка времени
            int bx = m.fromAdmin() ? x + width - bw : x;
            lines.add(new AdminLine(m, bx, cy, bw, bh, wrapped));
            cy += bh + 5;
        }
        detailContentH = cy;

        int maxScroll = Math.max(0, cy - areaH);
        if (detailStick) { detailScroll = maxScroll; detailStick = false; }
        detailScroll = Mth.clamp(detailScroll, 0, maxScroll);

        int originY = top - detailScroll;
        int bottom = top + areaH;
        for (AdminLine ln : lines) {
            int by = originY + ln.y;
            if (by + ln.h < top || by > bottom) continue;
            int bg = ln.msg.fromAdmin() ? PjmGuiUtils.ACCENT_DIM : PjmGuiUtils.SCREEN_ROW;
            int clipTop = Math.max(by, top);
            int clipBot = Math.min(by + ln.h, bottom);
            if (clipBot > clipTop) {
                g.fill(ln.x, clipTop, ln.x + ln.w, clipBot, bg);
                PjmGuiUtils.drawBorder(g, ln.x, clipTop, ln.w, clipBot - clipTop, PjmGuiUtils.SCREEN_BORDER);
            }
            int ty = by + 4;
            for (FormattedCharSequence l : ln.text) {
                if (ty >= top - 8 && ty <= bottom) g.drawString(this.font, l, ln.x + 5, ty, PjmGuiUtils.TEXT_PRIMARY, false);
                ty += 10;
            }
            String time = PjmChatTime.clock(ln.msg.time());
            if (ty >= top - 8 && ty <= bottom) {
                g.drawString(this.font, "§8" + time, ln.x + ln.w - this.font.width(time) - 5, ty, PjmGuiUtils.TEXT_MUTED, false);
            }
        }
        if (maxScroll > 0) {
            PjmGuiUtils.drawScrollbar(g, x + width - 2, top, areaH,
                    Math.max(1, cy), areaH, detailScroll * areaH / Math.max(1, cy));
        }
    }

    private record AdminLine(ReportMessage msg, int x, int y, int w, int h, List<FormattedCharSequence> text) {}

    private void addButton(GuiGraphics g, int x, int y, int w, String label, int mouseX, int mouseY,
                           boolean enabled, Runnable action) {
        boolean hover = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18;
        int bg = !enabled ? PjmGuiUtils.BTN_DISABLED : hover ? PjmGuiUtils.SCREEN_ROW_HOVER : PjmGuiUtils.SCREEN_ROW;
        g.fill(x, y, x + w, y + 18, bg);
        PjmGuiUtils.drawBorder(g, x, y, w, 18, PjmGuiUtils.SCREEN_BORDER);
        g.drawCenteredString(this.font, label, x + w / 2, y + 5, enabled ? 0xFFFFFFFF : 0xFF555555);
        if (enabled) buttons.add(new Clickable(x, y, w, 18, action));
    }

    private void send(int id, String action, String text) {
        PjmNetworking.sendToServer(new ReportActionPacket(id, action, text));
    }

    @Override
    protected boolean mouseClickedScaled(int mouseX, int mouseY, int button) {
        int left = guiLeft();
        int top = guiTop();
        int lx = left + 8;
        int ly = top + HEADER_H + 6;
        int lh = GUI_HEIGHT - HEADER_H - 14;
        int visible = lh / ROW_H;
        if (mouseX >= lx && mouseX < lx + LIST_W && mouseY >= ly && mouseY < ly + visible * ROW_H) {
            int idx = (mouseY - ly) / ROW_H + scroll;
            if (idx >= 0 && idx < snapshot.reports().size()) {
                selected = idx;
                selectedId = snapshot.reports().get(idx).id();
                detailScroll = 0;
                detailStick = true;   // показать свежие сообщения выбранного обращения
                PjmUiSounds.playClick();
                return true;
            }
        }
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
        boolean overDetail = mouseX >= detailAreaX && mouseX < detailAreaX + detailAreaW
                && mouseY >= detailAreaY && mouseY < detailAreaY + detailAreaH;
        if (overDetail && detailContentH > detailAreaH) {
            detailScroll = Mth.clamp(detailScroll - (int) (Math.signum(deltaY) * 16), 0,
                    Math.max(0, detailContentH - detailAreaH));
        } else {
            scroll -= (int) Math.signum(deltaY);
            clampScroll();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (replyBox != null && replyBox.isFocused() && keyCode == 256) {
            replyBox.setFocused(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Entry selectedEntry() {
        return selected >= 0 && selected < snapshot.reports().size() ? snapshot.reports().get(selected) : null;
    }

    private void clampScroll() {
        int h = GUI_HEIGHT - HEADER_H - 14;
        int visible = h / ROW_H;
        int max = Math.max(0, snapshot.reports().size() - visible);
        scroll = Mth.clamp(scroll, 0, max);
    }

    private record Clickable(int x, int y, int w, int h, Runnable action) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
}
