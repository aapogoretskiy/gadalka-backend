package ru.sapa.gadalka_backend.api.dto.payment;

/**
 * Чем пользователь может оплатить конкретную фичу — данные для модалки
 * подтверждения списания на фронте («Списать 1 квоту / N знаков?»).
 * <p>
 * Для безлимитной квоты (quotaUnlimited = true) числа remaining/total = 0
 * (скрытый дневной лимит не раскрывается); фронт списывает квоту молча,
 * без модалки. Если скрытый лимит на сегодня исчерпан — hasQuota приходит
 * как false, и работает обычная логика (знаки).
 */
public record SpendOptionsResponse(
        /* Стоимость фичи в знаках */
        int creditCost,
        /* Текущий баланс знаков */
        int balance,
        /* Хватает ли знаков */
        boolean canSpendCredits,
        /* Есть ли у активной подписки доступная квота на эту фичу */
        boolean hasQuota,
        /* Остаток квоты (0 если hasQuota = false или безлимит) */
        int quotaRemaining,
        /* Всего квоты (0 если hasQuota = false или безлимит) */
        int quotaTotal,
        /* DAILY или PER_PERIOD (null если hasQuota = false) */
        String quotaPeriod,
        /* Безлимитная квота — списывается без модалки */
        boolean quotaUnlimited
) {
}
