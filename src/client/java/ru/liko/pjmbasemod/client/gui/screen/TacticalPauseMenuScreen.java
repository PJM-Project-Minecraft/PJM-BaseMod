package ru.liko.pjmbasemod.client.gui.screen;

import com.mojang.realmsclient.RealmsMainScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.client.gui.PjmUiSounds;

/**
 * Кастомное меню паузы (ESC) в стиле BF5 «Game Menu»: слева — крупный заголовок,
 * строка «карта | режим» и вертикальный список пунктов плоским текстом с
 * подсветкой при наведении (без «коробок»-кнопок и линий). Фон — размытый живой
 * мир (ванильный {@code renderBackground} → {@code processBlurEffect}) с тёмным
 * скримом слева для читаемости. Открытие/закрытие — с fade-анимацией.
 *
 * <p>Перехватывается в {@code ClientEvents.onScreenOpening} при открытии
 * ванильного {@code PauseScreen(showPauseMenu=true)}.</p>
 */
public class TacticalPauseMenuScreen extends Screen {

    private static final int MENU_X = 48;
    private static final int ITEM_HEIGHT = 26;
    private static final int ITEM_WIDTH = 240;
    private static final int TITLE_Y = 46;
    private static final float ANIM_MS = 180f;

    private static final int COLOR_ACCENT = PjmGuiUtils.ACCENT;

    private final List<MenuItem> items = new ArrayList<>();

    private long openStart = -1L;
    private boolean closing;
    private long closeStart;

    public TacticalPauseMenuScreen() {
        super(Component.translatable("menu.pjm.pause.title"));
    }

    private float getScale() {
        return this.height / 540f;
    }

    private int getVWidth() {
        return (int) (this.width / getScale());
    }

    private int getVHeight() {
        return (int) (this.height / getScale());
    }

