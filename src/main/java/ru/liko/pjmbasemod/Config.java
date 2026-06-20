package ru.liko.pjmbasemod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Конфиг мода в формате JSON: {@code config/pjmbasemod/config.json}.
 *
 * <p>Раньше использовался NeoForge {@link net.neoforged.fml.config.ModConfig ModConfigSpec} (TOML
 * в корне {@code config/}), который при каждой нормализации значений перезаписывал файл и лежал
 * отдельно от остальных JSON-реестров мода. Теперь конфиг — обычный Gson-JSON рядом с прочими
 * (ranks.json, roles/, vehicles.json …): пишется только при отсутствии файла, перезагружается
 * через {@code /pjm config reload}.</p>
 *
 * <p>Публичный API (статические геттеры) сохранён один-в-один — места вызова не меняются.</p>
 */
public final class Config {

    private Config() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static volatile ConfigData data;

    // ---------------------------------------------------------------- загрузка / перезагрузка

    private static synchronized ConfigData data() {
        if (data == null) reload();
        return data;
    }

    /** Перезагружает конфиг из {@code config/pjmbasemod/config.json}. Создаёт файл с дефолтами, если его нет. */
    public static synchronized boolean reload() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                data = new ConfigData();
                data.normalize();
                write(file, data);
                Pjmbasemod.LOGGER.info("Config: создан конфиг по умолчанию {}", file);
                return true;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
                data = loaded == null ? new ConfigData() : loaded;
                data.normalize();
                Pjmbasemod.LOGGER.info("Config: загружен {}", file);
                return true;
            }
        } catch (Exception e) {
            data = new ConfigData();
            data.normalize();
            Pjmbasemod.LOGGER.error("Config: не удалось загрузить {}, используются значения по умолчанию.", file, e);
            return false;
        }
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(Pjmbasemod.MODID).resolve("config.json");
    }

    private static void write(Path file, ConfigData data) throws Exception {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        }
    }

    // ---------------------------------------------------------------- геттеры (публичный API)

    public static boolean isSquadHud()                { return data().hud.squadHud; }
    public static boolean isDebug()                   { return data().general.debug; }
    public static boolean isDisableHunger()           { return data().hud.disableHunger; }
    public static boolean isDisableArmor()            { return data().hud.hideArmorBar; }
    public static long    getItemSwitchDisplayTime()  { return data().hud.itemSwitchDisplayMs; }
    public static boolean isFrontlineEnabled()         { return data().frontline.enabled; }
    public static boolean isFrontlineHudEnabled()      { return data().frontline.hudEnabled; }
    public static boolean isFrontlineManualActive()    { return data().frontline.manualActive; }
    public static boolean useFrontlineRealTimeWindow() { return data().frontline.schedule.enabled; }
    public static String  getFrontlineRealTimeZone()   { return data().frontline.schedule.timeZone; }
    public static String  getFrontlineRealTimeStart()  { return data().frontline.schedule.start; }
    public static String  getFrontlineRealTimeEnd()    { return data().frontline.schedule.end; }
    public static int     getFrontlineCaptureTimeSeconds() { return data().frontline.capture.captureTimeSeconds; }
    public static int     getFrontlineDecayTimeSeconds() { return data().frontline.capture.decayTimeSeconds; }
    public static int     getFrontlineTickIntervalTicks() { return data().frontline.capture.tickIntervalTicks; }
    public static int     getFrontlineMinAdvantage()   { return data().frontline.capture.minAdvantage; }
    public static boolean isFrontlineContestedFreeze() { return data().frontline.capture.contestedFreeze; }
    public static boolean isFrontlineRequireAdjacentOwner() { return data().frontline.capture.requireAdjacentOwnedChunk; }
    public static boolean isFrontlineAllowNeutralOpening() { return data().frontline.capture.allowNeutralOpeningCapture; }
    public static int     getRegionMaxChunks(){ return data().region.maxChunks; }
    public static boolean isFrontlineBlueMapEnabled() { return data().frontline.bluemap.enabled; }
    public static int getFrontlineBlueMapSyncDebounceTicks() { return data().frontline.bluemap.syncDebounceTicks; }
    public static String getFrontlineBlueMapMarkerSetId() { return data().frontline.bluemap.markerSetId; }
    public static String getFrontlineBlueMapMarkerSetLabel() { return data().frontline.bluemap.markerSetLabel; }
    public static boolean isFrontlineBlueMapDefaultHidden() { return data().frontline.bluemap.defaultHidden; }
    public static List<? extends String> getFrontlineBlueMapDimensionWorldOverrides() { return data().frontline.bluemap.dimensionWorldOverrides; }
    public static int getFrontlineBlueMapFillAlpha() { return data().frontline.bluemap.fillAlpha; }
    public static int getFrontlineBlueMapLineAlpha() { return data().frontline.bluemap.lineAlpha; }
    public static int getFrontlineBlueMapLineWidth() { return data().frontline.bluemap.lineWidth; }
    public static int getFrontlineBlueMapMarkerHeight() { return data().frontline.bluemap.markerHeight; }
    public static boolean isFrontlineBlueMapDepthTest() { return data().frontline.bluemap.depthTest; }
    public static boolean isFrontlineJourneyMapEnabled() { return data().frontline.journeymap.enabled; }
    public static int getFrontlineJourneyMapFillAlpha() { return data().frontline.journeymap.fillAlpha; }
    public static int getFrontlineJourneyMapBorderAlpha() { return data().frontline.journeymap.borderAlpha; }
    public static int getFrontlineJourneyMapNeutralColorRgb() { return data().frontline.journeymap.neutralColorRgb; }
    public static int getFrontlineJourneyMapRegionBorderColorRgb() { return data().frontline.journeymap.regionBorderColorRgb; }
    public static List<? extends String> getStartupCommands() { return data().commands.startup; }
    public static boolean isGarageEnabled() { return data().garage.enabled; }
    public static int getFactionMaxDeputies()            { return data().faction.maxDeputies; }
    public static int getFactionOrderMaxLength()         { return data().faction.orderMaxLength; }
    public static int getFactionOrderDefaultTtlMinutes() { return data().faction.orderDefaultTtlMinutes; }
    public static int getFactionOrderMaxTtlMinutes()     { return data().faction.orderMaxTtlMinutes; }

    public static List<ConfiguredTeam> getTeams() {
        return parseTeams(data().teams.definitions);
    }

    public static List<ConfiguredTeam> getFrontlineTeams() {
        return getTeams();
    }

    private static List<ConfiguredTeam> parseTeams(List<? extends String> rawTeams) {
        List<ConfiguredTeam> teams = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (rawTeams != null) {
            for (String raw : rawTeams) {
                String id = parseTeamId(raw);
                if (!id.isBlank() && seen.add(id)) teams.add(new ConfiguredTeam(id));
            }
        }
        if (teams.isEmpty()) {
            teams.add(new ConfiguredTeam("team1"));
            teams.add(new ConfiguredTeam("team2"));
        }
        return List.copyOf(teams);
    }

    /**
     * Команды, выполняемые при выборе указанной фракции, в порядке объявления в конфиге.
     * teamId сравнивается без учёта регистра. Возвращает пустой список, если ничего не настроено.
     */
    public static List<String> getTeamJoinCommands(String teamId) {
        if (teamId == null || teamId.isBlank()) return List.of();
        String target = teamId.trim().toLowerCase(Locale.ROOT);
        List<String> commands = new ArrayList<>();
        for (String raw : data().teams.joinCommands) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isBlank()) continue;
            int split = indexOfWhitespace(line);
            if (split <= 0) continue;
            String id = line.substring(0, split).trim().toLowerCase(Locale.ROOT);
            String command = line.substring(split + 1).trim();
            if (command.isBlank() || !id.equals(target)) continue;
            commands.add(command);
        }
        return List.copyOf(commands);
    }

    private static int indexOfWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return i;
        }
        return -1;
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

    // ---------------------------------------------------------------- модель данных (Gson)

    private static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    private static long clamp(long value, long min, long max) {
        return value < min ? min : Math.min(value, max);
    }

    /**
     * Структура JSON-конфига. Значения по умолчанию заданы инициализаторами полей —
     * отсутствующие в файле ключи остаются дефолтными (Gson их не трогает).
     */
    static final class ConfigData {
        General general = new General();
        Hud hud = new Hud();
        Teams teams = new Teams();
        Region region = new Region();
        Frontline frontline = new Frontline();
        Garage garage = new Garage();
        Faction faction = new Faction();
        Commands commands = new Commands();

        /** Заменяет null-секции дефолтами и зажимает числовые значения в допустимые диапазоны. */
        void normalize() {
            if (general == null) general = new General();
            if (hud == null) hud = new Hud();
            if (teams == null) teams = new Teams();
            if (region == null) region = new Region();
            if (frontline == null) frontline = new Frontline();
            if (garage == null) garage = new Garage();
            if (faction == null) faction = new Faction();
            faction.maxDeputies = clamp(faction.maxDeputies, 0, 64);
            faction.orderMaxLength = clamp(faction.orderMaxLength, 1, 256);
            faction.orderMaxTtlMinutes = clamp(faction.orderMaxTtlMinutes, 0, 10_080);
            faction.orderDefaultTtlMinutes = clamp(faction.orderDefaultTtlMinutes, 0, faction.orderMaxTtlMinutes);
            if (commands == null) commands = new Commands();

            if (teams.definitions == null) teams.definitions = new ArrayList<>();
            if (teams.joinCommands == null) teams.joinCommands = new ArrayList<>();
            if (commands.startup == null) commands.startup = new ArrayList<>();

            hud.itemSwitchDisplayMs = clamp(hud.itemSwitchDisplayMs, 0L, 60_000L);
            region.maxChunks = clamp(region.maxChunks, 1, 1_000_000);

            frontline.normalize();
        }
    }

    static final class General {
        boolean debug = false;
    }

    static final class Hud {
        boolean squadHud = true;
        long itemSwitchDisplayMs = 1500L;
        boolean hideArmorBar = false;
        boolean disableHunger = false;
    }

    static final class Teams {
        List<String> definitions = new ArrayList<>(List.of("team1", "team2"));
        List<String> joinCommands = new ArrayList<>();
    }

    static final class Region {
        int maxChunks = 4096;
    }

    static final class Frontline {
        boolean enabled = true;
        boolean hudEnabled = true;
        boolean manualActive = true;
        Schedule schedule = new Schedule();
        Capture capture = new Capture();
        BlueMap bluemap = new BlueMap();
        JourneyMap journeymap = new JourneyMap();

        void normalize() {
            if (schedule == null) schedule = new Schedule();
            if (capture == null) capture = new Capture();
            if (bluemap == null) bluemap = new BlueMap();
            if (journeymap == null) journeymap = new JourneyMap();

            capture.captureTimeSeconds = clamp(capture.captureTimeSeconds, 5, 3600);
            capture.decayTimeSeconds = clamp(capture.decayTimeSeconds, 1, 3600);
            capture.tickIntervalTicks = clamp(capture.tickIntervalTicks, 1, 200);
            capture.minAdvantage = clamp(capture.minAdvantage, 1, 64);

            bluemap.syncDebounceTicks = clamp(bluemap.syncDebounceTicks, 1, 20_000);
            bluemap.fillAlpha = clamp(bluemap.fillAlpha, 0, 255);
            bluemap.lineAlpha = clamp(bluemap.lineAlpha, 0, 255);
            bluemap.lineWidth = clamp(bluemap.lineWidth, 1, 16);
            bluemap.markerHeight = clamp(bluemap.markerHeight, -64, 512);
            if (bluemap.dimensionWorldOverrides == null) bluemap.dimensionWorldOverrides = new ArrayList<>();

            journeymap.fillAlpha = clamp(journeymap.fillAlpha, 0, 255);
            journeymap.borderAlpha = clamp(journeymap.borderAlpha, 0, 255);
            journeymap.neutralColorRgb = clamp(journeymap.neutralColorRgb, 0, 0xFFFFFF);
            journeymap.regionBorderColorRgb = clamp(journeymap.regionBorderColorRgb, 0, 0xFFFFFF);
        }
    }

    static final class Schedule {
        boolean enabled = false;
        String timeZone = "Europe/Simferopol";
        String start = "18:00";
        String end = "23:00";
    }

    static final class Capture {
        int captureTimeSeconds = 45;
        int decayTimeSeconds = 30;
        int tickIntervalTicks = 20;
        int minAdvantage = 1;
        boolean contestedFreeze = true;
        boolean requireAdjacentOwnedChunk = true;
        boolean allowNeutralOpeningCapture = true;
    }

    static final class BlueMap {
        boolean enabled = true;
        int syncDebounceTicks = 40;
        String markerSetId = "pjm_frontline";
        String markerSetLabel = "Линия фронта";
        boolean defaultHidden = false;
        List<String> dimensionWorldOverrides = new ArrayList<>();
        int fillAlpha = 96;
        int lineAlpha = 220;
        int lineWidth = 2;
        int markerHeight = 80;
        boolean depthTest = false;
    }

    static final class JourneyMap {
        boolean enabled = true;
        int fillAlpha = 96;
        int borderAlpha = 220;
        int neutralColorRgb = 0x9B9B9B;
        int regionBorderColorRgb = 0xFFFFFF;
    }

    static final class Garage {
        boolean enabled = true;
    }

    static final class Faction {
        int maxDeputies = 3;
        int orderMaxLength = 120;
        int orderDefaultTtlMinutes = 30;
        int orderMaxTtlMinutes = 240;
    }

    static final class Commands {
        List<String> startup = new ArrayList<>();
    }
}
