package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.ConsentAction;

import java.time.OffsetDateTime;

/**
 * Журнал согласий/отзывов на автопродление подписки.
 * <p>
 * Append-only: строки никогда не изменяются и не удаляются, только добавляются.
 * Это прямое требование Robokassa при подключении рекуррентных платежей —
 * хранить историю согласий пользователя на автосписания, а не только текущее
 * состояние флага {@code Subscription.autoRenewEnabled}. Пригодится и как
 * доказательная база при спорах/возвратах: видно, какое согласие действовало
 * перед конкретным списанием.
 */
@Entity
@Table(name = "subscription_autorenew_consent_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionAutorenewConsentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Платёж, в рамках оформления которого дано согласие. NULL — простой отзыв вне оплаты */
    @Column(name = "payment_id")
    private Long paymentId;

    /** Подписка, к которой относится событие. NULL у самой первой покупки — подписки ещё нет */
    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private ConsentAction action;

    /** Версия оферты, действовавшая на момент согласия — для доказательной базы */
    @Column(name = "agreement_version", length = 50)
    private String agreementVersion;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
