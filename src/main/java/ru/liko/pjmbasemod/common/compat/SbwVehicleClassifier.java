package ru.liko.pjmbasemod.common.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import ru.liko.pjmbasemod.Pjmbasemod;
import ru.liko.pjmbasemod.common.garage.GarageType;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Автоматическая классификация техники SuperbWarfare на авиацию/наземку.
 *
 * <p>SuperbWarfare хранит список летающей техники в датапак-файле
 * {@code data/superbwarfare/sbw/containers/aircraft.json}. Мы читаем его через серверный
 * {@link ResourceManager} при старте сервера, поэтому список авто-обновляется вместе с модом.
 * Если SuperbWarfare не установлен или файл недоступен, используется захардкоженный фоллбэк.</p>
 */
public final class SbwVehicleClassifier {

    private static final ResourceLocation AIRCRAFT_FILE =
            ResourceLocation.fromNamespaceAndPath("superbwarfare", "sbw/containers/aircraft.json");

    /** Известная на момент написания авиация SuperbWarfare — фоллбэк, если датапак недоступен. */
    private static final Set<String> DEFAULT_AIRCRAFT = Set.of(
            "superbwarfare:tom_6",
            "superbwarfare:kv_16",
            "superbwarfare:ah_6",
            "superbwarfare:mi_28",
            "superbwarfare:a_10a",
            "superbwarfare:ju_87");

    private static volatile Set<String> aircraftIds = DEFAULT_AIRCRAFT;

    private SbwVehicleClassifier() {}

    /** Перечитывает список авиации из датапака SuperbWarfare. Вызывается при старте сервера. */
    public static void reload(@Nullable MinecraftServer server) {
        if (server == null) return;
        ResourceManager manager = server.getResourceManager();
        try {
            Resource resource = manager.getResource(AIRCRAFT_FILE).orElse(null);
            if (resource == null) {
                aircraftIds = DEFAULT_AIRCRAFT;
                Pjmbasemod.LOGGER.info("Garage: aircraft.json SuperbWarfare не найден, использую встроенный список авиации ({}).",
                        DEFAULT_AIRCRAFT.size());
                return;
            }
            Set<String> parsed = new LinkedHashSet<>();
            try (Reader reader = resource.openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray list = root.getAsJsonArray("List");
                if (list != null) {
                    for (JsonElement element : list) {
                        String id = extractType(element);
                        if (id != null && !id.isBlank()) parsed.add(id.trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
            if (parsed.isEmpty()) {
                aircraftIds = DEFAULT_AIRCRAFT;
                Pjmbasemod.LOGGER.warn("Garage: aircraft.json SuperbWarfare пуст, использую встроенный список авиации.");
            } else {
                aircraftIds = parsed;
                Pjmbasemod.LOGGER.info("Garage: загружено {} типов авиации из датапака SuperbWarfare.", parsed.size());
            }
        } catch (Exception e) {
            aircraftIds = DEFAULT_AIRCRAFT;
            Pjmbasemod.LOGGER.error("Garage: не удалось прочитать aircraft.json SuperbWarfare, использую встроенный список авиации.", e);
        }
    }

    /** Запись списка — либо объект {@code {"Type": "id", "Weight": n}}, либо просто строка id. */
    @Nullable
    private static String extractType(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("Type")) return obj.get("Type").getAsString();
        }
        return null;
    }

    /** Является ли entity id летающей техникой SuperbWarfare. */
    public static boolean isAircraft(@Nullable String entityId) {
        return entityId != null && aircraftIds.contains(entityId.trim().toLowerCase(Locale.ROOT));
    }

    /** Авто-определение типа гаража по entity id техники (по умолчанию — наземка). */
    public static GarageType classify(@Nullable ResourceLocation entityId) {
        return entityId != null && isAircraft(entityId.toString()) ? GarageType.AVIATION : GarageType.GROUND;
    }
}