    @Override
    protected void init() {
        super.init();
        this.items.clear();

        addItem(Component.translatable("menu.pjm.pause.back"), b -> this.onClose(), MenuItem.Kind.RESUME);
        addItem(Component.translatable("menu.pjm.pause.advancements"),
                b -> this.minecraft.setScreen(
                        new AdvancementsScreen(this.minecraft.player.connection.getAdvancements(), this)),
                MenuItem.Kind.NORMAL);
        addItem(Component.translatable("menu.pjm.pause.stats"),
                b -> this.minecraft.setScreen(new StatsScreen(this, this.minecraft.player.getStats())),
                MenuItem.Kind.NORMAL);
        addItem(Component.translatable("menu.pjm.character"), b -> {}, MenuItem.Kind.NORMAL);
        addItem(Component.translatable("menu.pjm.options"),
                b -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options)),
                MenuItem.Kind.NORMAL);
        addItem(Component.translatable("menu.pjm.pause.disconnect"), b -> this.onDisconnect(), MenuItem.Kind.EXIT);

        updateItemPositions();
    }

    private void addItem(Component label, Button.OnPress onPress, MenuItem.Kind kind) {
        MenuItem item = new MenuItem(MENU_X, 0, ITEM_WIDTH, ITEM_HEIGHT, label, onPress, kind);
        addRenderableWidget(item);
        items.add(item);
    }

    private void updateItemPositions() {
        int startY = TITLE_Y + 70;
        int currentY = startY;
        for (MenuItem item : items) {
            item.setY(currentY);
            currentY += ITEM_HEIGHT;
        }
    }

    private void onDisconnect() {
        boolean isLocalServer = this.minecraft.isLocalServer();
        ServerData serverdata = this.minecraft.getCurrentServer();
        this.minecraft.level.disconnect();
        if (isLocalServer) {
            this.minecraft.disconnect(new net.minecraft.client.gui.screens.GenericMessageScreen(
                    Component.translatable("menu.savingLevel")));
        } else {
            this.minecraft.disconnect();
        }

        TitleScreen titlescreen = new TitleScreen();
        if (isLocalServer) {
            this.minecraft.setScreen(titlescreen);
        } else if (serverdata != null && serverdata.isRealm()) {
            this.minecraft.setScreen(new RealmsMainScreen(titlescreen));
        } else {
            this.minecraft.setScreen(new JoinMultiplayerScreen(titlescreen));
        }
    }

    /** Строка «карта | режим»: имя сервера/мира + текущее измерение. */
    private String mapModeLine() {
        Minecraft mc = Minecraft.getInstance();
        String left;
        ServerData server = mc.getCurrentServer();
        if (server != null && server.name != null && !server.name.isBlank()) {
            left = server.name;
        } else if (mc.getSingleplayerServer() != null) {
            left = mc.getSingleplayerServer().getWorldData().getLevelName();
        } else {
            left = "PROJECT MINECRAFT";
        }
        String right = mc.level != null ? mc.level.dimension().location().getPath() : "";
        String line = right.isBlank() ? left : left + "  |  " + right;
        return line.toUpperCase(Locale.ROOT);
    }

    // ─────────────────────────── анимация ───────────────────────────

    private float animAlpha() {
        long now = Util.getMillis();
        if (openStart < 0) openStart = now;
        float in = Math.min(1f, (now - openStart) / ANIM_MS);
        if (closing) {
            float out = Math.min(1f, (now - closeStart) / ANIM_MS);
            return Math.max(0f, 1f - out);
        }
        return in;
    }

    private static int withAlpha(int color, float a) {
        int base = (color >>> 24) & 0xFF;
        int alpha = (int) (base * Math.max(0f, Math.min(1f, a)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    // ─────────────────────────── рендер ───────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Живой мир + blur + тёмный in-world оверлей (полноэкранно, без масштаба).
        this.renderBackground(g, mouseX, mouseY, partialTick);

        float alpha = animAlpha();
        if (closing && alpha <= 0.001f) {
            this.minecraft.setScreen(null);
            return;
        }

        // Тёмный градиент-скрим — в экранных координатах (без масштаба), попиксельно,
        // чтобы переход был гладким без полос-бандинга.
        drawLeftScrim(g, alpha);

        float scale = getScale();
        int vHeight = getVHeight();
        int vMouseX = (int) (mouseX / scale);
        int vMouseY = (int) (mouseY / scale);

        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0f);
        // Лёгкий slide-in слева.
        g.pose().translate((1f - alpha) * -18f, 0f, 0f);

        updateItemPositions();

        drawHeader(g, alpha);

        for (MenuItem item : items) {
            item.render(g, vMouseX, vMouseY, partialTick);
        }

        // Подсказка ESC + версия внизу слева.
        g.drawString(this.font, "◄ " + Component.translatable("menu.pjm.pause.back").getString().toUpperCase(Locale.ROOT)
                        + " [ESC]", MENU_X, vHeight - 40, withAlpha(0xFFBBBBBB, alpha), true);
        String version = "ver. " + Pjmbasemod.MODID.toUpperCase(Locale.ROOT) + " 0.1";
        g.drawString(this.font, version, MENU_X, vHeight - 20, withAlpha(0x88FFFFFF, alpha), true);

        g.pose().popPose();
    }

    /** Тёмный горизонтальный градиент слева (BF5-скрим), попиксельно — без полос. */
    private void drawLeftScrim(GuiGraphics g, float alpha) {
        int scrimWidth = (int) (this.width * 0.5f);
        int maxA = 0xC8;
        for (int x = 0; x < scrimWidth; x++) {
            float t = 1f - x / (float) scrimWidth;
            int a = (int) (maxA * t * t * alpha);
            if (a <= 0) continue;
            g.fill(x, 0, x + 1, this.height, a << 24);
        }
    }

    private void drawHeader(GuiGraphics g, float alpha) {
        // Заголовок «МЕНЮ ПАУЗЫ».
        String title = this.title.getString().toUpperCase(Locale.ROOT);
        g.pose().pushPose();
        g.pose().translate(MENU_X, TITLE_Y, 0);
        g.pose().scale(2.4f, 2.4f, 1.0f);
        g.drawString(this.font, title, 0, 0, withAlpha(0xFFFFFFFF, alpha), false);
        g.pose().popPose();

        // Подстрока «карта | режим».
        g.pose().pushPose();
        g.pose().translate(MENU_X + 1, TITLE_Y + 26, 0);
        g.pose().scale(1.15f, 1.15f, 1.0f);
        g.drawString(this.font, mapModeLine(), 0, 0, withAlpha(0xFFB0B0B0, alpha), false);
        g.pose().popPose();

        // Тонкая акцентная черта под заголовком.
        int lineY = TITLE_Y + 44;
        g.fill(MENU_X, lineY, MENU_X + 46, lineY + 2, withAlpha(COLOR_ACCENT, alpha));
    }

    // ─────────────────────────── ввод ───────────────────────────

    @Override
    public void onClose() {
        if (!closing) {
            closing = true;
            closeStart = Util.getMillis();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closing) return true;
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // ─────────────────────────── пункт меню (плоский текст) ───────────────────────────

    private class MenuItem extends Button {

        private enum Kind { NORMAL, RESUME, EXIT }

        private float hoverAnim = 0.0f;
        private final Kind kind;

        MenuItem(int x, int y, int width, int height, Component message, OnPress onPress, Kind kind) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.kind = kind;
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            PjmUiSounds.playPress(soundManager);
        }

        private int accentColor() {
            return switch (kind) {
                case RESUME -> 0xFF7CFF7C;
                case EXIT -> 0xFFFF6B6B;
                default -> COLOR_ACCENT;
            };
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            float ga = animAlpha();
            this.isHovered = mouseX >= getX() && mouseY >= getY()
                    && mouseX < getX() + width && mouseY < getY() + height;

            float target = this.isHovered ? 1.0f : 0.0f;
            this.hoverAnim += (target - this.hoverAnim) * 0.35f;

            int accent = accentColor();
            int slide = (int) (this.hoverAnim * 12);

            // Акцентная риска слева при наведении.
            if (this.hoverAnim > 0.02f) {
                int barH = height - 10;
                int barY = getY() + 5;
                g.fill(getX() - 10, barY, getX() - 10 + 3, barY + barH, withAlpha(accent, ga * this.hoverAnim));
            }

            // Цвет текста: белый → акцент по наведению.
            int base = 0xFFFFFFFF;
            int r = lerp((base >> 16) & 0xFF, (accent >> 16) & 0xFF, this.hoverAnim);
            int gg = lerp((base >> 8) & 0xFF, (accent >> 8) & 0xFF, this.hoverAnim);
            int bb = lerp(base & 0xFF, accent & 0xFF, this.hoverAnim);
            int color = withAlpha((0xFF << 24) | (r << 16) | (gg << 8) | bb, ga);

            String text = getMessage().getString().toUpperCase(Locale.ROOT);
            g.pose().pushPose();
            g.pose().translate(getX() + slide, getY() + (height - 8) / 2f, 0);
            g.pose().scale(1.15f, 1.15f, 1.0f);
            g.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
            g.pose().popPose();
        }

        private int lerp(int a, int b, float t) {
            return (int) (a + (b - a) * Math.max(0f, Math.min(1f, t)));
        }
    }

    // ─────────────────────────── масштабирование мыши ───────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true;
        float scale = getScale();
        return super.mouseClicked(mouseX / scale, mouseY / scale, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        float scale = getScale();
        return super.mouseReleased(mouseX / scale, mouseY / scale, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        float scale = getScale();
        return super.mouseDragged(mouseX / scale, mouseY / scale, button, dragX / scale, dragY / scale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float scale = getScale();
        return super.mouseScrolled(mouseX / scale, mouseY / scale, scrollX, scrollY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        float scale = getScale();
        super.mouseMoved(mouseX / scale, mouseY / scale);
    }
}
