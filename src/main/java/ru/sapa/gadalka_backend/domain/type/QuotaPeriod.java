package ru.sapa.gadalka_backend.domain.type;

/**
 * Периодичность квоты подписки.
 */
public enum QuotaPeriod {
    /** N использований в день, сбрасывается в полночь по МСК (ленивый сброс в SubscriptionQuotaService) */
    DAILY,
    /** N использований на весь срок действия подписки, не сбрасывается */
    PER_PERIOD
}
