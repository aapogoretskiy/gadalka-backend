package ru.sapa.gadalka_backend.domain.type;

/**
 * Причина отзыва согласия на автопродление — пишется в
 * {@link ru.sapa.gadalka_backend.domain.SubscriptionAutorenewConsentLog#getReason()}
 * вместе с действием REVOKED.
 * <p>
 * Отзыв инициирует не только пользователь: часть случаев — наши собственные действия,
 * и в разборе спорного списания это принципиально разные ситуации.
 */
public enum ConsentRevokeReason {

    /** Пользователь сам выключил автопродление в профиле (п. 6.15.1 соглашения) */
    USER_REQUEST,

    /** Все квоты PER_PERIOD потрачены, подписка закрыта досрочно (см. SubscriptionQuotaService) */
    SUBSCRIPTION_EXHAUSTED,

    /** Пользователь отказался от подписки целиком (см. SubscriptionCancellationService#cancelByUser) */
    SUBSCRIPTION_CANCELLED,

    /** Админ оформил возврат денег по подписочному платежу */
    PAYMENT_REFUNDED
}
