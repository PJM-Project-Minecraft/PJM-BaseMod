package ru.liko.pjmbasemod.common.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Safe optional WRBDrones bridge для события «налёт дронов».
 * Все обращения к классам wrbdrones — только через {@link WrbDronesIntegration},
 * который класслоадится исключительно при загруженном моде.
 */
public final class WrbDronesCompat {

    private WrbDronesCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded("wrbdrones");
    }

    /** Регистрирует слушатель ShahedShotDownEvent (XP за сбитый дрон). Вызывается из конструктора мода. */
    public static void init() {
        if (isLoaded()) {
            WrbDronesIntegration.register();
        }
    }

    /**
     * Спавнит и запускает Shahed-136: точка спавна, курс {@code yRot} (запуск идёт по курсу),
     * цель наведения, крейсерская скорость (км/ч — конвертируется во внутреннюю единицу WRBDrones
     * и клампится к [min_speed_kmh, max_speed_kmh] из конфига), высота полёта (блоки Y) и
     * command-tag для опознания «событийных» дронов. Возвращает UUID сущности или {@code null},
     * если мод не загружен либо спавн не удался.
     */
    @Nullable
    public static UUID spawnShahed(ServerLevel level, Vec3 spawnPos, float yRot, Vec3 target,
                                   float speedKmh, float altitude, boolean terrainFollow, String entityTag) {
        return isLoaded()
                ? WrbDronesIntegration.spawnShahed(level, spawnPos, yRot, target, speedKmh, altitude, terrainFollow, entityTag)
                : null;
    }
}
