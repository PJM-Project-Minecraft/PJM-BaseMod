package ru.liko.pjmbasemod.common.warehouse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;
import ru.liko.pjmbasemod.Pjmbasemod;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Каталог выдаваемых предметов склада. Читает JSON-конфиг
 * {@code config/pjmbasemod/warehouse/items.json}; для совместимости также
 * подхватывает отдельные файлы из {@code config/pjmbasemod/warehouse/items/}.
 */
public final class WarehouseItemRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final WarehouseItemRegistry INSTANCE = new WarehouseItemRegistry();

    private final Map<String, WarehouseItemDefinition> definitions = new LinkedHashMap<>();

    private WarehouseItemRegistry() {}

    public static WarehouseItemRegistry get() { return INSTANCE; }

    public static String sanitizeId(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private Path warehouseDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(Pjmbasemod.MODID).resolve("warehouse");
    }

    private Path configFile() {
        return warehouseDirectory().resolve("items.json");
    }

    private Path legacyDirectory() {
        return warehouseDirectory().resolve("items");
    }

    /** Полная перезагрузка каталога с диска. Создаёт конфиг с примерами при первом запуске. */
    public synchronized int reload() {
        definitions.clear();
        Path root = warehouseDirectory();
        Path file = configFile();
        Path legacyDir = legacyDirectory();
        try {
            Files.createDirectories(root);
            if (!Files.isRegularFile(file) && !hasJsonFiles(legacyDir)) {
                writeExampleConfig(file);
            }
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Warehouse: не удалось подготовить конфиг предметов склада {}", root, e);
            return 0;
        }

        if (Files.isDirectory(legacyDir)) {
            try (Stream<Path> files = Files.list(legacyDir)) {
                files.filter(this::isJsonFile)
                        .sorted()
                        .forEach(this::loadLegacyFile);
            } catch (IOException e) {
                Pjmbasemod.LOGGER.error("Warehouse: ошибка чтения каталога предметов из {}", legacyDir, e);
            }
        }

        if (Files.isRegularFile(file)) {
            loadConfigFile(file);
        }

        Pjmbasemod.LOGGER.info("Warehouse: загружено {} определений предметов.", definitions.size());
        return definitions.size();
    }

    private boolean hasJsonFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return false;
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(this::isJsonFile);
        }
    }

    private boolean isJsonFile(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private void loadConfigFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = GSON.fromJson(reader, JsonElement.class);
            if (root == null || root.isJsonNull()) return;

            JsonElement items = root;
            if (root.isJsonObject()) {
                JsonObject object = root.getAsJsonObject();
                items = object.get("items");
            }

            if (items == null || !items.isJsonArray()) {
                Pjmbasemod.LOGGER.warn("Warehouse: {} должен быть массивом предметов или объектом с массивом items", file.getFileName());
                return;
            }

            int index = 0;
            for (JsonElement item : items.getAsJsonArray()) {
                index++;
                try {
                    WarehouseItemDefinition def = GSON.fromJson(item, WarehouseItemDefinition.class);
                    registerDefinition(def, file, fallbackId(def), "#" + index);
                } catch (Exception e) {
                    Pjmbasemod.LOGGER.error("Warehouse: не удалось прочитать предмет {}#{}", file.getFileName(), index, e);
                }
            }
        } catch (Exception e) {
            Pjmbasemod.LOGGER.error("Warehouse: не удалось прочитать конфиг предметов {}", file.getFileName(), e);
        }
    }

    private void loadLegacyFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            WarehouseItemDefinition def = GSON.fromJson(reader, WarehouseItemDefinition.class);
            String name = file.getFileName().toString();
            registerDefinition(def, file, sanitizeId(name.substring(0, name.length() - ".json".length())), "");
        } catch (Exception e) {
            Pjmbasemod.LOGGER.error("Warehouse: не удалось прочитать определение предмета {}", file.getFileName(), e);
        }
    }

    private void registerDefinition(@Nullable WarehouseItemDefinition def, Path source, @Nullable String fallbackId, String label) {
        if (def == null) return;
        def.normalize();
        String id = sanitizeId(def.id());
        if (id.isBlank() && fallbackId != null) id = sanitizeId(fallbackId);
        def.setId(id);
        if (!def.isValid()) {
            if (def.hasInvalidAllowedRoles()) {
                Pjmbasemod.LOGGER.warn("Warehouse: пропущено определение в {}{} (allowedRoles задан, но не содержит валидных ролей)",
                        source.getFileName(), label);
                return;
            }
            Pjmbasemod.LOGGER.warn("Warehouse: пропущено невалидное определение в {}{} (нет id или предмета)",
                    source.getFileName(), label);
            return;
        }
        WarehouseItemDefinition previous = definitions.put(def.id(), def);
        if (previous != null) {
            Pjmbasemod.LOGGER.warn("Warehouse: предмет '{}' из {}{} заменил предыдущее определение", def.id(),
                    source.getFileName(), label);
        }
    }

    @Nullable
    private String fallbackId(@Nullable WarehouseItemDefinition def) {
        if (def == null || def.itemIdString().isBlank()) return null;
        return sanitizeId(def.itemIdString());
    }

    @Nullable
    public WarehouseItemDefinition get(String id) {
        return definitions.get(sanitizeId(id));
    }

    public Collection<WarehouseItemDefinition> all() {
        return List.copyOf(definitions.values());
    }

    public boolean isEmpty() { return definitions.isEmpty(); }

    private List<WarehouseItemDefinition> exampleDefinitions() {
        return List.of(
                new WarehouseItemDefinition("ak74m", "АК-74М", "superbwarfare:ak_47",
                        WarehousePoolCategory.WEAPON, "weapon", 1, 8, List.of("assault", "sso")),
                new WarehouseItemDefinition("svdm", "СВДМ", "superbwarfare:svd",
                        WarehousePoolCategory.WEAPON, "weapon", 3, 4, List.of("sniper", "marksman")),
                new WarehouseItemDefinition("pistol", "Пистолет", "superbwarfare:glock_17",
                        WarehousePoolCategory.WEAPON, "weapon", 1, 8),
                new WarehouseItemDefinition("ammo_545", "Патроны 5.45", "superbwarfare:rifle_ammo",
                        WarehousePoolCategory.SUPPLY, "ammo", 1, 64),
                new WarehouseItemDefinition("medkit", "Аптечка", "minecraft:golden_apple",
                        WarehousePoolCategory.SUPPLY, "medicine", 1, 16),
                new WarehouseItemDefinition("ration", "Паёк", "minecraft:cooked_beef",
                        WarehousePoolCategory.SUPPLY, "food", 1, 32),
                new WarehouseItemDefinition("iron_helmet", "Железный шлем", "minecraft:iron_helmet",
                        WarehousePoolCategory.EQUIPMENT, "equipment", 2, 1),
                new WarehouseItemDefinition("iron_chestplate", "Железный нагрудник", "minecraft:iron_chestplate",
                        WarehousePoolCategory.EQUIPMENT, "equipment", 4, 1),
                new WarehouseItemDefinition("iron_leggings", "Железные поножи", "minecraft:iron_leggings",
                        WarehousePoolCategory.EQUIPMENT, "equipment", 3, 1),
                new WarehouseItemDefinition("iron_boots", "Железные ботинки", "minecraft:iron_boots",
                        WarehousePoolCategory.EQUIPMENT, "equipment", 2, 1),
                new WarehouseItemDefinition("metal", "Металл", "minecraft:iron_ingot",
                        WarehousePoolCategory.RAW, "raw", 1, 64),
                new WarehouseItemDefinition("electronics", "Электроника", "minecraft:redstone",
                        WarehousePoolCategory.RAW, "raw", 1, 64),
                new WarehouseItemDefinition("blueprint", "Чертёж", "minecraft:paper",
                        WarehousePoolCategory.SPECIAL, "special", 5, 2));
    }

    private void writeExampleConfig(Path file) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.add("items", GSON.toJsonTree(exampleDefinitions()));
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Warehouse: не удалось создать пример конфига предметов {}", file, e);
            return;
        }
        Pjmbasemod.LOGGER.info("Warehouse: создан конфиг предметов с примерами в {}", file);
    }
}
