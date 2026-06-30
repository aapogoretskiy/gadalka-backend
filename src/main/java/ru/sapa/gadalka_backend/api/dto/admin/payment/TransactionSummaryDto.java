package ru.sapa.gadalka_backend.api.dto.admin.payment;

import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.PaymentProduct;
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
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static TransactionSummaryDto from(Payment p, User user, PaymentProduct product) {
        return new TransactionSummaryDto(
                p.getId(),
                p.getUserId(),
                user != null ? user.getTelegramId() : null,
                user != null ? user.getUsername() : null,
                user != null ? user.getFirstName() : null,
                p.getProductCode(),
                product != null ? product.getName() : p.getProductCode(),
                p.getProvider().name(),
                p.getStatus().name(),
                p.getAmountMinor(),
                p.getCurrency(),
                p.getCreditsToGrant(),
                p.getProviderPaymentId(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
