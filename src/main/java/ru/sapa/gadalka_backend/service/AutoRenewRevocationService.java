package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.domain.Subscription;
import ru.sapa.gadalka_backend.domain.SubscriptionAutorenewConsentLog;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.ConsentAction;
import ru.sapa.gadalka_backend.domain.type.ConsentRevokeReason;
import ru.sapa.gadalka_backend.repository.SubscriptionAutorenewConsentLogRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

/**
 * Единственная точка отзыва согласия на автопродление.
 * <p>
 * Отзыв нужен в нескольких местах и по разным поводам: пользователь выключил автопродление
 * сам, подписка исчерпана досрочно, пользователь отказался от подписки, админ вернул деньги.
 * Каждый раз надо сделать ровно две вещи вместе — погасить {@code autoRenewEnabled} и
 * записать REVOKED в журнал согласий. Разъедься эта пара по четырём копиям — рано или поздно
 * в одной из них забудут журнал, и мы останемся без доказательства, что списаний больше
 * не будет. Поэтому — один сервис на все случаи.
 * <p>
 * Важно, что подписка вне активных статусов и так не будет списана (шедулер берёт только
 * ACTIVE/SUSPENDED). Но горящий флаг виден пользователю в профиле и выглядит как действующее
 * согласие, а незакрытая запись в журнале — как согласие, действующее до сих пор.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoRenewRevocationService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionAutorenewConsentLogRepository consentLogRepository;
    private final UserRepository userRepository;

    /**
     * Гасит автопродление подписки и фиксирует отзыв согласия в журнале.
     * <p>
     * Идемпотентен: если автопродление уже выключено — не делает ничего и не плодит
     * дубликаты в журнале. Это существенно, потому что вызывается в том числе из общих
     * путей ({@code markCancelled}), которые отрабатывают и на подписках без автопродления.
     *
     * @return true, если согласие действительно было отозвано именно этим вызовом
     */
    @Transactional
    public boolean revoke(Subscription subscription, ConsentRevokeReason reason) {
        if (!Boolean.TRUE.equals(subscription.getAutoRenewEnabled())) {
            return false;
        }

        subscription.setAutoRenewEnabled(false);
        subscriptionRepository.save(subscription);

        // telegram_id дублируем намеренно: user_id обнулится при удалении аккаунта
        // (ON DELETE SET NULL, миграция V72), а отзыв должен остаться доказуемым.
        Long telegramId = userRepository.findById(subscription.getUserId())
                .map(User::getTelegramId)
                .orElse(null);

        consentLogRepository.save(SubscriptionAutorenewConsentLog.builder()
                .userId(subscription.getUserId())
                .telegramId(telegramId)
                .subscriptionId(subscription.getId())
                .action(ConsentAction.REVOKED)
                .reason(reason)
                .build());

        log.info("Согласие на автопродление отозвано: subscriptionId={}, userId={}, причина={}",
                subscription.getId(), subscription.getUserId(), reason);
        return true;
    }
}
