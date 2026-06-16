package ru.liko.pjmbasemod.common.role;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Какие боевые роли являются донатными. Загружается из
 * config/pjmbasemod/roles/access.json. Донатная роль требует permission node
 * pjmbasemod.role.unlock.<id> (см. RolePermissions).
 */
public final class RoleAccessRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final RoleAccessRegistry INSTANCE = new RoleAccessRegistry();

    private final Set<String> paidRoles = new LinkedHashSet<>();

    private RoleAccessRegistry() {
    }

    public static RoleAccessRegistry get() {
        return INSTANCE;
    }

    private Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(Pjmbasemod.MODID).resolve("roles");
    }

    private Path configFile() {
        return directory().resolve("access.json");
    }

    public synchronized int reload() {
        paidRoles.clear();
        Path dir = directory();
        Path file = configFile();
        try {
            Files.createDirectories(dir);
            if (!Files.isRegularFile(file)) {
                writeExampleConfig(file);
            }
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Roles: не удалось подготовить конфиг доступа {}", file, e);
            return 0;
        }

        if (Files.isRegularFile(file)) {
            loadConfigFile(file);
        }

        Pjmbasemod.LOGGER.info("Roles: загружены донат-роли: {}", paidRoles);
        return paidRoles.size();
    }

    public synchronized boolean isPaid(CombatRole role) {
        return role != null && paidRoles.contains(role.id());
    }

    private void loadConfigFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = GSON.fromJson(reader, JsonElement.class);
            if (root == null || !root.isJsonObject()) {
                Pjmbasemod.LOGGER.warn("Roles: {} должен быть JSON-объектом", file.getFileName());
                return;
            }
            JsonObject roles = root.getAsJsonObject().getAsJsonObject("roles");
            if (roles == null) {
                Pjmbasemod.LOGGER.warn("Roles: {} должен содержать объект roles", file.getFileName());
                return;
            }
            for (Map.Entry<String, JsonElement> entry : roles.entrySet()) {
                CombatRole role = CombatRole.byIdOrAlias(entry.getKey());
                if (role == null) {
                    Pjmbasemod.LOGGER.warn("Roles: пропущена неизвестная роль '{}'", entry.getKey());
                    continue;
                }
                if (entry.getValue().isJsonObject()) {
                    JsonObject obj = entry.getValue().getAsJsonObject();
                    if (obj.has("paid") && obj.get("paid").getAsBoolean()) {
                        paidRoles.add(role.id());
                    }
                }
            }
        } catch (Exception e) {
            Pjmbasemod.LOGGER.error("Roles: не удалось прочитать конфиг доступа {}", file.getFileName(), e);
            paidRoles.clear();
        }
    }

    private void writeExampleConfig(Path file) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        JsonObject roles = new JsonObject();
        for (CombatRole role : CombatRole.values()) {
            JsonObject obj = new JsonObject();
            boolean paid = role == CombatRole.UAV_OPERATOR || role == CombatRole.SSO;
            obj.addProperty("paid", paid);
            roles.add(role.id(), obj);
        }
        root.add("roles", roles);

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Roles: не удалось создать пример конфига доступа {}", file, e);
            return;
        }
        Pjmbasemod.LOGGER.info("Roles: создан конфиг доступа ролей в {}", file);
    }
}
