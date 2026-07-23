package ru.liko.pjmbasemod.client.worldmap.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Папка регионов карты для текущего мира+измерения:
 * {@code <gameDir>/pjmbasemod/worldmap/<worldKey>/<dimKey>/}.
 * worldKey: SP → имя папки сохранения, MP → адрес сервера. Так карта одного сервера не смешивается
 * с картой другого и переживает вайп кампании (файлы на клиенте, а не в мире).
 *
 * <p>К санитизированному имени добавляется короткий хэш исходного: кириллические и прочие
 * не-ASCII имена миров после санитизации выродились бы в одни подчёркивания и слиплись.
 */
public final class WorldId {

    private WorldId() {}

    public static Path resolveDir(Minecraft mc, String dimKey) {
        String world = worldKey(mc);
        String dim = sanitize(dimKey.replace(':', '.').replace('/', '_'));
        Path root = mc.gameDirectory.toPath().resolve("pjmbasemod").resolve("worldmap");
        migrateLegacy(root, world);
        return root.resolve(world).resolve(dim);
    }

    private static String worldKey(Minecraft mc) {
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            try {
                // normalize(): ROOT — это ".", без нормализации getFileName() вернёт "."
                // и все одиночные миры слипнутся в одну папку.
                Path wp = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).normalize();
                return "sp_" + keyOf(wp.getFileName().toString());
            } catch (Throwable ignored) {
                // на всякий — падаем в общий ключ
            }
        }
        ServerData sd = mc.getCurrentServer();
        if (sd != null && sd.ip != null && !sd.ip.isEmpty()) {
            return "mp_" + keyOf(sd.ip);
        }
        return "unknown";
    }

    private static String keyOf(String raw) {
        return sanitize(raw) + "-" + Integer.toHexString(raw.hashCode());
    }

    private static String sanitize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    /** Разовая миграция старой папки {@code sp_.} (баг с ненормализованным ROOT-путём). */
    private static void migrateLegacy(Path root, String worldKey) {
        if (!worldKey.startsWith("sp_")) return;
        Path legacy = root.resolve("sp_.");
        Path target = root.resolve(worldKey);
        if (!Files.isDirectory(legacy) || Files.exists(target)) return;
        try {
            Files.move(legacy, target);
        } catch (IOException ignored) {
            // не удалось перенести — новый мир просто пересканируется
        }
    }
}
