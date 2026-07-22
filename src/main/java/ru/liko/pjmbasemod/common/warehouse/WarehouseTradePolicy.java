package ru.liko.pjmbasemod.common.warehouse;

import java.util.Collection;

/**
 * Чистые правила экономики склада.
 *
 * <p>Возврат за штуку не может быть выше самой дешёвой цены покупки предмета,
 * который примет выбранная запись сдачи. Иначе две записи каталога с разными {@code quantity}
 * создают арбитраж:
 * например, купить 2 ракеты за 30 и сдать их по одной через запись с возвратом 35.</p>
 */
final class WarehouseTradePolicy {

    /** Цена одной покупки и число выдаваемых ею штук. */
    record PurchaseOffer(int pointCost, int quantity) {
        PurchaseOffer {
            pointCost = Math.max(1, pointCost);
            quantity = Math.max(1, quantity);
        }
    }

    private WarehouseTradePolicy() {}

    /**
     * Считает безопасный возврат с учётом износа.
     *
     * @param configuredBatchRefund возврат из конфига за пачку
     * @param configuredQuantity    размер пачки записи, по которой идёт сдача
     * @param itemWeightSum         сумма долей прочности сдаваемых штук
     * @param equivalentOffers      цены всех выдач, которые можно сдать по этой записи
     */
    static int safeRefund(int configuredBatchRefund, int configuredQuantity, double itemWeightSum,
                          Collection<PurchaseOffer> equivalentOffers) {
        if (configuredBatchRefund <= 0 || itemWeightSum <= 0.0 || !Double.isFinite(itemWeightSum)) return 0;

        long bestNumerator = configuredBatchRefund;
        long bestDenominator = Math.max(1, configuredQuantity);
        for (PurchaseOffer offer : equivalentOffers) {
            long candidateNumerator = offer.pointCost();
            long candidateDenominator = offer.quantity();
            if (candidateNumerator * bestDenominator < bestNumerator * candidateDenominator) {
                bestNumerator = candidateNumerator;
                bestDenominator = candidateDenominator;
            }
        }

        double refund = Math.floor(bestNumerator * itemWeightSum / bestDenominator);
        return refund >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) refund;
    }
}
