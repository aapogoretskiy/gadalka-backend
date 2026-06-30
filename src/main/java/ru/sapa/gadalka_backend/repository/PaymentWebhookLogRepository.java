package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.PaymentWebhookLog;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.domain.type.WebhookStatus;

import java.time.OffsetDateTime;
import java.util.List;

public interface PaymentWebhookLogRepository extends JpaRepository<PaymentWebhookLog, Long> {

    List<PaymentWebhookLog> findAllByStatus(WebhookStatus status);

    /**
     * Webhook-логи провайдера в окне времени вокруг транзакции (вкладка "Транзакции" в админке).
     * provider_payment_id внутри payments не привязан к payment_webhook_log напрямую —
     * связь устанавливается на уровне приложения парсингом raw_payload
     * (см. {@code AdminPaymentService.findMatchingWebhook}).
     */
    List<PaymentWebhookLog> findAllByProviderAndReceivedAtBetweenOrderByReceivedAtDesc(
            PaymentProvider provider, OffsetDateTime from, OffsetDateTime to);
}
