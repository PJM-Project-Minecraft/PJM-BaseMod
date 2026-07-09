package ru.liko.pjmbasemod.common.serverevent;

import net.minecraft.util.Mth;

/**
 * Gson-модель конфига события «радиоразведка»
 * (config/pjmbasemod/events/signal_hunt.json). Обычные классы с дефолтами вместо
 * records: Gson заполняет отсутствующие поля значениями по умолчанию.
 */
public final class SignalHuntDefinition {

    private SignalHuntDefinition() {}

    /** Зона радиоразведки — область, в которой прячутся маяки. */
    public static final class SignalHuntZone {
        public String name = "";
        public String dimension = "minecraft:overworld";
        public double centerX;
        public double centerY;
        public double centerZ;
        /** Радиус зоны (блоки) для карты и разброса маяков. */
        public int radius = 200;
        /** Количество маяков в зоне. */
        public int beaconCount = 3;
        /** Разброс маяков от центра зоны (блоки). 0 — все в центре. */
        public int beaconSpread = 150;

        public boolean isValid() {
            return name != null && !name.isBlank() && dimension != null && !dimension.isBlank();
        }

        public void normalize() {
            if (name == null) name = "";
            name = name.trim();
            if (dimension == null || dimension.isBlank()) dimension = "minecraft:overworld";
            dimension = dimension.trim();
            radius = Mth.clamp(radius, 16, 4000);
            beaconCount = Mth.clamp(beaconCount, 1, 32);
            beaconSpread = Mth.clamp(beaconSpread, 0, radius);
        }
    }

    /** Параметры радиоразведки. */
    public static final class SignalHuntSettings {
        /** Радиус вокруг маяка, в котором сигнал = 100% (блоки). */
        public int signalRadius = 20;
        /** Дальность, за которой сигнал = 0 (блоки). */
        public int signalMaxDistance = 400;
        /** Радиус, в котором возможен захват маяка (блоки). Должен быть <= signalRadius. */
        public int captureRadius = 6;
        /** Время удержания для перехвата одного маяка (секунды). */
        public int captureSeconds = 5;
        /** XP за перехват одного маяка нашедшему игроку. */
        public int xpPerBeacon = 30;
        /** Таймаут события — страховка от «зависшей» радиоразведки. */
        public int maxDurationMinutes = 20;

        public void normalize() {
            signalRadius = Mth.clamp(signalRadius, 1, 200);
            signalMaxDistance = Mth.clamp(signalMaxDistance, signalRadius + 10, 8000);
            captureRadius = Mth.clamp(captureRadius, 1, signalRadius);
            captureSeconds = Mth.clamp(captureSeconds, 0, 300);
            xpPerBeacon = Mth.clamp(xpPerBeacon, 0, 10000);
            maxDurationMinutes = Mth.clamp(maxDurationMinutes, 1, 240);
        }
    }

    /** Маяк: фиксированная точка в мире. Невидимая — существует только в состоянии события. */
    public static final class BeaconSnapshot {
        public double x;
        public double y;
        public double z;
        public boolean captured;
        /** UUID игрока, перехватившего маяк (для отчётности). */
        public java.util.UUID capturedBy;
    }
}
