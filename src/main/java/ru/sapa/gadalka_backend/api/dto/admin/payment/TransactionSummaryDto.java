package ru.sapa.gadalka_backend.api.dto.admin.payment;

import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.User;

import java.time.OffsetDateTime;

/**
 * Строка списка на вкладке "Транзакции" админ-панели.
 * Один объект — один платёж (Payment), обогащённый отображаемыми данными
 * пользователя и продукта (которые в Payment хранятся только как ID/код).
 */
public record TransactionSummaryDto(
        Long id,
        Long userId,
        Long telegramId,
        String username,
        String firstName,
        String productCode,
        String productName,
        String provider,
        String status,
        Integer amountMinor,
        String currency,
        Integer creditsToGrant,
        String providerPaymentId,
        /* CREDITS | SUBSCRIPTION — для кнопки «Оформить возврат» в админке */
        String purchaseType,
        /* true — платёж списан автоматически (рекуррентное продление подписки, без
         * действия пользователя), см. Payment.renewalOfSubscriptionId. false — обычная
         * покупка, инициированная пользователем вручную. */
        boolean automatic,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /**
     * @param productName отображаемое название продукта, уже разрешённое вызывающей стороной
     *                     (из {@code payment_products} для покупок знаков или из
     *                     {@code subscription_plans} для подписок). Если ничего не нашлось —
     *                     вызывающая сторона должна передать {@code p.getProductCode()} как fallback.
     */
    public static TransactionSummaryDto from(Payment p, User user, String productName) {
        return new TransactionSummaryDto(
                p.getId(),
                p.getUserId(),
                user != null ? user.getTelegramId() : null,
                user != null ? user.getUsername() : null,
                user != null ? user.getFirstName() : null,
                p.getProductCode(),
                productName,
                p.getProvider().name(),
                p.getStatus().name(),
                p.getAmountMinor(),
                p.getCurrency(),
                p.getCreditsToGrant(),
                p.getProviderPaymentId(),
                p.getPurchaseType() != null ? p.getPurchaseType().name() : "CREDITS",
                p.getRenewalOfSubscriptionId() != null,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
