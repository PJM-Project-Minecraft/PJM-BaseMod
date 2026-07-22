package ru.liko.pjmbasemod.common.warehouse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Регрессия: один ItemStack не должен создавать арбитраж между разными записями склада. */
class WarehouseTradePolicyTest {

    @Test
    void twoRocketsBoughtForThirtyCannotBeSoldIndividuallyForSeventy() {
        List<WarehouseTradePolicy.PurchaseOffer> equivalentOffers = List.of(
                new WarehouseTradePolicy.PurchaseOffer(30, 2),
                new WarehouseTradePolicy.PurchaseOffer(35, 1));

        int firstRocket = WarehouseTradePolicy.safeRefund(35, 1, 1.0, equivalentOffers);
        int secondRocket = WarehouseTradePolicy.safeRefund(35, 1, 1.0, equivalentOffers);

        assertEquals(30, firstRocket + secondRocket,
                "возврат за две ракеты не должен превышать самую дешёвую цену их покупки");
    }

    @Test
    void refundCannotExceedCostWithinOneDefinition() {
        int refund = WarehouseTradePolicy.safeRefund(
                35, 2, 2.0, List.of(new WarehouseTradePolicy.PurchaseOffer(30, 2)));

        assertEquals(30, refund);
    }

    @Test
    void legitimateDiscountedRefundIsPreserved() {
        int refund = WarehouseTradePolicy.safeRefund(
                20, 2, 2.0, List.of(new WarehouseTradePolicy.PurchaseOffer(30, 2)));

        assertEquals(20, refund);
    }

    @Test
    void durabilityStillReducesSafeRefund() {
        int refund = WarehouseTradePolicy.safeRefund(
                35, 1, 0.5, List.of(new WarehouseTradePolicy.PurchaseOffer(30, 2)));

        assertEquals(7, refund);
    }
}
