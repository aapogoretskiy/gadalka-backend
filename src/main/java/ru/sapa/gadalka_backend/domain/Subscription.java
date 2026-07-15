package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;

import java.time.OffsetDateTime;

/**
 * Подписка пользователя. Создаётся при успешной оплате плана из subscription_plans.
 * Квоты подписки (снапшот из плана) лежат в {@link SubscriptionQuota}.
 * <p>
 * v1 — без автопродления: подписка живёт durationDays плана, по истечении
 * пользователь продлевает вручную (шедулер шлёт напоминания за 3/2/0 дней).
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

    /** Легаси-поле (MONTHLY/YEARLY). Для новых подписок сюда пишется "PLAN_" + planId */
    @Column(name = "plan", nullable = false, length = 50)
    private String plan;

    /** Ссылка на план каталога. Может быть null у старых/ручных записей */
    @Column(name = "plan_id")
    private Long planId;

    /** Снапшот названия плана на момент покупки — план могут переименовать */
    @Column(name = "plan_name")
    private String planName;

    /** Момент активации подписки */
    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    /**
     * Последний отправленный "рубеж" напоминания об истечении: 3, 2 или 0 (дней до конца).
     * NULL — напоминания ещё не отправлялись. Защита от повторной отправки.
     */
    @Column(name = "last_reminder_days_left")
    private Integer lastReminderDaysLeft;

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
