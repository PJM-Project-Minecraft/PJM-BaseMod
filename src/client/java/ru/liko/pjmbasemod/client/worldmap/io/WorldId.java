package ru.liko.pjmbasemod.client.worldmap.io;

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
 */
public final class WorldId {

    private WorldId() {}

    public static Path resolveDir(Minecraft mc, String dimKey) {
        String world = worldKey(mc);
        String dim = sanitize(dimKey.replace(':', '.').replace('/', '_'));
        return mc.gameDirectory.toPath()
                .resolve("pjmbasemod").resolve("worldmap")
                .resolve(world).resolve(dim);
    }

    private static String worldKey(Minecraft mc) {
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            try {
                Path wp = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
                return "sp_" + sanitize(wp.getFileName().toString());
            } catch (Throwable ignored) {
                // на всякий — падаем в общий ключ
            }
        }
        ServerData sd = mc.getCurrentServer();
        if (sd != null && sd.ip != null && !sd.ip.isEmpty()) {
            return "mp_" + sanitize(sd.ip);
        }
        return "unknown";
    }

    private static String sanitize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }
}
