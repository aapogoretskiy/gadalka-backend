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
 * Две независимые задачи, обе по расписанию раз в час:
 * <p>
 * 1) {@link #sendRenewalNotices()} — обязательное по п. 6.12.4 соглашения уведомление
 *    не позднее чем за 3 календарных дня до автосписания, с возможностью отключить автопродление.
 * 2) {@link #chargeRenewals()} — само списание строго в последние 24 часа ТЕКУЩЕГО расчётного
 *    периода (п. 6.12.3 соглашения) — то есть ДО истечения подписки, а не после, и только
 *    если уведомление уже было отправлено.
 * <p>
 * Если уведомление за 3 дня не успели отправить до истечения подписки (например, бэкенд
 * лежал) — подписка просто истекает как обычно, без автопродления в этом цикле. Списывать
 * без предупреждения нельзя, поэтому такие подписки намеренно не попадают в выборку
 * (см. {@code SubscriptionRepository#findAutoRenewNoticeCandidates}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionRenewalScheduler {

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

            // Помечаем как отправленное даже если реально не отправили (нет юзера/забанен/бот
            // заблокирован) — иначе кандидат будет попадать в выборку каждый час без толку.
            // Раз уведомление физически не доставлено, дальнейшее автосписание всё равно
            // не должно случиться "молча" — но это уже проверяется на стороне chargeRenewals
            // отдельными данными, здесь просто не повторяем бесполезную попытку.
            subscription.setRenewalNoticeSentAt(now);

            if (user == null || user.isBanned()) {
                continue;
            }

            if (telegramBot.sendAutoRenewNotice(user.getTelegramId(), buildNoticeMessage(subscription))) {
                sent++;
            }
        }

        log.info("Уведомления об автосписании: кандидатов={}, доставлено={}", candidates.size(), sent);
    }

    /** Раз в час: реальные автосписания — строго в последние 24 часа ДО истечения текущего периода */
    @Scheduled(fixedDelayString = "${subscription.renewal.charge-check-interval-ms:3600000}")
    @Transactional
    public void chargeRenewals() {
        if (!enabled) return;

        OffsetDateTime now = OffsetDateTime.now();
        List<Subscription> candidates = subscriptionRepository.findAutoRenewChargeCandidates(now, now.plusHours(24));
        if (candidates.isEmpty()) return;

        for (Subscription subscription : candidates) {
            // Сразу уводим из ACTIVE, чтобы этот же кандидат не попал в выборку повторно
            // на следующем часовом тике — иначе риск задвоенного списания. autoRenewEnabled
            // и rootPaymentId НЕ трогаем — они нужны SubscriptionActivationService, чтобы
            // унаследовать их в новую подписку при успешном продлении.
            subscription.setStatus("RENEWAL_PENDING");
            subscriptionRepository.save(subscription);

            boolean accepted = paymentService.renewSubscription(subscription);

            if (!accepted) {
                // Robokassa отклонила запрос сразу (например, отвязана карта) — вебхука
                // по такому запросу не будет, сообщаем пользователю немедленно, а не молчим.
                User user = userRepository.findById(subscription.getUserId()).orElse(null);
                if (user != null && !user.isBanned()) {
                    telegramBot.sendAutoRenewFailedNotice(user.getTelegramId(), subscription.getPlanName());
                }
            }
        }

        log.info("Автосписания подписок: кандидатов={}", candidates.size());
    }

    private String buildNoticeMessage(Subscription subscription) {
        String planName = subscription.getPlanName() != null ? subscription.getPlanName() : "подписка";
        return String.format(
                """
                        🔮 В ближайшие дни автоматически продлится подписка *«%s»*.

                        Если не хотите продлевать — отключите автопродление в управлении подпиской.""",
                planName
        );
    }
}
