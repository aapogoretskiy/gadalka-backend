package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.PaymentWebhookLog;
import ru.sapa.gadalka_backend.domain.type.WebhookStatus;

import java.util.List;

public interface PaymentWebhookLogRepository extends JpaRepository<PaymentWebhookLog, Long> {

    List<PaymentWebhookLog> findAllByStatus(WebhookStatus status);
}
