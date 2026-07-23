package ru.liko.pjmbasemod.common.missile;

/**
 * Вертикальный профиль баллистической ракеты.
 *
 * <p>До апогея ракета плавно набирает высоту. На терминальном участке снижение
 * ускоряется от горизонтального полёта до заданного угла входа в цель.</p>
 */
public final class BallisticTrajectory {

    public static final double IMPACT_ANGLE_DEGREES = 80.0;

    private static final double IMPACT_SLOPE = Math.tan(Math.toRadians(IMPACT_ANGLE_DEGREES));
    /** Доля пикирования, за которую наклон плавно возрастает от 0 до 80°. */
    private static final double DIVE_TRANSITION_FRACTION = 0.25;
    /** Начало прямого участка в запасном профиле для слишком коротких маршрутов. */
    private static final double FALLBACK_LINEAR_START = 0.75;
    private static final double EPSILON = 1.0E-6;

    private BallisticTrajectory() {}

    /**
     * Возвращает высоту в нормализованный момент полёта {@code progress}.
     *
     * @param horizontalDistance горизонтальная длина всего маршрута
     * @param apexHeight         высота апогея над более высокой из начальной и конечной точек
     */
    public static double altitudeAt(double progress, double startY, double targetY,
                                    double horizontalDistance, double apexHeight) {
        double t = clamp01(progress);
        double distance = Math.max(0.0, horizontalDistance);
        if (distance < EPSILON) {
            return lerp(t, startY, targetY);
        }

        double apexY = Math.max(startY, targetY) + Math.max(0.0, apexHeight);
        double drop = apexY - targetY;
        // Первая четверть пикирования плавно доворачивает ракету до 80°, затем
        // она идёт по прямой. Средний наклон учитывает площадь переходного участка.
        double transitionFactor = 1.0 - DIVE_TRANSITION_FRACTION / 2.0;
        double diveDistance = drop / (IMPACT_SLOPE * transitionFactor);
        double diveStart = 1.0 - diveDistance / distance;

        if (diveStart <= EPSILON) {
            return hermiteFallback(t, startY, targetY, distance);
        }
        if (t < diveStart) {
            double ascent = t / diveStart;
            double smoothAscent = ascent * ascent * (3.0 - 2.0 * ascent);
            return lerp(smoothAscent, startY, apexY);
        }

        double dive = (t - diveStart) / (1.0 - diveStart);
        if (dive < DIVE_TRANSITION_FRACTION) {
            double transitionDrop = 0.5 * IMPACT_SLOPE * diveDistance
                    * dive * dive / DIVE_TRANSITION_FRACTION;
            return apexY - transitionDrop;
        }
        double transitionDrop = 0.5 * IMPACT_SLOPE * diveDistance
                * DIVE_TRANSITION_FRACTION;
        double linearDrop = IMPACT_SLOPE * diveDistance
                * (dive - DIVE_TRANSITION_FRACTION);
        return apexY - transitionDrop - linearDrop;
    }

    /**
     * Запасной профиль для слишком короткого маршрута. Сплайн плавно переходит
     * в прямой терминальный участок под 80°.
     */
    private static double hermiteFallback(double t, double startY, double targetY, double distance) {
        double linearStartY = targetY
                + IMPACT_SLOPE * distance * (1.0 - FALLBACK_LINEAR_START);
        if (t >= FALLBACK_LINEAR_START) {
            return targetY + IMPACT_SLOPE * distance * (1.0 - t);
        }

        double spline = t / FALLBACK_LINEAR_START;
        double spline2 = spline * spline;
        double spline3 = spline2 * spline;
        double startBasis = 2.0 * spline3 - 3.0 * spline2 + 1.0;
        double targetBasis = -2.0 * spline3 + 3.0 * spline2;
        double targetTangentBasis = spline3 - spline2;
        double splineDistance = distance * FALLBACK_LINEAR_START;
        return startBasis * startY
                + targetBasis * linearStartY
                + targetTangentBasis * (-splineDistance * IMPACT_SLOPE);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double lerp(double progress, double start, double end) {
        return start + (end - start) * progress;
    }
}
