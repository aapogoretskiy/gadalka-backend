package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.Subscription;
import ru.sapa.gadalka_backend.domain.SubscriptionPlan;
import ru.sapa.gadalka_backend.domain.SubscriptionPlanQuota;
import ru.sapa.gadalka_backend.domain.SubscriptionQuota;
import ru.sapa.gadalka_backend.domain.type.QuotaPeriod;
import ru.sapa.gadalka_backend.repository.SubscriptionPlanQuotaRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionPlanRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionQuotaRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Активация подписки после успешной оплаты.
 * Вызывается из {@link PaymentService#completePayment} для платежей
 * с purchaseType = SUBSCRIPTION (вместо начисления знаков).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionActivationService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionPlanQuotaRepository planQuotaRepository;
    private final SubscriptionQuotaRepository quotaRepository;

    /**
     * Создаёт подписку и СНАПШОТ её квот из плана.
     * <p>
     * Снапшот — ключевой момент: квоты копируются в subscription_quotas на момент
     * покупки, поэтому последующее редактирование плана в админке не меняет условия
     * уже купленной подписки (тот же принцип, что payments.credits_to_grant).
     * <p>
     * Бизнес-правило «одна активная подписка» проверяется при СОЗДАНИИ платежа.
     * Если к моменту webhook'а активная подписка всё же есть (гонка двух оплат) —
     * активируем всё равно: деньги уже списаны, отказ здесь хуже двойной подписки.
     * findActiveByUserId вернёт ту, что истекает позже.
     */
    @Transactional
    public void activateFromPayment(Payment payment) {
        SubscriptionPlan plan = planRepository.findById(payment.getSubscriptionPlanId())
                .orElseThrow(() -> new IllegalStateException(
                        "План подписки не найден при активации: planId=" + payment.getSubscriptionPlanId()
                                + ", paymentId=" + payment.getId()));

        subscriptionRepository.findActiveByUserId(payment.getUserId(), OffsetDateTime.now())
                .ifPresent(existing -> log.warn(
                        "Активация подписки при уже существующей активной: userId={}, existingSubId={}, paymentId={}",
                        payment.getUserId(), existing.getId(), payment.getId()));

        // Если это автопродление — подтягиваем продлеваемую подписку один раз и переиспользуем
        // и для расчёта периода, и для наследования autoRenewEnabled/rootPaymentId/lockedPriceRub.
        Subscription previousSubscription = null;
        if (payment.getRenewalOfSubscriptionId() != null) {
            previousSubscription = subscriptionRepository.findById(payment.getRenewalOfSubscriptionId())
                    .orElse(null);
            if (previousSubscription == null) {
                log.warn("Не найдена продлеваемая подписка при активации автосписания: renewalOfSubscriptionId={}, paymentId={}",
                        payment.getRenewalOfSubscriptionId(), payment.getId());
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        // При автопродлении новый расчётный период считается от expiresAt СТАРОЙ подписки,
        // а не от момента списания (п. 6.12.3 соглашения) — иначе списание чуть раньше или
        // позже строгого расписания сдвигало бы весь график подписки пользователя.
        OffsetDateTime periodStart = previousSubscription != null ? previousSubscription.getExpiresAt() : now;
        OffsetDateTime periodEnd = periodStart.plusDays(plan.getDurationDays());

        Subscription.SubscriptionBuilder builder = Subscription.builder()
                .userId(payment.getUserId())
                .plan("PLAN_" + plan.getId())
                .planId(plan.getId())
                .planName(plan.getName())
                .status("ACTIVE")
                .startedAt(periodStart)
                .expiresAt(periodEnd)
                .provider(payment.getProvider());
        applyAutoRenewState(builder, payment, previousSubscription);

        Subscription subscription = builder.build();
        subscription = subscriptionRepository.save(subscription);

        // Снапшот квот плана → квоты подписки
        List<SubscriptionPlanQuota> planQuotas = planQuotaRepository.findAllByPlanId(plan.getId());
        LocalDate todayMsk = LocalDate.now(SubscriptionQuotaService.MSK);
        for (SubscriptionPlanQuota pq : planQuotas) {
            quotaRepository.save(SubscriptionQuota.builder()
                    .subscriptionId(subscription.getId())
                    .featureType(pq.getFeatureType())
                    .quotaCount(pq.getQuotaCount())
                    .quotaPeriod(pq.getQuotaPeriod())
                    .usedCount(0)
                    .usageDate(pq.getQuotaPeriod() == QuotaPeriod.DAILY ? todayMsk : null)
                    .isUnlimited(Boolean.TRUE.equals(pq.getIsUnlimited()))
                    .build());
        }

        if (previousSubscription != null) {
            previousSubscription.setStatus("RENEWED");
            subscriptionRepository.save(previousSubscription);
        }

        log.info("Подписка активирована: subscriptionId={}, userId={}, plan='{}' (planId={}), до {}, квот: {}, autoRenew={}",
                subscription.getId(), payment.getUserId(), plan.getName(), plan.getId(),
                subscription.getExpiresAt(), planQuotas.size(), subscription.getAutoRenewEnabled());
    }

    /**
     * Переносит состояние автопродления в новую подписку.
     * <p>
     * Если платёж — автоматическое рекуррентное продление ({@code renewalOfSubscriptionId}
     * заполнен, см. {@code PaymentService#renewSubscription}), наследуем autoRenewEnabled,
     * rootPaymentId и lockedPriceRub у продлеваемой подписки — цепочка PreviousInvoiceID у
     * Robokassa должна продолжать указывать на один и тот же материнский платёж, а зафиксированная
     * цена (п. 6.11.3(1) соглашения) не должна молча съезжать на текущую цену плана.
     * <p>
     * Иначе (первая или ручная покупка) — состояние берём из самого платежа: если
     * пользователь дал согласие на автопродление (отдельный чекбокс), этот платёж
     * сам становится корнем будущей цепочки, а его сумма — зафиксированной ценой.
     *
     * @param previousSubscription продлеваемая подписка, уже загруженная вызывающим методом
     *                              (или null — при первой/ручной покупке либо если её не нашли)
     */
    private void applyAutoRenewState(Subscription.SubscriptionBuilder builder, Payment payment, Subscription previousSubscription) {
        if (payment.getRenewalOfSubscriptionId() != null) {
            if (previousSubscription != null) {
                builder.autoRenewEnabled(previousSubscription.getAutoRenewEnabled());
                builder.rootPaymentId(previousSubscription.getRootPaymentId());
                builder.lockedPriceRub(previousSubscription.getLockedPriceRub());
            }
            return;
        }

        boolean autoRenew = Boolean.TRUE.equals(payment.getAutoRenewRequested());
        builder.autoRenewEnabled(autoRenew);
        builder.rootPaymentId(autoRenew ? payment.getId() : null);
        builder.lockedPriceRub(autoRenew ? payment.getAmountMinor() : null);
    }
}
