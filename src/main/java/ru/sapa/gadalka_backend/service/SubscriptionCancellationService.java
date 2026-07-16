package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.bot.GadalkaTelegramBot;
import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.Subscription;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.domain.type.PaymentStatus;
import ru.sapa.gadalka_backend.domain.type.PurchaseType;
import ru.sapa.gadalka_backend.repository.PaymentRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Отказ от подписки и возвраты.
 * <p>
 * Два сценария:
 * <ul>
 *   <li><b>Отказ пользователем</b> ({@link #cancelByUser}) — добровольное освобождение
 *       слота «одной подписки»: квоты и оставшийся срок сгорают, деньги автоматически
 *       НЕ возвращаются (право на возврат по ст. 32 ЗоЗПП реализуется через поддержку).</li>
 *   <li><b>Возврат админом</b> ({@link #refundSubscriptionPayment}) — по обращению
 *       в поддержку: подписка отменяется, платёж помечается REFUNDED. Деньги:
 *       Telegram Stars возвращаются автоматически (refundStarPayment), рублёвые
 *       платежи админ возвращает вручную через ЛК Robokassa.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionCancellationService {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final GadalkaTelegramBot telegramBot;

    /**
     * Отказ от активной подписки самим пользователем.
     *
     * @return отменённая подписка
     * @throws IllegalStateException если активной подписки нет
     */
    @Transactional
    public Subscription cancelByUser(Long userId) {
        Subscription subscription = subscriptionRepository
                .findActiveByUserId(userId, OffsetDateTime.now())
                .orElseThrow(() -> new IllegalStateException("Активной подписки нет"));

        markCancelled(subscription);
        log.info("Пользователь отказался от подписки: userId={}, subscriptionId={}, plan='{}'",
                userId, subscription.getId(), subscription.getPlanName());
        return subscription;
    }

    /** Результат оформления возврата — для сообщения админу */
    public record RefundResult(boolean subscriptionCancelled, boolean starsRefunded, String note) {
    }

    /**
     * Оформляет возврат подписочного платежа (вызывается из админки).
     * <p>
     * Действия: платёж → REFUNDED, активная подписка пользователя отменяется.
     * Для TELEGRAM_STARS возврат звёзд выполняется автоматически через Bot API;
     * для Robokassa деньги нужно вернуть вручную через личный кабинет — об этом говорит note в ответе.
     */
    @Transactional
    public RefundResult refundSubscriptionPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Платёж не найден: id=" + paymentId));

        if (payment.getPurchaseType() != PurchaseType.SUBSCRIPTION) {
            throw new IllegalArgumentException("Возврат оформляется только по подписочным платежам");
        }
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new IllegalArgumentException("Возврат возможен только по успешному платежу (сейчас: " + payment.getStatus() + ")");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(OffsetDateTime.now());
        paymentRepository.save(payment);

        // Отменяем активную подписку пользователя (если ещё активна)
        Optional<Subscription> activeOpt = subscriptionRepository
                .findActiveByUserId(payment.getUserId(), OffsetDateTime.now());
        boolean cancelled = false;
        if (activeOpt.isPresent()) {
            markCancelled(activeOpt.get());
            cancelled = true;
        }

        // Stars возвращаем автоматически через Bot API, рубли — вручную через ЛК Robokassa
        boolean starsRefunded = false;
        String note;
        if (payment.getProvider() == PaymentProvider.TELEGRAM_STARS) {
            User user = userRepository.findById(payment.getUserId()).orElse(null);
            if (user != null && payment.getProviderPaymentId() != null) {
                starsRefunded = telegramBot.refundStarPayment(user.getTelegramId(), payment.getProviderPaymentId());
            }
            note = starsRefunded
                    ? "Звёзды возвращены автоматически"
                    : "Не удалось вернуть звёзды автоматически — проверьте вручную (см. логи)";
        } else {
            note = "Рублёвый платёж: верните деньги вручную через личный кабинет Robokassa";
        }

        log.info("Оформлен возврат: paymentId={}, userId={}, provider={}, подписка отменена={}, starsRefunded={}", paymentId, payment.getUserId(), payment.getProvider(), cancelled, starsRefunded);

        return new RefundResult(cancelled, starsRefunded, note);
    }

    private void markCancelled(Subscription subscription) {
        subscription.setStatus("CANCELLED");
        subscription.setCancelledAt(OffsetDateTime.now());
        subscriptionRepository.save(subscription);
    }
}
