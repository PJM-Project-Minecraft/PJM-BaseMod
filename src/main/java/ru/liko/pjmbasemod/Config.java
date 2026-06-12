package ru.liko.pjmbasemod;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Config {

    private Config() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SQUAD_HUD;
    public static final ModConfigSpec.BooleanValue DEBUG;
    public static final ModConfigSpec.BooleanValue DISABLE_HUNGER;
    public static final ModConfigSpec.BooleanValue DISABLE_ARMOR;
    public static final ModConfigSpec.LongValue ITEM_SWITCH_DISPLAY_MS;
    public static final ModConfigSpec.IntValue CAPTURE_TIME_SECONDS;
    public static final ModConfigSpec.BooleanValue CAPTURE_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TEAMS;
    public static final ModConfigSpec.BooleanValue FRONTLINE_ENABLED;
    public static final ModConfigSpec.BooleanValue FRONTLINE_HUD_ENABLED;
    public static final ModConfigSpec.BooleanValue FRONTLINE_MANUAL_ACTIVE;
    public static final ModConfigSpec.BooleanValue FRONTLINE_USE_REAL_TIME_WINDOW;
    public static final ModConfigSpec.ConfigValue<String> FRONTLINE_REAL_TIME_ZONE;
    public static final ModConfigSpec.ConfigValue<String> FRONTLINE_REAL_TIME_START;
    public static final ModConfigSpec.ConfigValue<String> FRONTLINE_REAL_TIME_END;
    public static final ModConfigSpec.IntValue FRONTLINE_CAPTURE_TIME_SECONDS;
    public static final ModConfigSpec.IntValue FRONTLINE_DECAY_TIME_SECONDS;
    public static final ModConfigSpec.IntValue FRONTLINE_TICK_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue FRONTLINE_MIN_ADVANTAGE;
    public static final ModConfigSpec.BooleanValue FRONTLINE_CONTESTED_FREEZE;
    public static final ModConfigSpec.BooleanValue FRONTLINE_REQUIRE_ADJACENT_OWNER;
    public static final ModConfigSpec.BooleanValue FRONTLINE_ALLOW_NEUTRAL_OPENING;
    public static final ModConfigSpec.IntValue REGION_MAX_CHUNKS;
    public static final ModConfigSpec.BooleanValue FRONTLINE_BLUEMAP_ENABLED;
    public static final ModConfigSpec.IntValue FRONTLINE_BLUEMAP_SYNC_DEBOUNCE_TICKS;
    public static final ModConfigSpec.ConfigValue<String> FRONTLINE_BLUEMAP_MARKER_SET_ID;
    public static final ModConfigSpec.ConfigValue<String> FRONTLINE_BLUEMAP_MARKER_SET_LABEL;
    public static final ModConfigSpec.BooleanValue FRONTLINE_BLUEMAP_DEFAULT_HIDDEN;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FRONTLINE_BLUEMAP_DIMENSION_WORLD_OVERRIDES;
    public static final ModConfigSpec.IntValue FRONTLINE_BLUEMAP_FILL_ALPHA;
    public static final ModConfigSpec.IntValue FRONTLINE_BLUEMAP_LINE_ALPHA;
    public static final ModConfigSpec.IntValue FRONTLINE_BLUEMAP_LINE_WIDTH;
    public static final ModConfigSpec.BooleanValue FRONTLINE_JOURNEYMAP_ENABLED;
    public static final ModConfigSpec.IntValue FRONTLINE_JOURNEYMAP_FILL_ALPHA;
    public static final ModConfigSpec.IntValue FRONTLINE_JOURNEYMAP_BORDER_ALPHA;
    public static final ModConfigSpec.IntValue FRONTLINE_JOURNEYMAP_NEUTRAL_COLOR_RGB;
    public static final ModConfigSpec.IntValue FRONTLINE_JOURNEYMAP_REGION_BORDER_COLOR_RGB;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STARTUP_COMMANDS;
    public static final ModConfigSpec.BooleanValue GARAGE_ENABLED;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("general");
        DEBUG = b.comment("Включить подробные логи мода").define("debug", false);
        b.pop();

        b.push("hud");
        SQUAD_HUD = b.comment("Использовать Squad-style HUD").define("squadHud", true);
        ITEM_SWITCH_DISPLAY_MS = b.comment("Время отображения панели переключения предметов (мс)")
                .defineInRange("itemSwitchDisplayMs", 1500L, 0L, 60_000L);
        DISABLE_ARMOR = b.comment("Скрыть ванильную полоску брони").define("hideArmorBar", false);
        b.pop();

        b.push("milsim");
        DISABLE_HUNGER = b.comment("Отключить механику голода и скрыть полоску еды").define("disableHunger", false);
        b.pop();

        b.push("controlPoints");
        CAPTURE_ENABLED = b.comment("Включить старую систему контрольных точек").define("enabled", true);
        CAPTURE_TIME_SECONDS = b.comment("Время полного захвата точки (секунды)")
                .defineInRange("captureTimeSeconds", 30, 5, 600);
        b.pop();

        b.push("teams");
        TEAMS = b.comment(
                "Основной список id scoreboard-команд, которые участвуют в системах мода.",
                "Имя и цвет подтягиваются из самой scoreboard team.",
                "Пример настройки в игре: /team add team1; /team modify team1 displayName \"Белые\"; /team modify team1 color white",
                "Линия фронта, радио и другие системы берут команды отсюда."
        ).defineListAllowEmpty("definitions", List.of(
                "team1",
                "team2"
        ), o -> o instanceof String s && !s.isBlank());
        b.pop();

        b.push("region");
        REGION_MAX_CHUNKS = b.comment("Максимальный размер региона в чанках")
                .defineInRange("maxChunks", 4096, 1, 1_000_000);
        b.pop();

        b.push("frontline");
        FRONTLINE_ENABLED = b.comment("Включить линию фронта").define("enabled", true);
        FRONTLINE_HUD_ENABLED = b.comment("Показывать HUD линии фронта").define("hudEnabled", true);
        FRONTLINE_MANUAL_ACTIVE = b.comment("Разрешить захват. Можно менять командой /pjm frontline active <true|false>")
                .define("manualActive", true);

        b.push("schedule");
        FRONTLINE_USE_REAL_TIME_WINDOW = b.comment("Ограничить захват окном реального времени")
                .define("enabled", false);
        FRONTLINE_REAL_TIME_ZONE = b.comment(
                "Часовой пояс для окна захвата. Пример: Europe/Simferopol, Europe/Moscow, UTC",
                "Если значение неверное, будет использован системный часовой пояс сервера."
        ).define("timeZone", "Europe/Simferopol");
        FRONTLINE_REAL_TIME_START = b.comment("Начало окна захвата в формате HH:mm")
                .define("start", "18:00", Config::isValidTime);
        FRONTLINE_REAL_TIME_END = b.comment("Конец окна захвата в формате HH:mm")
                .define("end", "23:00", Config::isValidTime);
        b.pop();

        b.push("capture");
        FRONTLINE_CAPTURE_TIME_SECONDS = b.comment("Сколько секунд нужно для полного захвата сектора 3x3 при преимуществе в 1 игрока")
                .defineInRange("captureTimeSeconds", 45, 5, 3600);
        FRONTLINE_DECAY_TIME_SECONDS = b.comment("За сколько секунд пустой/заблокированный захват откатывается с 100% до 0%")
                .defineInRange("decayTimeSeconds", 30, 1, 3600);
        FRONTLINE_TICK_INTERVAL_TICKS = b.comment("Как часто сервер пересчитывает захват секторов, в тиках")
                .defineInRange("tickIntervalTicks", 20, 1, 200);
        FRONTLINE_MIN_ADVANTAGE = b.comment("Минимальное преимущество игроков для продвижения захвата. 1 значит 'белых больше красных'")
                .defineInRange("minAdvantage", 1, 1, 64);
        FRONTLINE_CONTESTED_FREEZE = b.comment("Если true, при равенстве сил прогресс замораживается; если false, медленно откатывается")
                .define("contestedFreeze", true);
        FRONTLINE_REQUIRE_ADJACENT_OWNER = b.comment("Требовать связь с соседней территорией своей команды для атаки")
                .define("requireAdjacentOwnedChunk", true);
        FRONTLINE_ALLOW_NEUTRAL_OPENING = b.comment("Разрешить стартовый захват нейтральных секторов без соседней своей территории")
                .define("allowNeutralOpeningCapture", true);
        b.pop();

        b.push("bluemap");
        FRONTLINE_BLUEMAP_ENABLED = b.comment("Включить интеграцию линии фронта с BlueMap")
                .define("enabled", true);
        FRONTLINE_BLUEMAP_SYNC_DEBOUNCE_TICKS = b.comment("Дебаунс синхронизации маркеров BlueMap, в тиках")
                .defineInRange("syncDebounceTicks", 40, 1, 20_000);
        FRONTLINE_BLUEMAP_MARKER_SET_ID = b.comment("ID marker-set в BlueMap")
                .define("markerSetId", "pjm_frontline", o -> o instanceof String s && !s.isBlank());
        FRONTLINE_BLUEMAP_MARKER_SET_LABEL = b.comment("Название marker-set в BlueMap")
                .define("markerSetLabel", "Линия фронта", o -> o instanceof String s && !s.isBlank());
        FRONTLINE_BLUEMAP_DEFAULT_HIDDEN = b.comment("Скрывать ли marker-set по умолчанию в UI BlueMap")
                .define("defaultHidden", false);
        FRONTLINE_BLUEMAP_DIMENSION_WORLD_OVERRIDES = b.comment(
                "Явные сопоставления minecraft dimension -> BlueMap worldId",
                "Формат строки: minecraft:overworld=world",
                "Используется, если авто-сопоставление не подходит."
        ).defineListAllowEmpty("dimensionWorldOverrides", List.of(), o -> o instanceof String);
        FRONTLINE_BLUEMAP_FILL_ALPHA = b.comment("Прозрачность заливки территорий 0..255")
                .defineInRange("fillAlpha", 96, 0, 255);
        FRONTLINE_BLUEMAP_LINE_ALPHA = b.comment("Прозрачность линий 0..255")
                .defineInRange("lineAlpha", 220, 0, 255);
        FRONTLINE_BLUEMAP_LINE_WIDTH = b.comment("Ширина контура marker shape")
                .defineInRange("lineWidth", 2, 1, 16);
        b.pop();

        b.push("journeymap");
        FRONTLINE_JOURNEYMAP_ENABLED = b.comment("Включить интеграцию линии фронта с JourneyMap")
                .define("enabled", true);
        FRONTLINE_JOURNEYMAP_FILL_ALPHA = b.comment("Прозрачность заливки территорий 0..255")
                .defineInRange("fillAlpha", 96, 0, 255);
        FRONTLINE_JOURNEYMAP_BORDER_ALPHA = b.comment("Прозрачность границ территорий/регионов 0..255")
                .defineInRange("borderAlpha", 220, 0, 255);
        FRONTLINE_JOURNEYMAP_NEUTRAL_COLOR_RGB = b.comment("RGB цвет нейтральных секторов без alpha (0xRRGGBB)")
                .defineInRange("neutralColorRgb", 0x9B9B9B, 0, 0xFFFFFF);
        FRONTLINE_JOURNEYMAP_REGION_BORDER_COLOR_RGB = b.comment("RGB цвет границ регионов без alpha")
                .defineInRange("regionBorderColorRgb", 0xFFFFFF, 0, 0xFFFFFF);
        b.pop();
        b.pop();

        b.push("garage");
        GARAGE_ENABLED = b.comment("Включить систему техники (виртуальный гараж и сборку)").define("enabled", true);
        b.pop();

        b.push("commands");
        STARTUP_COMMANDS = b.comment(
                "Команды, выполняемые на старте сервера. Пример: \"scoreboard objectives add kills playerKillCount\"",
                "Слэш в начале не обязателен."
        ).defineListAllowEmpty("startup", List.of(), o -> o instanceof String s && !s.isBlank());
        b.pop();

        SPEC = b.build();
    }

    public static boolean isSquadHud()                { return SQUAD_HUD.get(); }
    public static boolean isDebug()                   { return DEBUG.get(); }
    public static boolean isDisableHunger()           { return DISABLE_HUNGER.get(); }
    public static boolean isDisableArmor()            { return DISABLE_ARMOR.get(); }
    public static long    getItemSwitchDisplayTime()  { return ITEM_SWITCH_DISPLAY_MS.get(); }
    public static int     getCaptureTimeSeconds()     { return CAPTURE_TIME_SECONDS.get(); }
    public static boolean isCaptureSystemEnabled()    { return CAPTURE_ENABLED.get(); }
    public static boolean isFrontlineEnabled()         { return FRONTLINE_ENABLED.get(); }
    public static boolean isFrontlineHudEnabled()      { return FRONTLINE_HUD_ENABLED.get(); }
    public static boolean isFrontlineManualActive()    { return FRONTLINE_MANUAL_ACTIVE.get(); }
    public static boolean useFrontlineRealTimeWindow() { return FRONTLINE_USE_REAL_TIME_WINDOW.get(); }
    public static String  getFrontlineRealTimeZone()   { return FRONTLINE_REAL_TIME_ZONE.get(); }
    public static String  getFrontlineRealTimeStart()  { return FRONTLINE_REAL_TIME_START.get(); }
    public static String  getFrontlineRealTimeEnd()    { return FRONTLINE_REAL_TIME_END.get(); }
    public static int     getFrontlineCaptureTimeSeconds() { return FRONTLINE_CAPTURE_TIME_SECONDS.get(); }
    public static int     getFrontlineDecayTimeSeconds() { return FRONTLINE_DECAY_TIME_SECONDS.get(); }
    public static int     getFrontlineTickIntervalTicks() { return FRONTLINE_TICK_INTERVAL_TICKS.get(); }
    public static int     getFrontlineMinAdvantage()   { return FRONTLINE_MIN_ADVANTAGE.get(); }
    public static boolean isFrontlineContestedFreeze() { return FRONTLINE_CONTESTED_FREEZE.get(); }
    public static boolean isFrontlineRequireAdjacentOwner() { return FRONTLINE_REQUIRE_ADJACENT_OWNER.get(); }
    public static boolean isFrontlineAllowNeutralOpening() { return FRONTLINE_ALLOW_NEUTRAL_OPENING.get(); }
    public static int     getRegionMaxChunks(){ return REGION_MAX_CHUNKS.get(); }
    public static boolean isFrontlineBlueMapEnabled() { return FRONTLINE_BLUEMAP_ENABLED.get(); }
    public static int getFrontlineBlueMapSyncDebounceTicks() { return FRONTLINE_BLUEMAP_SYNC_DEBOUNCE_TICKS.get(); }
    public static String getFrontlineBlueMapMarkerSetId() { return FRONTLINE_BLUEMAP_MARKER_SET_ID.get(); }
    public static String getFrontlineBlueMapMarkerSetLabel() { return FRONTLINE_BLUEMAP_MARKER_SET_LABEL.get(); }
    public static boolean isFrontlineBlueMapDefaultHidden() { return FRONTLINE_BLUEMAP_DEFAULT_HIDDEN.get(); }
    public static List<? extends String> getFrontlineBlueMapDimensionWorldOverrides() { return FRONTLINE_BLUEMAP_DIMENSION_WORLD_OVERRIDES.get(); }
    public static int getFrontlineBlueMapFillAlpha() { return FRONTLINE_BLUEMAP_FILL_ALPHA.get(); }
    public static int getFrontlineBlueMapLineAlpha() { return FRONTLINE_BLUEMAP_LINE_ALPHA.get(); }
    public static int getFrontlineBlueMapLineWidth() { return FRONTLINE_BLUEMAP_LINE_WIDTH.get(); }
    public static boolean isFrontlineJourneyMapEnabled() { return FRONTLINE_JOURNEYMAP_ENABLED.get(); }
    public static int getFrontlineJourneyMapFillAlpha() { return FRONTLINE_JOURNEYMAP_FILL_ALPHA.get(); }
    public static int getFrontlineJourneyMapBorderAlpha() { return FRONTLINE_JOURNEYMAP_BORDER_ALPHA.get(); }
    public static int getFrontlineJourneyMapNeutralColorRgb() { return FRONTLINE_JOURNEYMAP_NEUTRAL_COLOR_RGB.get(); }
    public static int getFrontlineJourneyMapRegionBorderColorRgb() { return FRONTLINE_JOURNEYMAP_REGION_BORDER_COLOR_RGB.get(); }
    public static List<? extends String> getStartupCommands() { return STARTUP_COMMANDS.get(); }
    public static boolean isGarageEnabled() { return GARAGE_ENABLED.get(); }

    public static List<ConfiguredTeam> getTeams() {
        return parseTeams(TEAMS.get());
    }

    public static List<ConfiguredTeam> getFrontlineTeams() {
        return getTeams();
    }

    private static List<ConfiguredTeam> parseTeams(List<? extends String> rawTeams) {
        List<ConfiguredTeam> teams = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : rawTeams) {
            String id = parseTeamId(raw);
            if (!id.isBlank() && seen.add(id)) teams.add(new ConfiguredTeam(id));
        }
        if (teams.isEmpty()) {
            teams.add(new ConfiguredTeam("team1"));
            teams.add(new ConfiguredTeam("team2"));
        }
        return List.copyOf(teams);
    }

    public static String getTeam1Name() { return getFrontlineTeams().getFirst().id(); }
    public static String getTeam2Name() {
        List<ConfiguredTeam> teams = getFrontlineTeams();
        return teams.size() > 1 ? teams.get(1).id() : "team2";
    }

    private static String parseTeamId(String raw) {
        if (raw == null) return "";
        String id = raw.trim();
        int legacySeparator = id.indexOf('|');
        if (legacySeparator >= 0) id = id.substring(0, legacySeparator).trim();
        return sanitizeId(id);
    }

    private static String sanitizeId(String raw) {
        String id = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (id.isBlank()) return "";
        return id.replaceAll("[^a-z0-9_\\-]", "_");
    }

    private static boolean isValidTime(Object value) {
        if (!(value instanceof String raw)) return false;
        return parseTimeToMinute(raw) >= 0;
    }

    public static int parseTimeToMinute(String raw) {
        if (raw == null) return -1;
        String[] parts = raw.trim().split(":");
        if (parts.length != 2) return -1;
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return -1;
            return hour * 60 + minute;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public static Map<String, String> parseFrontlineBlueMapDimensionWorldOverrides() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String raw : getFrontlineBlueMapDimensionWorldOverrides()) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isBlank()) continue;
            int split = line.indexOf('=');
            if (split <= 0 || split >= line.length() - 1) continue;
            String dimensionId = normalizeDimensionId(line.substring(0, split));
            String worldId = line.substring(split + 1).trim();
            if (dimensionId.isBlank() || worldId.isBlank()) continue;
            result.put(dimensionId, worldId);
        }
        return Map.copyOf(result);
    }

    private static String normalizeDimensionId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record ConfiguredTeam(String id) {}
}
