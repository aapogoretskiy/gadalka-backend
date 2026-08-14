package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.Subscription;
import ru.sapa.gadalka_backend.domain.SubscriptionQuota;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.QuotaPeriod;
import ru.sapa.gadalka_backend.exception.QuotaExceededException;
import ru.sapa.gadalka_backend.repository.SubscriptionQuotaRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionRepository;
import ru.sapa.gadalka_backend.service.event.SubscriptionQuotasExhaustedEvent;

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

        notifyIfFullyExhausted(subscription);
    }

    /**
     * Сообщает пользователю, что Лимиты подписки закончились. Саму подписку НЕ трогает.
     * <p>
     * Раньше здесь было досрочное закрытие: подписка уходила в EXHAUSTED, слот «одной
     * подписки» освобождался. От этого отказались — оплаченный период должен жить до
     * expires_at, а при включённом автопродлении продлеваться, обновляя Лимиты. Вместо
     * закрытия пользователь получает сообщение и сам решает: продолжить за знаки или
     * оформить другую подписку взамен текущей (покупку разрешает
     * {@code SubscriptionController#createSubscriptionPayment}, замену выполняет
     * {@code SubscriptionActivationService}).
     * <p>
     * Уведомляем один раз за расчётный период: состояние «всё потрачено» наступает при
     * каждой следующей попытке списать Лимит, а сообщение нужно одно.
     * <p>
     * Вызывается в той же транзакции, что и списание последнего Лимита — поэтому само
     * уведомление уходит не отсюда, а событием после коммита
     * (см. {@link SubscriptionQuotasExhaustedEvent}).
     */
    private void notifyIfFullyExhausted(Subscription subscription) {
        if (subscription.getQuotasExhaustedNotifiedAt() != null) return;
        // Вызов через this — прокси в обход, поэтому @Transactional на isFullyExhausted здесь
        // не действует. Это не ошибка: транзакция уже открыта снаружи (consumeQuota помечен
        // @Transactional и вызывается через прокси), чтение идёт в ней. Подробнее — в javadoc
        // самого isFullyExhausted.
        if (!isFullyExhausted(subscription.getId())) return;

        subscription.setQuotasExhaustedNotifiedAt(OffsetDateTime.now());
        subscriptionRepository.save(subscription);

        log.info("Лимиты подписки полностью исчерпаны: subscriptionId={}, userId={}, plan='{}', период до {}",
                subscription.getId(), subscription.getUserId(), subscription.getPlanName(), subscription.getExpiresAt());

        eventPublisher.publishEvent(new SubscriptionQuotasExhaustedEvent(
                subscription.getUserId(),
                subscription.getId(),
                subscription.getPlanName(),
                subscription.getExpiresAt(),
                Boolean.TRUE.equals(subscription.getAutoRenewEnabled())));
    }

    /**
     * Исчерпаны ли Лимиты подписки безвозвратно до конца оплаченного периода.
     * <p>
     * true ТОЛЬКО если все квоты плана — PER_PERIOD и среди них нет безлимитных: дневные
     * и безлимитные восстанавливаются каждый день, их «ноль» — это состояние на сегодня,
     * а не конец Лимитов. Поэтому план хотя бы с одной DAILY-квотой исчерпанным не
     * считается никогда.
     * <p>
     * Используется дважды: для одноразового уведомления и для разрешения купить новую
     * подписку взамен текущей (см. {@code SubscriptionController}).
     * <p>
     * <b>Про {@code @Transactional} здесь — чтобы не спотыкаться повторно.</b> Аннотация
     * работает только для вызовов ИЗВНЕ бина: из {@code SubscriptionController} (он не
     * транзакционный, поэтому прокси откроет новую read-only транзакцию) и из
     * {@code SubscriptionCatalogService} (там присоединится к существующей). Вызов из
     * соседнего {@link #notifyIfFullyExhausted} идёт через {@code this}, минует прокси, и
     * аннотация игнорируется — но транзакция там уже открыта снаружи (см. {@code consumeQuota}),
     * так что чтение всё равно выполняется в ней.
     * <p>
     * Причём даже сработай прокси в том пути, {@code readOnly = true} ничего бы не изменил:
     * при propagation REQUIRED присоединение к существующей транзакции её флаг readOnly
     * не переопределяет — он учитывается только при создании транзакции с нуля.
     * <p>
     * Оставлено как есть сознательно. Если однажды захочется убрать сам повод для вопроса —
     * вынести проверку в приватный метод, принимающий уже загруженный список квот, и звать
     * его из обоих мест; тогда self-invocation исчезнет, а аннотация останется только там,
     * где реально управляет транзакцией.
     */
    @Transactional(readOnly = true)
    public boolean isFullyExhausted(Long subscriptionId) {
        List<SubscriptionQuota> quotas = quotaRepository.findAllBySubscriptionId(subscriptionId);
        if (quotas.isEmpty()) return false;

        boolean onlyPerPeriod = quotas.stream().allMatch(q ->
                q.getQuotaPeriod() == QuotaPeriod.PER_PERIOD && !Boolean.TRUE.equals(q.getIsUnlimited()));
        if (!onlyPerPeriod) return false;

        return quotas.stream().allMatch(q -> q.getUsedCount() >= q.getQuotaCount());
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
