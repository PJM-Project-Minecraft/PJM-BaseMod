package ru.liko.pjmbasemod.client.gui.screen;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.client.gui.PjmUiSounds;

/**
 * Анимированное руководство по серверу, показываемое игроку при входе (по пакету
 * {@code OpenWelcomeGuidePacket}). Секции появляются каскадом (staggered fade + slide-in),
 * стиль — общая янтарно-тактическая палитра {@link PjmGuiUtils}.
 *
 * <p>Открытие откладывается до входа в мир: клиент вызывает {@link #requestOpen()} при получении
 * пакета, а {@code ClientEvents.onClientTick} открывает экран, когда закрыт экран выбора фракции
 * ({@code mc.screen == null}).</p>
 */
public final class WelcomeGuideScreen extends PjmBaseScreen {

    // -- отложенное открытие (не поверх экрана выбора фракции) --
    private static volatile boolean pending = false;

    public static void requestOpen() { pending = true; }

    /** Возвращает true и сбрасывает флаг, если открытие было запрошено. */
    public static boolean consumePending() {
        if (!pending) return false;
        pending = false;
        return true;
    }

    public static void reset() { pending = false; }

    // -- макет --
    private static final int PANEL_W = 420;
    private static final int HEADER_H = 44;
    private static final int PAD = 16;
    private static final int ROW_H = 26;
    private static final int BADGE_W = 28;
    private static final int BADGE_H = 18;
    private static final int ROWS_GAP = 8;
    private static final int FOOTER_H = 12;
    private static final int BTN_W = 140;
    private static final int BTN_H = 22;

    // -- тайминги анимации (мс) --
    private static final float HEADER_DELAY = 120f;
    private static final float STAGGER = 70f;
    private static final float ROW_DUR = 260f;

    /** Секция руководства: значок-бейдж + заголовок + описание (по ключам локализации). */
    private record Section(String badge, String titleKey, String descKey) {}

    private static final Section[] SECTIONS = {
            new Section("1",   "gui.pjmbasemod.welcome.faction.title",  "gui.pjmbasemod.welcome.faction.desc"),
            new Section("H",   "gui.pjmbasemod.welcome.role.title",     "gui.pjmbasemod.welcome.role.desc"),
            new Section("XP",  "gui.pjmbasemod.welcome.rank.title",     "gui.pjmbasemod.welcome.rank.desc"),
            new Section("ПКМ", "gui.pjmbasemod.welcome.garage.title",   "gui.pjmbasemod.welcome.garage.desc"),
            new Section("$",   "gui.pjmbasemod.welcome.warehouse.title","gui.pjmbasemod.welcome.warehouse.desc"),
            new Section("CAP", "gui.pjmbasemod.welcome.capture.title",  "gui.pjmbasemod.welcome.capture.desc"),
            new Section("!",   "gui.pjmbasemod.welcome.base.title",     "gui.pjmbasemod.welcome.base.desc"),
            new Section("Y/Z", "gui.pjmbasemod.welcome.comms.title",    "gui.pjmbasemod.welcome.comms.desc"),
            new Section("EV",  "gui.pjmbasemod.welcome.events.title",   "gui.pjmbasemod.welcome.events.desc"),
    };

    /** Высота панели считается от числа секций — строки никогда не наезжают на футер. */
    private static final int PANEL_H =
            HEADER_H + ROWS_GAP + SECTIONS.length * ROW_H + ROWS_GAP + FOOTER_H + BTN_H + PAD;

    private long openStart = -1L;

    public WelcomeGuideScreen() {
        super(Component.translatable("gui.pjmbasemod.welcome.title"), PANEL_W, PANEL_H);
    }

    // -- геометрия кнопки «В БОЙ» (в виртуальных координатах, детерминирована) --
    private int btnW() { return BTN_W; }
    private int btnH() { return BTN_H; }
    private int btnX() { return guiLeft() + (PANEL_W - btnW()) / 2; }
    private int btnY() { return guiTop() + PANEL_H - PAD - btnH(); }

    private float elapsed() {
        long now = Util.getMillis();
        if (openStart < 0) openStart = now;
        return now - openStart;
    }

