package ru.sapa.gadalka_backend.api.dto.subscription;

/**
 * Запрос на покупку подписки.
 *
 * @param planId           ID плана из каталога
 * @param autoRenewConsent явное отдельное согласие пользователя на автопродление
 *                         (отдельный чекбокс на экране оплаты, не выставлен по умолчанию —
 *                         требование 376-ФЗ). false/не передано — разовая покупка без автопродления.
 */
public record CreateSubscriptionPaymentRequest(Long planId, boolean autoRenewConsent) {
}
