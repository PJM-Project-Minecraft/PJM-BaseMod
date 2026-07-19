package ru.liko.pjmbasemod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Ключи warehouseByTeam из config.json сверяются с ownerTeamId точки, а тот всегда
 * нормализован (Teams.normalize: trim + lowercase). Проверяем, что normalize() приводит
 * карту к тому же виду — иначе доход с точек молча не начисляется.
 */
class CapturePointIncomeConfigTest {

    @Test
    void normalizeLowercasesTeamKeysAndTrims() {
        Config.CapturePoints data = new Config.CapturePoints();
        data.warehouseByTeam.put("Team1", "wh_red");
        data.warehouseByTeam.put("  TEAM2  ", "  wh_blue  ");

        data.normalize();

        assertEquals("wh_red", data.warehouseByTeam.get("team1"));
        assertEquals("wh_blue", data.warehouseByTeam.get("team2"));
    }

    @Test
    void normalizeDropsBlankAndNullEntries() {
        Config.CapturePoints data = new Config.CapturePoints();
        data.warehouseByTeam.put("team1", "");
        data.warehouseByTeam.put("", "wh_red");
        data.warehouseByTeam.put("team2", null);

        data.normalize();

        assertEquals(0, data.warehouseByTeam.size());
        assertNull(data.warehouseByTeam.get("team1"));
    }
}
