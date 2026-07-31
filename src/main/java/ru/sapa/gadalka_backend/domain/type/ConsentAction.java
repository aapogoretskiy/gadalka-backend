package ru.sapa.gadalka_backend.domain.type;

/**
 * Событие журнала согласий на автопродление (см. {@link ru.sapa.gadalka_backend.domain.SubscriptionAutorenewConsentLog}).
 * Robokassa прямо требует хранить историю таких событий, а не только текущее состояние.
 */
public enum ConsentAction {
    /** Пользователь явно согласился на автопродление (отдельный чекбокс, не по умолчанию) */
    GRANTED,
    /** Пользователь отключил автопродление или отозвал согласие на использование реквизитов */
    REVOKED
}
