package ru.liko.pjmbasemod.common.teams;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import ru.liko.pjmbasemod.Config;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * Утилита резолва боевых команд (фракций) по scoreboard + конфигу.
 * Ранее проживала в пакете {@code common.frontline} как {@code Teams};
 * после удаления системы линии фронта перенесена сюда без изменения API.
 */
public final class Teams {

    public static final String NEUTRAL_ID = "";
    public static final String NEUTRAL_NAME = "Нейтрально";
    public static final int NEUTRAL_COLOR = 0x9B9B9B;
    public static final String GRAY_ZONE_ID = "__gray_zone";
    public static final String GRAY_ZONE_NAME = "Серая Зона";
    public static final int GRAY_ZONE_COLOR = 0x737373;

    private Teams() {}

    public static List<Config.ConfiguredTeam> all() {
        return Config.getTeams();
    }

    @Nullable
    public static Config.ConfiguredTeam byId(String id) {
        String normalized = normalize(id);
        if (normalized.isBlank()) return null;
        for (Config.ConfiguredTeam team : all()) {
            if (team.id().equalsIgnoreCase(normalized)) return team;
        }
        return null;
    }

    public static boolean exists(String id) {
        return byId(id) != null;
    }

    public static String displayName(@Nullable MinecraftServer server, String id) {
        if (id == null || id.isBlank()) return NEUTRAL_NAME;
        if (GRAY_ZONE_ID.equals(normalize(id))) return GRAY_ZONE_NAME;
        PlayerTeam scoreboardTeam = scoreboardTeam(server, id);
        if (scoreboardTeam != null) return scoreboardTeam.getDisplayName().getString();
        Config.ConfiguredTeam configured = byId(id);
        return configured == null ? NEUTRAL_NAME : configured.id();
    }

    public static int color(@Nullable MinecraftServer server, String id) {
        if (id == null || id.isBlank()) return NEUTRAL_COLOR;
        if (GRAY_ZONE_ID.equals(normalize(id))) return GRAY_ZONE_COLOR;
        PlayerTeam scoreboardTeam = scoreboardTeam(server, id);
        if (scoreboardTeam == null) return fallbackColor(id);
        Integer color = scoreboardTeam.getColor().getColor();
        return color == null ? fallbackColor(id) : color & 0xFFFFFF;
    }

    @Nullable
    public static String resolvePlayerTeamId(ServerPlayer player) {
        if (player == null) return null;
        Team scoreboardTeam = player.getTeam();
        if (scoreboardTeam == null) return null;
        String current = normalize(scoreboardTeam.getName());
        return exists(current) ? current : null;
    }

    /**
     * Команда по scoreboard-имени игрока — работает и для оффлайн-игрока: членство в
     * scoreboard-команде хранится по имени и переживает выход с сервера.
     */
    @Nullable
    public static String resolveTeamIdByName(@Nullable MinecraftServer server, @Nullable String scoreboardName) {
        if (server == null || scoreboardName == null || scoreboardName.isBlank()) return null;
        PlayerTeam team = server.getScoreboard().getPlayersTeam(scoreboardName);
        if (team == null) return null;
        String current = normalize(team.getName());
        return exists(current) ? current : null;
    }

    /** Все члены команды по scoreboard-именам, включая оффлайн. Пустой список, если команды нет. */
    public static List<String> memberNames(@Nullable MinecraftServer server, String teamId) {
        PlayerTeam team = scoreboardTeam(server, teamId);
        return team == null ? List.of() : List.copyOf(team.getPlayers());
    }

    @Nullable
    public static String resolveAlias(String scoreboardNameOrId) {
        String current = normalize(scoreboardNameOrId);
        return exists(current) ? current : null;
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isCombatTeam(String id) {
        String normalized = normalize(id);
        return !normalized.isBlank() && !GRAY_ZONE_ID.equals(normalized) && exists(normalized);
    }

    @Nullable
    private static PlayerTeam scoreboardTeam(@Nullable MinecraftServer server, String id) {
        if (server == null || id == null || id.isBlank()) return null;
        PlayerTeam exact = server.getScoreboard().getPlayerTeam(id);
        if (exact != null) return exact;

        String normalized = normalize(id);
        for (PlayerTeam team : server.getScoreboard().getPlayerTeams()) {
            if (normalize(team.getName()).equals(normalized)) return team;
        }
        return null;
    }

    private static int fallbackColor(String id) {
        Config.ConfiguredTeam team = byId(id);
        if (team == null) return NEUTRAL_COLOR;
        int index = all().indexOf(team);
        return switch (index) {
            case 0 -> 0xE8E8E8;
            case 1 -> 0xD43A3A;
            case 2 -> 0x4A90E2;
            case 3 -> 0xD8B15F;
            default -> ChatFormatting.WHITE.getColor() == null ? NEUTRAL_COLOR : ChatFormatting.WHITE.getColor();
        };
    }
}
