package ru.liko.pjmbasemod.client.worldmap.io;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import ru.liko.pjmbasemod.client.worldmap.data.Region;
import ru.liko.pjmbasemod.client.worldmap.data.RegionKey;

/**
 * Диск-хранилище регионов. Кодирование — на главном потоке (консистентный снимок массивов),
 * запись — асинхронно в один демон-поток, атомарно (.tmp → ATOMIC_MOVE). Загрузка — синхронно
 * при входе в регион (нечасто). Пустой регион удаляет свой файл.
 */
public final class RegionStore {

    private static final Logger LOG = LogUtils.getLogger();

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pjm-worldmap-io");
        t.setDaemon(true);
        return t;
    });

    private Path baseDir;

    public void setBaseDir(Path dir) {
        this.baseDir = dir;
    }

    public Region load(RegionKey key) {
        if (baseDir == null) return null;
        Path f = fileFor(key);
        if (!Files.exists(f)) return null;
        try {
            byte[] data = Files.readAllBytes(f);
            return RegionCodec.decode(key, data);
        } catch (Exception e) {
            LOG.warn("[pjm worldmap] не читается регион {}: {}", f, e.toString());
            return null;
        }
    }

    /** Кодирует регион здесь (главный поток), пишет асинхронно. Пустой → удаляет файл. */
    public void saveAsync(Region region) {
        if (baseDir == null) return;
        Path f = fileFor(region.key);
        if (!region.hasAnyScanned()) {
            WRITER.submit(() -> deleteQuietly(f));
            return;
        }
        byte[] data;
        try {
            data = RegionCodec.encode(region);
        } catch (IOException e) {
            LOG.warn("[pjm worldmap] не кодируется регион {}: {}", region.key, e.toString());
            return;
        }
        WRITER.submit(() -> writeAtomic(f, data));
    }

    /** Синхронно дождаться слива очереди записи (логаут/смена измерения). */
    public void flush() {
        try {
            WRITER.submit(() -> {}).get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // таймаут/прерывание — не блокируем выход
        }
    }

    private Path fileFor(RegionKey key) {
        return baseDir.resolve("r." + key.rx() + "." + key.rz() + ".pjmmap");
    }

    private static void writeAtomic(Path f, byte[] data) {
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName().toString() + ".tmp");
            Files.write(tmp, data);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.warn("[pjm worldmap] не пишется регион {}: {}", f, e.toString());
        }
    }

    private static void deleteQuietly(Path f) {
        try {
            Files.deleteIfExists(f);
        } catch (IOException ignored) {
            // не критично
        }
    }
}
