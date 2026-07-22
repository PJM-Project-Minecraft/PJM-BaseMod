package ru.liko.pjmbasemod.common.missile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissileDefinitionTest {

    @Test
    void normalizeClampsDangerousAndBrokenValues() {
        MissileDefinition definition = new MissileDefinition();
        definition.id = "  Storm Shadow!!!  ";
        definition.trajectory = "unknown";
        definition.supplyCost = -20;
        definition.cooldownSeconds = -1;
        definition.flightSeconds = 0;
        definition.spawnDistance = 9000;
        definition.cruiseHeight = -10;
        definition.terminalDiveDistance = 0;
        definition.ballisticApex = 5000;
        definition.damage = -1;
        definition.radius = 1000;
        definition.hitPoints = 0;
        definition.shotDownPower = 3;

        definition.normalize();

        assertEquals("storm_shadow", definition.id());
        assertEquals(MissileDefinition.Trajectory.CRUISE, definition.trajectoryType());
        assertEquals(0, definition.supplyCost());
        assertEquals(0, definition.cooldownSeconds());
        assertEquals(2, definition.flightSeconds());
        assertEquals(160, definition.spawnDistance());
        assertEquals(4, definition.cruiseHeight());
        assertEquals(8, definition.terminalDiveDistance());
        assertEquals(320, definition.ballisticApex());
        assertEquals(1.0f, definition.damage());
        assertEquals(40.0f, definition.radius());
        assertEquals(1.0f, definition.hitPoints());
        assertEquals(1.0f, definition.shotDownPower());
    }

    @Test
    void normalizedDefinitionKeepsStableDisplayAndValidity() {
        MissileDefinition definition = MissileDefinition.create(
                "iskander_m", "Искандер-М", MissileDefinition.Trajectory.BALLISTIC,
                80, 1200, 8, 96, 24, 32, 220,
                260.0f, 14.0f, 80.0f, 0.35f, false);

        assertTrue(definition.isValid());
        assertEquals("missile.pjmbasemod.iskander_m", definition.translationKey());
        assertEquals(MissileDefinition.Trajectory.BALLISTIC, definition.trajectoryType());
        assertFalse(definition.destroyBlocks());
    }
}
