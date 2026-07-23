package ru.liko.pjmbasemod.common.missile;

/**
 * Чистая математика терминального захода крылатой ракеты.
 *
 * <p>Крылатая ракета сохраняет малую высоту и переходит к цели плавной квадратичной
 * дугой. Дистанция захода автоматически растягивается, если заданная в профиле
 * заставила бы ракету пикировать круче допустимого угла.</p>
 */
public final class CruiseTrajectory {

    public static final double MAX_IMPACT_ANGLE_DEGREES = 30.0;
    private static final double MAX_IMPACT_SLOPE = Math.tan(Math.toRadians(MAX_IMPACT_ANGLE_DEGREES));

    private CruiseTrajectory() {}

    public static double terminalAltitude(
            double cruiseAltitude,
            double targetAltitude,
            double horizontalRemaining,
            double terminalDistance) {
        double heightDifference = Math.abs(cruiseAltitude - targetAltitude);
        double distanceForAngle = 2.0 * heightDifference / MAX_IMPACT_SLOPE;
        double approachDistance = Math.max(Math.max(1.0, terminalDistance), distanceForAngle);
        double approach = clamp(1.0 - horizontalRemaining / approachDistance, 0.0, 1.0);
        return lerp(approach * approach, cruiseAltitude, targetAltitude);
    }

    private static double lerp(double amount, double start, double end) {
        return start + amount * (end - start);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
