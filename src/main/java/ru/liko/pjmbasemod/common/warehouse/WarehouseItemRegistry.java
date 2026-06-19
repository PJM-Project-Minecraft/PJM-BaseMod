package ru.liko.pjmbasemod.common.warehouse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
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

    /**
     * Захватывает предмет из руки игрока (полный NBT) и дозаписывает его в {@code items.json},
     * затем перезагружает каталог. Возвращает id созданного определения или {@code null} при ошибке.
     */
    public synchronized String captureAndAdd(MinecraftServer server, ItemStack stack,
                                             WarehousePoolCategory pool, int cost, int quantity,
                                             @Nullable String category) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());

        String base = sanitizeId(itemKey.getPath());
        if (base.isBlank()) base = "item";
        String id = base;
        for (int n = 2; definitions.containsKey(id); n++) {
            id = base + "_" + n;
        }

        // Имя не сохраняем: GUI сам подтянет локализованное имя предмета по itemId.
        String cat = (category == null || category.isBlank()) ? pool.id() : category.trim();
        int qty = Math.max(1, quantity);
        WarehouseItemDefinition def = new WarehouseItemDefinition(id, "", itemKey.toString(),
                pool, cat, Math.max(1, cost), qty);
        def.setQuantity(qty);

        // TACZ-ствол захватываем через родной API (gunId, патроны, режим огня, обвесы) — надёжнее
        // сырого NBT: декларативный блок tacz гарантированно восстанавливает GunId при выдаче.
        ru.liko.pjmbasemod.common.compat.TaczWarehouseCompat.CapturedGun gun =
                ru.liko.pjmbasemod.common.compat.TaczWarehouseCompat.captureGun(stack);
        if (gun != null) {
            def.setTaczGun(gun.gunId(), gun.ammo(), gun.fireMode(), gun.ammoInBarrel(), gun.attachments());
            Pjmbasemod.LOGGER.info("Warehouse: захвачен TACZ-ствол '{}' (gunId={}, патроны={}, обвесов={}).",
                    id, gun.gunId(), gun.ammo(), gun.attachments().size());
        } else {
            String snbt;
            try {
                // Полный SNBT предмета: {components:{...},count:..,id:"..."}.
                snbt = stack.save(server.registryAccess()).toString();
            } catch (Exception e) {
                Pjmbasemod.LOGGER.error("Warehouse: не удалось сохранить NBT предмета '{}'", itemKey, e);
                return null;
            }
            def.setItemNbt(snbt);
        }
        def.normalize();
        if (!def.isValid()) {
            Pjmbasemod.LOGGER.warn("Warehouse: захваченный предмет '{}' не прошёл валидацию.", itemKey);
            return null;
        }
        if (!appendToConfig(def)) return null;
        reload();
        Pjmbasemod.LOGGER.info("Warehouse: предмет '{}' дозаписан в {} (всего предметов в каталоге: {})",
                id, configFile().toAbsolutePath(), definitions.size());
        return id;
    }

    /**
     * Удаляет определение предмета по id из {@code items.json} и перезагружает каталог.
     * Возвращает {@code true}, если запись была найдена и удалена.
     * Предметы из legacy-каталога {@code items/} этой командой не удаляются.
     */
    public synchronized boolean removeAndSave(String rawId) {
        String id = sanitizeId(rawId);
        if (id.isBlank()) return false;
        if (!removeFromConfig(id)) return false;
        reload();
        Pjmbasemod.LOGGER.info("Warehouse: предмет '{}' удалён из {} (осталось предметов: {})",
                id, configFile().toAbsolutePath(), definitions.size());
        return true;
    }

    /** Абсолютный путь к items.json — для диагностики из команд. */
    public String configPath() {
        return configFile().toAbsolutePath().toString();
    }

    public int size() {
        return definitions.size();
    }

    /** Дозаписывает одно определение в конец массива {@code items} в items.json, не трогая остальные. */
    private boolean appendToConfig(WarehouseItemDefinition def) {
        Path file = configFile();
        try {
            Files.createDirectories(warehouseDirectory());
            JsonObject root = new JsonObject();
            JsonArray items = new JsonArray();
            if (Files.isRegularFile(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement existing = GSON.fromJson(reader, JsonElement.class);
                    if (existing != null && existing.isJsonObject()) {
                        root = existing.getAsJsonObject();
                        if (root.has("items") && root.get("items").isJsonArray()) {
                            items = root.getAsJsonArray("items");
                        }
                    } else if (existing != null && existing.isJsonArray()) {
                        items = existing.getAsJsonArray();
                    }
                }
            }
            if (!root.has("schemaVersion")) root.addProperty("schemaVersion", 1);
            items.add(GSON.toJsonTree(def));
            root.add("items", items);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            return true;
        } catch (Exception e) {
            Pjmbasemod.LOGGER.error("Warehouse: не удалось дозаписать предмет в конфиг {}", file, e);
            return false;
        }
    }

    /** Удаляет из items.json запись с заданным id. Возвращает true, если запись найдена и файл переписан. */
    private boolean removeFromConfig(String id) {
        Path file = configFile();
        if (!Files.isRegularFile(file)) return false;
        try {
            JsonObject root;
            JsonArray items;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement existing = GSON.fromJson(reader, JsonElement.class);
                if (existing != null && existing.isJsonObject()) {
                    root = existing.getAsJsonObject();
                    items = root.has("items") && root.get("items").isJsonArray()
                            ? root.getAsJsonArray("items") : new JsonArray();
                } else if (existing != null && existing.isJsonArray()) {
                    root = new JsonObject();
                    root.addProperty("schemaVersion", 1);
                    items = existing.getAsJsonArray();
                } else {
                    return false;
                }
            }

            JsonArray filtered = new JsonArray();
            boolean found = false;
            for (JsonElement element : items) {
                if (element.isJsonObject()) {
                    JsonElement idEl = element.getAsJsonObject().get("id");
                    String entryId = idEl != null && idEl.isJsonPrimitive()
                            ? sanitizeId(idEl.getAsString()) : "";
                    if (entryId.equals(id)) {
                        found = true;
                        continue;
                    }
                }
                filtered.add(element);
            }
            if (!found) return false;

            if (!root.has("schemaVersion")) root.addProperty("schemaVersion", 1);
            root.add("items", filtered);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            return true;
        } catch (Exception e) {
            Pjmbasemod.LOGGER.error("Warehouse: не удалось удалить предмет '{}' из конфига {}", id, file, e);
            return false;
        }
    }

    private List<WarehouseItemDefinition> exampleDefinitions() {
        // Пример ограничения по рангу: СВД доступна только начиная с ранга «сержант» и выше.
        // allowedTeams ограничивает выдачу командами (id из конфига teams); пусто/нет поля — всем командам.
        WarehouseItemDefinition svdm = new WarehouseItemDefinition("svdm", "СВДМ", "superbwarfare:svd",
                WarehousePoolCategory.WEAPON, "weapon", 3, 4, List.of("sniper", "marksman"));
        svdm.setMinRank("sergeant");
        svdm.setAllowedTeams(List.of("team1"));
        // Пример компонентов (NBT) в синтаксисе команды /give: применяются поверх выданного стека.
        WarehouseItemDefinition ak74m = new WarehouseItemDefinition("ak74m", "АК-74М", "superbwarfare:ak_47",
                WarehousePoolCategory.WEAPON, "weapon", 1, 8, List.of("assault", "sso"));
        ak74m.setComponents("[minecraft:custom_name='АК-74М',minecraft:max_stack_size=1]");
        // Пример quantity: за 1 очко выдаётся сразу 64 патрона.
        WarehouseItemDefinition ammo545 = new WarehouseItemDefinition("ammo_545", "Патроны 5.45",
                "superbwarfare:rifle_ammo", WarehousePoolCategory.SUPPLY, "ammo", 1, 64);
        ammo545.setQuantity(64);
        return List.of(
                ak74m,
                svdm,
                new WarehouseItemDefinition("pistol", "Пистолет", "superbwarfare:glock_17",
                        WarehousePoolCategory.WEAPON, "weapon", 1, 8),
                ammo545,
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
