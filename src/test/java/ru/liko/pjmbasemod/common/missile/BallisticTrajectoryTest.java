package ru.liko.pjmbasemod.common.missile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BallisticTrajectoryTest {

    private static final double START_Y = 110.0;
    private static final double TARGET_Y = 70.0;
    private static final double DISTANCE = 2500.0;
    private static final double APEX_HEIGHT = 500.0;
    private static final double APEX_Y = START_Y + APEX_HEIGHT;

    private static BallisticTrajectory.Sample at(double t) {
        return BallisticTrajectory.sample(t, START_Y, TARGET_Y, DISTANCE, APEX_HEIGHT);
    }

    @Test
    void keepsStartAndTarget() {
        assertEquals(START_Y, at(0.0).altitude(), 1.0E-9);
        assertEquals(0.0, at(0.0).horizontalFraction(), 1.0E-9);
        assertEquals(TARGET_Y, at(1.0).altitude(), 1.0E-9);
        assertEquals(1.0, at(1.0).horizontalFraction(), 1.0E-9);
    }

    @Test
    void reachesConfiguredApex() {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 4000; i++) max = Math.max(max, at(i / 4000.0).altitude());
        assertEquals(APEX_Y, max, 1.0);
    }

    @Test
    void startsVertically() {
        BallisticTrajectory.Sample early = at(0.01);
        double horizontal = early.horizontalFraction() * DISTANCE;
        double climb = early.altitude() - START_Y;
        assertTrue(climb > 0.0);
        double angle = Math.toDegrees(Math.atan2(climb, horizontal));
        assertTrue(angle > 75.0, "старт должен быть почти вертикальным, а не " + angle + "°");
    }

    @Test
    void entersTargetAtEightyDegrees() {
        BallisticTrajectory.Sample before = at(1.0 - 1.0 / 220.0);
        BallisticTrajectory.Sample impact = at(1.0);
        double horizontal = (impact.horizontalFraction() - before.horizontalFraction()) * DISTANCE;
        double angle = Math.toDegrees(Math.atan((before.altitude() - impact.altitude()) / horizontal));
        assertEquals(BallisticTrajectory.IMPACT_ANGLE_DEGREES, angle, 1.0E-4);
    }

    @Test
    void continuousWithoutJumps() {
        BallisticTrajectory.Sample prev = at(0.0);
        for (int i = 1; i <= 4000; i++) {
            BallisticTrajectory.Sample cur = at(i / 4000.0);
            assertTrue(cur.horizontalFraction() >= prev.horizontalFraction() - 1.0E-9,
                    "горизонталь не должна откатываться назад");
            assertTrue(Math.abs(cur.altitude() - prev.altitude()) < 5.0,
                    "скачок высоты на шаге " + i);
            prev = cur;
        }
    }

    @Test
    void shortRouteRemainsContinuousAndKeepsImpactAngle() {
        double lastTick = 1.0 / 40.0;
        BallisticTrajectory.Sample before = BallisticTrajectory.sample(
                1.0 - lastTick, 120.0, 70.0, 64.0, 800.0);
        BallisticTrajectory.Sample impact = BallisticTrajectory.sample(
                1.0, 120.0, 70.0, 64.0, 800.0);
        double horizontal = 64.0 * (impact.horizontalFraction() - before.horizontalFraction());
        double angle = Math.toDegrees(Math.atan(
                (before.altitude() - impact.altitude()) / horizontal));
        assertTrue(Double.isFinite(before.altitude()));
        assertEquals(70.0, impact.altitude(), 1.0E-9);
        assertEquals(BallisticTrajectory.IMPACT_ANGLE_DEGREES, angle, 1.0E-4);
    }
}
