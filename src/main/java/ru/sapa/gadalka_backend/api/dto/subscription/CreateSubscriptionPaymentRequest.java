package ru.sapa.gadalka_backend.api.dto.subscription;

/** Запрос на покупку подписки: ID плана из каталога. */
public record CreateSubscriptionPaymentRequest(Long planId) {
}
