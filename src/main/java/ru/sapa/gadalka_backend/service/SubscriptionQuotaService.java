package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.Subscription;
import ru.sapa.gadalka_backend.domain.SubscriptionQuota;
import ru.sapa.gadalka_backend.domain.type.ConsentRevokeReason;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.QuotaPeriod;
import ru.sapa.gadalka_backend.exception.QuotaExceededException;
import ru.sapa.gadalka_backend.repository.SubscriptionQuotaRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionRepository;
import ru.sapa.gadalka_backend.service.event.SubscriptionExhaustedEvent;

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
    private final AutoRenewRevocationService autoRenewRevocationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Остаток квоты по фиче: total, remaining, периодичность. Для отображения на фронте
     */
    /**
     * У безлимитных квот (unlimited = true) total/remaining — СКРЫТЫЙ дневной
     * анти-абьюз лимит, наружу в публичные DTO числа не отдаются.
     */
    public record QuotaState(DiaryFeatureType featureType, QuotaPeriod period, int total, int remaining, boolean unlimited) {
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
            log.info("Квота исчерпана: userId={}, feature={}, period={}, unlimited={}, использовано {}/{}", userId, featureType, quota.getQuotaPeriod(), quota.getIsUnlimited(), quota.getUsedCount(), quota.getQuotaCount());
            // Для «безлимита» это скрытый дневной анти-абьюз лимит — другое сообщение,
            // числа лимита пользователю не раскрываем
            throw Boolean.TRUE.equals(quota.getIsUnlimited())
                    ? QuotaExceededException.dailyLimitReached()
                    : QuotaExceededException.quotaExhausted();
        }

        quota.setUsedCount(quota.getUsedCount() + 1);
        quotaRepository.save(quota);

        log.info("Списана квота подписки: userId={}, subscriptionId={}, feature={}, использовано {}/{}", userId, subscription.getId(), featureType, quota.getUsedCount(), quota.getQuotaCount());

        completeIfFullyExhausted(subscription);
    }

    /**
     * Досрочное завершение полностью исчерпанной подписки.
     * <p>
     * Срабатывает ТОЛЬКО если у подписки все квоты PER_PERIOD (нет ни дневных,
     * ни безлимитных — те пополняются каждый день, закрывать их подписку нельзя)
     * и все потрачены до нуля. Пользователю в подписке больше нечего получать —
     * освобождаем слот «одной подписки», чтобы он мог сразу купить новую
     * (не дожидаясь expires_at и без ручного отказа в профиле).
     * <p>
     * Вместе с подпиской обязательно гасим автопродление: шедулер продлевает только
     * ACTIVE/SUSPENDED, поэтому у закрытой подписки автопродление всё равно уже не
     * сработает — но горящий флаг создавал бы у пользователя ощущение, что подписка
     * продлится сама, и оставлял бы в журнале действующее согласие на списания.
     * <p>
     * Вызывается в той же транзакции, что и списание последней квоты — поэтому
     * уведомление пользователю уходит не отсюда, а событием после коммита
     * (см. {@link SubscriptionExhaustedEvent}).
     */
    private void completeIfFullyExhausted(Subscription subscription) {
        List<SubscriptionQuota> quotas = quotaRepository.findAllBySubscriptionId(subscription.getId());
        if (quotas.isEmpty()) return;

        boolean onlyPerPeriod = quotas.stream().allMatch(q ->
                q.getQuotaPeriod() == QuotaPeriod.PER_PERIOD && !Boolean.TRUE.equals(q.getIsUnlimited()));
        if (!onlyPerPeriod) return;

        boolean allSpent = quotas.stream().allMatch(q -> q.getUsedCount() >= q.getQuotaCount());
        if (!allSpent) return;

        subscription.setStatus("EXHAUSTED");
        subscription.setCancelledAt(OffsetDateTime.now());
        boolean autoRenewWasEnabled = autoRenewRevocationService.revoke(subscription, ConsentRevokeReason.SUBSCRIPTION_EXHAUSTED);
        subscriptionRepository.save(subscription);

        log.info("Подписка полностью исчерпана и завершена досрочно: subscriptionId={}, userId={}, plan='{}', автопродление было включено={}",
                subscription.getId(), subscription.getUserId(), subscription.getPlanName(), autoRenewWasEnabled);

        eventPublisher.publishEvent(new SubscriptionExhaustedEvent(subscription.getUserId(), subscription.getId(), subscription.getPlanName(), autoRenewWasEnabled));
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
                Math.max(0, quota.getQuotaCount() - used),
                Boolean.TRUE.equals(quota.getIsUnlimited()));
    }
}