    /** easeOutCubic-прогресс появления строки i (0..1). */
    private float rowProgress(int i, float elapsed) {
        float t = (elapsed - HEADER_DELAY - i * STAGGER) / ROW_DUR;
        t = Math.max(0f, Math.min(1f, t));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static int withAlpha(int color, float a) {
        int base = (color >>> 24) & 0xFF;
        int alpha = (int) (base * Math.max(0f, Math.min(1f, a)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    @Override
    protected void renderScaled(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = this.font;
        float elapsed = elapsed();
        int x = guiLeft();
        int y = guiTop();

        // Панель + хедер с акцент-линией.
        PjmGuiUtils.drawScreenPanel(g, x, y, PANEL_W, PANEL_H, 0, HEADER_H);

        float headerA = Math.min(1f, elapsed / 200f);
        String title = Component.translatable("gui.pjmbasemod.welcome.title").getString();
        String subtitle = Component.translatable("gui.pjmbasemod.welcome.subtitle").getString();
        g.pose().pushPose();
        g.pose().translate(x + PAD, y + 9, 0);
        g.pose().scale(1.4f, 1.4f, 1f);
        g.drawString(font, title, 0, 0, withAlpha(PjmGuiUtils.ACCENT, headerA), false);
        g.pose().popPose();
        g.drawString(font, subtitle, x + PAD, y + 27, withAlpha(PjmGuiUtils.TEXT_MUTED, headerA), false);

        // Секции каскадом.
        int rowsTop = y + HEADER_H + ROWS_GAP;
        int textX = x + PAD + BADGE_W + 10;
        int descW = PANEL_W - PAD - BADGE_W - 10 - PAD;
        for (int i = 0; i < SECTIONS.length; i++) {
            Section s = SECTIONS[i];
            float a = rowProgress(i, elapsed);
            if (a <= 0.001f) continue;
            int slide = (int) ((1f - a) * 22f);
            int rowY = rowsTop + i * ROW_H;
            int bx = x + PAD + slide;
            int by = rowY + (ROW_H - BADGE_H) / 2;

            // Бейдж (рамка + текст по центру).
            g.fill(bx, by, bx + BADGE_W, by + BADGE_H, withAlpha(0x66000000, a));
            PjmGuiUtils.drawBorder(g, bx, by, BADGE_W, BADGE_H, withAlpha(PjmGuiUtils.ACCENT_DIM, a));
            int badgeW = font.width(s.badge());
            g.drawString(font, s.badge(), bx + (BADGE_W - badgeW) / 2, by + (BADGE_H - 8) / 2,
                    withAlpha(PjmGuiUtils.ACCENT, a), false);

            String sTitle = Component.translatable(s.titleKey()).getString();
            String sDesc = PjmGuiUtils.ellipsize(font, Component.translatable(s.descKey()).getString(), descW);
            g.drawString(font, sTitle, textX + slide, rowY + 3, withAlpha(PjmGuiUtils.TEXT_PRIMARY, a), false);
            g.drawString(font, sDesc, textX + slide, rowY + 14, withAlpha(PjmGuiUtils.TEXT_DIM, a), false);
        }

        // Футер-подсказка + кнопка «В БОЙ» — после последней секции.
        float footerA = rowProgress(SECTIONS.length, elapsed);
        String footer = Component.translatable("gui.pjmbasemod.welcome.footer").getString();
        g.drawString(font, footer, x + PAD, btnY() - 12, withAlpha(PjmGuiUtils.TEXT_MUTED, footerA), false);

        boolean hover = mouseX >= btnX() && mouseX < btnX() + btnW() && mouseY >= btnY() && mouseY < btnY() + btnH();
        int bg = hover ? PjmGuiUtils.BTN_GREEN_HOVER : PjmGuiUtils.BTN_GREEN;
        g.fill(btnX(), btnY(), btnX() + btnW(), btnY() + btnH(), withAlpha(bg, footerA));
        PjmGuiUtils.drawBorder(g, btnX(), btnY(), btnW(), btnH(), withAlpha(PjmGuiUtils.ACCENT_DIM, footerA));
        String label = Component.translatable("gui.pjmbasemod.welcome.close").getString();
        g.drawCenteredString(font, label, btnX() + btnW() / 2, btnY() + (btnH() - 8) / 2,
                withAlpha(PjmGuiUtils.TEXT_PRIMARY, footerA));
    }

    @Override
    protected boolean mouseClickedScaled(int mouseX, int mouseY, int button) {
        if (button == 0 && mouseX >= btnX() && mouseX < btnX() + btnW()
                && mouseY >= btnY() && mouseY < btnY() + btnH()) {
            close();
            return true;
        }
        return super.mouseClickedScaled(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void close() {
        if (this.minecraft != null) {
            PjmUiSounds.playPress(this.minecraft.getSoundManager());
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
