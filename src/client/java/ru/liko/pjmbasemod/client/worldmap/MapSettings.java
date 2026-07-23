package ru.liko.pjmbasemod.client.worldmap;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.minecraft.client.Minecraft;

/**
 * Клиентские настройки карты — общие для всех миров, файл
 * {@code <gameDir>/pjmbasemod/worldmap/settings.properties}. Правятся панелью «⚙» на самой карте.
 */
public final class MapSettings {

    private static final MapSettings INSTANCE = new MapSettings();
    private static final float[] LABEL_SCALES = {0.5f, 0.75f, 1.0f, 1.25f};

    private float labelScale = 1.0f;
    private boolean openAnimation = true;
    private boolean loaded;

    private MapSettings() {}

    public static MapSettings get() {
        return INSTANCE;
    }

    /** Масштаб подписей меток (пилюль) на карте. */
    public float labelScale() {
        load();
        return labelScale;
    }

    /** Анимация выезда карты при открытии/закрытии. */
    public boolean openAnimation() {
        load();
        return openAnimation;
    }

    /** Перебор ступеней 50 → 75 → 100 → 125 %. */
    public void cycleLabelScale() {
        load();
        int i = 0;
        while (i < LABEL_SCALES.length && LABEL_SCALES[i] != labelScale) i++;
        labelScale = LABEL_SCALES[(i + 1) % LABEL_SCALES.length];
        save();
    }

    public void toggleOpenAnimation() {
        load();
        openAnimation = !openAnimation;
        save();
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("pjmbasemod").resolve("worldmap").resolve("settings.properties");
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file())) {
            p.load(r);
        } catch (IOException ignored) {
            return; // файла ещё нет — остаются дефолты
        }
        labelScale = parseScale(p.getProperty("labelScale"));
        openAnimation = !"false".equals(p.getProperty("openAnimation"));
    }

    private static float parseScale(String s) {
        try {
            float v = Float.parseFloat(s);
            for (float allowed : LABEL_SCALES) {
                if (allowed == v) return v;
            }
        } catch (RuntimeException ignored) {
            // null или битое значение — дефолт
        }
        return 1.0f;
    }

    private void save() {
        Properties p = new Properties();
        p.setProperty("labelScale", Float.toString(labelScale));
        p.setProperty("openAnimation", Boolean.toString(openAnimation));
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f)) {
                p.store(w, "PJM world map settings");
            }
        } catch (IOException ignored) {
            // не записалось — настройки доживут до перезахода
        }
    }
}
