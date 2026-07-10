package ru.liko.pjmbasemod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import ru.liko.pjmbasemod.client.faction.ClientFactionOrderState;

/** Постоянная плашка приказа фракции в левом верхнем углу. */
public final class FactionOrderHudOverlay {

    public static final LayeredDraw.Layer OVERLAY = (graphics, deltaTracker) -> render(graphics);

    private FactionOrderHudOverlay() {
    }

    private static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

        ClientFactionOrderState.State order = ClientFactionOrderState.current();
        if (order == null) return;

        Font font = mc.font;
        String prefix = Component.translatable("gui.pjmbasemod.faction.order.hud_prefix").getString();
        String text = order.text();
        int remaining = ClientFactionOrderState.remainingSeconds();
        String timeText = remaining < 0
                ? Component.translatable("gui.pjmbasemod.faction.order.remaining_permanent").getString()
                : remaining + "С";

        int textWidth = Math.max(font.width(prefix + " " + text), font.width(timeText));
        int width = Math.min(260, textWidth + 12);
        int x = 5;
        int y = 45;
        int height = 30;
        int accent = 0xFF000000 | (order.color() & 0x00FFFFFF);

        graphics.pose().pushPose();
        RenderSystem.enableBlend();
        try {
            graphics.fill(x, y, x + width, y + height, 0xBB0D0D0D);
            graphics.fill(x, y, x + 2, y + height, accent);
            graphics.drawString(font, prefix, x + 8, y + 5, accent, false);
            graphics.drawString(font, ellipsize(font, text, width - 12), x + 8, y + 16, 0xFFCCCCCC, false);
            graphics.drawString(font, timeText, x + width - font.width(timeText) - 6, y + 5, 0xFFFFAA00, false);
        } finally {
            RenderSystem.disableBlend();
            graphics.pose().popPose();
        }
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
