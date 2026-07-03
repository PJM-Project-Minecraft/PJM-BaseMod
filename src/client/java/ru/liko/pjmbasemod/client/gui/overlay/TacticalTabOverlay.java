package ru.liko.pjmbasemod.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import ru.liko.pjmbasemod.client.faction.FactionRankIcons;
import ru.liko.pjmbasemod.common.rank.RankDefinition;
import ru.liko.pjmbasemod.common.rank.RankRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Кастомный TAB-список: колонка на каждую scoreboard-команду (фракцию),
 * игроки без команды — компактной секцией снизу. Ванильный TAB отменён
 * в {@link CancelVanillaHotbar}.
 */
public class TacticalTabOverlay {

    private static final int COL_WIDTH = 150;
    private static final int ROW_HEIGHT = 12;
    private static final int HEADER_HEIGHT = 20;
    private static final int TEAM_HEADER_HEIGHT = 16;
    private static final int PADDING = 6;
    private static final int TOP = 30;

    private static final int BG_COLOR = 0xCC101418;
    private static final int BORDER_COLOR = 0x44FFFFFF;
    private static final int DIVIDER_COLOR = 0x33FFFFFF;
    private static final int NEUTRAL_COLOR = 0xFF9AA0A6;

    public static final LayeredDraw.Layer LAYER = (graphics, deltaTracker) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;

        // Show only when holding TAB key
        if (!mc.options.keyPlayerList.isDown()) return;

