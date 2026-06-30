package ru.sapa.gadalka_backend.api.dto.admin.payment;

import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.PaymentProduct;
import ru.sapa.gadalka_backend.domain.User;

/**
 * Детальная карточка транзакции (боковая панель админки).
 * Расширяет {@link TransactionSummaryDto} блоком {@code webhook} —
 * это позволяет администратору увидеть не только статус платежа,
 * но и сырое подтверждение от платёжной системы, если оно было найдено.
 */
public record TransactionDetailsDto(
        TransactionSummaryDto payment,
        WebhookInfoDto webhook
) {
    public static TransactionDetailsDto of(Payment p, User user, PaymentProduct product, WebhookInfoDto webhook) {
        return new TransactionDetailsDto(TransactionSummaryDto.from(p, user, product), webhook);
    }
}
