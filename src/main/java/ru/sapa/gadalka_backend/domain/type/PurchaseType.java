package ru.sapa.gadalka_backend.domain.type;

/**
 * Что покупает пользователь в платеже.
 */
public enum PurchaseType {
    /** Пакет знаков (payment_products) */
    CREDITS,
    /** Подписка (subscription_plans) */
    SUBSCRIPTION
}
