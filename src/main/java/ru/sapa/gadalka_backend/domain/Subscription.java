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

    /**
     * ACTIVE, EXPIRED, CANCELLED (отказ/возврат), REPLACED (пользователь купил другую
     * подписку взамен этой, см. SubscriptionActivationService).
     * EXHAUSTED — исторический: так помечались подписки с полностью потраченными
     * PER_PERIOD-квотами, пока действовало досрочное закрытие. Больше не проставляется —
     * оплаченный период живёт до expires_at независимо от остатка Лимитов.
     * Плюс для автопродления (см. SubscriptionRenewalScheduler): RENEWAL_PENDING (в процессе
     * списания, ждём вебхук), SUSPENDED (списание не удалось, идут ретраи — доступ к Лимитам
     * приостановлен, п. 6.13.2), RENEWED (успешно продлена, историческая запись — актуальная
     * подписка теперь в новой строке).
     */
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
     * Последняя ПОПЫТКА отправить уведомление об автосписании. Права на списание не даёт —
     * это чисто диагностика: попытка могла провалиться (бот заблокирован, юзер удалён).
     * Факт доставки — в {@link #renewalNoticeDeliveredAt}.
     */
    @Column(name = "renewal_notice_sent_at")
    private OffsetDateTime renewalNoticeSentAt;

    /**
     * Когда обязательное уведомление было реально ДОСТАВЛЕНО пользователю (Telegram принял
     * сообщение). NULL — не доставлено, списывать нельзя ни при каких условиях.
     * <p>
     * Два требования к моменту доставки: не позднее чем за 3 календарных дня до списания
     * (п. 6.12.4 соглашения с Robokassa) и не позднее чем за 24 часа (ст. 16.1 ЗоЗПП).
     * Первое обеспечивает окно выборки кандидатов, второе — проверяется явно при отборе
     * на списание (см. SubscriptionRepository#findAutoRenewChargeCandidates).
     */
    @Column(name = "renewal_notice_delivered_at")
    private OffsetDateTime renewalNoticeDeliveredAt;

    /**
     * Цена подписки (в копейках), зафиксированная на момент оформления согласия на
     * автопродление — и дальше переносимая из цикла в цикл вместе с rootPaymentId.
     * Если админ впоследствии меняет цену плана, уже подключённые к автопродлению
     * подписчики продолжают платить по своей зафиксированной цене (п. 6.11.3(1) соглашения).
     */
    @Column(name = "locked_price_rub")
    private Integer lockedPriceRub;

    /**
     * Момент ПЕРВОЙ неудачной попытки автосписания за текущий цикл. NULL — попыток
     * ещё не было или последняя прошла успешно. От этого поля отсчитываются 7 календарных
     * дней ретраев (п. 6.13.1 соглашения) — см. SubscriptionRenewalScheduler.
     */
    @Column(name = "renewal_first_failed_at")
    private OffsetDateTime renewalFirstFailedAt;

    /**
     * Момент последней попытки списания (успешной или нет) — не только первой. Нужен,
     * чтобы (1) не пытаться списывать чаще раза в сутки во время ретраев (п. 6.13.1) и
     * (2) обнаружить платёж, зависший без вебхука дольше разумного времени
     * (см. SubscriptionRenewalScheduler#reconcileStuckRenewals).
     */
    @Column(name = "last_renewal_attempt_at")
    private OffsetDateTime lastRenewalAttemptAt;

    /**
     * Когда пользователю сообщили, что Лимиты подписки закончились. NULL — не сообщали.
     * <p>
     * Защита от повторов: состояние «всё потрачено» наступает при каждой следующей попытке
     * списать Лимит, а сообщение должно уйти один раз за период. При автопродлении создаётся
     * новая строка, где поле пустое — в новом периоде уведомим заново.
     */
    @Column(name = "quotas_exhausted_notified_at")
    private OffsetDateTime quotasExhaustedNotifiedAt;

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
