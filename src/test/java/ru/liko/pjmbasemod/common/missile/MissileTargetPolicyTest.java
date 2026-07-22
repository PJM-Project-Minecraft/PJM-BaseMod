package ru.liko.pjmbasemod.common.missile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissileTargetPolicyTest {

    @Test
    void rejectsCircleTouchingProtectedRectangle() {
        assertTrue(MissileTargetPolicy.circleIntersectsRectangle(
                115, 105, 5, 100, 100, 110, 110));
    }

    @Test
    void acceptsTargetOutsideProtectedFootprint() {
        assertFalse(MissileTargetPolicy.circleIntersectsRectangle(
                116, 105, 5, 100, 100, 110, 110));
    }

    @Test
    void handlesReversedRectangleCorners() {
        assertTrue(MissileTargetPolicy.circleIntersectsRectangle(
                105, 105, 0, 110, 110, 100, 100));
    }
}
