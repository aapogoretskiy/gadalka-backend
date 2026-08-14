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

    /**
     * Исторический: так отзывалось согласие при досрочном закрытии полностью исчерпанной
     * подписки. Досрочного закрытия больше нет — оплаченный период живёт до конца срока,
     * а исчерпание Лимитов автопродления не отменяет. В новых записях не встречается.
     */
    SUBSCRIPTION_EXHAUSTED,

    /** Пользователь купил другую подписку взамен текущей (см. SubscriptionActivationService) */
    SUBSCRIPTION_REPLACED,

    /** Пользователь отказался от подписки целиком (см. SubscriptionCancellationService#cancelByUser) */
    SUBSCRIPTION_CANCELLED,

    /** Админ оформил возврат денег по подписочному платежу */
    PAYMENT_REFUNDED
}
