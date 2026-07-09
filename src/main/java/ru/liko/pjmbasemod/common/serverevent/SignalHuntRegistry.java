package ru.liko.pjmbasemod.common.serverevent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.serverevent.SignalHuntDefinition.SignalHuntSettings;
import ru.liko.pjmbasemod.common.serverevent.SignalHuntDefinition.SignalHuntZone;

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
 * Зоны и параметры «радиоразведки» из config/pjmbasemod/events/signal_hunt.json.
 * Перезагрузка на ServerStartedEvent и через /pjm event reload.
 */
public final class SignalHuntRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final SignalHuntRegistry INSTANCE = new SignalHuntRegistry();

    private static final class FileModel {
        int schemaVersion = 1;
        SignalHuntSettings settings = new SignalHuntSettings();
        List<SignalHuntZone> zones = new ArrayList<>();
    }

    private volatile SignalHuntSettings settings = new SignalHuntSettings();
    private volatile List<SignalHuntZone> zones = List.of();

    private SignalHuntRegistry() {
    }

    public static SignalHuntRegistry get() {
        return INSTANCE;
    }

    private Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(Pjmbasemod.MODID).resolve("events");
    }

    private Path configFile() {
        return directory().resolve("signal_hunt.json");
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
            Pjmbasemod.LOGGER.error("Events: не удалось подготовить конфиг радиоразведки {}", file, e);
            return 0;
        }

        FileModel model = null;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            model = GSON.fromJson(reader, FileModel.class);
        } catch (Exception e) {
            Pjmbasemod.LOGGER.error("Events: не удалось прочитать конфиг радиоразведки {}", file.getFileName(), e);
        }
        if (model == null) {
            model = new FileModel();
        }

        SignalHuntSettings loadedSettings = model.settings != null ? model.settings : new SignalHuntSettings();
        loadedSettings.normalize();

        List<SignalHuntZone> loadedZones = new ArrayList<>();
        if (model.zones != null) {
            for (SignalHuntZone zone : model.zones) {
                if (zone == null) continue;
                zone.normalize();
                if (!zone.isValid()) {
                    Pjmbasemod.LOGGER.warn("Events: пропущена зона радиоразведки без имени/измерения");
                    continue;
                }
                loadedZones.add(zone);
            }
        }

        settings = loadedSettings;
        zones = List.copyOf(loadedZones);
        Pjmbasemod.LOGGER.info("Events: загружено {} зон радиоразведки.", zones.size());
        return zones.size();
    }

    public SignalHuntSettings settings() {
        return settings;
    }

    public List<SignalHuntZone> zones() {
        return zones;
    }

    @Nullable
    public SignalHuntZone zoneByName(String name) {
        if (name == null || name.isBlank()) return null;
        for (SignalHuntZone zone : zones) {
            if (zone.name.equalsIgnoreCase(name.trim())) {
                return zone;
            }
        }
        return null;
    }

    private void writeExampleConfig(Path file) {
        FileModel example = new FileModel();
        SignalHuntZone zone = new SignalHuntZone();
        zone.name = "Полигон Альфа";
        zone.dimension = "minecraft:overworld";
        zone.centerX = 250;
        zone.centerY = 70;
        zone.centerZ = -180;
        zone.radius = 200;
        zone.beaconCount = 3;
        zone.beaconSpread = 150;
        example.zones.add(zone);

        example.settings.signalRadius = 20;
        example.settings.signalMaxDistance = 400;
        example.settings.captureRadius = 6;
        example.settings.captureSeconds = 5;
        example.settings.xpPerBeacon = 30;
        example.settings.maxDurationMinutes = 20;

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(example, writer);
        } catch (IOException e) {
            Pjmbasemod.LOGGER.error("Events: не удалось создать пример конфига радиоразведки {}", file, e);
            return;
        }
        Pjmbasemod.LOGGER.info("Events: создан пример конфига радиоразведки в {}", file);
    }
}
