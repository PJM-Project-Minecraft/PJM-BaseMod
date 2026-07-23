package ru.liko.pjmbasemod.common.missile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CruiseTrajectoryTest {

    @Test
    void cruiseMissileEntersTargetWithoutBallisticDive() {
        double cruiseY = 124.0;
        double targetY = 64.0;
        double speedPerTick = 2500.0 / (16.0 * 20.0);
        double previousY = CruiseTrajectory.terminalAltitude(
                cruiseY, targetY, speedPerTick, 48.0);
        double impactY = CruiseTrajectory.terminalAltitude(
                cruiseY, targetY, 0.0, 48.0);

        double impactAngle = Math.toDegrees(Math.atan2(previousY - impactY, speedPerTick));

        assertEquals(targetY, impactY, 1.0E-9);
        assertTrue(impactAngle <= CruiseTrajectory.MAX_IMPACT_ANGLE_DEGREES + 0.5,
                "крылатая ракета не должна входить как баллистическая: " + impactAngle + "°");
    }

    @Test
    void approachIsLowSmoothAndMonotonic() {
        double cruiseY = 92.0;
        double targetY = 64.0;
        double speedPerTick = 2500.0 / (17.0 * 20.0);
        double previousY = CruiseTrajectory.terminalAltitude(
                cruiseY, targetY, 400.0, 56.0);

        assertEquals(cruiseY, previousY, 1.0E-9);
        for (double remaining = 400.0 - speedPerTick; remaining >= 0.0; remaining -= speedPerTick) {
            double currentY = CruiseTrajectory.terminalAltitude(
                    cruiseY, targetY, remaining, 56.0);
            double pitch = Math.toDegrees(Math.atan2(previousY - currentY, speedPerTick));

            assertTrue(currentY <= previousY + 1.0E-9, "заход не должен снова набирать высоту");
            assertTrue(currentY >= targetY - 1.0E-9, "траектория не должна проскакивать ниже цели");
            assertTrue(pitch <= CruiseTrajectory.MAX_IMPACT_ANGLE_DEGREES + 0.5,
                    "слишком резкое изменение тангажа: " + pitch + "°");
            previousY = currentY;
        }
    }
}
