package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.bot.GadalkaTelegramBot;
import ru.sapa.gadalka_backend.domain.Subscription;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.repository.SubscriptionRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Автопродление подписки через Robokassa (самостоятельная интеграция, Recurring/PreviousInvoiceID).
 * Четыре независимые задачи, все по расписанию раз в час:
 * <p>
 * 1) {@link #sendRenewalNotices()} — обязательное по п. 6.12.4 соглашения уведомление
 *    не позднее чем за 3 календарных дня до автосписания, с возможностью отключить автопродление.
 * 2) {@link #chargeRenewals()} — первая попытка списания строго в последние 24 часа ТЕКУЩЕГО
 *    расчётного периода (п. 6.12.3 соглашения) — то есть ДО истечения подписки, а не после,
 *    и только если уведомление реально ДОСТАВЛЕНО пользователю и с момента доставки прошло
 *    не меньше 24 часов (ст. 16.1 ЗоЗПП).
 * 3) {@link #reconcileStuckRenewals()} — подчищает попытки списания, зависшие без вебхука
 *    (Robokassa приняла запрос, но ResultURL так и не пришёл).
 * 4) {@link #retryFailedRenewals()} — повторные попытки для подписок в SUSPENDED, не чаще
 *    раза в сутки, в течение 7 календарных дней с первой неудачи (п. 6.13.1 соглашения);
 *    по истечении срока — подписка завершается (п. 6.13.4), без штрафа и без задолженности.
 * <p>
 * Пока идут ретраи — статус подписки SUSPENDED: доступ к Лимитам приостановлен, но Баланс
 * знаков и бесплатные функции доступны как обычно (п. 6.13.2). Единая точка обработки любой
 * неудачной попытки (из чем бы она ни была вызвана) — {@link #handleFailedRenewalAttempt}.
 * <p>
 * Если уведомление за 3 дня не успели доставить до истечения подписки (бэкенд лежал,
 * пользователь заблокировал бота) — подписка просто истекает как обычно, без автопродления
 * в этом цикле. Списывать без доставленного предупреждения нельзя ни при каких условиях,
 * даже ценой недополученных денег: {@code renewalNoticeDeliveredAt} остаётся NULL, и такая
 * подписка не попадает в выборку на списание
 * (см. {@code SubscriptionRepository#findAutoRenewChargeCandidates}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionRenewalScheduler {

    /** См. PendingPaymentExpiryService: таймаут автоотмены зависшего PENDING-платежа. */
    private static final int STUCK_RENEWAL_GRACE_MINUTES = 35;

    /** Максимум ретраев (п. 6.13.1 соглашения) — 7 календарных дней с первой неудачи. */
    private static final int MAX_RETRY_DAYS = 7;

    /**
     * Минимальный срок между ДОСТАВКОЙ уведомления и списанием — 24 часа (ст. 16.1 ЗоЗПП).
     * Обычно уведомление уходит за ~3 суток, но при простое бэкенда подписка может попасть
     * в выборку позже — тогда этот запас сдвигает списание, а если он не помещается
     * в оставшееся окно, автопродления в этом цикле просто не будет.
     */
    private static final int NOTICE_MIN_LEAD_HOURS = 24;

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final GadalkaTelegramBot telegramBot;
    private final PaymentService paymentService;

    @Value("${subscription.renewal.enabled:true}")
    private boolean enabled;

    /** Раз в час: уведомления о предстоящем автосписании (окно — 3 календарных дня до истечения) */
    @Scheduled(fixedDelayString = "${subscription.renewal.notice-check-interval-ms:3600000}")
    @Transactional
    public void sendRenewalNotices() {
        if (!enabled) return;

        OffsetDateTime now = OffsetDateTime.now();
        List<Subscription> candidates = subscriptionRepository.findAutoRenewNoticeCandidates(now, now.plusDays(3));
        if (candidates.isEmpty()) return;

        int sent = 0;
        for (Subscription subscription : candidates) {
            User user = userRepository.findById(subscription.getUserId()).orElse(null);

            // Фиксируем сам факт попытки — это диагностика, права на списание она не даёт.
            subscription.setRenewalNoticeSentAt(now);

            // Юзера нет или он забанен — доставлять некому, и само это не изменится:
            // Telegram даже не дёргаем. renewalNoticeDeliveredAt остаётся NULL, а значит
            // chargeRenewals такую подписку не возьмёт и деньги не спишутся.
            if (user == null || user.isBanned()) {
                continue;
            }

            // Право на списание даёт ТОЛЬКО реально доставленное уведомление (ст. 16.1 ЗоЗПП).
            // Не доставили (бот заблокирован) — кандидат остаётся в выборке, повторим
            // на следующем цикле, пока не закроется 3-дневное окно.
            if (telegramBot.sendAutoRenewNotice(user.getTelegramId(), buildNoticeMessage(subscription))) {
                subscription.setRenewalNoticeDeliveredAt(now);
                sent++;
            }
        }

        log.info("Уведомления об автосписании: кандидатов={}, доставлено={}", candidates.size(), sent);
    }

    /** Раз в час: первая попытка списания — строго в последние 24 часа ДО истечения текущего периода */
    @Scheduled(fixedDelayString = "${subscription.renewal.charge-check-interval-ms:3600000}")
    @Transactional
    public void chargeRenewals() {
        if (!enabled) return;

        OffsetDateTime now = OffsetDateTime.now();
        List<Subscription> candidates = subscriptionRepository.findAutoRenewChargeCandidates(now, now.plusHours(24), now.minusHours(NOTICE_MIN_LEAD_HOURS));
        if (candidates.isEmpty()) return;

        for (Subscription subscription : candidates) {
            attemptCharge(subscription, now);
        }

        log.info("Автосписания подписок (первая попытка): кандидатов={}", candidates.size());
    }

    /**
     * Раз в час: подчищает подписки, зависшие в RENEWAL_PENDING дольше разумного времени
     * ожидания вебхука — Robokassa приняла запрос на списание, но ResultURL так и не пришёл
     * (см. {@code SubscriptionRepository#findStuckRenewalPending}). Без этой задачи такая
     * подписка осталась бы в RENEWAL_PENDING навсегда — ни ретраев, ни уведомления пользователю.
     */
    @Scheduled(fixedDelayString = "${subscription.renewal.reconcile-check-interval-ms:3600000}")
    @Transactional
    public void reconcileStuckRenewals() {
        if (!enabled) return;

        OffsetDateTime now = OffsetDateTime.now();
        List<Subscription> stuck = subscriptionRepository.findStuckRenewalPending(now.minusMinutes(STUCK_RENEWAL_GRACE_MINUTES));
        if (stuck.isEmpty()) return;

        for (Subscription subscription : stuck) {
            log.warn("Зависшее автосписание без вебхука: subscriptionId={}, userId={}, lastAttempt={}",  subscription.getId(), subscription.getUserId(), subscription.getLastRenewalAttemptAt());
            handleFailedRenewalAttempt(subscription);
        }

        log.info("Реконсиляция зависших автосписаний: обработано={}", stuck.size());
    }

    /**
     * Раз в час: повторные попытки списания для подписок в SUSPENDED — но фактически не чаще
     * раза в сутки на подписку (фильтр в самом запросе, см.
     * {@code SubscriptionRepository#findAutoRenewRetryCandidates}). Если с первой неудачи
     * прошло уже 7 календарных дней — ретраи прекращаются и подписка завершается (п. 6.13.4),
     * без попытки очередного списания.
     */
    @Scheduled(fixedDelayString = "${subscription.renewal.retry-check-interval-ms:3600000}")
    @Transactional
    public void retryFailedRenewals() {
        if (!enabled) return;

        OffsetDateTime now = OffsetDateTime.now();
        List<Subscription> candidates = subscriptionRepository.findAutoRenewRetryCandidates(now.minusHours(24));
        if (candidates.isEmpty()) return;

        int retried = 0;
        int terminated = 0;
        for (Subscription subscription : candidates) {
            if (retryDeadlinePassed(subscription, now)) {
                terminateAfterExhaustedRetries(subscription);
                terminated++;
            } else {
                attemptCharge(subscription, now);
                retried++;
            }
        }

        log.info("Ретраи автосписаний: кандидатов={}, повторных попыток={}, завершено по сроку={}",
                candidates.size(), retried, terminated);
    }

    /** Запускает саму попытку списания (первую или повторную) и переводит подписку в RENEWAL_PENDING. */
    private void attemptCharge(Subscription subscription, OffsetDateTime now) {
        // autoRenewEnabled, rootPaymentId и lockedPriceRub НЕ трогаем — они нужны
        // SubscriptionActivationService, чтобы унаследовать их в новую подписку при успехе.
        subscription.setStatus("RENEWAL_PENDING");
        subscription.setLastRenewalAttemptAt(now);
        subscriptionRepository.save(subscription);

        boolean accepted = paymentService.renewSubscription(subscription);
        if (!accepted) {
            // Robokassa отклонила запрос сразу (например, отвязана карта) — вебхука
            // по такому запросу не будет, обрабатываем как неудачную попытку немедленно.
            handleFailedRenewalAttempt(subscription);
        }
    }

    private boolean retryDeadlinePassed(Subscription subscription, OffsetDateTime now) {
        OffsetDateTime firstFailedAt = subscription.getRenewalFirstFailedAt();
        return firstFailedAt != null && !now.isBefore(firstFailedAt.plusDays(MAX_RETRY_DAYS));
    }

    /**
     * Единая точка обработки любой неудачной попытки списания — вызывается из
     * {@link #attemptCharge} (немедленный отказ Robokassa) и из {@link #reconcileStuckRenewals}
     * (платёж завис без вебхука). Решает: это первая неудача за цикл (тогда фиксируем
     * renewalFirstFailedAt и уведомляем о приостановке) или уже вышли отведённые 7 дней
     * (тогда завершаем подписку) — иначе просто оставляем в SUSPENDED до следующего ретрая.
     */
    private void handleFailedRenewalAttempt(Subscription subscription) {
        boolean firstFailure = subscription.getRenewalFirstFailedAt() == null;
        if (firstFailure) {
            // Точка отсчёта 7 дней — момент САМОЙ попытки (lastRenewalAttemptAt), а не момент,
            // когда мы это обнаружили (важно для reconcileStuckRenewals — там это разные вещи).
            OffsetDateTime attemptTime = subscription.getLastRenewalAttemptAt() != null
                    ? subscription.getLastRenewalAttemptAt() : OffsetDateTime.now();
            subscription.setRenewalFirstFailedAt(attemptTime);
        }

        if (retryDeadlinePassed(subscription, OffsetDateTime.now())) {
            terminateAfterExhaustedRetries(subscription);
            return;
        }

        subscription.setStatus("SUSPENDED");
        subscriptionRepository.save(subscription);
        log.info("Подписка приостановлена после неудачного автосписания: subscriptionId={}, userId={}, firstFailure={}",
                subscription.getId(), subscription.getUserId(), firstFailure);

        // Уведомляем только один раз — при входе в SUSPENDED, а не на каждый последующий ретрай.
        if (firstFailure) {
            User user = userRepository.findById(subscription.getUserId()).orElse(null);
            if (user != null && !user.isBanned()) {
                OffsetDateTime retryDeadline = subscription.getRenewalFirstFailedAt().plusDays(MAX_RETRY_DAYS);
                telegramBot.sendAutoRenewSuspendedNotice(user.getTelegramId(), subscription.getPlanName(), retryDeadline);
            }
        }
    }

    /** Ретраи исчерпаны без успеха (п. 6.13.4) — автопродление выключается, подписка завершена. */
    private void terminateAfterExhaustedRetries(Subscription subscription) {
        subscription.setStatus("EXPIRED");
        subscription.setAutoRenewEnabled(false);
        subscriptionRepository.save(subscription);
        log.info("Автопродление прекращено после исчерпания ретраев: subscriptionId={}, userId={}", subscription.getId(), subscription.getUserId());

        User user = userRepository.findById(subscription.getUserId()).orElse(null);
        if (user != null && !user.isBanned()) {
            telegramBot.sendAutoRenewTerminatedNotice(user.getTelegramId(), subscription.getPlanName());
        }
    }

    private String buildNoticeMessage(Subscription subscription) {
        String planName = subscription.getPlanName() != null ? subscription.getPlanName() : "подписка";
        return String.format(
                """
                        🔮 В ближайшие дни автоматически продлится подписка *«%s»*.

                        Все действия с подпиской — статус, отключение автопродления — доступны в разделе «Профиль».""",
                planName
        );
    }
}
