package ru.liko.pjmbasemod.common.missile;

/** Чистая геометрия серверных ограничений цели, без зависимости от мира Minecraft. */
public final class MissileTargetPolicy {

    private MissileTargetPolicy() {}

    public static boolean circleIntersectsRectangle(double x, double z, double radius,
                                                    double minX, double minZ, double maxX, double maxZ) {
        double nearestX = Math.max(Math.min(minX, maxX), Math.min(Math.max(minX, maxX), x));
        double nearestZ = Math.max(Math.min(minZ, maxZ), Math.min(Math.max(minZ, maxZ), z));
        double dx = x - nearestX;
        double dz = z - nearestZ;
        double safeRadius = Math.max(0.0, radius);
        return dx * dx + dz * dz <= safeRadius * safeRadius;
    }
}
