package ru.sapa.gadalka_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;

import java.time.OffsetDateTime;

/**
 * Подписка пользователя. Создаётся при успешной оплате плана из subscription_plans.
 * Квоты подписки (снапшот из плана) лежат в {@link SubscriptionQuota}.
 * <p>
 * Подписка живёт durationDays плана. Если {@link #autoRenewEnabled} выключен —
 * по истечении пользователь продлевает вручную (шедулер шлёт напоминания за 3/2/0 дней,
 * см. SubscriptionReminderScheduler). Если включён — списание происходит автоматически
 * через Robokassa (см. SubscriptionRenewalScheduler) в последние 24 часа текущего
 * Расчётного периода, с уведомлением не позднее чем за 3 календарных дня (п. 6.12
 * пользовательского соглашения) и созданием новой строки Subscription при каждом продлении,
 * период которой считается от expiresAt предыдущей, а не от момента списания.
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

    /** ACTIVE, EXPIRED, CANCELLED (отказ/возврат), EXHAUSTED (все PER_PERIOD-квоты потрачены) */
    @Column(name = "status", nullable = false, length = 50)
    private String status;

    /** Момент отказа от подписки (пользователем или админом при возврате) */
    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 50)
    private PaymentProvider provider;

    @Column(name = "provider_subscription_id", length = 255)
    private String providerSubscriptionId;

    /** Включено ли автопродление. Требует отдельного явного согласия пользователя (см. SubscriptionAutorenewConsentLog). */
    @Column(name = "auto_renew_enabled", nullable = false)
    private Boolean autoRenewEnabled;

    /**
     * Id самого первого успешного платежа цепочки этой подписки —
     * передаётся Robokassa как PreviousInvoiceID при каждом рекуррентном списании.
     * При продлении наследуется в новую строку Subscription, чтобы цепочка
     * всегда ссылалась на один и тот же материнский платёж.
     */
    @Column(name = "root_payment_id")
    private Long rootPaymentId;

    /**
     * Когда отправлено обязательное по п. 6.12.4 соглашения уведомление (не позднее чем
     * за 3 календарных дня до списания). NULL — не отправлялось. SubscriptionRenewalScheduler
     * не спишет деньги, пока не пройдут положенные 24 часа с этого момента (доп. защита сверх
     * самого 3-дневного окна — см. javadoc SubscriptionRenewalScheduler).
     */
    @Column(name = "renewal_notice_sent_at")
    private OffsetDateTime renewalNoticeSentAt;

    /**
     * Цена подписки (в копейках), зафиксированная на момент оформления согласия на
     * автопродление — и дальше переносимая из цикла в цикл вместе с rootPaymentId.
     * Если админ впоследствии меняет цену плана, уже подключённые к автопродлению
     * подписчики продолжают платить по своей зафиксированной цене (п. 6.11.3(1) соглашения).
     */
    @Column(name = "locked_price_rub")
    private Integer lockedPriceRub;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        if (autoRenewEnabled == null) autoRenewEnabled = false;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status) && expiresAt.isAfter(OffsetDateTime.now());
    }
}
