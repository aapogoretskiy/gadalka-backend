package ru.sapa.gadalka_backend.service.event;

/**
 * Подписка закрыта досрочно, потому что все её квоты потрачены
 * (см. {@code SubscriptionQuotaService#completeIfFullyExhausted}).
 * <p>
 * Событие, а не прямой вызов уведомлений, по двум причинам. Первая: списание квоты
 * происходит внутри пользовательского запроса — человек прямо сейчас ждёт свой расклад,
 * и держать его транзакцию открытой на время HTTP-вызова к Telegram нельзя. Вторая:
 * если транзакция откатится, подписка останется живой — а сообщение об её закрытии
 * уже улетело бы. Слушатель работает строго после коммита, см.
 * {@code SubscriptionExhaustedNotifier}.
 *
 * @param autoRenewWasEnabled было ли у подписки включено автопродление на момент закрытия —
 *                            от этого зависит текст (упоминать отключение или нет)
 */
public record SubscriptionExhaustedEvent(
        Long userId,
        Long subscriptionId,
        String planName,
        boolean autoRenewWasEnabled
) {
}
