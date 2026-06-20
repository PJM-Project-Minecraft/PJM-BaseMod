package ru.liko.pjmbasemod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import ru.liko.pjmbasemod.client.faction.FactionRankIcons;
import ru.liko.pjmbasemod.common.rank.RankDefinition;
import ru.liko.pjmbasemod.common.rank.RankRegistry;

import java.util.Comparator;
import java.util.List;

public class TacticalTabOverlay {

    public static final LayeredDraw.Layer LAYER = (graphics, deltaTracker) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;
        
        // Show only when holding TAB key
        if (!mc.options.keyPlayerList.isDown()) return;

        List<PlayerInfo> players = mc.player.connection.getListedOnlinePlayers().stream()
                .sorted(Comparator.comparing((PlayerInfo info) -> info.getProfile().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (players.isEmpty()) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        Font font = mc.font;

        // Constants for layout
        int width = 300;
        int rowHeight = 12;
        int headerHeight = 20;
        int padding = 6;
        int maxRows = 20; // limit visible players or implement scrolling/columns later
        int visiblePlayers = Math.min(players.size(), maxRows);
        
        int height = headerHeight + (visiblePlayers * rowHeight) + padding * 2;
        
        int left = (screenWidth - width) / 2;
        int top = 30; // 30 pixels from the top
        int right = left + width;
        int bottom = top + height;

        graphics.pose().pushPose();
        RenderSystem.enableBlend();

        // Background (Dark gray/black, 80% opacity)
        graphics.fill(left, top, right, bottom, 0xCC111111);
        
        // Header Border (1px light gray line)
        graphics.fill(left, top + headerHeight, right, top + headerHeight + 1, 0x88EEEEEE);

        // Header Text
        Component title = Component.literal("PLAYER ROSTER");
        int titleWidth = font.width(title);
        graphics.drawString(font, title, left + (width - titleWidth) / 2, top + 6, 0xFFFFFFFF, false);

        // Player List
        int currentY = top + headerHeight + padding;
        Scoreboard scoreboard = mc.level != null ? mc.level.getScoreboard() : null;

        for (int i = 0; i < visiblePlayers; i++) {
            PlayerInfo info = players.get(i);
            String rawName = info.getProfile().getName();
            
            PlayerTeam team = scoreboard != null ? scoreboard.getPlayersTeam(rawName) : null;
            int nickColor = (team != null && team.getColor() != null && team.getColor().getColor() != null) 
                    ? team.getColor().getColor() : 0xFFDDDDDD;

            Component tabListDisplay = info.getTabListDisplayName();
            String rawText = tabListDisplay != null ? tabListDisplay.getString() : "";

            int currentX = left + padding;

            // Иконка командира (погоны) или зама (звёзды).
            if (rawText.contains("[КМД]")) {
                FactionRankIcons.draw(graphics, FactionRankIcons.COMMANDER, currentX, currentY + 1, 10);
                currentX += 14;
            } else if (rawText.contains("[ЗАМ]")) {
                FactionRankIcons.draw(graphics, FactionRankIcons.DEPUTY, currentX, currentY + 1, 10);
                currentX += 14;
            }

            // Check for Rank
            RankDefinition matchedRank = null;
            if (!rawText.isEmpty()) {
                for (RankDefinition rank : RankRegistry.get().config().ranks()) {
                    if (rawText.contains("[" + rank.shortName() + "]")) {
                        matchedRank = rank;
                        break;
                    }
                }
            }

            if (matchedRank != null) {
                ResourceLocation iconLoc = ResourceLocation.parse(matchedRank.icon());
                graphics.blit(iconLoc, currentX, currentY + 1, 0, 0, 10, 10, 10, 10);
                currentX += 14;
            }

            // Draw Nickname
            graphics.drawString(font, rawName, currentX, currentY, nickColor, false);

            // Ping
            int ping = info.getLatency();
            int pingColor = ping < 100 ? 0xFF00FF00 : (ping < 200 ? 0xFFFFAA00 : 0xFFFF0000);
            String pingStr = ping + " ms";
            int pingWidth = font.width(pingStr);
            graphics.drawString(font, pingStr, right - padding - pingWidth, currentY, pingColor, false);

            currentY += rowHeight;
        }

        RenderSystem.disableBlend();
        graphics.pose().popPose();
    };
}
