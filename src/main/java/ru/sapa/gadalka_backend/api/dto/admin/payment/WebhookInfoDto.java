package ru.sapa.gadalka_backend.api.dto.admin.payment;

import ru.sapa.gadalka_backend.domain.PaymentWebhookLog;

import java.time.OffsetDateTime;

/**
 * Сведения о webhook-уведомлении, сопоставленном с конкретной транзакцией
 * (см. {@code AdminPaymentService.findMatchingWebhook}).
 * Если связанный webhook не найден (например, для Telegram Stars — там
 * webhook-лог в принципе не ведётся, подтверждение синхронное) — поле
 * {@code webhook} в {@link TransactionDetailsDto} будет {@code null}.
 */
public record WebhookInfoDto(
        Long id,
        String status,
        String errorMessage,
        String rawPayload,
        OffsetDateTime receivedAt,
        OffsetDateTime processedAt
) {
    public static WebhookInfoDto from(PaymentWebhookLog log) {
        return new WebhookInfoDto(
                log.getId(),
                log.getStatus().name(),
                log.getErrorMessage(),
                log.getRawPayload(),
                log.getReceivedAt(),
                log.getProcessedAt()
        );
    }
}
