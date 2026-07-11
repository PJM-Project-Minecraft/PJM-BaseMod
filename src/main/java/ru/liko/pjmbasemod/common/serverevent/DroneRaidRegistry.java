package ru.liko.pjmbasemod.common.serverevent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.serverevent.DroneRaidDefinition.RaidPoint;
import ru.liko.pjmbasemod.common.serverevent.DroneRaidDefinition.RaidSettings;
import ru.liko.pjmbasemod.common.serverevent.DroneRaidDefinition.WaveProfile;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Точки и параметры «налёта дронов» из config/pjmbasemod/events/drone_raid.json.
 * Перезагрузка на ServerStartedEvent и через /pjm event reload.
 */
public final class DroneRaidRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DroneRaidRegistry INSTANCE = new DroneRaidRegistry();

    /** Модель файла целиком. */
    private static final class FileModel {
        int schemaVersion = 1;
        RaidSettings settings = new RaidSettings();
        List<RaidPoint> points = new ArrayList<>();
    }

    private volatile RaidSettings settings = new RaidSettings();
    private volatile List<RaidPoint> points = List.of();

    private DroneRaidRegistry() {
    }

    public static DroneRaidRegistry get() {
        return INSTANCE;
    }

    private Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(Pjmbasemod.MODID).resolve("events");
    }

    private Path configFile() {
        return directory().resolve("drone_raid.json");
    }

    public synchronized int reload() {
        Path dir = directory();
        Path file = configFile();
        try {
            Files.createDirectories(dir);
            if (!Files.isRegularFile(file)) {
                writeExampleConfig(file);
            }
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Events: не удалось подготовить конфиг налёта дронов {}", file, e);
            return 0;
        }

        FileModel model = null;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            model = GSON.fromJson(reader, FileModel.class);
        } catch (Exception e) {
            Pjmbasemod.LOGGER.error("Events: не удалось прочитать конфиг налёта дронов {}", file.getFileName(), e);
        }
        if (model == null) {
            model = new FileModel();
        }

        RaidSettings loadedSettings = model.settings != null ? model.settings : new RaidSettings();
        loadedSettings.normalize();

        List<RaidPoint> loadedPoints = new ArrayList<>();
        if (model.points != null) {
            for (RaidPoint point : model.points) {
                if (point == null) continue;
                point.normalize();
                if (!point.isValid()) {
                    Pjmbasemod.LOGGER.warn("Events: пропущена точка налёта без имени/измерения");
                    continue;
                }
                loadedPoints.add(point);
            }
        }

        settings = loadedSettings;
        points = List.copyOf(loadedPoints);
        Pjmbasemod.LOGGER.info("Events: загружено {} точек налёта дронов (spawnDistance должен быть меньше "
                + "shahed136MaxDistance из конфига WRBDrones).", points.size());
        return points.size();
    }

    public RaidSettings settings() {
        return settings;
    }

    public List<RaidPoint> points() {
        return points;
    }

    @Nullable
    public RaidPoint pointByName(String name) {
        if (name == null || name.isBlank()) return null;
        for (RaidPoint point : points) {
            if (point.name.equalsIgnoreCase(name.trim())) {
                return point;
            }
        }
        return null;
    }

    private void writeExampleConfig(Path file) {
        FileModel example = new FileModel();
        // По одной точке налёта на каждую боевую команду из конфига — готовый шаблон для правки.
        List<ru.liko.pjmbasemod.Config.ConfiguredTeam> teams =
                ru.liko.pjmbasemod.common.teams.Teams.all();
        int offset = 0;
        for (ru.liko.pjmbasemod.Config.ConfiguredTeam team : teams) {
            RaidPoint point = new RaidPoint();
            point.name = "Аэродром " + team.id();
            point.dimension = "minecraft:overworld";
            point.x = 120 + offset;
            point.y = 70;
            point.z = -340;
            point.radius = 80;
            point.teamId = team.id();
            example.points.add(point);
            offset += 500;
        }
        if (example.points.isEmpty()) {
            RaidPoint point = new RaidPoint();
            point.name = "Аэродром";
            point.dimension = "minecraft:overworld";
            point.x = 120;
            point.y = 70;
            point.z = -340;
            point.radius = 80;
            point.teamId = "red";
            example.points.add(point);
        }

        example.settings.allowCombined = true;
        example.settings.teamFailPenaltyXp = 300;
        example.settings.minShotDownRatio = 0.5;
        WaveProfile fast = new WaveProfile();
        fast.dronesPerWave = 2;
        fast.speed = 280.0;
        fast.spawnAltitude = 200;
        example.settings.combinedProfiles.add(fast);
        WaveProfile low = new WaveProfile();
        low.dronesPerWave = 6;
        low.speed = 160.0;
        low.spawnAltitude = 110;
        low.terrainFollow = true;
        example.settings.combinedProfiles.add(low);

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(example, writer);
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Events: не удалось создать пример конфига налёта дронов {}", file, e);
            return;
        }
        Pjmbasemod.LOGGER.info("Events: создан пример конфига налёта дронов в {}", file);
    }
}