        List<PlayerInfo> players = mc.player.connection.getListedOnlinePlayers().stream()
                .sorted(Comparator.comparing((PlayerInfo info) -> info.getProfile().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (players.isEmpty()) return;

        Font font = mc.font;
        Scoreboard scoreboard = mc.level != null ? mc.level.getScoreboard() : null;

        // Группировка по scoreboard-командам (стабильный порядок по id команды).
        Map<PlayerTeam, List<PlayerInfo>> byTeam = new TreeMap<>(Comparator.comparing(PlayerTeam::getName));
        List<PlayerInfo> noTeam = new ArrayList<>();
        for (PlayerInfo info : players) {
            PlayerTeam team = scoreboard != null ? scoreboard.getPlayersTeam(info.getProfile().getName()) : null;
            if (team != null) {
                byTeam.computeIfAbsent(team, t -> new ArrayList<>()).add(info);
            } else {
                noTeam.add(info);
            }
        }

        // Нет ни одной фракции — все идут одной нейтральной колонкой.
        boolean noTeamsAtAll = byTeam.isEmpty();
        List<Column> columns = new ArrayList<>();
        if (noTeamsAtAll) {
            columns.add(new Column(Component.translatable("gui.pjmbasemod.tab.no_faction").getString(), NEUTRAL_COLOR, sortColumn(noTeam)));
        } else {
            for (Map.Entry<PlayerTeam, List<PlayerInfo>> entry : byTeam.entrySet()) {
                PlayerTeam team = entry.getKey();
                int color = (team.getColor() != null && team.getColor().getColor() != null)
                        ? 0xFF000000 | team.getColor().getColor() : NEUTRAL_COLOR;
                columns.add(new Column(team.getDisplayName().getString(), color, sortColumn(entry.getValue())));
            }
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        int width = Math.max(200, columns.size() * COL_WIDTH);
        int colWidth = width / columns.size();

        // Вместимость колонки ограничена высотой экрана; при переполнении — строка «+N…».
        int maxRows = Math.max(4, (screenHeight - TOP - 30 - HEADER_HEIGHT - TEAM_HEADER_HEIGHT - PADDING * 2) / ROW_HEIGHT);
        int columnRows = 0;
        for (Column col : columns) {
            columnRows = Math.max(columnRows, Math.min(col.players.size(), maxRows));
        }

        // Секция «Без фракции» под колонками.
        List<FormattedCharSequence> noTeamLines = List.of();
        boolean showNoTeamSection = !noTeamsAtAll && !noTeam.isEmpty();
        if (showNoTeamSection) {
            StringBuilder names = new StringBuilder();
            for (PlayerInfo info : noTeam) {
                if (!names.isEmpty()) names.append(", ");
                names.append(info.getProfile().getName());
            }
            noTeamLines = font.split(Component.literal(names.toString()), width - PADDING * 2);
        }
        int noTeamHeight = showNoTeamSection ? 12 + noTeamLines.size() * (font.lineHeight + 1) + PADDING : 0;

        int height = HEADER_HEIGHT + TEAM_HEADER_HEIGHT + columnRows * ROW_HEIGHT + PADDING + noTeamHeight + PADDING;

        int left = (screenWidth - width) / 2;
        int right = left + width;
        int bottom = TOP + height;

        graphics.pose().pushPose();
        RenderSystem.enableBlend();

        // Фон и тонкая внешняя рамка.
        graphics.fill(left, TOP, right, bottom, BG_COLOR);
        graphics.fill(left, TOP, right, TOP + 1, BORDER_COLOR);
        graphics.fill(left, bottom - 1, right, bottom, BORDER_COLOR);
        graphics.fill(left, TOP, left + 1, bottom, BORDER_COLOR);
        graphics.fill(right - 1, TOP, right, bottom, BORDER_COLOR);

        // Шапка: заголовок + общий онлайн.
        Component title = Component.translatable("gui.pjmbasemod.tab.title")
                .append(Component.literal(" • " + players.size()));
        int titleWidth = font.width(title);
        graphics.drawString(font, title, left + (width - titleWidth) / 2, TOP + 6, 0xFFFFFFFF, false);
        graphics.fill(left, TOP + HEADER_HEIGHT, right, TOP + HEADER_HEIGHT + 1, DIVIDER_COLOR);

        int teamHeaderTop = TOP + HEADER_HEIGHT + 1;
        int rowsTop = teamHeaderTop + TEAM_HEADER_HEIGHT;
        int rowsBottom = rowsTop + columnRows * ROW_HEIGHT;

        for (int c = 0; c < columns.size(); c++) {
            Column col = columns.get(c);
            int colLeft = left + c * colWidth;
            int colRight = colLeft + colWidth;

            // Вертикальный разделитель между колонками.
            if (c > 0) {
                graphics.fill(colLeft, teamHeaderTop, colLeft + 1, rowsBottom, DIVIDER_COLOR);
            }

            // Заголовок фракции: полоса-акцент + имя + счётчик.
            graphics.fill(colLeft + PADDING, teamHeaderTop + 2, colRight - PADDING, teamHeaderTop + 4, col.color);
            String teamLabel = font.plainSubstrByWidth(col.name + " — " + col.players.size(), colWidth - PADDING * 2);
            graphics.drawString(font, teamLabel, colLeft + PADDING, teamHeaderTop + 6, col.color, false);

            int shown = Math.min(col.players.size(), maxRows);
            boolean overflow = col.players.size() > maxRows;
            if (overflow) shown = maxRows - 1;

            int currentY = rowsTop + 1;
            for (int i = 0; i < shown; i++) {
                drawPlayerRow(graphics, font, col.players.get(i), colLeft, colRight, currentY, col.color);
                currentY += ROW_HEIGHT;
            }
            if (overflow) {
                graphics.drawString(font, "+" + (col.players.size() - shown) + "…", colLeft + PADDING, currentY, NEUTRAL_COLOR, false);
            }
        }

        // Секция игроков без фракции.
        if (showNoTeamSection) {
            int sectionTop = rowsBottom + PADDING;
            graphics.fill(left, sectionTop, right, sectionTop + 1, DIVIDER_COLOR);
            Component label = Component.translatable("gui.pjmbasemod.tab.no_faction")
                    .append(Component.literal(" — " + noTeam.size()));
            graphics.drawString(font, label, left + PADDING, sectionTop + 4, NEUTRAL_COLOR, false);
            int lineY = sectionTop + 14;
            for (FormattedCharSequence line : noTeamLines) {
                graphics.drawString(font, line, left + PADDING, lineY, 0xFF777777, false);
                lineY += font.lineHeight + 1;
            }
        }

        RenderSystem.disableBlend();
        graphics.pose().popPose();
    };

    /** КМД первым, ЗАМ вторым, остальные по алфавиту. */
    private static List<PlayerInfo> sortColumn(List<PlayerInfo> players) {
        List<PlayerInfo> sorted = new ArrayList<>(players);
        sorted.sort(Comparator
                .comparingInt((PlayerInfo info) -> {
                    String text = tabText(info);
                    if (text.contains("[КМД]")) return 0;
                    if (text.contains("[ЗАМ]")) return 1;
                    return 2;
                })
                .thenComparing(info -> info.getProfile().getName(), String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private static String tabText(PlayerInfo info) {
        Component display = info.getTabListDisplayName();
        return display != null ? display.getString() : "";
    }

    private static void drawPlayerRow(GuiGraphics graphics, Font font, PlayerInfo info,
                                      int colLeft, int colRight, int y, int teamColor) {
        String rawName = info.getProfile().getName();
        String rawText = tabText(info);

        int currentX = colLeft + PADDING;

        // Иконка командира (погоны) или зама (звёзды).
        if (rawText.contains("[КМД]")) {
            FactionRankIcons.draw(graphics, FactionRankIcons.COMMANDER, currentX, y + 1, 10);
            currentX += 14;
        } else if (rawText.contains("[ЗАМ]")) {
            FactionRankIcons.draw(graphics, FactionRankIcons.DEPUTY, currentX, y + 1, 10);
            currentX += 14;
        }

        // Иконка XP-звания.
        if (!rawText.isEmpty()) {
            for (RankDefinition rank : RankRegistry.get().config().ranks()) {
                if (rawText.contains("[" + rank.shortName() + "]")) {
                    ResourceLocation iconLoc = ResourceLocation.parse(rank.icon());
                    graphics.blit(iconLoc, currentX, y + 1, 0, 0, 10, 10, 10, 10);
                    currentX += 14;
                    break;
                }
            }
        }

        int pingAreaWidth = 12;
        String name = font.plainSubstrByWidth(rawName, colRight - PADDING - pingAreaWidth - currentX);
        graphics.drawString(font, name, currentX, y, teamColor, false);

        drawPingBars(graphics, colRight - PADDING - 8, y, info.getLatency());
    }

    /** Три ping-бара: количество и цвет по латентности. */
    private static void drawPingBars(GuiGraphics graphics, int x, int y, int ping) {
        int filled = ping < 0 ? 0 : ping < 80 ? 3 : ping < 160 ? 2 : 1;
        int color = ping < 80 ? 0xFF55FF55 : ping < 160 ? 0xFFFFAA00 : 0xFFFF5555;
        int baseline = y + 8;
        for (int i = 0; i < 3; i++) {
            int barHeight = 3 + i * 2;
            int barColor = i < filled ? color : 0xFF333333;
            graphics.fill(x + i * 3, baseline - barHeight, x + i * 3 + 2, baseline, barColor);
        }
    }

    private record Column(String name, int color, List<PlayerInfo> players) {
    }
}
