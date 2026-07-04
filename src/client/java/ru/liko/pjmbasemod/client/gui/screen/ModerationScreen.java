package ru.liko.pjmbasemod.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.client.gui.PjmUiSounds;
import ru.liko.pjmbasemod.common.moderation.DurationParser;
import ru.liko.pjmbasemod.common.moderation.ModerationSnapshot;
import ru.liko.pjmbasemod.common.moderation.ModerationSnapshot.PlayerModEntry;
import ru.liko.pjmbasemod.common.moderation.PunishmentType;
import ru.liko.pjmbasemod.common.network.PjmNetworking;
import ru.liko.pjmbasemod.common.network.packet.ModerationActionPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Экран модерации: список игроков + панель действий (варн/бан/кик/мут), поля причины и длительности. */
public class ModerationScreen extends PjmBaseScreen {

    private static final int GUI_WIDTH = 480;
    private static final int GUI_HEIGHT = 300;
    private static final int HEADER_H = 26;
    private static final int LIST_W = 200;
    private static final int ROW_H = 22;

    private ModerationSnapshot snapshot;
    private int selected = -1;
    private int scroll;
    private UUID selectedId;

    private EditBox reasonBox;
    private EditBox durationBox;

    private final List<Clickable> buttons = new ArrayList<>();

    public ModerationScreen(ModerationSnapshot snapshot) {
        super(Component.translatable("gui.pjmbasemod.moderation.title"), GUI_WIDTH, GUI_HEIGHT);
        this.snapshot = snapshot == null ? new ModerationSnapshot(List.of()) : snapshot;
    }

    public static void open(ModerationSnapshot snapshot) {
        Minecraft.getInstance().setScreen(new ModerationScreen(snapshot));
    }

