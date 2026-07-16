package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.Subscription;
import ru.sapa.gadalka_backend.domain.SubscriptionQuota;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.QuotaPeriod;
import ru.sapa.gadalka_backend.repository.SubscriptionQuotaRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Выдача квот пользователю из админ-панели — аналог «Подарить знаки»
 * (см. AdminController#giftCredits), но для квот подписки.
 * <p>
 * Квоты живут внутри активной подписки, поэтому два сценария:
 * <ul>
 *   <li>подписка ЕСТЬ — квота добавляется в неё: существующая увеличивается
 *       на N (периодичность не меняем), отсутствующая создаётся новой строкой;</li>
 *   <li>подписки НЕТ — создаётся «подарочная» подписка (plan = ADMIN_GIFT,
 *       без плана из каталога) на указанный срок, и квота кладётся в неё.
 *       Вся остальная механика — списание, «Моя подписка», сгорание,
 *       напоминания об истечении — работает автоматически.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionGiftService {

    /** Значение legacy-колонки plan для подарочных подписок (без плана из каталога) */
    public static final String GIFT_PLAN_CODE = "ADMIN_GIFT";
    /** Отображаемое имя подарочной подписки (видно в «Моя подписка» и напоминаниях) */
    public static final String GIFT_PLAN_NAME = "Подарок от Liora";

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionQuotaRepository quotaRepository;

    /** Результат выдачи — для сообщения админу и уведомления пользователю */
    public record GiftQuotaResult(
            Long subscriptionId,
            boolean giftSubscriptionCreated,
            int newQuotaCount,
            OffsetDateTime subscriptionExpiresAt
    ) {
    }

    /**
     * Выдаёт пользователю квоту на фичу.
     *
     * @param userId           ID пользователя
     * @param featureType      фича, на которую выдаётся квота
     * @param count            сколько использований добавить (> 0)
     * @param period           периодичность НОВОЙ квоты (у существующей не меняется)
     * @param giftDurationDays срок подарочной подписки в днях — используется только
     *                         если активной подписки нет и её нужно создать
     */
    @Transactional
    public GiftQuotaResult grantQuota(Long userId, DiaryFeatureType featureType, int count,
                                      QuotaPeriod period, int giftDurationDays) {
        if (count <= 0) throw new IllegalArgumentException("Количество квот должно быть > 0");
        if (giftDurationDays <= 0) throw new IllegalArgumentException("Срок подарочной подписки должен быть > 0 дней");

        Optional<Subscription> activeOpt = subscriptionRepository.findActiveByUserId(userId, OffsetDateTime.now());

        boolean created = false;
        Subscription subscription;
        if (activeOpt.isPresent()) {
            subscription = activeOpt.get();
        } else {
            // Подписки нет — создаём подарочную как носитель квот
            OffsetDateTime now = OffsetDateTime.now();
            subscription = subscriptionRepository.save(Subscription.builder()
                    .userId(userId)
                    .plan(GIFT_PLAN_CODE)
                    .planId(null)
                    .planName(GIFT_PLAN_NAME)
                    .status("ACTIVE")
                    .startedAt(now)
                    .expiresAt(now.plusDays(giftDurationDays))
                    .build());
            created = true;
            log.info("Создана подарочная подписка: subscriptionId={}, userId={}, до {}",
                    subscription.getId(), userId, subscription.getExpiresAt());
        }

        // Существующую квоту увеличиваем (с локом — параллельно её может тратить пользователь),
        // отсутствующую создаём новой строкой
        SubscriptionQuota quota = quotaRepository.findForUpdate(subscription.getId(), featureType)
                .orElse(null);
        if (quota != null) {
            quota.setQuotaCount(quota.getQuotaCount() + count);
        } else {
            quota = SubscriptionQuota.builder()
                    .subscriptionId(subscription.getId())
                    .featureType(featureType)
                    .quotaCount(count)
                    .quotaPeriod(period)
                    .usedCount(0)
                    .usageDate(period == QuotaPeriod.DAILY ? LocalDate.now(SubscriptionQuotaService.MSK) : null)
                    .isUnlimited(false)
                    .build();
        }
        quotaRepository.save(quota);

        log.info("Выдана квота: userId={}, subscriptionId={}, feature={}, +{} (итого {}), giftCreated={}",
                userId, subscription.getId(), featureType, count, quota.getQuotaCount(), created);

        return new GiftQuotaResult(subscription.getId(), created, quota.getQuotaCount(), subscription.getExpiresAt());
    }
}
