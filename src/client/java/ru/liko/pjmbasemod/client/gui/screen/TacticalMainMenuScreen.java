package ru.liko.pjmbasemod.client.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import ru.liko.pjmbasemod.Pjmbasemod;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.client.gui.PjmUiSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TacticalMainMenuScreen extends Screen {

    // --- Layout Constants --- (BF5-стиль: плоский текстовый список)
    private static final int NAV_X = 40;
    private static final int NAV_WIDTH = 240;
    private static final int BUTTON_HEIGHT = 26;
    private static final int BUTTON_SPACING = 2;
    private static final int BAR_HEIGHT = 22;

    // --- Colors --- (единая палитра с PjmGuiUtils)
    private static final int COLOR_ACCENT = PjmGuiUtils.ACCENT;
    private static final int COLOR_TEXT_IDLE = 0xFFB8BDC4; // стальной бело-серый

    // Background Slideshow
    private static final ResourceLocation[] BACKGROUNDS = {
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/gui/menu_background_1.png"),
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/gui/menu_background_2.png"),
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/gui/menu_background_3.png"),
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/gui/menu_background_4.png"),
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/gui/menu_background_5.png"),
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/gui/menu_background_6.png"),
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/gui/menu_background_7.png")
    };
    private static final ResourceLocation LOGO =
            ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/icon/pjm_512x512.png");

    private static final long SLIDE_DURATION = 10000L;
    private static final long FADE_DURATION = 2000L;

    private static final int TEX_WIDTH = 640;
    private static final int TEX_HEIGHT = 330;

    private int preloadIndex = 0;
    private boolean allTexturesPreloaded = false;
    private long slideshowStartTime = 0L;

    // --- State ---
    private long devMessageUntil = 0L;
    private long openStart = -1L;
    private static final float ANIM_MS = 180f;

    // --- Widgets ---
    private final List<TacticalButton> mainButtons = new ArrayList<>();
    private TacticalButton btnPlay;

    public TacticalMainMenuScreen() {
        super(Component.literal("Tactical Main Menu"));
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

    private float animAlpha() {
        long now = Util.getMillis();
        if (openStart < 0) openStart = now;
        return Math.min(1f, (now - openStart) / ANIM_MS);
    }

    private static int withAlpha(int color, float a) {
        int base = (color >>> 24) & 0xFF;
        int alpha = (int) (base * Math.max(0f, Math.min(1f, a)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /** Маленькая заострённая стрелка ► (основание слева, остриё справа). */
    private static void drawArrow(GuiGraphics g, int x, int cy, int size, int color) {
        for (int c = 0; c < size; c++) {
            int half = size - c;
            g.fill(x + c, cy - half, x + c + 1, cy + half, color);
        }
    }

    @Override
    protected void init() {
        super.init();
        this.mainButtons.clear();

        int startX = NAV_X;

        // 1. PLAY Button — прямой коннект на основной сервер
        btnPlay = new TacticalButton(startX, 0, NAV_WIDTH, BUTTON_HEIGHT, Component.translatable("menu.pjm.play"), button -> {
            ServerData serverData = new ServerData("Project Minecraft Server", "minecraft.likonchik.xyz", ServerData.Type.OTHER);
            ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(serverData.ip), serverData, false, null);
        }, false, true, false);
        addRenderableWidget(btnPlay);
        mainButtons.add(btnPlay);

        // 2. MULTIPLAYER Button
        TacticalButton btnMulti = new TacticalButton(startX, 0, NAV_WIDTH, BUTTON_HEIGHT, Component.translatable("menu.pjm.multiplayer"), button -> {
            this.minecraft.setScreen(new JoinMultiplayerScreen(this));
        }, false, false, false);
        addRenderableWidget(btnMulti);
        mainButtons.add(btnMulti);

        // 3. SINGLEPLAYER Button
        TacticalButton btnSingle = new TacticalButton(startX, 0, NAV_WIDTH, BUTTON_HEIGHT, Component.translatable("menu.pjm.singleplayer"), button -> {
            this.minecraft.setScreen(new SelectWorldScreen(this));
        }, false, false, false);
        addRenderableWidget(btnSingle);
        mainButtons.add(btnSingle);

        // 4. CHARACTER Button
        TacticalButton btnChar = new TacticalButton(startX, 0, NAV_WIDTH, BUTTON_HEIGHT, Component.translatable("menu.pjm.character"), button -> {
            devMessageUntil = System.currentTimeMillis() + 3000L;
        }, false, false, false);
        addRenderableWidget(btnChar);
        mainButtons.add(btnChar);

        // 5. OPTIONS Button
        TacticalButton btnOpt = new TacticalButton(startX, 0, NAV_WIDTH, BUTTON_HEIGHT, Component.translatable("menu.pjm.options"), button -> {
            this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options));
        }, false, false, false);
        addRenderableWidget(btnOpt);
        mainButtons.add(btnOpt);

        // 6. QUIT Button
        TacticalButton btnQuit = new TacticalButton(startX, 0, NAV_WIDTH, BUTTON_HEIGHT, Component.translatable("menu.pjm.exit"), button -> {
            this.minecraft.stop();
        }, true, false, false);
        addRenderableWidget(btnQuit);
        mainButtons.add(btnQuit);

        updateButtonPositions();
    }

    private void updateButtonPositions() {
        int vHeight = getVHeight();
        int totalBaseHeight = mainButtons.size() * (BUTTON_HEIGHT + BUTTON_SPACING);

        // BF5: список привязан к нижней трети экрана, над панелью управления
        int startY = vHeight - BAR_HEIGHT - 40 - totalBaseHeight;
        if (startY < 90) startY = 90;

        int currentY = startY;

        for (TacticalButton btn : mainButtons) {
            btn.setY(currentY);
            currentY += BUTTON_HEIGHT + BUTTON_SPACING;
        }
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // We override this to manually control when the background and blur are rendered
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        long renderTime = System.currentTimeMillis();
        
        float scale = getScale();
        int vWidth = getVWidth();
        int vHeight = getVHeight();
        int vMouseX = (int) (mouseX / scale);
        int vMouseY = (int) (mouseY / scale);

        // Полноэкранный слайд-шоу фон (без блюра — как в BF5)
        renderSlideshowBackground(guiGraphics, renderTime);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        // Лёгкий slide-in слева при открытии (как в меню паузы).
        guiGraphics.pose().translate((1f - animAlpha()) * -18f, 0f, 0f);

        updateButtonPositions();

        float alpha = animAlpha();

        // Левый скрим для читаемости — плавный попиксельный градиент (как в меню паузы).
        int scrimWidth = (int) (vWidth * 0.5f);
        int maxA = 0xC8;
        for (int x = 0; x < scrimWidth; x++) {
            float t = 1f - x / (float) scrimWidth;
            int a = (int) (maxA * t * t);
            if (a <= 0) continue;
            guiGraphics.fill(x, 0, x + 1, vHeight, a << 24);
        }

        // Нижняя панель управления
        int barY = vHeight - BAR_HEIGHT;
        guiGraphics.fill(0, barY, vWidth, vHeight, withAlpha(0xB0000000, alpha));
        guiGraphics.fill(0, barY, vWidth, barY + 1, withAlpha(COLOR_ACCENT & 0x66FFFFFF, alpha));

        // Шапка: лого + вордмарк + акцент-черта (стиль меню паузы).
        int logoSize = 46;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.setColor(1f, 1f, 1f, alpha);
        guiGraphics.blit(LOGO, 2, 10, logoSize, logoSize, 0f, 0f, 512, 512, 512, 512);
        guiGraphics.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        int textX = 2 + logoSize + 16;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(textX, 14, 0);
        guiGraphics.pose().scale(2.0f, 2.0f, 1.0f);
        guiGraphics.drawString(this.font, "PROJECT", 0, 0, withAlpha(COLOR_ACCENT, alpha), false);
        guiGraphics.drawString(this.font, "MINECRAFT", 0, 10, withAlpha(0xFFFFFFFF, alpha), false);
        guiGraphics.pose().popPose();
        // Акцент-черта под вордмарком (MINECRAFT занимает по низ ~50px, черта ниже).
        guiGraphics.fill(textX, 55, textX + 46, 57, withAlpha(COLOR_ACCENT, alpha));

        // Render Main Buttons
        for (TacticalButton btn : mainButtons) {
            btn.render(guiGraphics, vMouseX, vMouseY, partialTick);
        }

        // Панель управления: подсказка слева, версия справа
        String hint = "ЛКМ  ВЫБРАТЬ";
        try { hint = Component.translatable("menu.pjm.hint.select").getString().toUpperCase(Locale.ROOT); } catch (Exception ignored) {}
        drawArrow(guiGraphics, 18, barY + BAR_HEIGHT / 2, 4, withAlpha(COLOR_ACCENT, alpha));
        guiGraphics.drawString(this.font, hint, 30, barY + (BAR_HEIGHT - 8) / 2, withAlpha(COLOR_TEXT_IDLE, alpha), false);

        String version = PjmGuiUtils.versionLabel();
        guiGraphics.drawString(this.font, version, vWidth - this.font.width(version) - 12, barY + (BAR_HEIGHT - 8) / 2, withAlpha(0xFFFFFFFF, alpha * 0.6f), false);

        // Dev Message Right Top
        if (System.currentTimeMillis() < devMessageUntil) {
            String msg = "WORK IN PROGRESS";
            try {
                msg = Component.translatable("menu.pjm.in_dev").getString();
            } catch (Exception ignored) {
            }

            long remaining = devMessageUntil - System.currentTimeMillis();
            int boxAlpha = remaining < 500 ? (int) ((remaining / 500.0) * 255) : 255;
            int colorAccent = (boxAlpha << 24) | (COLOR_ACCENT & 0x00FFFFFF);

            if (boxAlpha > 5) {
                int textW = this.font.width(msg);
                int boxW = textW + 20;
                int boxH = 24;
                int boxX = vWidth - boxW - 20;
                int boxY = 20;
                
                guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, (int) (alpha * 0.8) << 24);
                guiGraphics.renderOutline(boxX, boxY, boxW, boxH, colorAccent);
                guiGraphics.drawCenteredString(this.font, msg, boxX + boxW / 2, boxY + 8, colorAccent);
            }
        }
        
        guiGraphics.pose().popPose();
    }

    // --- BF5 helpers ---

    // --- Custom Button ---
    private class TacticalButton extends Button {
        private float hoverAnim = 0.0f;
        private final boolean isExit;
        private final boolean isPlay;
        private final boolean isSub;

        public TacticalButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean isExit, boolean isPlay, boolean isSub) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.isExit = isExit;
            this.isPlay = isPlay;
            this.isSub = isSub;
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            PjmUiSounds.playPress(soundManager);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            float ga = animAlpha();
            this.isHovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width && mouseY < getY() + height;

            float target = this.isHovered ? 1.0f : 0.0f;
            this.hoverAnim += (target - this.hoverAnim) * 0.35f;

            int accent = isExit ? 0xFFFF6B6B : (isPlay ? 0xFF7CFF7C : COLOR_ACCENT);
            int slide = (int) (this.hoverAnim * 12);

            // Акцентная стрелка ► слева при наведении.
            if (this.hoverAnim > 0.02f) {
                drawArrow(guiGraphics, getX() + slide, getY() + height / 2, 4,
                        withAlpha(accent, ga * this.hoverAnim));
            }

            // Цвет текста: офф-вайт → акцент по наведению.
            int r = lerp((COLOR_TEXT_IDLE >> 16) & 0xFF, (accent >> 16) & 0xFF, this.hoverAnim);
            int gg = lerp((COLOR_TEXT_IDLE >> 8) & 0xFF, (accent >> 8) & 0xFF, this.hoverAnim);
            int bb = lerp(COLOR_TEXT_IDLE & 0xFF, accent & 0xFF, this.hoverAnim);
            int textColor = withAlpha((0xFF << 24) | (r << 16) | (gg << 8) | bb, ga);

            String text = getMessage().getString().toUpperCase(Locale.ROOT);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(getX() + 14 + slide, getY() + (height - 8) / 2f, 0);
            guiGraphics.pose().scale(1.15f, 1.15f, 1.0f);
            guiGraphics.drawString(Minecraft.getInstance().font, text, 0, 0, textColor, false);
            guiGraphics.pose().popPose();
        }

        private int lerp(int a, int b, float t) {
            return (int) (a + (b - a) * Math.max(0f, Math.min(1f, t)));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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

    private void preloadNextTexture(GuiGraphics guiGraphics) {
        if (allTexturesPreloaded || preloadIndex >= BACKGROUNDS.length) {
            allTexturesPreloaded = true;
            slideshowStartTime = System.currentTimeMillis();
            return;
        }
        try {
            guiGraphics.blit(BACKGROUNDS[preloadIndex], -1, -1, 1, 1, 0.0f, 0.0f, 1, 1, TEX_WIDTH, TEX_HEIGHT);
        } catch (Exception ignored) {}
        preloadIndex++;
        if (preloadIndex >= BACKGROUNDS.length) {
            allTexturesPreloaded = true;
            slideshowStartTime = System.currentTimeMillis();
        }
    }

    private void renderSlideshowBackground(GuiGraphics guiGraphics, long currentTime) {
        try {
            if (!allTexturesPreloaded) {
                preloadNextTexture(guiGraphics);
                RenderSystem.disableBlend();
                guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                guiGraphics.blit(BACKGROUNDS[0], 0, 0, this.width, this.height, 0.0f, 0.0f, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
                return;
            }

            long elapsed = currentTime - slideshowStartTime;
            long totalLoop = SLIDE_DURATION * BACKGROUNDS.length;
            long pos = elapsed % totalLoop;
            int currentIndex = (int) (pos / SLIDE_DURATION);
            int nextIndex = (currentIndex + 1) % BACKGROUNDS.length;
            long timeInSlide = pos % SLIDE_DURATION;

            RenderSystem.disableBlend();
            guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            guiGraphics.blit(BACKGROUNDS[currentIndex], 0, 0, this.width, this.height, 0.0f, 0.0f, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);

            if (timeInSlide > (SLIDE_DURATION - FADE_DURATION)) {
                float alpha = (float) (timeInSlide - (SLIDE_DURATION - FADE_DURATION)) / FADE_DURATION;
                if (alpha > 1.0f) alpha = 1.0f;

                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                guiGraphics.setColor(1.0f, 1.0f, 1.0f, alpha);
                guiGraphics.blit(BACKGROUNDS[nextIndex], 0, 0, this.width, this.height, 0.0f, 0.0f, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
                RenderSystem.disableBlend();
                guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
        } catch (Exception e) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xFF000000);
        }
    }
}
