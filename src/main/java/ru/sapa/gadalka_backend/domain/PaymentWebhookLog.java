package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.domain.type.WebhookStatus;

import java.time.OffsetDateTime;

/**
 * Буфер входящих webhook-уведомлений от платёжных провайдеров.
 * <p>
 * Паттерн работы:
 * 1. Контроллер получает webhook → сохраняет сырой payload сюда за ~1мс → возвращает HTTP 200.
 * 2. @Scheduled каждые 30с забирает PENDING записи → обрабатывает → ставит PROCESSED или FAILED.
 * <p>
 * Это гарантирует что провайдер всегда получит подтверждение,
 * даже если обработка займёт время или временно упадёт БД основных таблиц.
 */
@Entity
@Table(name = "payment_webhook_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentWebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private PaymentProvider provider;

    /** Полный сырой JSON-payload от провайдера — ничего не теряем */
    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private WebhookStatus status;

    /** Текст исключения при статусе FAILED */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @PrePersist
    void prePersist() {
        if (receivedAt == null) receivedAt = OffsetDateTime.now();
        if (status == null) status = WebhookStatus.PENDING;
    }
}