    public void updateSnapshot(ModerationSnapshot next) {
        this.snapshot = next == null ? new ModerationSnapshot(List.of()) : next;
        // Сохранить выделение по UUID.
        selected = -1;
        if (selectedId != null) {
            for (int i = 0; i < snapshot.players().size(); i++) {
                if (selectedId.equals(snapshot.players().get(i).id())) {
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
        String prevReason = reasonBox != null ? reasonBox.getValue() : "";
        String prevDuration = durationBox != null ? durationBox.getValue() : "";
        reasonBox = new EditBox(this.font, 0, 0, 200, 16,
                Component.translatable("gui.pjmbasemod.moderation.reason.placeholder"));
        reasonBox.setMaxLength(128);
        reasonBox.setBordered(false);
        reasonBox.setTextColor(PjmGuiUtils.TEXT_PRIMARY);
        reasonBox.setValue(prevReason);
        durationBox = new EditBox(this.font, 0, 0, 64, 16, Component.literal("30m"));
        durationBox.setMaxLength(32);
        durationBox.setBordered(false);
        durationBox.setTextColor(PjmGuiUtils.TEXT_PRIMARY);
        durationBox.setValue(prevDuration.isBlank() ? "30m" : prevDuration);
        addWidget(reasonBox);
        addWidget(durationBox);
    }

    // ---------------------------------------------------------------- рендер

    @Override
    protected void renderScaled(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        buttons.clear();
        int left = guiLeft();
        int top = guiTop();

        PjmGuiUtils.drawScreenPanel(g, left, top, GUI_WIDTH, GUI_HEIGHT, 0, HEADER_H);
        g.drawString(this.font, "§l" + Component.translatable("gui.pjmbasemod.moderation.title").getString(),
                left + 12, top + 9, PjmGuiUtils.TEXT_GOLD, false);
        String hint = Component.translatable("gui.pjmbasemod.moderation.hint").getString();
        g.drawString(this.font, "§7" + hint, left + GUI_WIDTH - this.font.width(hint) - 12, top + 9, PjmGuiUtils.TEXT_MUTED, false);

        renderList(g, left + 8, top + HEADER_H + 6, mouseX, mouseY);
        renderDetail(g, left + LIST_W + 20, top + HEADER_H + 6, mouseX, mouseY);
    }

    private void renderList(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int h = GUI_HEIGHT - HEADER_H - 14;
        PjmGuiUtils.drawDarkPanel(g, x, y, LIST_W, h);
        int visible = h / ROW_H;
        List<PlayerModEntry> players = snapshot.players();
        clampScroll();
        for (int i = 0; i < visible && i + scroll < players.size(); i++) {
            int idx = i + scroll;
            PlayerModEntry e = players.get(idx);
            int ry = y + i * ROW_H;
            boolean hover = mouseX >= x && mouseX < x + LIST_W && mouseY >= ry && mouseY < ry + ROW_H;
            int bg = idx == selected ? PjmGuiUtils.SCREEN_SELECT : hover ? PjmGuiUtils.SCREEN_ROW_HOVER : PjmGuiUtils.SCREEN_ROW;
            g.fill(x + 2, ry + 1, x + LIST_W - 2, ry + ROW_H - 1, bg);
            int nameColor = e.online() ? PjmGuiUtils.TEXT_PRIMARY : PjmGuiUtils.TEXT_MUTED;
            String name = PjmGuiUtils.ellipsize(this.font, e.name(), LIST_W - 60);
            g.drawString(this.font, name, x + 8, ry + 7, nameColor, false);
            // бейджи статуса
            String badges = (e.banned() ? "§cB" : "") + (e.voiceMuted() ? " §6V" : "")
                    + (e.textMuted() ? " §eT" : "") + (e.warnCount() > 0 ? " §f" + e.warnCount() + "w" : "");
            g.drawString(this.font, badges, x + LIST_W - this.font.width(badges.replaceAll("§.", "")) - 10, ry + 7, PjmGuiUtils.TEXT_DIM, false);
        }
        PjmGuiUtils.drawScrollbar(g, x + LIST_W - 3, y, h, players.size(), visible, scroll);
    }

    private void renderDetail(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int w = GUI_WIDTH - LIST_W - 28;
        int h = GUI_HEIGHT - HEADER_H - 14;
        PjmGuiUtils.drawDarkPanel(g, x, y, w, h);
        PlayerModEntry sel = selectedEntry();
        if (sel == null) {
            g.drawCenteredString(this.font, "§7" + Component.translatable("gui.pjmbasemod.moderation.select_player").getString(),
                    x + w / 2, y + h / 2 - 4, PjmGuiUtils.TEXT_MUTED);
            return;
        }
        String yes = Component.translatable("gui.pjmbasemod.moderation.yes").getString();
        String no = Component.translatable("gui.pjmbasemod.moderation.no").getString();
        int px = x + 12;
        int py = y + 10;
        g.drawString(this.font, "§l" + sel.name(), px, py, PjmGuiUtils.TEXT_PRIMARY, false);
        py += 14;
        g.drawString(this.font, "§7" + Component.translatable("gui.pjmbasemod.moderation.status").getString() + " "
                + (sel.online() ? "§a" + Component.translatable("gui.pjmbasemod.moderation.status.online").getString()
                                : "§8" + Component.translatable("gui.pjmbasemod.moderation.status.offline").getString()),
                px, py, PjmGuiUtils.TEXT_DIM, false);
        py += 12;
        String banTail = sel.banned()
                ? "§c" + yes + (sel.banExpiresMs() == DurationParser.PERMANENT
                        ? " " + Component.translatable("gui.pjmbasemod.moderation.ban.permanent").getString()
                        : " " + Component.translatable("gui.pjmbasemod.moderation.ban.expires",
                                DurationParser.format(sel.banExpiresMs() - System.currentTimeMillis())).getString())
                : "§a" + no;
        g.drawString(this.font, "§7" + Component.translatable("gui.pjmbasemod.moderation.ban.label").getString() + " " + banTail,
                px, py, PjmGuiUtils.TEXT_DIM, false);
        py += 12;
        g.drawString(this.font, "§7" + Component.translatable("gui.pjmbasemod.moderation.voice_mute").getString() + " "
                        + (sel.voiceMuted() ? "§6" + yes : "§a" + no)
                + "   §7" + Component.translatable("gui.pjmbasemod.moderation.text_mute").getString() + " "
                        + (sel.textMuted() ? "§e" + yes : "§a" + no),
                px, py, PjmGuiUtils.TEXT_DIM, false);
        py += 12;
        g.drawString(this.font, "§7" + Component.translatable("gui.pjmbasemod.moderation.warns").getString() + " §f" + sel.warnCount(),
                px, py, PjmGuiUtils.TEXT_DIM, false);
        py += 18;

        // Поля ввода: причина и длительность.
        g.drawString(this.font, "§7" + Component.translatable("gui.pjmbasemod.moderation.reason").getString(), px, py, PjmGuiUtils.TEXT_LABEL, false);
        int reasonY = py + 10;
        drawBoxFrame(g, px, reasonY, w - 24, 16, reasonBox);
        reasonBox.setX(px + 5);
        reasonBox.setY(reasonY + 4);
        reasonBox.setWidth(w - 34);
        reasonBox.render(g, mouseX, mouseY, 0);

        int durLabelY = reasonY + 24;
        g.drawString(this.font, "§7" + Component.translatable("gui.pjmbasemod.moderation.duration").getString(), px, durLabelY, PjmGuiUtils.TEXT_LABEL, false);
        int durY = durLabelY + 10;
        drawBoxFrame(g, px, durY, 80, 16, durationBox);
        durationBox.setX(px + 5);
        durationBox.setY(durY + 4);
        durationBox.setWidth(70);
        durationBox.render(g, mouseX, mouseY, 0);
        g.drawString(this.font, "§8" + Component.translatable("gui.pjmbasemod.moderation.duration.hint").getString(),
                px + 92, durY + 4, PjmGuiUtils.TEXT_MUTED, false);

        // Кнопки действий.
        int by = durY + 26;
        int bw = (w - 24 - 6) / 2;
        UUID id = sel.id();
        addButton(g, px, by, bw, "§e" + Component.translatable("gui.pjmbasemod.moderation.btn.warn").getString(),
                mouseX, mouseY, true,
                () -> send(PunishmentType.WARN, "apply", id, 0));
        addButton(g, px + bw + 6, by, bw, "§c" + Component.translatable("gui.pjmbasemod.moderation.btn.kick").getString(),
                mouseX, mouseY, sel.online(),
                () -> send(PunishmentType.KICK, "apply", id, 0));
        by += 22;
        addButton(g, px, by, bw,
                sel.banned() ? "§a" + Component.translatable("gui.pjmbasemod.moderation.btn.unban").getString()
                             : "§c" + Component.translatable("gui.pjmbasemod.moderation.btn.ban").getString(),
                mouseX, mouseY, true,
                () -> send(PunishmentType.BAN, sel.banned() ? "revoke" : "apply", id, parseDuration()));
        addButton(g, px + bw + 6, by, bw,
                sel.voiceMuted() ? "§a" + Component.translatable("gui.pjmbasemod.moderation.btn.unmute_voice").getString()
                                 : "§6" + Component.translatable("gui.pjmbasemod.moderation.btn.voice_mute").getString(),
                mouseX, mouseY, true,
                () -> send(PunishmentType.MUTE_VOICE, sel.voiceMuted() ? "revoke" : "apply", id, parseDuration()));
        by += 22;
        addButton(g, px, by, bw,
                sel.textMuted() ? "§a" + Component.translatable("gui.pjmbasemod.moderation.btn.unmute_text").getString()
                                : "§e" + Component.translatable("gui.pjmbasemod.moderation.btn.text_mute").getString(),
                mouseX, mouseY, true,
                () -> send(PunishmentType.MUTE_TEXT, sel.textMuted() ? "revoke" : "apply", id, parseDuration()));
    }

    private void drawBoxFrame(GuiGraphics g, int x, int y, int w, int h, EditBox box) {
        boolean focused = box != null && box.isFocused();
        g.fill(x, y, x + w, y + h, focused ? 0xFF2A3550 : 0xFF222229);
        PjmGuiUtils.drawBorder(g, x, y, w, h, focused ? 0xFF4A6A9E : PjmGuiUtils.SCREEN_BORDER);
    }

    private void addButton(GuiGraphics g, int x, int y, int w, String label, int mouseX, int mouseY,
                           boolean enabled, Runnable action) {
        boolean hover = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18;
        int bg = !enabled ? 0xFF1A1A1F : hover ? 0xFF33333F : 0xFF26262E;
        g.fill(x, y, x + w, y + 18, bg);
        PjmGuiUtils.drawBorder(g, x, y, w, 18, PjmGuiUtils.SCREEN_BORDER);
        int color = enabled ? 0xFFFFFFFF : 0xFF555555;
        g.drawCenteredString(this.font, label, x + w / 2, y + 5, color);
        if (enabled) buttons.add(new Clickable(x, y, w, 18, action));
    }

    // ---------------------------------------------------------------- ввод

    @Override
    protected boolean mouseClickedScaled(int mouseX, int mouseY, int button) {
        // Список игроков.
        int left = guiLeft();
        int top = guiTop();
        int lx = left + 8;
        int ly = top + HEADER_H + 6;
        int lh = GUI_HEIGHT - HEADER_H - 14;
        int visible = lh / ROW_H;
        if (mouseX >= lx && mouseX < lx + LIST_W && mouseY >= ly && mouseY < ly + visible * ROW_H) {
            int idx = (mouseY - ly) / ROW_H + scroll;
            if (idx >= 0 && idx < snapshot.players().size()) {
                selected = idx;
                selectedId = snapshot.players().get(idx).id();
                PjmUiSounds.playClick();
                return true;
            }
        }
        // Кнопки действий.
        for (Clickable c : buttons) {
            if (c.contains(mouseX, mouseY)) {
                c.action().run();
                PjmUiSounds.playClick();
                return true;
            }
        }
        // EditBox-фокус (делегируем в базовый scaled-обработчик, который дойдёт до виджетов —
        // НЕ вызываем super.mouseClicked, иначе PjmBaseScreen.mouseClicked снова войдёт сюда → рекурсия).
        return super.mouseClickedScaled(mouseX, mouseY, button);
    }

    @Override
    protected boolean mouseScrolledScaled(int mouseX, int mouseY, double deltaX, double deltaY) {
        scroll -= (int) Math.signum(deltaY);
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((reasonBox != null && reasonBox.isFocused()) || (durationBox != null && durationBox.isFocused())) {
            if (keyCode == 256) { // ESC — снять фокус
                reasonBox.setFocused(false);
                durationBox.setFocused(false);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- логика

    private void send(PunishmentType type, String action, UUID target, long durationMs) {
        String reason = reasonBox == null || reasonBox.getValue().isBlank() ? "Без причины" : reasonBox.getValue().trim();
        PjmNetworking.sendToServer(new ModerationActionPacket(type, action, target, durationMs, reason));
    }

    private long parseDuration() {
        String raw = durationBox == null ? "" : durationBox.getValue().trim();
        if (raw.isBlank()) return DurationParser.PERMANENT;
        long ms = DurationParser.parseToMillis(raw);
        return ms == DurationParser.INVALID ? DurationParser.PERMANENT : ms;
    }

    private PlayerModEntry selectedEntry() {
        return selected >= 0 && selected < snapshot.players().size() ? snapshot.players().get(selected) : null;
    }

    private void clampScroll() {
        int h = GUI_HEIGHT - HEADER_H - 14;
        int visible = h / ROW_H;
        int max = Math.max(0, snapshot.players().size() - visible);
        scroll = Mth.clamp(scroll, 0, max);
    }

    private record Clickable(int x, int y, int w, int h, Runnable action) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    // silence unused warning on generic consumer helper if referenced later
    @SuppressWarnings("unused")
    private void forEachPlayer(Consumer<PlayerModEntry> c) {
        snapshot.players().forEach(c);
    }
}
