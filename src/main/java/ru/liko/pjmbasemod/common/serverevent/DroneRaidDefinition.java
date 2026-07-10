package ru.liko.pjmbasemod.common.serverevent;

import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Gson-модель конфига события «налёт дронов» (config/pjmbasemod/events/drone_raid.json).
 * Обычные классы с дефолтами вместо records: Gson заполняет отсутствующие поля значениями по умолчанию.
 */
public final class DroneRaidDefinition {

    private DroneRaidDefinition() {}

    /** Идентификаторы состава налёта. */
    public static final String COMPOSITION_SHAHED_ONLY = "shahed_only";
    public static final String COMPOSITION_COMBINED = "combined";

    /** Точка налёта — заранее заданная админом цель. */
    public static final class RaidPoint {
        public String name = "";
        public String dimension = "minecraft:overworld";
        public double x;
        public double y;
        public double z;
        /** Радиус зоны опасности (блоки): разброс целей дронов и круг на карте. */
        public int radius = 80;
        /** Команда scoreboard, к которой привязана точка (атакуемая команда). Пусто — без штрафа XP. */
        public String teamId = "";

        public boolean isValid() {
            return name != null && !name.isBlank() && dimension != null && !dimension.isBlank();
        }

        public void normalize() {
            if (name == null) name = "";
            name = name.trim();
            if (dimension == null || dimension.isBlank()) dimension = "minecraft:overworld";
            dimension = dimension.trim();
            radius = Mth.clamp(radius, 8, 512);
            if (teamId == null) teamId = "";
            teamId = teamId.trim();
        }
    }

    /** Общие параметры налёта. */
    public static final class RaidSettings {
        public int waveCount = 3;
        public int dronesPerWave = 4;
        public int waveIntervalSeconds = 45;
        /** Дистанция спавна дронов от цели (блоки). Должна быть меньше shahed136MaxDistance в конфиге WRBDrones. */
        public int spawnDistance = 600;
        /** Высота полёта (абсолютная Y); фактически берётся max(spawnAltitude, y точки + 60). */
        public int spawnAltitude = 150;
        /** Крейсерская скорость Shahed-136, км/ч. Конвертируется во внутреннюю единицу WRBDrones (км/ч / 72) и клампится к [min_speed_kmh, max_speed_kmh] из конфига WRBDrones. */
        public double speed = 200;
        public boolean terrainFollow = false;
        public int xpPerKill = 15;
        /** Таймаут события — страховка от «зависших» дронов. */
        public int maxDurationMinutes = 15;

        /** Разрешить случайный выбор «комбинированного» налёта (волны с разными параметрами). */
        public boolean allowCombined = true;
        /** Пул вариаций параметров волны для комбинированного налёта. Пусто — комбинированные невозможны. */
        public List<WaveProfile> combinedProfiles = new ArrayList<>();
        /** XP-штраф атакуемой команде за КАЖДЫЙ дрон, упавший в цель (при проигрыше налёта). */
        public int xpLossPerHit = 40;
        /** Доля дронов, упавших в цель, при превышении которой налёт считается проигранным (0..1). */
        public double lossThreshold = 0.5;

        public void normalize() {
            waveCount = Mth.clamp(waveCount, 1, 20);
            dronesPerWave = Mth.clamp(dronesPerWave, 1, 16);
            waveIntervalSeconds = Mth.clamp(waveIntervalSeconds, 5, 600);
            spawnDistance = Mth.clamp(spawnDistance, 100, 4000);
            spawnAltitude = Mth.clamp(spawnAltitude, 80, 320);
            speed = Mth.clamp(speed, 50.0, 1000.0);
            xpPerKill = Mth.clamp(xpPerKill, 0, 10000);
            maxDurationMinutes = Mth.clamp(maxDurationMinutes, 1, 120);
            xpLossPerHit = Mth.clamp(xpLossPerHit, 0, 10000);
            lossThreshold = Mth.clamp(lossThreshold, 0.0, 1.0);
            if (combinedProfiles == null) {
                combinedProfiles = new ArrayList<>();
            } else {
                combinedProfiles.removeIf(p -> p == null);
                for (WaveProfile profile : combinedProfiles) {
                    profile.normalize();
                }
            }
        }
    }

    /**
     * Вариация параметров одной волны для «комбинированного» налёта. Поля {@code null}/{@code -1}
     * означают «наследовать общий параметр из {@link RaidSettings}».
     */
    public static final class WaveProfile {
        @Nullable public Integer dronesPerWave = null;
        @Nullable public Double speed = null;
        @Nullable public Integer spawnAltitude = null;
        @Nullable public Integer spawnDistance = null;
        @Nullable public Boolean terrainFollow = null;

        public void normalize() {
            if (dronesPerWave != null) dronesPerWave = Mth.clamp(dronesPerWave, 1, 16);
            if (speed != null) speed = Mth.clamp(speed, 50.0, 1000.0);
            if (spawnAltitude != null) spawnAltitude = Mth.clamp(spawnAltitude, 80, 320);
            if (spawnDistance != null) spawnDistance = Mth.clamp(spawnDistance, 100, 4000);
        }
    }
}
