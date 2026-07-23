package ru.liko.pjmbasemod.common.missile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BallisticTrajectoryTest {

    @Test
    void keepsStartTargetAndConfiguredApex() {
        double startY = 110.0;
        double targetY = 70.0;
        double distance = 2500.0;
        double apexHeight = 500.0;
        double apexY = startY + apexHeight;
        double diveDistance = (apexY - targetY)
                / (Math.tan(Math.toRadians(BallisticTrajectory.IMPACT_ANGLE_DEGREES)) * 0.875);
        double diveStart = 1.0 - diveDistance / distance;

        assertEquals(startY,
                BallisticTrajectory.altitudeAt(0.0, startY, targetY, distance, apexHeight),
                1.0E-9);
        assertEquals(apexY,
                BallisticTrajectory.altitudeAt(diveStart, startY, targetY, distance, apexHeight),
                1.0E-9);
        assertEquals(targetY,
                BallisticTrajectory.altitudeAt(1.0, startY, targetY, distance, apexHeight),
                1.0E-9);
    }

    @Test
    void entersTargetAtEightyDegrees() {
        double startY = 110.0;
        double targetY = 70.0;
        double distance = 2500.0;
        double apexHeight = 500.0;
        double lastTick = 1.0 / 220.0;
        double beforeImpact = BallisticTrajectory.altitudeAt(
                1.0 - lastTick, startY, targetY, distance, apexHeight);
        double impact = BallisticTrajectory.altitudeAt(
                1.0, startY, targetY, distance, apexHeight);
        double verticalPerHorizontal = (beforeImpact - impact) / (distance * lastTick);
        double angle = Math.toDegrees(Math.atan(verticalPerHorizontal));

        assertEquals(BallisticTrajectory.IMPACT_ANGLE_DEGREES, angle, 1.0E-4);
    }

    @Test
    void shortRouteRemainsContinuousAndKeepsImpactAngle() {
        double startY = 120.0;
        double targetY = 70.0;
        double distance = 64.0;
        double lastTick = 1.0 / 40.0;
        double beforeImpact = BallisticTrajectory.altitudeAt(
                1.0 - lastTick, startY, targetY, distance, 800.0);
        double impact = BallisticTrajectory.altitudeAt(
                1.0, startY, targetY, distance, 800.0);
        double angle = Math.toDegrees(Math.atan(
                (beforeImpact - impact) / (distance * lastTick)));

        assertTrue(Double.isFinite(beforeImpact));
        assertEquals(targetY, impact, 1.0E-9);
        assertEquals(BallisticTrajectory.IMPACT_ANGLE_DEGREES, angle, 1.0E-4);
    }
}
