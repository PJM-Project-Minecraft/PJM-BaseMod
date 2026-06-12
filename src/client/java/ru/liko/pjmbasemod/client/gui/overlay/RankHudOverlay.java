package ru.liko.pjmbasemod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.client.faction.ClientFactionCommanderState;
import ru.liko.pjmbasemod.client.rank.ClientRankState;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Locale;
import java.util.Queue;

@EventBusSubscriber(modid = Pjmbasemod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class RankHudOverlay {

    public static final LayeredDraw.Layer OVERLAY = (graphics, deltaTracker) -> render(graphics);

    private static final Queue<XpPopup> POPUPS = new ArrayDeque<>();
    private static final long POPUP_DURATION_MS = 1800L;

    private RankHudOverlay() {
    }

    public static void showDelta(int delta, String reason, int accentColor) {
        if (delta == 0) return;
        while (POPUPS.size() >= 5) POPUPS.poll();
        POPUPS.offer(new XpPopup(delta, reason == null ? "" : reason, accentColor, System.currentTimeMillis()));
    }

    public static ResourceLocation icon(String raw) {
        ResourceLocation parsed = raw == null ? null : ResourceLocation.tryParse(raw);
        return parsed == null
                ? ResourceLocation.fromNamespaceAndPath(Pjmbasemod.MODID, "textures/rangs/private.png")
                : parsed;
    }

    private static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

        ClientRankState.State state = ClientRankState.state();
        if (!state.enabled()) {
            POPUPS.clear();
            return;
        }

        graphics.pose().pushPose();
        RenderSystem.enableBlend();
        try {
            // Main badge is now rendered in the inventory screen, so we only render popups here on the HUD.
            if (state.showXpPopups()) {
                renderPopups(graphics, mc);
            }
        } finally {
            RenderSystem.disableBlend();
            graphics.pose().popPose();
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen)) {
            return;
        }
        
        ClientRankState.State state = ClientRankState.state();
        ClientFactionCommanderState.State commander = ClientFactionCommanderState.state();
        boolean showRank = state.enabled() && state.showRankHud();
        if (!showRank && !commander.active()) {
            return;
        }
        
        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        
        graphics.pose().pushPose();
        RenderSystem.enableBlend();
        try {
            renderBadge(graphics, mc, state, commander, showRank);
        } finally {
            RenderSystem.disableBlend();
            graphics.pose().popPose();
        }
    }

    private static void renderBadge(GuiGraphics graphics, Minecraft mc, ClientRankState.State state,
                                    ClientFactionCommanderState.State commander, boolean showRank) {
        Font font = mc.font;
        int x = 12;
        int y = 12;
        int width = commander.active() ? 184 : 160;
        int height = commander.active() ? 42 : 28;
        int baseAccent = showRank ? state.accentColor() : commander.teamColor();
        int accent = withAlpha(baseAccent, 0xFF);

        // Tactical Background
        graphics.fill(x, y, x + width, y + height, 0x99000000);
        
        // Accent Left Border
        graphics.fill(x, y, x + 3, y + height, accent);
        
        // Thin Top/Bottom Borders
        graphics.fill(x + 3, y, x + width, y + 1, 0x22FFFFFF);
        graphics.fill(x + 3, y + height - 1, x + width, y + height, 0x22FFFFFF);

        // Icon
        ResourceLocation icon = icon(state.icon());
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.blit(icon, x + 8, y + 6, 0, 0, 16, 16, 16, 16);

        if (showRank) {
            // Title & XP
            String title = state.shortName().toUpperCase(Locale.ROOT) + " " + state.displayName().toUpperCase(Locale.ROOT);
            String xpText = state.xp() + " XP";

            graphics.drawString(font, font.plainSubstrByWidth(title, commander.active() ? 104 : 80),
                    x + 28, y + 5, 0xFFDDDDDD, false);
            graphics.drawString(font, xpText, x + width - 6 - font.width(xpText), y + 5, accent, false);

            // Progress Bar
            int barX = x + 28;
            int barY = y + 17;
            int barW = width - 34;
            int barH = 5;

            graphics.fill(barX, barY, barX + barW, barY + barH, 0x66000000);

            // Outline
            graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY, 0x33FFFFFF);
            graphics.fill(barX - 1, barY + barH, barX + barW + 1, barY + barH + 1, 0x33FFFFFF);
            graphics.fill(barX - 1, barY, barX, barY + barH, 0x33FFFFFF);
            graphics.fill(barX + barW, barY, barX + barW + 1, barY + barH, 0x33FFFFFF);

            if (state.nextMinXp() > state.minXp()) {
                int span = Math.max(1, state.nextMinXp() - state.minXp());
                int progress = Mth.clamp(state.xp() - state.minXp(), 0, span);
                int fill = Math.max(1, barW * progress / span);
                graphics.fill(barX, barY, barX + fill, barY + barH, withAlpha(accent, 0xEE));
            }
        } else {
            String title = commander.roleShortName().toUpperCase(Locale.ROOT) + " "
                    + commander.roleDisplayName().toUpperCase(Locale.ROOT);
            graphics.drawString(font, font.plainSubstrByWidth(title, width - 34),
                    x + 28, y + 7, 0xFFDDDDDD, false);
        }

        if (commander.active()) {
            String roleText = net.minecraft.network.chat.Component
                    .translatable("gui.pjmbasemod.faction.commander.badge").getString();
            int roleColor = withAlpha(commander.teamColor(), 0xFF);
            graphics.drawString(font, font.plainSubstrByWidth(roleText, width - 34),
                    x + 28, y + 28, roleColor, false);
        }
    }

    private static void renderPopups(GuiGraphics graphics, Minecraft mc) {
        long now = System.currentTimeMillis();
        int index = 0;
        Iterator<XpPopup> it = POPUPS.iterator();
        while (it.hasNext()) {
            XpPopup popup = it.next();
            long age = now - popup.createdAtMs();
            if (age > POPUP_DURATION_MS) {
                it.remove();
                continue;
            }

            float progress = age / (float) POPUP_DURATION_MS;
            float alpha = progress < 0.75f ? 1.0f : 1.0f - ((progress - 0.75f) / 0.25f);
            alpha = Mth.clamp(alpha, 0.0f, 1.0f);
            
            // Y position slides up as they age
            int y = 46 + index * 18 - (int) (progress * 8.0f);
            int x = 12;
            
            int color = popup.delta() > 0 ? 0xFFFFCC00 : 0xFFFF4444; // Tactical yellow/red
            String text = (popup.delta() > 0 ? "+" : "") + popup.delta() + " XP";
            String reason = reasonLabel(popup.reason());
            if (!reason.isBlank()) text += " | " + reason;
            
            int textW = mc.font.width(text);
            
            // Tactical Background for popup
            int bgAlpha = (int)(alpha * 0x99) << 24;
            graphics.fill(x, y, x + textW + 14, y + 14, bgAlpha);
            
            // Left border accent
            int lineAlpha = (int)(alpha * 0xFF) << 24;
            graphics.fill(x, y, x + 2, y + 14, (color & 0x00FFFFFF) | lineAlpha);
            
            // Thin borders top and bottom
            int borderAlpha = (int)(alpha * 0x22) << 24;
            graphics.fill(x + 2, y, x + textW + 14, y + 1, (0xFFFFFF & 0x00FFFFFF) | borderAlpha);
            graphics.fill(x + 2, y + 13, x + textW + 14, y + 14, (0xFFFFFF & 0x00FFFFFF) | borderAlpha);
            
            // Flat text rendering (no shadow)
            int textColor = (0xFFFFFFFF & 0x00FFFFFF) | lineAlpha;
            graphics.drawString(mc.font, text, x + 6, y + 3, textColor, false);

            index++;
        }
    }

    private static String reasonLabel(String reason) {
        return switch (reason == null ? "" : reason) {
            case "kill" -> "УБИЙСТВО";
            case "teamkill" -> "ТИМКИЛЛ";
            case "sector" -> "СЕКТОР";
            case "admin" -> "АДМИН";
            default -> "";
        };
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    private record XpPopup(int delta, String reason, int accentColor, long createdAtMs) {
    }
}
