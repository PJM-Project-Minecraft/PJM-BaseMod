package ru.liko.pjmbasemod.common.missile;

/**
 * Квазибаллистический профиль стратегической ракеты.
 *
 * <p>Вертикальный старт (квадратичная Безье с вертикальной начальной касательной),
 * параболический набор до апогея с нулевым наклоном в вершине, затем терминальное
 * пикирование с плавным доворотом до 80°. Все участки C1-непрерывны. Позиция —
 * чистая функция нормализованного времени: класс без MC-зависимостей.</p>
 */
public final class BallisticTrajectory {

    public static final double IMPACT_ANGLE_DEGREES = 80.0;

    /** Точка профиля: доля горизонтального пути [0..1] и высота. */
    public record Sample(double horizontalFraction, double altitude) {}

    private static final double IMPACT_SLOPE = Math.tan(Math.toRadians(IMPACT_ANGLE_DEGREES));
    /** Доля пикирования, за которую наклон плавно возрастает от 0 до 80°. */
    private static final double DIVE_TRANSITION_FRACTION = 0.25;
    /** Начало прямого участка в запасном профиле для слишком коротких маршрутов. */
    private static final double FALLBACK_LINEAR_START = 0.75;
    /** Доля горизонтали, занимаемая вертикальным стартом. */
    private static final double BOOST_HORIZONTAL_FRACTION = 0.06;
    /** Доля времени на буст — из непрерывности горизонтальной скорости: 2β/(1+β). */
    private static final double BOOST_TIME_FRACTION =
            2.0 * BOOST_HORIZONTAL_FRACTION / (1.0 + BOOST_HORIZONTAL_FRACTION);
    /** Какую долю подъёма к апогею проходит буст. */
    private static final double BOOST_CLIMB_FRACTION = 0.25;
    private static final double EPSILON = 1.0E-6;

    private BallisticTrajectory() {}

    /**
     * Точка траектории в нормализованный момент полёта {@code progress}.
     *
     * @param horizontalDistance горизонтальная длина всего маршрута
     * @param apexHeight         высота апогея над более высокой из начальной и конечной точек
     */
    public static Sample sample(double progress, double startY, double targetY,
                                double horizontalDistance, double apexHeight) {
        double t = clamp01(progress);
        double distance = Math.max(0.0, horizontalDistance);
        if (distance < EPSILON) {
            return new Sample(t, lerp(t, startY, targetY));
        }

        double apexY = Math.max(startY, targetY) + Math.max(0.0, apexHeight);
        double drop = apexY - targetY;
        // Средний наклон пикирования учитывает площадь переходного участка (доворот 0→80°).
        double transitionFactor = 1.0 - DIVE_TRANSITION_FRACTION / 2.0;
        double diveDistance = drop / (IMPACT_SLOPE * transitionFactor);
        double diveStart = 1.0 - diveDistance / distance;

        if (diveStart <= BOOST_HORIZONTAL_FRACTION + 0.05) {
            // Маршрут слишком короткий для буста и параболы — запасной сплайн.
            return new Sample(t, hermiteFallback(t, startY, targetY, distance));
        }

        double u = horizontalAt(t);
        return new Sample(u, altitudeAt(u, startY, apexY, diveStart, diveDistance));
    }

    /** Тайм-варп: буст разгоняет горизонтальную скорость от нуля, дальше она постоянна. */
    private static double horizontalAt(double t) {
        if (t < BOOST_TIME_FRACTION) {
            double w = t / BOOST_TIME_FRACTION;
            return BOOST_HORIZONTAL_FRACTION * w * w;
        }
        return BOOST_HORIZONTAL_FRACTION + (1.0 - BOOST_HORIZONTAL_FRACTION)
                * (t - BOOST_TIME_FRACTION) / (1.0 - BOOST_TIME_FRACTION);
    }

    private static double altitudeAt(double u, double startY, double apexY,
                                     double diveStart, double diveDistance) {
        double burnoutY = startY + BOOST_CLIMB_FRACTION * (apexY - startY);
        if (u < BOOST_HORIZONTAL_FRACTION) {
            // Безье: старт строго вверх, конечная касательная равна начальному наклону параболы.
            double w = Math.sqrt(u / BOOST_HORIZONTAL_FRACTION);
            double slopeU = 2.0 * (apexY - burnoutY) / (diveStart - BOOST_HORIZONTAL_FRACTION);
            double controlY = Math.max(startY + 1.0,
                    burnoutY - slopeU * BOOST_HORIZONTAL_FRACTION);
            double inv = 1.0 - w;
            return inv * inv * startY + 2.0 * w * inv * controlY + w * w * burnoutY;
        }
        if (u < diveStart) {
            // Парабола: замедляющийся набор к апогею, наклон в вершине 0 (стык с пикированием).
            double w = (u - BOOST_HORIZONTAL_FRACTION) / (diveStart - BOOST_HORIZONTAL_FRACTION);
            double inv = 1.0 - w;
            return apexY - (apexY - burnoutY) * inv * inv;
        }
        double dive = (u - diveStart) / (1.0 - diveStart);
        if (dive < DIVE_TRANSITION_FRACTION) {
            double transitionDrop = 0.5 * IMPACT_SLOPE * diveDistance
                    * dive * dive / DIVE_TRANSITION_FRACTION;
            return apexY - transitionDrop;
        }
        double transitionDrop = 0.5 * IMPACT_SLOPE * diveDistance * DIVE_TRANSITION_FRACTION;
        double linearDrop = IMPACT_SLOPE * diveDistance * (dive - DIVE_TRANSITION_FRACTION);
        return apexY - transitionDrop - linearDrop;
    }

    /**
     * Запасной профиль для слишком короткого маршрута. Сплайн плавно переходит
     * в прямой терминальный участок под 80°. Горизонталь — равномерная (u = t).
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
