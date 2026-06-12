package ru.liko.pjmbasemod.common.garage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
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
 * Каталог определений техники. Читает отдельные JSON-файлы из
 * {@code config/pjmbasemod/vehicles/} и держит их в памяти.
 *
 * <p>Загрузка/добавление потокобезопасны на уровне synchronized-методов; чтение каталога
 * происходит на серверном потоке (команды, обработка пакетов).</p>
 */
public final class VehicleRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final VehicleRegistry INSTANCE = new VehicleRegistry();

    private final Map<String, VehicleDefinition> definitions = new LinkedHashMap<>();

    private VehicleRegistry() {}

    public static VehicleRegistry get() { return INSTANCE; }

    public static String sanitizeId(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(Pjmbasemod.MODID).resolve("vehicles");
    }

    /** Полная перезагрузка каталога с диска. Создаёт папку и примеры при первом запуске. */
    public synchronized int reload() {
        definitions.clear();
        Path dir = directory();
        try {
            Files.createDirectories(dir);
            if (isEmptyDir(dir)) {
                writeExamples(dir);
            }
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Garage: не удалось подготовить директорию каталога техники {}", dir, e);
            return 0;
        }

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted()
                    .forEach(this::loadFile);
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Garage: ошибка чтения каталога техники из {}", dir, e);
        }

        Pjmbasemod.LOGGER.info("Garage: загружено {} определений техники.", definitions.size());
        return definitions.size();
    }

    private boolean isEmptyDir(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.noneMatch(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"));
        }
    }

    private void loadFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            VehicleDefinition def = GSON.fromJson(reader, VehicleDefinition.class);
            if (def == null) return;
            def.normalize();
            // id определяется именем файла, если не задан в JSON
            if (def.id().isBlank()) {
                String name = file.getFileName().toString();
                def.setId(sanitizeId(name.substring(0, name.length() - ".json".length())));
            }
            if (!def.isValid()) {
                if (def.hasInvalidAllowedRoles()) {
                    Pjmbasemod.LOGGER.warn("Garage: пропущено определение в {} (allowedRoles задан, но не содержит валидных ролей)", file.getFileName());
                    return;
                }
                Pjmbasemod.LOGGER.warn("Garage: пропущено невалидное определение в {} (нет id или entityType)", file.getFileName());
                return;
            }
            definitions.put(def.id(), def);
        } catch (Exception e) {
            Pjmbasemod.LOGGER.error("Garage: не удалось прочитать определение техники {}", file.getFileName(), e);
        }
    }

    /** Создаёт новое определение, пишет JSON-файл и добавляет в каталог. Возвращает false если id уже занят. */
    public synchronized boolean addDefinition(VehicleDefinition def) {
        def.normalize();
        if (!def.isValid()) {
            if (def.hasInvalidAllowedRoles()) {
                Pjmbasemod.LOGGER.warn("Garage: не удалось добавить {} (allowedRoles задан, но не содержит валидных ролей)", def.id());
            }
            return false;
        }
        Path dir = directory();
        Path file = dir.resolve(def.id() + ".json");
        try {
            Files.createDirectories(dir);
            if (Files.exists(file)) return false;
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(def, writer);
            }
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Garage: не удалось записать определение техники {}", file, e);
            return false;
        }
        definitions.put(def.id(), def);
        return true;
    }

    @Nullable
    public synchronized VehicleDefinition findByEntityType(@Nullable ResourceLocation typeId) {
        if (typeId == null) return null;
        for (VehicleDefinition def : definitions.values()) {
            if (typeId.equals(def.entityTypeId())) return def;
        }
        return null;
    }

    /**
     * Создаёт JSON-описание для техники, найденной в мире, если такого entityType ещё нет в каталоге.
     * Это нужно для SBW-техники, которую игрок сначала загрузил в гараж, а потом хочет удобно править в config.
     */
    public synchronized VehicleDefinition ensureDefinitionForEntity(ResourceLocation typeId, String displayName) {
        VehicleDefinition existing = findByEntityType(typeId);
        if (existing != null) {
            return existing;
        }

        String baseId = sanitizeId(typeId.getPath());
        if (baseId.isBlank()) {
            baseId = "vehicle";
        }
        String id = baseId;
        VehicleDefinition conflicting = definitions.get(id);
        if (conflicting != null && !typeId.equals(conflicting.entityTypeId())) {
            id = sanitizeId(typeId.getNamespace() + "_" + typeId.getPath());
            if (id.isBlank()) {
                id = baseId;
            }
        }
        Path dir = directory();
        String uniqueId = id;
        int suffix = 2;
        while (definitions.containsKey(uniqueId) || Files.exists(dir.resolve(uniqueId + ".json"))) {
            uniqueId = id + "_" + suffix++;
        }

        String name = displayName == null || displayName.isBlank() ? uniqueId : displayName;
        VehicleDefinition def = new VehicleDefinition(uniqueId, name, typeId.toString(),
                "minecraft:minecart", "sbw_imported", 0, List.of());
        if (!addDefinition(def)) {
            Pjmbasemod.LOGGER.warn("Garage: не удалось автоматически создать JSON для техники {}", typeId);
            return def;
        }
        Pjmbasemod.LOGGER.info("Garage: автоматически создано определение техники {} для {}", uniqueId, typeId);
        return def;
    }

    @Nullable
    public VehicleDefinition get(String id) {
        return definitions.get(sanitizeId(id));
    }

    public Collection<VehicleDefinition> all() {
        return List.copyOf(definitions.values());
    }

    public boolean isEmpty() { return definitions.isEmpty(); }

    private void writeExamples(Path dir) {
        VehicleDefinition tank = new VehicleDefinition(
                "m1a2", "M1A2 Abrams", "superbwarfare:m_1a_2",
                "minecraft:iron_block", "tank", 0,
                List.of(new CostEntry("minecraft:iron_block", 16), new CostEntry("minecraft:redstone_block", 8)),
                List.of("crew"));
        // garageType не задан: техника SuperbWarfare классифицируется автоматически (m1a2 → наземка).
        // При необходимости можно переопределить вручную: tank.setGarageType("ground").
        VehicleDefinition heli = new VehicleDefinition(
                "mi28", "Mi-28", "superbwarfare:mi_28",
                "minecraft:iron_block", "heli", 0,
                List.of(new CostEntry("minecraft:iron_block", 24), new CostEntry("minecraft:gold_block", 4)),
                List.of("crew"));
        // mi_28 авто-определяется как авиация (открывается ноутбуком авиагаража).
        // minRank ограничивает выдачу рангом «сержант» и выше.
        heli.setMinRank("sergeant");
        for (VehicleDefinition def : List.of(tank, heli)) {
            try (Writer writer = Files.newBufferedWriter(dir.resolve(def.id() + ".json"), StandardCharsets.UTF_8)) {
                GSON.toJson(def, writer);
            } catch (IOException e) {
                Pjmbasemod.LOGGER.error("Garage: не удалось создать пример определения {}", def.id(), e);
            }
        }
        Pjmbasemod.LOGGER.info("Garage: создан каталог техники с примерами в {}", dir);
    }
}
