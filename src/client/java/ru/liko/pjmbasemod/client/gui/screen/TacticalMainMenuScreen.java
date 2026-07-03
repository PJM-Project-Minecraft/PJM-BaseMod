package ru.liko.pjmbasemod.client.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import ru.liko.pjmbasemod.Pjmbasemod;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ru.liko.pjmbasemod.client.gui.PjmUiSounds;

import java.util.ArrayList;
import java.util.List;

public class TacticalMainMenuScreen extends Screen {

    // --- Layout Constants ---
    private static final int PANEL_WIDTH = 280;
    private static final int BUTTON_HEIGHT = 30;
    private static final int BUTTON_SPACING = 5;
    private static final int SUB_BUTTON_HEIGHT = 24;
    private static final int SUB_BUTTON_SPACING = 3;

    // --- Colors ---
    private static final int COLOR_ACCENT = 0xFFFFAA00; // Amber/Gold
    private static final int COLOR_PANEL_BG = 0xAA0F0F0F; // Semi-transparent dark background for blur

    // --- Resources ---
    private static final ResourceLocation ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID,
            "textures/icon/pjm_512x512.png");

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
    private static final long SLIDE_DURATION = 10000L;
    private static final long FADE_DURATION = 2000L;

    private static final int TEX_WIDTH = 640;
    private static final int TEX_HEIGHT = 330;

    private int preloadIndex = 0;
    private boolean allTexturesPreloaded = false;
    private long slideshowStartTime = 0L;

    // --- State ---
    private boolean isPlayMenuOpen = false;
    private long devMessageUntil = 0L;

    // --- Widgets ---
    private final List<TacticalButton> mainButtons = new ArrayList<>();
    private final List<TacticalButton> playSubMenuButtons = new ArrayList<>();
    private TacticalButton btnPlay;

    // --- Animation State ---
    private float playMenuAnim = 0.0f;

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

    @Override
    protected void init() {
        super.init();
        this.mainButtons.clear();
        this.playSubMenuButtons.clear();

        int startX = 40;

        // 1. PLAY Button
        btnPlay = new TacticalButton(startX, 0, PANEL_WIDTH - 80, BUTTON_HEIGHT, Component.translatable("menu.pjm.play"), button -> {
            togglePlayMenu();
        }, false, true, false);
        addRenderableWidget(btnPlay);
        mainButtons.add(btnPlay);

        // 2. Sub-Menu Buttons (Connect Main, Alt 1, Alt 2, Singleplayer)
        TacticalButton btnConnectMain = new TacticalButton(startX + 20, 0, PANEL_WIDTH - 100, SUB_BUTTON_HEIGHT, Component.translatable("menu.pjm.connect_main"),
                button -> {
                    ServerData serverData = new ServerData("Project Minecraft Server", "81.88.221.192:30000", ServerData.Type.OTHER);
                    ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(serverData.ip), serverData, false, null);
                }, false, false, true);
        addRenderableWidget(btnConnectMain);
        playSubMenuButtons.add(btnConnectMain);

        TacticalButton btnConnectAlt1 = new TacticalButton(startX + 20, 0, PANEL_WIDTH - 100, SUB_BUTTON_HEIGHT, Component.translatable("menu.pjm.connect_alt1"),
                button -> {
                    ServerData serverData = new ServerData("Project Minecraft Alt 1", "pl1.hoxen.one:25569", ServerData.Type.OTHER);
                    ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(serverData.ip), serverData, false, null);
                }, false, false, true);
        addRenderableWidget(btnConnectAlt1);
        playSubMenuButtons.add(btnConnectAlt1);

        TacticalButton btnSingle = new TacticalButton(startX + 20, 0, PANEL_WIDTH - 100, SUB_BUTTON_HEIGHT, Component.translatable("menu.pjm.singleplayer"),
                button -> {
                    this.minecraft.setScreen(new SelectWorldScreen(this));
                }, false, false, true);
        addRenderableWidget(btnSingle);
        playSubMenuButtons.add(btnSingle);

        // 3. MULTIPLAYER Button
        TacticalButton btnMulti = new TacticalButton(startX, 0, PANEL_WIDTH - 80, BUTTON_HEIGHT, Component.translatable("menu.pjm.multiplayer"), button -> {
            this.minecraft.setScreen(new JoinMultiplayerScreen(this));
        }, false, false, false);
        addRenderableWidget(btnMulti);
        mainButtons.add(btnMulti);

        // 4. CHARACTER Button
        TacticalButton btnChar = new TacticalButton(startX, 0, PANEL_WIDTH - 80, BUTTON_HEIGHT, Component.translatable("menu.pjm.character"), button -> {
            devMessageUntil = System.currentTimeMillis() + 3000L;
        }, false, false, false);
        addRenderableWidget(btnChar);
        mainButtons.add(btnChar);

        // 5. OPTIONS Button
        TacticalButton btnOpt = new TacticalButton(startX, 0, PANEL_WIDTH - 80, BUTTON_HEIGHT, Component.translatable("menu.pjm.options"), button -> {
            this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options));
        }, false, false, false);
        addRenderableWidget(btnOpt);
        mainButtons.add(btnOpt);

        // 6. QUIT Button
        TacticalButton btnQuit = new TacticalButton(startX, 0, PANEL_WIDTH - 80, BUTTON_HEIGHT, Component.translatable("menu.pjm.exit"), button -> {
            this.minecraft.stop();
        }, true, false, false);
        addRenderableWidget(btnQuit);
        mainButtons.add(btnQuit);

        updateButtonPositions();
    }

    private void togglePlayMenu() {
        isPlayMenuOpen = !isPlayMenuOpen;
    }

    private void updateButtonPositions() {
        int vHeight = getVHeight();
        int totalBaseHeight = mainButtons.size() * (BUTTON_HEIGHT + BUTTON_SPACING);
        int subMenuTotalHeight = playSubMenuButtons.size() * (SUB_BUTTON_HEIGHT + SUB_BUTTON_SPACING);
        int maxTotalHeight = totalBaseHeight + subMenuTotalHeight;
        
        // Calculate dynamic starting Y centered around the maximum expanded height
        // This ensures the menu doesn't push into the bottom status text on 3x/4x scales
        int startY = (vHeight - maxTotalHeight) / 2;
        
        // Push startY down to ensure it stays below the logo (logo takes ~60-70px)
        if (startY < 75) startY = 75;
        
        int currentY = startY;

        for (int i = 0; i < mainButtons.size(); i++) {
            TacticalButton btn = mainButtons.get(i);
            btn.setY(currentY);

            if (btn == btnPlay) {
                currentY += BUTTON_HEIGHT + BUTTON_SPACING;
                
                // Sub-buttons space based on animation
                int animatedOffset = (int) (subMenuTotalHeight * playMenuAnim);

                for (int j = 0; j < playSubMenuButtons.size(); j++) {
                    TacticalButton subBtn = playSubMenuButtons.get(j);
                    // Base Y offset for each sub-button
                    int subY = btnPlay.getY() + BUTTON_HEIGHT + BUTTON_SPACING + j * (SUB_BUTTON_HEIGHT + SUB_BUTTON_SPACING);
                    subBtn.setY(subY);
                    
                    // Visibility & Activity
                    boolean visible = playMenuAnim > 0.01f;
                    subBtn.visible = visible;
                    subBtn.active = isPlayMenuOpen; // Only clickable if fully open or opening
                    subBtn.setAlpha(playMenuAnim);
                }

                currentY += animatedOffset;
            } else {
                currentY += BUTTON_HEIGHT + BUTTON_SPACING;
            }
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

        // 1. Render the base slideshow (this will be the blurred one for the panel)
        renderSlideshowBackground(guiGraphics, renderTime);
        
        // 2. Apply vanilla 1.21.1 full-screen blur over the slideshow
        this.renderBlurredBackground(partialTick);
        
        // 3. Render the clear slideshow OUTSIDE the left panel using scissor
        guiGraphics.enableScissor((int)(PANEL_WIDTH * scale), 0, this.width, this.height);
        renderSlideshowBackground(guiGraphics, renderTime);
        guiGraphics.disableScissor();

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);

        // Update Animation
        float targetAnim = isPlayMenuOpen ? 1.0f : 0.0f;
        float diff = targetAnim - this.playMenuAnim;
        if (Math.abs(diff) < 0.001f) {
            this.playMenuAnim = targetAnim;
        } else {
            // Scale lerp speed based on framerate to keep it consistent
            this.playMenuAnim += diff * 0.2f;
        }

        updateButtonPositions();

        // Left Panel Background (Semi-transparent overlay to tint the blur)
        guiGraphics.fill(0, 0, PANEL_WIDTH, vHeight, COLOR_PANEL_BG);
        
        // Accent line on the right edge of the panel
        guiGraphics.fill(PANEL_WIDTH - 2, 0, PANEL_WIDTH, vHeight, COLOR_ACCENT & 0x77FFFFFF);

        // Logo & Title
        // Scale logo down on very short screens (like 3x scale) to avoid overlap
        boolean smallScreen = vHeight < 400;
        int logoSize = smallScreen ? 32 : 48;
        int logoY = smallScreen ? 15 : 25;
        
        RenderSystem.enableBlend();
        guiGraphics.blit(ICON_TEXTURE, 20, logoY, logoSize, logoSize, 0, 0, 512, 512, 512, 512);
        RenderSystem.disableBlend();

        guiGraphics.pose().pushPose();
        int textX = 20 + logoSize + 12;
        int textY = logoY + (logoSize / 2) - (smallScreen ? 10 : 15);
        guiGraphics.pose().translate(textX, textY, 0);
        
        float textScale = smallScreen ? 1.2f : 1.5f;
        guiGraphics.pose().scale(textScale, textScale, 1.0f);
        guiGraphics.drawString(this.font, "PROJECT", 0, 0, COLOR_ACCENT, true);
        guiGraphics.drawString(this.font, "MINECRAFT", 0, 10, 0xFFFFFFFF, true);
        guiGraphics.pose().popPose();

        // Render Sub-menu buttons with Scissor for smooth accordion reveal
        if (playMenuAnim > 0.01f) {
            int subMenuTotalHeight = playSubMenuButtons.size() * (SUB_BUTTON_HEIGHT + SUB_BUTTON_SPACING);
            int animatedHeight = (int) (subMenuTotalHeight * playMenuAnim);
            
            int scissorX = 0;
            int scissorY = btnPlay.getY() + BUTTON_HEIGHT + BUTTON_SPACING;
            
            guiGraphics.enableScissor(
                (int)(scissorX * scale), 
                (int)(scissorY * scale), 
                (int)((scissorX + PANEL_WIDTH) * scale), 
                (int)((scissorY + animatedHeight) * scale)
            );
            
            for (TacticalButton subBtn : playSubMenuButtons) {
                subBtn.render(guiGraphics, vMouseX, vMouseY, partialTick);
            }
            
            guiGraphics.disableScissor();
        }

        // Render Main Buttons
        for (TacticalButton btn : mainButtons) {
            btn.render(guiGraphics, vMouseX, vMouseY, partialTick);
        }

        // Version Bottom Left
        String version = "ver. " + Pjmbasemod.MODID.toUpperCase() + " 0.1";
        guiGraphics.drawString(this.font, version, 10, vHeight - 15, 0xAAFFFFFF, true);

        // Dev Message Right Top
        if (System.currentTimeMillis() < devMessageUntil) {
            String msg = "WORK IN PROGRESS";
            try {
                msg = Component.translatable("menu.pjm.in_dev").getString();
            } catch (Exception ignored) {
            }

            long remaining = devMessageUntil - System.currentTimeMillis();
            int alpha = remaining < 500 ? (int) ((remaining / 500.0) * 255) : 255;
            int colorAccent = (alpha << 24) | (COLOR_ACCENT & 0x00FFFFFF);

            if (alpha > 5) {
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
            this.isHovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width && mouseY < getY() + height;
            boolean hovered = this.isHovered;
            boolean activeState = hovered || (isPlay && isPlayMenuOpen);

            float target = activeState ? 1.0f : 0.0f;
            this.hoverAnim += (target - this.hoverAnim) * 0.3f;

            int targetColor = isExit ? 0xFFFF4444 : (isPlay ? 0xFF44FF44 : COLOR_ACCENT);
            
            // Background
            int bgAlpha = (int) (this.hoverAnim * 120); // More opaque on hover
            int bgColor = (bgAlpha << 24) | (targetColor & 0x00FFFFFF);
            
            if (isSub) {
                guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, 0x66000000); // Darker base for sub
            } else {
                guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, 0x77000000); // Base dark transparent
            }
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
            
            // Outline border
            int borderAlpha = (int) (Math.max(0.1f, this.hoverAnim * 0.5f) * 255);
            int borderColor = (borderAlpha << 24) | (targetColor & 0x00FFFFFF);
            guiGraphics.renderOutline(getX(), getY(), width, height, borderColor);

            // Left accent line
            int lineAlpha = (int) (Math.max(0.4f, this.hoverAnim) * 255);
            int lineColor = (lineAlpha << 24) | (targetColor & 0x00FFFFFF);
            guiGraphics.fill(getX(), getY(), getX() + 4, getY() + height, lineColor);

            // Text with slide effect
            int textXOffset = (int) (this.hoverAnim * 8);
            int baseTextColor = isSub ? 0xFFCCCCCC : 0xFFFFFFFF;
            
            int r = (int) ( ((baseTextColor >> 16) & 0xFF) + this.hoverAnim * (((targetColor >> 16) & 0xFF) - ((baseTextColor >> 16) & 0xFF)) );
            int g = (int) ( ((baseTextColor >> 8) & 0xFF) + this.hoverAnim * (((targetColor >> 8) & 0xFF) - ((baseTextColor >> 8) & 0xFF)) );
            int b = (int) ( (baseTextColor & 0xFF) + this.hoverAnim * ((targetColor & 0xFF) - (baseTextColor & 0xFF)) );
            
            // Sub buttons have overall alpha controlled by playMenuAnim
            int finalAlpha = 255;
            if (isSub) {
                finalAlpha = (int) (this.alpha * 255);
            }
            int textColor = (finalAlpha << 24) | (r << 16) | (g << 8) | b;

            guiGraphics.drawString(Minecraft.getInstance().font, getMessage(), getX() + 15 + textXOffset, getY() + (height - 8) / 2, textColor, true);
            
            // Play button indicator (arrow)
            if (isPlay) {
                String arrow = isPlayMenuOpen ? "▼" : "▶";
                int arrowW = Minecraft.getInstance().font.width(arrow);
                guiGraphics.drawString(Minecraft.getInstance().font, arrow, getX() + width - arrowW - 10, getY() + (height - 8) / 2, textColor, true);
            }
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
