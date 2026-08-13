package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.ConsentAction;
import ru.sapa.gadalka_backend.domain.type.ConsentRevokeReason;

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
 * <p>
 * Единственное исключение из append-only — дозаполнение {@link #subscriptionId}
 * сразу после активации подписки (см. SubscriptionActivationService): в момент
 * клика по чекбоксу подписки ещё не существует, связать строку не с чем.
 * <p>
 * Журнал переживает удаление пользователя (миграция V72): {@code user_id} обнуляется
 * по ON DELETE SET NULL, а {@link #telegramId} остаётся — иначе вместе с аккаунтом
 * исчезало бы и доказательство правомерности уже совершённых списаний.
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

    /**
     * Ссылка на пользователя, пока он существует. NULL — аккаунт удалён:
     * FK стоит с ON DELETE SET NULL, сама строка журнала при этом сохраняется
     * (см. {@link #telegramId}).
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * Telegram id пользователя на момент события — денормализован намеренно.
     * Это единственный идентификатор, который остаётся у строки после удаления
     * аккаунта, поэтому именно по нему журнал сопоставляется с прошлыми списаниями.
     */
    @Column(name = "telegram_id")
    private Long telegramId;

    /** Платёж, в рамках оформления которого дано согласие. NULL — простой отзыв вне оплаты */
    @Column(name = "payment_id")
    private Long paymentId;

    /** Подписка, к которой относится событие. NULL у самой первой покупки — подписки ещё нет */
    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private ConsentAction action;

    /**
     * Почему согласие отозвано. Заполняется только у REVOKED: отзыв бывает и по воле
     * пользователя, и по нашей инициативе (исчерпание квот, отказ от подписки, возврат
     * денег) — в споре это принципиально разные ситуации. У GRANTED всегда NULL.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 50)
    private ConsentRevokeReason reason;

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
