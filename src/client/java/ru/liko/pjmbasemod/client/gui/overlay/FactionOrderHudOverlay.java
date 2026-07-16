package ru.liko.pjmbasemod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import ru.liko.pjmbasemod.client.faction.ClientFactionOrderState;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;

/**
 * Постоянная плашка приказа фракции в левом верхнем углу (BF5-glass):
 * акцент-полоса цвета команды слева, верхняя строка «ПРИКАЗ … таймер»,
 * ниже — текст приказа. Ширина считается по самой длинной строке, поэтому
 * заголовок и таймер больше не налезают друг на друга.
 */
public final class FactionOrderHudOverlay {

    public static final LayeredDraw.Layer OVERLAY = (graphics, deltaTracker) -> render(graphics);

    private static final int PAD_X = 8;
    private static final int STRIPE_W = 3;
    private static final int TOP_GAP = 12; // мин. зазор между заголовком и таймером
    private static final int MAX_TEXT_W = 240;

    private FactionOrderHudOverlay() {
    }

    private static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

        ClientFactionOrderState.State order = ClientFactionOrderState.current();
        if (order == null) return;

        Font font = mc.font;
        String label = Component.translatable("gui.pjmbasemod.faction.order.hud_prefix")
                .getString().toUpperCase(Locale.ROOT);
        int remaining = ClientFactionOrderState.remainingSeconds();
        String timeText = remaining < 0 ? "∞" : formatTime(remaining);
        String body = ellipsize(font, order.text(), MAX_TEXT_W);

        int accent = 0xFF000000 | (order.color() & 0x00FFFFFF);
        int topRowW = font.width(label) + TOP_GAP + font.width(timeText);
        int contentW = Math.max(topRowW, font.width(body));
        int width = STRIPE_W + PAD_X + contentW + PAD_X;

        int x = 5;
        int y = 45;
        int height = 30;
        int textX = x + STRIPE_W + PAD_X;

        graphics.pose().pushPose();
        RenderSystem.enableBlend();
        try {
            graphics.fill(x, y, x + width, y + height, PjmGuiUtils.SCREEN_HEADER);
            PjmGuiUtils.drawBorder(graphics, x, y, width, height, PjmGuiUtils.SCREEN_BORDER);
            graphics.fill(x, y, x + STRIPE_W, y + height, accent);

            // Верхняя строка: заголовок слева (цвет команды), таймер справа (золото).
            graphics.drawString(font, label, textX, y + 6, accent, false);
            graphics.drawString(font, timeText, x + width - PAD_X - font.width(timeText), y + 6,
                    PjmGuiUtils.TEXT_GOLD, false);
            // Текст приказа.
            graphics.drawString(font, body, textX, y + 17, PjmGuiUtils.TEXT_PRIMARY, false);
        } finally {
            RenderSystem.disableBlend();
            graphics.pose().popPose();
        }
    }

    /** «M:SS» для минут и больше, иначе «Nс». */
    private static String formatTime(int seconds) {
        if (seconds >= 60) {
            return (seconds / 60) + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
        }
        return seconds + "с"; // с
    }

    private static String ellipsize(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        while (!text.isEmpty() && font.width(text + ellipsis) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }
}
