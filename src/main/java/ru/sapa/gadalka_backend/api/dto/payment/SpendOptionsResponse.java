package ru.sapa.gadalka_backend.api.dto.payment;

/**
 * Чем пользователь может оплатить конкретную фичу — данные для модалки
 * подтверждения списания на фронте («Списать 1 квоту / N знаков?»).
 */
public record SpendOptionsResponse(
        /* Стоимость фичи в знаках */
        int creditCost,
        /* Текущий баланс знаков */
        int balance,
        /* Хватает ли знаков */
        boolean canSpendCredits,
        /* Есть ли у активной подписки квота на эту фичу вообще */
        boolean hasQuota,
        /* Остаток квоты (0 если hasQuota = false) */
        int quotaRemaining,
        /* Всего квоты (0 если hasQuota = false) */
        int quotaTotal,
        /* DAILY или PER_PERIOD (null если hasQuota = false) */
        String quotaPeriod
) {
}
