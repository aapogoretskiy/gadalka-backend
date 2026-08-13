package ru.sapa.gadalka_backend.api.dto.admin.subscription;

/**
 * Счётчики над таблицей вкладки «Подписчики».
 *
 * @param active               действующие подписки (ACTIVE и срок ещё не истёк)
 * @param activeWithAutoRenew  из них с включённым автопродлением
 * @param suspended            списание не удалось, идут повторные попытки (доступ к лимитам приостановлен)
 * @param renewalPending       списание запущено, ждём подтверждения от Robokassa
 * @param expiringInWeek       действующие подписки, истекающие в ближайшие 7 дней
 * @param zombies              автопродление включено, но фактически уже невозможно — в норме 0,
 *                             любое другое число означает незакрытый сценарий (см. AdminSubscriptionRowDto#zombie)
 * @param autoRenewVolumeMinor суммарная зафиксированная цена подписок на автопродлении, в копейках —
 *                             сколько спишется за следующий полный цикл, если все продления пройдут
 */
public record AdminSubscriptionStatsDto(
        long active,
        long activeWithAutoRenew,
        long suspended,
        long renewalPending,
        long expiringInWeek,
        long zombies,
        long autoRenewVolumeMinor
) {
}
