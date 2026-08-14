package ru.sapa.gadalka_backend.service.event;

import java.time.OffsetDateTime;

/**
 * В подписке закончились все Лимиты: до конца оплаченного периода взять больше нечего
 * (см. {@code SubscriptionQuotaService#notifyIfFullyExhausted}).
 * <p>
 * Сама подписка при этом НЕ закрывается — оплаченный период живёт до {@code expiresAt},
 * а при включённом автопродлении продлевается, и Лимиты обновляются в новой строке.
 * Публикуется один раз за период.
 * <p>
 * Событие, а не прямой вызов уведомлений, по двум причинам. Первая: списание Лимита
 * происходит внутри пользовательского запроса — человек прямо сейчас ждёт свой расклад,
 * и держать его транзакцию открытой на время HTTP-вызова к Telegram нельзя. Вторая:
 * если транзакция откатится, Лимит не будет списан — а сообщение об исчерпании уже
 * улетело бы. Слушатель работает строго после коммита, см.
 * {@code SubscriptionQuotasExhaustedNotifier}.
 *
 * @param expiresAt          до какого момента действует оплаченный период — попадает в текст
 * @param autoRenewEnabled   включено ли автопродление: от этого зависит, обещаем ли мы
 *                           обновление Лимитов или говорим только про срок
 */
public record SubscriptionQuotasExhaustedEvent(
        Long userId,
        Long subscriptionId,
        String planName,
        OffsetDateTime expiresAt,
        boolean autoRenewEnabled
) {
}
