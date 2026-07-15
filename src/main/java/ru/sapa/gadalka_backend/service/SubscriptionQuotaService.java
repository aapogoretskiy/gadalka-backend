package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.Subscription;
import ru.sapa.gadalka_backend.domain.SubscriptionQuota;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.QuotaPeriod;
import ru.sapa.gadalka_backend.exception.QuotaExceededException;
import ru.sapa.gadalka_backend.repository.SubscriptionQuotaRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Управление квотами активной подписки пользователя.
 * <p>
 * Квота — это N использований конкретной фичи: DAILY (N в день, сброс в полночь МСК)
 * или PER_PERIOD (N на весь срок подписки). Сброс DAILY-квот ленивый: при первом
 * обращении в новый день счётчик обнуляется прямо в момент чтения/списания —
 * отдельный шедулер не нужен, и «догонять» пропущенные дни не приходится.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionQuotaService {

    /**
     * Дневные квоты живут по московскому времени — продукт запускается в РФ
     */
    public static final ZoneId MSK = ZoneId.of("Europe/Moscow");

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionQuotaRepository quotaRepository;

    /**
     * Остаток квоты по фиче: total, remaining, периодичность. Для отображения на фронте
     */
    public record QuotaState(DiaryFeatureType featureType, QuotaPeriod period, int total, int remaining) {
    }

    /**
     * Активная подписка пользователя (по бизнес-правилу она максимум одна)
     */
    @Transactional(readOnly = true)
    public Optional<Subscription> findActiveSubscription(Long userId) {
        return subscriptionRepository.findActiveByUserId(userId, OffsetDateTime.now());
    }

    /**
     * Остаток квоты по конкретной фиче. Empty — если нет активной подписки
     * или подписка не включает квоту на эту фичу.
     * Не пишет в БД: ленивый сброс здесь только «виртуальный» (в расчёте remaining),
     * фактическое обнуление счётчика происходит при списании.
     */
    @Transactional(readOnly = true)
    public Optional<QuotaState> getQuotaState(Long userId, DiaryFeatureType featureType) {
        return findActiveSubscription(userId)
                .flatMap(sub -> quotaRepository.findBySubscriptionIdAndFeatureType(sub.getId(), featureType))
                .map(this::toState);
    }

    /**
     * Остатки всех квот активной подписки — для блока «Моя подписка»
     */
    @Transactional(readOnly = true)
    public List<QuotaState> getAllQuotaStates(Long subscriptionId) {
        return quotaRepository.findAllBySubscriptionId(subscriptionId)
                .stream()
                .map(this::toState)
                .toList();
    }

    /**
     * Списывает 1 использование квоты по фиче.
     * PESSIMISTIC_WRITE lock — защита от гонки при одновременных запросах
     * (тот же приём, что в FortuneCreditService.spendCredits).
     *
     * @throws QuotaExceededException если подписки нет, квоты на фичу нет или она исчерпана
     */
    @Transactional
    public void spendQuota(Long userId, DiaryFeatureType featureType) {
        Subscription subscription = findActiveSubscription(userId)
                .orElseThrow(QuotaExceededException::noActiveSubscription);

        SubscriptionQuota quota = quotaRepository.findForUpdate(subscription.getId(), featureType)
                .orElseThrow(QuotaExceededException::noQuotaForFeature);

        // Ленивый сброс DAILY-квоты: новый день по МСК — счётчик обнуляется
        LocalDate todayMsk = LocalDate.now(MSK);
        if (quota.getQuotaPeriod() == QuotaPeriod.DAILY && !todayMsk.equals(quota.getUsageDate())) {
            quota.setUsedCount(0);
            quota.setUsageDate(todayMsk);
        }

        if (quota.getUsedCount() >= quota.getQuotaCount()) {
            log.info("Квота исчерпана: userId={}, feature={}, period={}, использовано {}/{}", userId, featureType, quota.getQuotaPeriod(), quota.getUsedCount(), quota.getQuotaCount());
            throw QuotaExceededException.quotaExhausted();
        }

        quota.setUsedCount(quota.getUsedCount() + 1);
        quotaRepository.save(quota);

        log.info("Списана квота подписки: userId={}, subscriptionId={}, feature={}, использовано {}/{}", userId, subscription.getId(), featureType, quota.getUsedCount(), quota.getQuotaCount());
    }

    private QuotaState toState(SubscriptionQuota quota) {
        int used = quota.getUsedCount();
        // Для DAILY-квоты из «прошлого дня» счётчик логически обнулён, даже если строка ещё не обновлена
        if (quota.getQuotaPeriod() == QuotaPeriod.DAILY && !LocalDate.now(MSK).equals(quota.getUsageDate())) {
            used = 0;
        }
        return new QuotaState(quota.getFeatureType(),
                quota.getQuotaPeriod(),
                quota.getQuotaCount(),
                Math.max(0, quota.getQuotaCount() - used));
    }
}
