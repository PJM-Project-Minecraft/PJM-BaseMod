package ru.liko.pjmbasemod.common.customization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.neoforged.fml.loading.FMLPaths;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.teams.Teams;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Реестр пулов скинов по командам. Грузит {@code config/pjmbasemod/skins.json} через Gson
 * (паттерн {@link ru.liko.pjmbasemod.common.rank.RankRegistry}). Источник истины — сервер.
 */
public final class SkinRegistry {

    /**
     * Скин-текстуры, лежащие в {@code assets/pjmbasemod/textures/skins/} (без {@code .png}).
     * Имена обязаны быть валидны для {@code ResourceLocation} (без '+' и пробелов).
     */
    public static final List<String> KNOWN_SKINS = List.of(
            "skin_emr", "skin_emr_jaket", "skin_emr_atacsfg", "skin_emr_multicam",
            "skin_atacsfg", "skin_mc", "skin_mc_jacket", "skin_m05",
            "skin_berezka", "skin_flectarn", "skin_m70", "skin_black_mc",
            "skin_koss", "skin_colis");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final SkinRegistry INSTANCE = new SkinRegistry();

    private SkinConfig config = SkinConfig.defaults();
    private boolean loaded;

    private SkinRegistry() {
    }

    public static SkinRegistry get() {
        return INSTANCE;
    }

    public synchronized SkinConfig config() {
        if (!loaded) reload();
        return config;
    }

    public synchronized boolean reload() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                config = SkinConfig.defaults();
                write(file, config);
                loaded = true;
                Pjmbasemod.LOGGER.info("Skins: created default config at {}", file);
                return true;
            }

            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                SkinConfig loadedConfig = GSON.fromJson(reader, SkinConfig.class);
                config = loadedConfig == null ? SkinConfig.defaults() : loadedConfig;
                config.normalize();
                loaded = true;
                Pjmbasemod.LOGGER.info("Skins: loaded skin pools for {} team(s).", config.teams.size());
                return true;
            }
        } catch (Exception e) {
            loaded = true;
            config = SkinConfig.defaults();
            Pjmbasemod.LOGGER.error("Skins: failed to load {}, using defaults.", file, e);
            return false;
        }
    }

    /** Разрешённые скины команды; пустой список, если пул не сконфигурирован. */
    public List<String> skinsForTeam(String teamId) {
        SkinPool pool = poolFor(teamId);
        return pool == null ? List.of() : List.copyOf(pool.skins);
    }

    /** Дефолтный скин команды или {@code ""}, если пула нет. */
    public String defaultForTeam(String teamId) {
        SkinPool pool = poolFor(teamId);
        if (pool == null) return "";
        if (pool.defaultSkin != null && !pool.defaultSkin.isBlank()) return pool.defaultSkin;
        return pool.skins.isEmpty() ? "" : pool.skins.getFirst();
    }

    /** Количество сконфигурированных командных пулов (для отчётов/команд). */
    public int teamCount() {
        return config().teams.size();
    }

    /** Персональный скин игрока по нику из секции {@code players} конфига или {@code ""}. */
    public String skinForPlayer(String playerName) {
        if (playerName == null || playerName.isBlank()) return "";
        String skin = config().players.get(playerName.trim().toLowerCase(Locale.ROOT));
        return skin == null ? "" : skin;
    }

    public boolean isAllowed(String teamId, String skinId) {
        SkinPool pool = poolFor(teamId);
        return pool != null && pool.skins.contains(sanitize(skinId));
    }

    private SkinPool poolFor(String teamId) {
        String team = Teams.normalize(teamId);
        if (team.isBlank()) return null;
        return config().teams.get(team);
    }

    /** Приводит id скина к виду, валидному для пути {@code ResourceLocation}. */
    public static String sanitize(String value) {
        if (value == null) return "";
        String lower = value.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean ok = c == '_' || c == '-' || c == '.' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            sb.append(ok ? c : '_');
        }
        return sb.toString();
    }

    private Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(Pjmbasemod.MODID).resolve("skins.json");
    }

    private void write(Path file, SkinConfig config) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        }
    }

    // ===== JSON-модель =====

    public static final class SkinConfig {
        int schemaVersion = 1;
        Map<String, SkinPool> teams = new LinkedHashMap<>();
        /** Персональные скины: ник игрока (без учёта регистра) → id скина. Приоритетнее пула команды. */
        Map<String, String> players = new LinkedHashMap<>();

        static SkinConfig defaults() {
            SkinConfig cfg = new SkinConfig();
            int index = 0;
            for (Config.ConfiguredTeam team : Teams.all()) {
                SkinPool pool = new SkinPool();
                pool.skins = new ArrayList<>(KNOWN_SKINS);
                pool.defaultSkin = index == 0 ? "skin_emr" : "skin_mc";
                cfg.teams.put(Teams.normalize(team.id()), pool);
                index++;
            }
            if (cfg.teams.isEmpty()) {
                SkinPool pool = new SkinPool();
                pool.skins = new ArrayList<>(KNOWN_SKINS);
                pool.defaultSkin = "skin_emr";
                cfg.teams.put("team1", pool);
            }
            return cfg;
        }

        void normalize() {
            if (teams == null) teams = new LinkedHashMap<>();
            Map<String, SkinPool> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, SkinPool> e : teams.entrySet()) {
                SkinPool pool = e.getValue() == null ? new SkinPool() : e.getValue();
                pool.normalize();
                normalized.put(Teams.normalize(e.getKey()), pool);
            }
            teams = normalized;

            if (players == null) players = new LinkedHashMap<>();
            Map<String, String> cleanedPlayers = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : players.entrySet()) {
                String nick = e.getKey() == null ? "" : e.getKey().trim().toLowerCase(Locale.ROOT);
                String skin = sanitize(e.getValue());
                if (nick.isBlank() || skin.isBlank()) continue;
                if (!KNOWN_SKINS.contains(skin)) {
                    Pjmbasemod.LOGGER.warn("Skins: unknown skin '{}' for player '{}' in players section, skipped.",
                            skin, e.getKey());
                    continue;
                }
                cleanedPlayers.put(nick, skin);
            }
            players = cleanedPlayers;
        }
    }

    public static final class SkinPool {
        @SerializedName("default")
        String defaultSkin = "";
        List<String> skins = new ArrayList<>();

        void normalize() {
            List<String> cleaned = new ArrayList<>();
            if (skins != null) {
                for (String s : skins) {
                    String id = sanitize(s);
                    if (!id.isBlank() && !cleaned.contains(id)) cleaned.add(id);
                }
            }
            skins = cleaned;
            defaultSkin = sanitize(defaultSkin);
            if ((defaultSkin.isBlank() || !skins.contains(defaultSkin)) && !skins.isEmpty()) {
                defaultSkin = skins.getFirst();
            }
        }
    }
}
