package ru.liko.pjmbasemod.common.missile;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON-каталог из {@code config/pjmbasemod/missiles/missiles.json}. */
public final class MissileRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final MissileRegistry INSTANCE = new MissileRegistry();

    private static final class FileModel {
        int schemaVersion = 1;
        List<MissileDefinition> missiles = defaultDefinitions();
    }

    private volatile Map<String, MissileDefinition> definitions = defaultMap();

    private MissileRegistry() {}

    public static MissileRegistry get() { return INSTANCE; }

    public synchronized int reload() {
        Path directory = FMLPaths.CONFIGDIR.get().resolve(Pjmbasemod.MODID).resolve("missiles");
        Path file = directory.resolve("missiles.json");
        try {
            Files.createDirectories(directory);
            if (!Files.isRegularFile(file)) writeExample(file);
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Ракеты: не удалось подготовить конфиг {}", file, e);
            return definitions.size();
        }

        FileModel model;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            model = GSON.fromJson(reader, FileModel.class);
        } catch (Exception e) {
            Pjmbasemod.LOGGER.error("Ракеты: не удалось прочитать {} — сохранён предыдущий каталог", file, e);
            return definitions.size();
        }

        if (model == null || model.missiles == null) {
            Pjmbasemod.LOGGER.error("Ракеты: {} не содержит массива missiles — сохранён предыдущий каталог", file);
            return definitions.size();
        }

        LinkedHashMap<String, MissileDefinition> loaded = new LinkedHashMap<>();
        for (MissileDefinition definition : model.missiles) {
            if (definition == null) continue;
            definition.normalize();
            if (!definition.isValid()) continue;
            if (loaded.putIfAbsent(definition.id(), definition) != null) {
                Pjmbasemod.LOGGER.warn("Ракеты: повторяющийся id '{}' пропущен", definition.id());
            }
        }
        if (loaded.isEmpty()) {
            Pjmbasemod.LOGGER.error("Ракеты: после загрузки каталог пуст — сохранён предыдущий каталог");
            return definitions.size();
        }
        definitions = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
        Pjmbasemod.LOGGER.info("Ракеты: загружено {} профилей", definitions.size());
        return definitions.size();
    }

    @Nullable
    public MissileDefinition find(String id) {
        return id == null ? null : definitions.get(id);
    }

    public List<MissileDefinition> all() {
        return List.copyOf(definitions.values());
    }

    private static Map<String, MissileDefinition> defaultMap() {
        LinkedHashMap<String, MissileDefinition> result = new LinkedHashMap<>();
        for (MissileDefinition definition : defaultDefinitions()) result.put(definition.id(), definition);
        return Collections.unmodifiableMap(result);
    }

    private static List<MissileDefinition> defaultDefinitions() {
        List<MissileDefinition> result = new ArrayList<>();
        // Скорость = spawnDistance / (flightSeconds * 20); при спавне за 2500 держим ~4-6 блоков/тик.
        result.add(MissileDefinition.create("iskander_k", "Искандер-К", MissileDefinition.Trajectory.CRUISE,
                25, 480, 25, 2500, 24, 48, 140, 120, 12, 40, 0.30f, false)
                .withApproach(4.0f, 1.0f));
        result.add(MissileDefinition.create("storm_shadow", "Storm Shadow", MissileDefinition.Trajectory.CRUISE,
                35, 600, 28, 2500, 20, 56, 150, 145, 14, 48, 0.30f, false)
                .withApproach(8.0f, 1.5f));
        result.add(MissileDefinition.create("kh_101", "Х-101", MissileDefinition.Trajectory.CRUISE,
                50, 780, 31, 2500, 32, 64, 170, 175, 17, 60, 0.35f, false)
                .withApproach(14.0f, 2.0f));
        result.add(MissileDefinition.create("iskander_m", "Искандер-М", MissileDefinition.Trajectory.BALLISTIC,
                70, 1080, 20, 2500, 24, 32, 500, 230, 20, 85, 0.25f, false));
        result.add(MissileDefinition.create("flamingo", "Flamingo", MissileDefinition.Trajectory.CRUISE,
                95, 1500, 30, 2500, 38, 72, 190, 280, 24, 95, 0.40f, false)
                .withApproach(2.0f, 0.5f));
        return result;
    }

    private static void writeExample(Path file) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(new FileModel(), writer);
        }
        Pjmbasemod.LOGGER.info("Ракеты: создан пример конфига {}", file);
    }
}
