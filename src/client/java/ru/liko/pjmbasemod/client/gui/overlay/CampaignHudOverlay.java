package ru.liko.pjmbasemod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import ru.liko.pjmbasemod.client.campaign.ClientCampaignState;
import ru.liko.pjmbasemod.client.gui.PjmGuiUtils;
import ru.liko.pjmbasemod.common.network.packet.CampaignSyncPacket;

/**
 * Счёт недельной кампании вверху по центру: «ВСУ 340 · РФ 290 · до вайпа 3д 14ч».
 * Имена фракций — в цветах команд, таймер — золотом. Рисуется только при активной кампании.
 */
public final class CampaignHudOverlay {

    public static final LayeredDraw.Layer OVERLAY = (graphics, deltaTracker) -> render(graphics);

    private static final int PAD_X = 8;
    private static final int TOP = 4;
    private static final int HEIGHT = 14;
    private static final String SEP = " · ";

    private CampaignHudOverlay() {}

    private static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

        List<CampaignSyncPacket.TeamScore> scores = ClientCampaignState.scores();
        if (scores == null || scores.isEmpty()) return;

        Font font = mc.font;
        String timeText = Component.translatable("gui.pjmbasemod.campaign.time_left",
                formatTime(ClientCampaignState.secondsRemaining())).getString();

        int contentW = font.width(timeText);
        for (CampaignSyncPacket.TeamScore score : scores) {
            contentW += font.width(label(score)) + font.width(SEP);
        }
        int width = PAD_X + contentW + PAD_X;
        int x = (graphics.guiWidth() - width) / 2;

        // Масштаб от разрешения вокруг верх-центра (плашка top-center).
        PjmGuiUtils.pushHudScale(graphics, graphics.guiWidth() / 2f, 0);
        RenderSystem.enableBlend();
        try {
            graphics.fill(x, TOP, x + width, TOP + HEIGHT, PjmGuiUtils.SCREEN_HEADER);
            PjmGuiUtils.drawBorder(graphics, x, TOP, width, HEIGHT, PjmGuiUtils.SCREEN_BORDER);

            int textX = x + PAD_X;
            int textY = TOP + 3;
            for (CampaignSyncPacket.TeamScore score : scores) {
                String label = label(score);
                PjmGuiUtils.drawOutlinedString(graphics, font, label, textX, textY,
                        0xFF000000 | (score.color() & 0xFFFFFF));
                textX += font.width(label);
                graphics.drawString(font, SEP, textX, textY, PjmGuiUtils.TEXT_MUTED, false);
                textX += font.width(SEP);
            }
            graphics.drawString(font, timeText, textX, textY, PjmGuiUtils.TEXT_GOLD, false);
        } finally {
            RenderSystem.disableBlend();
            graphics.pose().popPose();
        }
    }

    private static String label(CampaignSyncPacket.TeamScore score) {
        return score.displayName() + " " + score.vp();
    }

    /** «3д 14ч», «5ч 12м», «42м» — крупнее двух единиц не показываем. */
    private static String formatTime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        if (days > 0) return String.format(Locale.ROOT, "%dд %dч", days, hours);
        if (hours > 0) return String.format(Locale.ROOT, "%dч %dм", hours, minutes);
        return minutes + "м";
    }
}
