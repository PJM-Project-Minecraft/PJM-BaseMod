package ru.liko.pjmbasemod.client.gui.overlay;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import ru.liko.pjmbasemod.client.radio.RadioManager;

public class VoiceChatOverlay {

    private static final int COMMAND_RADIO_COLOR = 0xFFFFD700;
    private static final int LOCAL_CHAT_COLOR = 0xFF00FFFF;
    private static final int BG_COLOR = 0x80000000;

    private static final int ELEMENT_HEIGHT = 16;
    private static final int ELEMENT_GAP = 2;
    private static final int COMPASS_HALF_WIDTH = 150;
    private static final int COMPASS_GAP = 8;
    private static final int COMPASS_BOTTOM_MARGIN = 10;

    private static float localChatAlpha = 0f;
    private static float commandRadioAlpha = 0f;
    private static final float FADE_SPEED = 0.2f;

    public static final LayeredDraw.Layer INSTANCE = (g, deltaTracker) -> {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer pl = mc.player;
        if (pl == null || mc.options.hideGui) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        RadioManager radio = RadioManager.get();
        String radioSpeakerName = radio.getRadioSpeakerName();
        String localSpeakerName = radio.getLocalSpeakerName();
        boolean commandActive = radio.isTransmitting() || radioSpeakerName != null;
        boolean localActive = radio.isLocalChatActive();

        commandRadioAlpha = lerp(commandRadioAlpha, commandActive ? 1f : 0f, FADE_SPEED);
        localChatAlpha = lerp(localChatAlpha, localActive ? 1f : 0f, FADE_SPEED);

        if (commandRadioAlpha < 0.05f && localChatAlpha < 0.05f) return;

        int xStart = sw / 2 + COMPASS_HALF_WIDTH + COMPASS_GAP;
        int currentY = sh - COMPASS_BOTTOM_MARGIN - ELEMENT_HEIGHT;

        if (localChatAlpha > 0.05f) {
            String localLabel = localSpeakerName == null || localSpeakerName.isBlank()
                    ? Component.translatable("overlay.pjmbasemod.voice.local_chat").getString()
                    : Component.translatable("overlay.pjmbasemod.voice.local_chat").getString() + ": " + localSpeakerName;
            renderChannel(g, xStart, currentY,
                    Component.literal(localLabel),
                    LOCAL_CHAT_COLOR, localChatAlpha, deltaTracker);
            currentY -= (ELEMENT_HEIGHT + ELEMENT_GAP);
        }

        if (commandRadioAlpha > 0.05f) {
            String radioLabel = radioSpeakerName == null || radioSpeakerName.isBlank()
                    ? Component.translatable("overlay.pjmbasemod.voice.command_radio").getString()
                    : Component.translatable("overlay.pjmbasemod.voice.command_radio").getString() + ": " + radioSpeakerName;
            renderChannel(g, xStart, currentY,
                    Component.literal(radioLabel),
                    COMMAND_RADIO_COLOR, commandRadioAlpha, deltaTracker);
        }
    };

    private static void renderChannel(GuiGraphics g, int x, int y, Component label, int color, float alpha, DeltaTracker deltaTracker) {
        Font f = Minecraft.getInstance().font;
        int a = (int) (alpha * 255);
        if (a < 5) return;

        int bgColor = (Math.min(a, 128) << 24) | (BG_COLOR & 0x00FFFFFF);
        int textColor = (a << 24) | (color & 0x00FFFFFF);
        int iconColor = textColor;

        String text = "[ " + label.getString() + " ]";
        int textWidth = f.width(text);
        int waveWidth = 20;
        int totalWidth = 20 + textWidth + 5 + waveWidth;

        g.fill(x, y, x + totalWidth, y + ELEMENT_HEIGHT, bgColor);
        renderSpeakerIcon(g, x + 4, y + 4, iconColor);
        g.drawString(f, text, x + 20, y + 4, textColor, false);
        renderSignalWave(g, x + 20 + textWidth + 4, y + 4, alpha, color, deltaTracker);
    }

    private static void renderSpeakerIcon(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y + 2, x + 2, y + 6, color);
        g.fill(x + 2, y + 1, x + 3, y + 7, color);
        g.fill(x + 3, y, x + 4, y + 8, color);

        g.fill(x + 6, y + 2, x + 7, y + 6, color);
        g.fill(x + 8, y + 1, x + 9, y + 7, color);
        g.fill(x + 10, y, x + 11, y + 8, color);
    }

    private static float waveTime;

    private static void renderSignalWave(GuiGraphics g, int x, int y, float alpha, int color, DeltaTracker deltaTracker) {
        int a = (int) (alpha * 255);
        int waveColor = (a << 24) | (color & 0x00FFFFFF);

        waveTime += deltaTracker.getRealtimeDeltaTicks() * 0.5f;
        if (waveTime > 1000f) waveTime -= 1000f;

        for (int i = 0; i < 4; i++) {
            float val = (float) Math.sin(waveTime + i * 1.2f);
            float normalized = (val + 1f) / 2f;
            int h = (int) (normalized * 7) + 2;
            int barX = x + i * 4;
            int barY = y + (9 - h) / 2;
            g.fill(barX, barY, barX + 2, barY + h, waveColor);
        }
    }

    private static float lerp(float current, float target, float speed) {
        if (Math.abs(current - target) < 0.01f) return target;
        return current + (target - current) * speed;
    }
}
