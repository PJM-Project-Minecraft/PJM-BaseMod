package ru.liko.pjmbasemod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import javax.annotation.Nullable;

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
 * (ranks.json, roles/, vehicles.json …), перезагружается через {@code /pjm config reload}.</p>
 *
 * <p><b>Миграция при обновлении мода.</b> При загрузке уже существующего файла новые поля/секции
 * (добавленные в новой версии мода) домёрдживаются в файл с дефолтными значениями, а всё, что
 * администратор уже вписал, сохраняется — Gson оставляет отсутствующие ключи дефолтными, после
 * чего модель пишется обратно. Файл переписывается только если состав/значения реально изменились
 * (иначе диск не трогается). Незнакомые (удалённые из модели) ключи при этом отбрасываются.</p>
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
            String original = Files.readString(file, StandardCharsets.UTF_8);
            ConfigData loaded = GSON.fromJson(original, ConfigData.class);
            data = loaded == null ? new ConfigData() : loaded;
            data.normalize();
            // Домёрдживаем новые поля/секции, не теряя внесённых значений: сериализуем актуальную
            // модель (загруженные значения + дефолты для отсутствовавших ключей) и переписываем файл,
            // только если результат отличается от того, что на диске.
            String merged = GSON.toJson(data);
            if (!merged.strip().equals(original.strip())) {
                write(file, data);
                Pjmbasemod.LOGGER.info("Config: обновлён (домёрджены новые поля) {}", file);
            } else {
                Pjmbasemod.LOGGER.info("Config: загружен {}", file);
            }
            return true;
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
    public static boolean isWelcomeGuideEnabled()     { return data().general.welcomeGuideEnabled; }
    public static boolean isGrassClickThroughEnabled(){ return data().general.grassClickThrough; }
    public static boolean isDisableHunger()           { return data().hud.disableHunger; }
    public static boolean isDisableArmor()            { return data().hud.hideArmorBar; }
    public static long    getItemSwitchDisplayTime()  { return data().hud.itemSwitchDisplayMs; }
    public static List<? extends String> getStartupCommands() { return data().commands.startup; }
    public static boolean isGarageEnabled() { return data().garage.enabled; }
    public static boolean isFleetEnabled()                 { return data().fleet.enabled; }
    public static int getFleetMaxActivePerTeam()           { return data().fleet.maxActivePerTeam; }
    public static int getFleetMaxActivePerPlayer()         { return data().fleet.maxActivePerPlayer; }
    public static int getFleetAviationMaxActivePerTeam()   { return data().fleet.aviationMaxActivePerTeam; }
    public static int getFleetAviationMaxActivePerPlayer() { return data().fleet.aviationMaxActivePerPlayer; }
    public static int getFleetSpawnCooldownSeconds()       { return data().fleet.spawnCooldownSeconds; }
    public static int getFleetAbandonTimeoutSeconds()      { return data().fleet.abandonTimeoutSeconds; }
    public static int getFleetAbandonWarningSeconds()      { return data().fleet.abandonWarningSeconds; }
    public static int getFactionMaxDeputies()            { return data().faction.maxDeputies; }
    public static int getFactionOrderMaxLength()         { return data().faction.orderMaxLength; }
    public static int getFactionOrderDefaultTtlMinutes() { return data().faction.orderDefaultTtlMinutes; }
    public static int getFactionOrderMaxTtlMinutes()     { return data().faction.orderMaxTtlMinutes; }
    public static int getFactionInviteTtlMinutes()       { return data().faction.inviteTtlMinutes; }
    public static boolean isAntiGriefEnabled()           { return data().antigrief.enabled; }
    public static boolean isAntiGriefExemptCreative()    { return data().antigrief.exemptCreative; }
    public static int getAntiGriefBypassPermissionLevel(){ return data().antigrief.bypassPermissionLevel; }
    public static List<? extends String> getAntiGriefAllowedBreakBlocks()    { return data().antigrief.allowedBreakBlocks; }
    public static List<? extends String> getAntiGriefAllowedInteractBlocks() { return data().antigrief.allowedInteractBlocks; }
    public static List<? extends String> getAntiGriefAllowedPlaceBlocks()    { return data().antigrief.allowedPlaceBlocks; }
    public static boolean isBaseZoneEnabled()          { return data().baseZone.enabled; }
    public static int     getBaseZoneCountdownSeconds() { return data().baseZone.countdownSeconds; }
    public static boolean isBaseZoneBlockExplosions()   { return data().baseZone.blockExplosions; }

    public static boolean isNightDarknessEnabled()      { return data().nightDarkness.enabled; }
    public static double  getNightDarknessIntensity()   { return data().nightDarkness.intensity; }

    public static boolean isEventsEnabled()                 { return data().events.enabled; }
    public static int     getEventsMinIntervalMinutes()     { return data().events.minIntervalMinutes; }
    public static int     getEventsMaxIntervalMinutes()     { return data().events.maxIntervalMinutes; }
    public static boolean isEventsRequireCaptureInactive()  { return data().events.requireCaptureInactive; }
    public static int     getEventsStartDelaySeconds()      { return data().events.startDelaySeconds; }
    public static double  getEventsSpawnChance()            { return data().events.spawnChance; }

    /** Веса типов событий для случайного выбора. Пустая карта = все типы равновероятны. */
    public static Map<String, Double> getEventTypeWeights() {
        Map<String, Double> weights = data().events.typeWeights;
        return weights == null ? Map.of() : weights;
    }

    public static int getWeaponsMaxPrimary()        { return data().weapons.maxPrimary; }
    public static int getWeaponsMaxSecondary()      { return data().weapons.maxSecondary; }
    public static int getWeaponsMaxSuperbWarfare()  { return data().weapons.maxSuperbWarfare; }

    /** Является ли тип ствола TACZ вторичным (см. {@link Weapons#secondaryGunTypes}). */
    public static boolean isSecondaryGunType(String gunType) {
        if (gunType == null || gunType.isBlank()) return false;
        return data().weapons.secondaryGunTypes.contains(gunType.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isCapturePointsEnabled()          { return data().capturePoints.enabled; }
    public static int     getCapturePointCaptureTimeSeconds() { return data().capturePoints.captureTimeSeconds; }
    public static int     getCapturePointDecayTimeSeconds()    { return data().capturePoints.decayTimeSeconds; }
    public static int     getCapturePointTickIntervalTicks()   { return data().capturePoints.tickIntervalTicks; }
    public static int     getCapturePointMinAdvantage()        { return data().capturePoints.minAdvantage; }
    public static boolean isCapturePointContestedFreeze()      { return data().capturePoints.contestedFreeze; }
    public static boolean isCapturePointSequential()           { return data().capturePoints.sequential; }
    public static boolean isCapturePointJourneyMapEnabled()    { return data().capturePoints.journeymapEnabled; }
    public static int     getCapturePointJourneyMapFillAlpha()   { return data().capturePoints.journeymapFillAlpha; }
    public static int     getCapturePointJourneyMapBorderAlpha() { return data().capturePoints.journeymapBorderAlpha; }
    public static int     getCapturePointJourneyMapNeutralColorRgb() { return data().capturePoints.journeymapNeutralColorRgb; }
    public static boolean isCapturePointScheduleEnabled()      { return data().capturePoints.scheduleEnabled; }
    public static boolean isCapturePointIncomeEnabled()        { return data().capturePoints.incomeEnabled; }
    public static int     getCapturePointIncomePerPointPerMinute() { return data().capturePoints.incomePerPointPerMinute; }

    /** Склад команды, куда капает доход с точек. Ключ — id команды. */
    public static Map<String, String> getCapturePointWarehouseByTeam() {
        Map<String, String> map = data().capturePoints.warehouseByTeam;
        return map == null ? Map.of() : map;
    }

    /** Окна расписания захвата в неизменяемом виде (внутренняя модель наружу не отдаётся). */
    public static List<ScheduleWindow> getCapturePointScheduleWindows() {
        List<ScheduleWindow> result = new ArrayList<>();
        for (ScheduleWindowData w : data().capturePoints.scheduleWindows) {
            result.add(new ScheduleWindow(w.startHour, w.startMinute, w.endHour, w.endMinute));
        }
        return result;
    }

    // Сеттеры пишут файл сразу: правки приходят из команд/веб-панели, а не из редактирования JSON

    public static synchronized void setCapturePointsEnabled(boolean enabled) {
        data().capturePoints.enabled = enabled;
        persist();
    }

    public static synchronized void setCapturePointScheduleEnabled(boolean enabled) {
        data().capturePoints.scheduleEnabled = enabled;
        persist();
    }

    @Nullable
    public static Boolean getCapturePointScheduleLastState() { return data().capturePoints.scheduleLastState; }

    public static synchronized void setCapturePointScheduleLastState(boolean state) {
        data().capturePoints.scheduleLastState = state;
        persist();
    }

    public static synchronized void addCapturePointScheduleWindow(int startHour, int startMinute, int endHour, int endMinute) {
        ScheduleWindowData w = new ScheduleWindowData();
        w.startHour = startHour;
        w.startMinute = startMinute;
        w.endHour = endHour;
        w.endMinute = endMinute;
        w.normalize();
        data().capturePoints.scheduleWindows.add(w);
        persist();
    }

    public static synchronized boolean removeCapturePointScheduleWindow(int index) {
        List<ScheduleWindowData> windows = data().capturePoints.scheduleWindows;
        if (index < 0 || index >= windows.size()) return false;
        windows.remove(index);
        persist();
        return true;
    }

    public static synchronized void clearCapturePointScheduleWindows() {
        data().capturePoints.scheduleWindows.clear();
        persist();
    }

    private static void persist() {
        try {
            write(file(), data);
        } catch (Exception e) {
            Pjmbasemod.LOGGER.error("Config: не удалось сохранить {}", file(), e);
        }
    }

    public static boolean isModerationOverrideVanilla()      { return data().moderation.overrideVanillaCommands; }
    public static int  getModerationDefaultTempBanMinutes()  { return data().moderation.defaultTempBanMinutes; }
    public static int  getModerationDefaultMuteMinutes()     { return data().moderation.defaultMuteMinutes; }
    public static boolean isModerationBroadcast()            { return data().moderation.broadcastPunishments; }
    public static int  getModerationWarnDecayDays()          { return data().moderation.warnDecayDays; }
    public static List<? extends String> getModerationWarnEscalationRaw() { return data().moderation.warnEscalation; }

    public static boolean isLoggingEnabled()       { return data().logging.enabled; }

    public static boolean isWebEnabled()           { return data().web.enabled; }
    public static int     getWebPort()             { return data().web.port; }
    public static String  getWebBindAddress()      { return data().web.bindAddress; }
    public static int     getWebSessionTtlMinutes(){ return data().web.sessionTtlMinutes; }
    public static int     getWebHistoryMinutes()   { return data().web.historyMinutes; }
    public static boolean isWebProfilerAllowed()   { return data().web.profilerEnabled; }
    public static String  getWebPublicUrl()        { return data().web.publicUrl; }

    public static List<ConfiguredTeam> getTeams() {
        return parseTeams(data().teams.definitions);
    }

    /** Фракция «по приглашению»: вступить может только приглашённый игрок. */
    public static boolean isTeamInviteOnly(String teamId) {
        if (teamId == null || teamId.isBlank()) return false;
        for (String raw : data().teams.inviteOnly) {
            if (raw != null && sanitizeId(raw).equalsIgnoreCase(teamId.trim())) return true;
        }
        return false;
    }

    public static boolean isTeamBalancerEnabled()      { return data().teams.balancer.enabled; }
    public static int     getTeamBalancerMaxShare()    { return data().teams.balancer.maxSharePercent; }
    public static int     getTeamBalancerMinPlayers()  { return data().teams.balancer.minPlayers; }

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
            teams.add(new ConfiguredTeam("team3"));
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

    public static String getTeam1Name() { return getTeams().getFirst().id(); }
    public static String getTeam2Name() {
        List<ConfiguredTeam> teams = getTeams();
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
        Garage garage = new Garage();
        Fleet fleet = new Fleet();
        Faction faction = new Faction();
        AntiGrief antigrief = new AntiGrief();
        Moderation moderation = new Moderation();
        Web web = new Web();
        Logging logging = new Logging();
        BaseZone baseZone = new BaseZone();
        NightDarkness nightDarkness = new NightDarkness();
        Commands commands = new Commands();
        Events events = new Events();
        CapturePoints capturePoints = new CapturePoints();
        Weapons weapons = new Weapons();

        /** Заменяет null-секции дефолтами и зажимает числовые значения в допустимые диапазоны. */
        void normalize() {
            if (general == null) general = new General();
            if (hud == null) hud = new Hud();
            if (teams == null) teams = new Teams();
            if (garage == null) garage = new Garage();
            if (fleet == null) fleet = new Fleet();
            fleet.maxActivePerTeam = fleet.maxActivePerTeam < 0 ? -1 : clamp(fleet.maxActivePerTeam, 0, 4096);
            fleet.maxActivePerPlayer = fleet.maxActivePerPlayer < 0 ? -1 : clamp(fleet.maxActivePerPlayer, 0, 4096);
            fleet.aviationMaxActivePerTeam = fleet.aviationMaxActivePerTeam < 0 ? -1 : clamp(fleet.aviationMaxActivePerTeam, 0, 4096);
            fleet.aviationMaxActivePerPlayer = fleet.aviationMaxActivePerPlayer < 0 ? -1 : clamp(fleet.aviationMaxActivePerPlayer, 0, 4096);
            fleet.spawnCooldownSeconds = clamp(fleet.spawnCooldownSeconds, 0, 86_400);
            fleet.abandonTimeoutSeconds = clamp(fleet.abandonTimeoutSeconds, 5, 86_400);
            fleet.abandonWarningSeconds = clamp(fleet.abandonWarningSeconds, 0, 3_600);
            if (faction == null) faction = new Faction();
            faction.maxDeputies = clamp(faction.maxDeputies, 0, 64);
            faction.orderMaxLength = clamp(faction.orderMaxLength, 1, 256);
            faction.orderMaxTtlMinutes = clamp(faction.orderMaxTtlMinutes, 0, 10_080);
            faction.orderDefaultTtlMinutes = clamp(faction.orderDefaultTtlMinutes, 0, faction.orderMaxTtlMinutes);
            faction.inviteTtlMinutes = clamp(faction.inviteTtlMinutes, 0, 43_200);
            if (antigrief == null) antigrief = new AntiGrief();
            antigrief.bypassPermissionLevel = clamp(antigrief.bypassPermissionLevel, 0, 4);
            if (antigrief.allowedBreakBlocks == null) antigrief.allowedBreakBlocks = new ArrayList<>();
            if (antigrief.allowedInteractBlocks == null) antigrief.allowedInteractBlocks = new ArrayList<>();
            if (antigrief.allowedPlaceBlocks == null) antigrief.allowedPlaceBlocks = new ArrayList<>();
            if (moderation == null) moderation = new Moderation();
            moderation.defaultTempBanMinutes = clamp(moderation.defaultTempBanMinutes, 1, 5_256_000);
            moderation.defaultMuteMinutes = clamp(moderation.defaultMuteMinutes, 1, 5_256_000);
            moderation.warnDecayDays = clamp(moderation.warnDecayDays, 0, 3650);
            if (moderation.warnEscalation == null) moderation.warnEscalation = new ArrayList<>();
            if (web == null) web = new Web();
            web.port = clamp(web.port, 1, 65_535);
            web.sessionTtlMinutes = clamp(web.sessionTtlMinutes, 5, 43_200);
            web.historyMinutes = clamp(web.historyMinutes, 5, 1_440);
            if (web.bindAddress == null || web.bindAddress.isBlank()) web.bindAddress = "0.0.0.0";
            if (web.publicUrl == null) web.publicUrl = "";
            if (logging == null) logging = new Logging();
            if (baseZone == null) baseZone = new BaseZone();
            baseZone.countdownSeconds = clamp(baseZone.countdownSeconds, 1, 60);
            if (nightDarkness == null) nightDarkness = new NightDarkness();
            nightDarkness.intensity = nightDarkness.intensity < 0.0 ? 0.0 : Math.min(nightDarkness.intensity, 1.0);
            if (commands == null) commands = new Commands();
            if (events == null) events = new Events();
            events.minIntervalMinutes = clamp(events.minIntervalMinutes, 1, 10_080);
            events.maxIntervalMinutes = clamp(events.maxIntervalMinutes, events.minIntervalMinutes, 10_080);
            events.startDelaySeconds = clamp(events.startDelaySeconds, 0, 3600);
            events.spawnChance = events.spawnChance < 0.0 ? 0.0 : Math.min(events.spawnChance, 1.0);
            if (events.typeWeights == null) events.typeWeights = new LinkedHashMap<>();

            if (capturePoints == null) capturePoints = new CapturePoints();
            capturePoints.normalize();

            if (weapons == null) weapons = new Weapons();
            weapons.normalize();

            if (teams.definitions == null) teams.definitions = new ArrayList<>();
            if (teams.joinCommands == null) teams.joinCommands = new ArrayList<>();
            if (teams.inviteOnly == null) teams.inviteOnly = new ArrayList<>();
            if (teams.balancer == null) teams.balancer = new Balancer();
            teams.balancer.maxSharePercent = clamp(teams.balancer.maxSharePercent, 50, 100);
            teams.balancer.minPlayers = clamp(teams.balancer.minPlayers, 0, 256);
            if (commands.startup == null) commands.startup = new ArrayList<>();

            hud.itemSwitchDisplayMs = clamp(hud.itemSwitchDisplayMs, 0L, 60_000L);
        }
    }

    static final class General {
        boolean debug = false;
        /** Показывать анимированное руководство по серверу при входе игрока (клиентский экран). */
        boolean welcomeGuideEnabled = true;
        /** Трава (short_grass/fern/tall_grass/large_fern) не таргетится в выживании: клики проходят сквозь неё. */
        boolean grassClickThrough = true;
    }

    static final class Hud {
        boolean squadHud = true;
        long itemSwitchDisplayMs = 1500L;
        boolean hideArmorBar = false;
        boolean disableHunger = false;
    }

    static final class Teams {
        List<String> definitions = new ArrayList<>(List.of("team1", "team2", "team3"));
        /** id фракций, вступление в которые возможно только по приглашению. */
        List<String> inviteOnly = new ArrayList<>();
        List<String> joinCommands = new ArrayList<>();
        Balancer balancer = new Balancer();
    }

    /** Автобалансер команд: мягкий блок вступления в перегруженную команду при выборе фракции. */
    static final class Balancer {
        boolean enabled = true;
        /** Команда не может превышать этот % от всех боевых игроков онлайн (50 — идеальный паритет). */
        int maxSharePercent = 60;
        /** Порог не действует, пока боевых игроков онлайн меньше этого числа (защита от тупика). */
        int minPlayers = 4;
    }

    static final class Garage {
        boolean enabled = true;
    }

    /**
     * Лимиты и очистка активной техники гаража. {@code -1} в любом лимите = без ограничения.
     * Тайминги в секундах; брошенная (пустая) техника удаляется через
     * {@code abandonTimeoutSeconds + abandonWarningSeconds} после того, как её покинул водитель.
     */
    static final class Fleet {
        boolean enabled = true;
        int maxActivePerTeam = 8;
        int maxActivePerPlayer = 1;
        int spawnCooldownSeconds = 120;
        int abandonTimeoutSeconds = 180;
        int abandonWarningSeconds = 30;
        int aviationMaxActivePerTeam = 3;
        int aviationMaxActivePerPlayer = 1;
    }

    static final class Faction {
        int maxDeputies = 3;
        int orderMaxLength = 120;
        int orderDefaultTtlMinutes = 30;
        int orderMaxTtlMinutes = 240;
        /** Срок жизни приглашения в закрытую фракцию, минуты; 0 = бессрочно. */
        int inviteTtlMinutes = 1440;
    }

    /**
     * Анти-гриф: ломание/установка/взаимодействие с блоками запрещены всем,
     * кроме позиций из whitelist-ов. Записи — id блока ({@code minecraft:lever})
     * или тег с решёткой ({@code #minecraft:doors}).
     * {@code bypassPermissionLevel} — permission level, с которого защита не действует
     * (0 = обход по правам выключен). Креатив обходит защиту при {@code exemptCreative}.
     */
    static final class AntiGrief {
        boolean enabled = false;
        boolean exemptCreative = true;
        int bypassPermissionLevel = 2;
        List<String> allowedBreakBlocks = new ArrayList<>();
        List<String> allowedInteractBlocks = new ArrayList<>(List.of(
                "#minecraft:doors", "#minecraft:trapdoors", "#minecraft:buttons", "minecraft:lever"));
        List<String> allowedPlaceBlocks = new ArrayList<>();
    }

    /**
     * Система модерации: варны с автоэскалацией, баны (замена ванильных), муты войса/текста.
     * {@code warnEscalation} — правила автонаказания при накоплении варнов, формат строки
     * {@code "<count> <action> <duration>"}: action ∈ {mute_voice, mute_text, tempban, ban, kick},
     * duration в формате DurationParser ({@code 30m}, {@code 1d}, {@code permanent}). Пример:
     * {@code "3 mute_voice 30m"}, {@code "5 tempban 1d"}, {@code "7 ban permanent"}.
     * Строки интерпретируются в {@code ModerationService.parsedThresholds()}.
     * {@code warnDecayDays} — через сколько дней варн перестаёт учитываться (0 = не сгорают).
     */
    static final class Moderation {
        boolean overrideVanillaCommands = true;
        int defaultTempBanMinutes = 1440;
        int defaultMuteMinutes = 30;
        boolean broadcastPunishments = true;
        int warnDecayDays = 0;
        List<String> warnEscalation = new ArrayList<>(List.of(
                "3 mute_voice 30m", "5 tempban 1d", "7 ban permanent"));
    }

    /**
     * Веб-панель админа (common/web/): встроенный Javalin-сервер с графиками
     * TPS/нагрузки, списками игроков/entity и действиями модерации.
     * Вход — {@code /pjm web login}. Панель доступна напрямую по порту; TLS не встроен —
     * при желании ставится за reverse proxy (см. docs/WEBPANEL.md).
     * {@code publicUrl} — базовый URL для кликабельной ссылки входа
     * ({@code https://panel.example.com}); пустой → ссылка строится из IP сервера и порта.
     */
    static final class Web {
        boolean enabled = false;
        int port = 33005;
        String bindAddress = "0.0.0.0";
        int sessionTtlMinutes = 720;
        int historyMinutes = 120;
        boolean profilerEnabled = true;
        String publicUrl = "";
    }

    /**
     * Логирование действий игроков в читаемые файлы {@code <game>/pjmlogs/YYYY-MM-DD.log}:
     * убийства (PvP), уничтожение техники SuperbWarfare, вход/выход, действия подсистем.
     * {@code enabled} — единый выключатель всей записи (см. {@code common/logging/PjmActionLogger}).
     */
    static final class Logging {
        boolean enabled = true;
    }

    static final class BaseZone {
        boolean enabled = true;
        int countdownSeconds = 5;
        /** Отключать урон от взрывов (SBW) и гранат (WarBorn) внутри зоны базы. */
        boolean blockExplosions = true;
    }

    /**
     * «True Darkness»: ночью небесный свет не освещает мир (см. {@code mixin/LightTextureMixin}).
     * {@code intensity} — глубина затемнения в глухую ночь: 1.0 = только блочный свет,
     * 0.5 = вдвое темнее ванили, 0.0 = ваниль.
     */
    static final class NightDarkness {
        boolean enabled = true;
        double intensity = 1.0;
    }

    static final class Commands {
        List<String> startup = new ArrayList<>();
    }

    static final class Weapons {
        /**
         * Типы TACZ, считающиеся вторичным оружием — их можно носить вдобавок к основному.
         * Тип берётся из индекса ганпака ({@code CommonGunIndex.getType()}): pistol, smg, rifle,
         * sniper, shotgun, rpg, mg. Всё, чего нет в этом списке, — основное оружие.
         */
        List<String> secondaryGunTypes = new ArrayList<>(List.of("pistol", "smg"));
        /** Сколько основных стволов TACZ можно нести. */
        int maxPrimary = 1;
        /** Сколько вторичных стволов TACZ можно нести. */
        int maxSecondary = 1;
        /** Сколько стволов SuperbWarfare можно нести (у SBW типов нет — общий лимит). */
        int maxSuperbWarfare = 1;

        void normalize() {
            if (secondaryGunTypes == null) secondaryGunTypes = new ArrayList<>();
            secondaryGunTypes.replaceAll(type -> type == null ? "" : type.trim().toLowerCase(Locale.ROOT));
            secondaryGunTypes.removeIf(String::isBlank);
            maxPrimary = clamp(maxPrimary, 0, 64);
            maxSecondary = clamp(maxSecondary, 0, 64);
            maxSuperbWarfare = clamp(maxSuperbWarfare, 0, 64);
        }
    }

    static final class Events {
        boolean enabled = false;
        /** Случайный интервал автозапуска события, минуты. */
        int minIntervalMinutes = 60;
        int maxIntervalMinutes = 180;
        /** Автозапуск только когда захват фронтлайна неактивен. */
        boolean requireCaptureInactive = true;
        /** Задержка между анонсом события и его фактическим стартом. */
        int startDelaySeconds = 300;
        /** Вероятность того, что подошедший по таймеру запуск действительно состоится. */
        double spawnChance = 1.0;
        /** Веса типов событий при случайном выборе: id типа → вес. */
        Map<String, Double> typeWeights = new LinkedHashMap<>(Map.of("drone_raid", 1.0, "signal_hunt", 1.0));
    }

    static final class CapturePoints {
        boolean enabled = true;
        int captureTimeSeconds = 300;
        int decayTimeSeconds = 30;
        int tickIntervalTicks = 20;
        int minAdvantage = 1;
        boolean contestedFreeze = true;
        /** Точки захватываются строго по цепочке, а не в произвольном порядке. */
        boolean sequential = true;
        boolean journeymapEnabled = true;
        int journeymapFillAlpha = 96;
        int journeymapBorderAlpha = 220;
        int journeymapNeutralColorRgb = 0x9B9B9B;
        /** Захват разрешён только внутри окон {@link #scheduleWindows}. */
        boolean scheduleEnabled = false;
        /**
         * Последнее применённое расписанием состояние (null — ещё не оценивалось).
         * Переживает рестарт, чтобы ручной enable/disable не перебивался расписанием
         * заново при каждом перезапуске сервера внутри того же окна.
         */
        Boolean scheduleLastState;
        List<ScheduleWindowData> scheduleWindows = new ArrayList<>();
        /** Начисление дохода команде за удерживаемые точки. */
        boolean incomeEnabled = true;
        int incomePerPointPerMinute = 10;
        /** Склад-получатель дохода по командам: id команды → id склада. */
        Map<String, String> warehouseByTeam = new LinkedHashMap<>();

        void normalize() {
            captureTimeSeconds = clamp(captureTimeSeconds, 5, 3600);
            decayTimeSeconds = clamp(decayTimeSeconds, 1, 3600);
            tickIntervalTicks = clamp(tickIntervalTicks, 1, 200);
            minAdvantage = clamp(minAdvantage, 1, 64);
            incomePerPointPerMinute = clamp(incomePerPointPerMinute, 0, 10000);
            if (warehouseByTeam == null) warehouseByTeam = new LinkedHashMap<>();
            journeymapFillAlpha = clamp(journeymapFillAlpha, 0, 255);
            journeymapBorderAlpha = clamp(journeymapBorderAlpha, 0, 255);
            journeymapNeutralColorRgb = clamp(journeymapNeutralColorRgb, 0, 0xFFFFFF);
            if (scheduleWindows == null) scheduleWindows = new ArrayList<>();
            for (ScheduleWindowData w : scheduleWindows) w.normalize();
        }
    }

    /** Окно расписания захвата, отдаётся наружу вместо внутренней модели. */
    public record ScheduleWindow(int startHour, int startMinute, int endHour, int endMinute) {
        public int startTotalMinutes() { return startHour * 60 + startMinute; }
        public int endTotalMinutes()   { return endHour * 60 + endMinute; }
    }

    static final class ScheduleWindowData {
        int startHour = 18;
        int startMinute = 0;
        int endHour = 23;
        int endMinute = 0;

        void normalize() {
            startHour = clamp(startHour, 0, 23);
            startMinute = clamp(startMinute, 0, 59);
            endHour = clamp(endHour, 0, 23);
            endMinute = clamp(endMinute, 0, 59);
        }
    }
}
