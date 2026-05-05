package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.domain.type.PaymentStatus;

import java.time.OffsetDateTime;

/**
 * Платёжная транзакция. Создаётся в момент инициализации оплаты (статус PENDING),
 * статус обновляется по мере прохождения платежа через провайдера.
 * <p>
 * provider_payment_id имеет UNIQUE constraint — гарантия идемпотентности:
 * даже если webhook придёт дважды, второй INSERT упадёт с ошибкой дубликата.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Код продукта на момент покупки (PACK_3, PACK_7, PACK_15) */
    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private PaymentProvider provider;

    /** ID платежа на стороне провайдера. Уникален — ключ идемпотентности */
    @Column(name = "provider_payment_id", length = 255, unique = true)
    private String providerPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private PaymentStatus status;

    /** Сумма: копейки для RUB, штуки для XTR */
    @Column(name = "amount_minor", nullable = false)
    private Integer amountMinor;

    /** RUB или XTR (код валюты Telegram Stars) */
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    /**
     * Сколько гаданий начислить при успехе.
     * Фиксируется в момент создания платежа, независимо от будущих изменений каталога.
     */
    @Column(name = "credits_to_grant", nullable = false)
    private Integer creditsToGrant;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
