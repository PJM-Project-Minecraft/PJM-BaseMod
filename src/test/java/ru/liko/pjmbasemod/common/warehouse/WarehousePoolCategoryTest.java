package ru.liko.pjmbasemod.common.warehouse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Контракт схлопывания пулов: весь старый конфиг (items.json, crates/) и NBT сохранённых
 * миров читаются через {@link WarehousePoolCategory#byId} — если алиасы отвалятся,
 * склады молча обнулятся при первой загрузке мира.
 */
class WarehousePoolCategoryTest {

    @Test
    void poolsAreSupplyAndRawOnly() {
        assertEquals(2, WarehousePoolCategory.values().length);
        assertEquals(WarehousePoolCategory.SUPPLY, WarehousePoolCategory.byId("supply"));
        assertEquals(WarehousePoolCategory.RAW, WarehousePoolCategory.byId("raw"));
    }

    @Test
    void legacyPoolsCollapseIntoSupply() {
        assertEquals(WarehousePoolCategory.SUPPLY, WarehousePoolCategory.byId("weapon"));
        assertEquals(WarehousePoolCategory.SUPPLY, WarehousePoolCategory.byId("equipment"));
        assertEquals(WarehousePoolCategory.SUPPLY, WarehousePoolCategory.byId("special"));
    }

    @Test
    void parsingIsCaseAndWhitespaceInsensitive() {
        assertEquals(WarehousePoolCategory.SUPPLY, WarehousePoolCategory.byId("  WeApOn "));
        assertEquals(WarehousePoolCategory.RAW, WarehousePoolCategory.byId("RAW"));
    }

    @Test
    void unknownPoolFallsBack() {
        assertNull(WarehousePoolCategory.byId("bogus"));
        assertNull(WarehousePoolCategory.byId(null));
        assertEquals(WarehousePoolCategory.RAW,
                WarehousePoolCategory.byIdOrDefault("bogus", WarehousePoolCategory.RAW));
    }

    /** id() — ключ записи в NBT/JSON; сырьё должно остаться отдельным пулом, а не слиться в supply. */
    @Test
    void idsRoundTrip() {
        for (WarehousePoolCategory pool : WarehousePoolCategory.values()) {
            assertEquals(pool, WarehousePoolCategory.byId(pool.id()));
        }
        assertEquals("supply", WarehousePoolCategory.SUPPLY.id());
        assertEquals("raw", WarehousePoolCategory.RAW.id());
    }
}
