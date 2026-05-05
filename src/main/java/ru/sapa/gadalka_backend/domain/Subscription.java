package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;

import java.time.OffsetDateTime;

/**
 * Подписка пользователя. Таблица создана как задел на будущее.
 * FortuneCreditService.canUseFeature() уже проверяет активные подписки,
 * поэтому добавление подписочной логики в будущем не потребует изменения
 * точек вызова в бизнес-сервисах.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** MONTHLY, YEARLY */
    @Column(name = "plan", nullable = false, length = 50)
    private String plan;

    /** ACTIVE, EXPIRED, CANCELLED */
    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 50)
    private PaymentProvider provider;

    @Column(name = "provider_subscription_id", length = 255)
    private String providerSubscriptionId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }

    public boolean isActive() {
        return "ACTIVE".equals(status) && expiresAt.isAfter(OffsetDateTime.now());
    }
}
