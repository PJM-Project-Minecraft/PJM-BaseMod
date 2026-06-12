package ru.liko.pjmbasemod.common.inventory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Реестр конфига ограничения слотов инвентаря. Грузит/создаёт
 * {@code config/pjmbasemod/inventory/slots.json}. Образец — {@code RankRegistry}.
 */
public final class InventoryLimitRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final InventoryLimitRegistry INSTANCE = new InventoryLimitRegistry();

    private InventoryLimitConfig config = InventoryLimitConfig.defaults();
    private boolean loaded;

    // Кэш для быстрой авторитетной проверки из SlotMixin (вызывается на каждый клик).
    private volatile boolean enabledCache = true;
    private volatile Set<Integer> lockedSlotCache = new HashSet<>();

    private InventoryLimitRegistry() {
        refreshCache();
    }

    public static InventoryLimitRegistry get() {
        return INSTANCE;
    }

    public synchronized InventoryLimitConfig config() {
        if (!loaded) reload();
        return config;
    }

    public synchronized boolean reload() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                config = InventoryLimitConfig.defaults();
                write(file, config);
                loaded = true;
                refreshCache();
                Pjmbasemod.LOGGER.info("InventoryLimit: created default config at {}", file);
                return true;
            }

            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                InventoryLimitConfig loadedConfig = GSON.fromJson(reader, InventoryLimitConfig.class);
                config = loadedConfig == null ? InventoryLimitConfig.defaults() : loadedConfig;
                config.normalize();
                loaded = true;
                refreshCache();
                Pjmbasemod.LOGGER.info("InventoryLimit: loaded {} locked slot(s), enabled={}.",
                        config.lockedSlots().size(), config.enabled());
                return true;
            }
        } catch (Exception e) {
            loaded = true;
            config = InventoryLimitConfig.defaults();
            refreshCache();
            Pjmbasemod.LOGGER.error("InventoryLimit: failed to load {}, using defaults.", file, e);
            return false;
        }
    }

    private void refreshCache() {
        enabledCache = config.enabled();
        lockedSlotCache = new HashSet<>(config.lockedSlots());
    }

    public boolean isEnabled() {
        return config().enabled();
    }

    /**
     * Быстрая авторитетная проверка: заблокирован ли слот контейнера инвентаря игрока.
     * Читается из кэша — пригодна для вызова на каждый клик (из {@code SlotMixin}).
     */
    public boolean isSlotLocked(int containerSlot) {
        if (!loaded) reload();
        return enabledCache && lockedSlotCache.contains(containerSlot);
    }

    /** Заблокированные индексы слотов как множество (для быстрой проверки {@code contains}). */
    public Set<Integer> lockedSlots() {
        return new LinkedHashSet<>(config().lockedSlots());
    }

    private Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(Pjmbasemod.MODID).resolve("inventory_limit.json");
    }

    private void write(Path file, InventoryLimitConfig config) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        }
    }
}
