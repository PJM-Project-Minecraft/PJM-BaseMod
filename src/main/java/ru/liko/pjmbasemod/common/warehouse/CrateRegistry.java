package ru.liko.pjmbasemod.common.warehouse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import ru.liko.pjmbasemod.Pjmbasemod;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Конфиг типов ящиков поставки. Читает JSON-файлы из
 * {@code config/pjmbasemod/warehouse/crates/} и держит их в памяти.
 */
public final class CrateRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final CrateRegistry INSTANCE = new CrateRegistry();

    private final Map<String, CrateDefinition> definitions = new LinkedHashMap<>();

    private CrateRegistry() {}

    public static CrateRegistry get() { return INSTANCE; }

    public static String sanitizeId(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(Pjmbasemod.MODID).resolve("warehouse").resolve("crates");
    }

    public synchronized int reload() {
        definitions.clear();
        Path dir = directory();
        try {
            Files.createDirectories(dir);
            writeMissingExamples(dir);
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Warehouse: не удалось подготовить директорию ящиков {}", dir, e);
            return 0;
        }

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted()
                    .forEach(this::loadFile);
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Warehouse: ошибка чтения ящиков из {}", dir, e);
        }

        Pjmbasemod.LOGGER.info("Warehouse: загружено {} типов ящиков.", definitions.size());
        return definitions.size();
    }

    private void loadFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            CrateDefinition def = GSON.fromJson(reader, CrateDefinition.class);
            if (def == null) return;
            def.normalize();
            if (def.id().isBlank()) {
                String name = file.getFileName().toString();
                def.setId(sanitizeId(name.substring(0, name.length() - ".json".length())));
            }
            if (!def.isValid()) {
                Pjmbasemod.LOGGER.warn("Warehouse: пропущен невалидный ящик в {}", file.getFileName());
                return;
            }
            definitions.put(def.id(), def);
        } catch (Exception e) {
            Pjmbasemod.LOGGER.error("Warehouse: не удалось прочитать конфиг ящика {}", file.getFileName(), e);
        }
    }

    @Nullable
    public CrateDefinition get(String id) {
        return definitions.get(sanitizeId(id));
    }

    public boolean isEmpty() { return definitions.isEmpty(); }

    private List<CrateDefinition> exampleDefinitions() {
        return List.of(
                new CrateDefinition("weapon_crate", WarehousePoolCategory.SUPPLY, 3),
                new CrateDefinition("supply_crate", WarehousePoolCategory.SUPPLY, 5),
                new CrateDefinition("equipment_crate", WarehousePoolCategory.SUPPLY, 4),
                new CrateDefinition("raw_crate", WarehousePoolCategory.RAW, 5),
                new CrateDefinition("special_crate", WarehousePoolCategory.SUPPLY, 2));
    }

    private void writeMissingExamples(Path dir) {
        for (CrateDefinition def : exampleDefinitions()) {
            Path file = dir.resolve(def.id() + ".json");
            if (Files.exists(file)) continue;
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(def, writer);
            } catch (IOException e) {
                Pjmbasemod.LOGGER.error("Warehouse: не удалось создать пример ящика {}", def.id(), e);
            }
        }
        Pjmbasemod.LOGGER.info("Warehouse: стандартные конфиги ящиков проверены в {}", dir);
    }
}
