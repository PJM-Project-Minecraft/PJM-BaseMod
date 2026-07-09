package ru.liko.pjmbasemod.common.role;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.teams.Teams;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-team role caps loaded from config/pjmbasemod/roles/limits.json.
 */
public final class RoleLimitRegistry {

    public static final int UNLIMITED = -1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final RoleLimitRegistry INSTANCE = new RoleLimitRegistry();

    private final Map<String, Map<String, Integer>> limitsByTeam = new LinkedHashMap<>();

    private RoleLimitRegistry() {
    }

    public static RoleLimitRegistry get() {
        return INSTANCE;
    }

    private Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(Pjmbasemod.MODID).resolve("roles");
    }

    private Path configFile() {
        return directory().resolve("limits.json");
    }

    public synchronized int reload() {
        limitsByTeam.clear();
        Path dir = directory();
        Path file = configFile();
        try {
            Files.createDirectories(dir);
            if (!Files.isRegularFile(file)) {
                writeExampleConfig(file);
            }
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Roles: не удалось подготовить конфиг лимитов {}", file, e);
            return 0;
        }

        if (Files.isRegularFile(file)) {
            loadConfigFile(file);
        }

        Pjmbasemod.LOGGER.info("Roles: загружены лимиты для {} фракций.", limitsByTeam.size());
        return limitsByTeam.size();
    }

    public synchronized int limitFor(String teamId, CombatRole role) {
        if (role == null) return UNLIMITED;
        Map<String, Integer> teamLimits = limitsByTeam.get(Teams.normalize(teamId));
        if (teamLimits == null) return UNLIMITED;
        return teamLimits.getOrDefault(role.id(), UNLIMITED);
    }

    private void loadConfigFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = GSON.fromJson(reader, JsonElement.class);
            if (root == null || !root.isJsonObject()) {
                Pjmbasemod.LOGGER.warn("Roles: {} должен быть JSON-объектом", file.getFileName());
                return;
            }

            JsonObject teams = root.getAsJsonObject().getAsJsonObject("teams");
            if (teams == null) {
                Pjmbasemod.LOGGER.warn("Roles: {} должен содержать объект teams", file.getFileName());
                return;
            }

            for (Map.Entry<String, JsonElement> teamEntry : teams.entrySet()) {
                String teamId = Teams.normalize(teamEntry.getKey());
                if (teamId.isBlank() || !Teams.isCombatTeam(teamId)) {
                    Pjmbasemod.LOGGER.warn("Roles: пропущена неизвестная фракция '{}'", teamEntry.getKey());
                    continue;
                }
                if (!teamEntry.getValue().isJsonObject()) {
                    Pjmbasemod.LOGGER.warn("Roles: лимиты фракции '{}' должны быть объектом", teamEntry.getKey());
                    continue;
                }

                Map<String, Integer> roleLimits = new LinkedHashMap<>();
                JsonObject roles = teamEntry.getValue().getAsJsonObject();
                for (Map.Entry<String, JsonElement> roleEntry : roles.entrySet()) {
                    CombatRole role = CombatRole.byIdOrAlias(roleEntry.getKey());
                    if (role == null) {
                        Pjmbasemod.LOGGER.warn("Roles: пропущена неизвестная роль '{}'", roleEntry.getKey());
                        continue;
                    }
                    int limit = parseLimit(roleEntry.getValue(), teamId, role);
                    roleLimits.put(role.id(), limit);
                }
                limitsByTeam.put(teamId, Map.copyOf(roleLimits));
            }
        } catch (Exception e) {
            Pjmbasemod.LOGGER.error("Roles: не удалось прочитать конфиг лимитов {}", file.getFileName(), e);
            limitsByTeam.clear();
        }
    }

    private int parseLimit(JsonElement element, String teamId, CombatRole role) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            Pjmbasemod.LOGGER.warn("Roles: лимит {}:{} должен быть числом, использую unlimited", teamId, role.id());
            return UNLIMITED;
        }
        int limit;
        try {
            limit = element.getAsInt();
        } catch (NumberFormatException e) {
            Pjmbasemod.LOGGER.warn("Roles: лимит {}:{} не является int, использую unlimited", teamId, role.id());
            return UNLIMITED;
        }
        if (limit < UNLIMITED) {
            Pjmbasemod.LOGGER.warn("Roles: лимит {}:{} меньше -1, использую unlimited", teamId, role.id());
            return UNLIMITED;
        }
        return limit;
    }

    private void writeExampleConfig(Path file) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        JsonObject teams = new JsonObject();
        for (var team : Teams.all()) {
            JsonObject roles = new JsonObject();
            for (CombatRole role : CombatRole.values()) {
                roles.addProperty(role.id(), UNLIMITED);
            }
            teams.add(team.id(), roles);
        }
        root.add("teams", teams);

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Roles: не удалось создать пример конфига лимитов {}", file, e);
            return;
        }
        Pjmbasemod.LOGGER.info("Roles: создан конфиг лимитов ролей в {}", file);
    }
}
