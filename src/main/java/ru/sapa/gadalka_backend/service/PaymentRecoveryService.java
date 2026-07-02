package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.bot.GadalkaTelegramBot;
import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.User;
import ru.sapa.gadalka_backend.domain.type.PaymentProvider;
import ru.sapa.gadalka_backend.domain.type.PaymentStatus;
import ru.sapa.gadalka_backend.repository.PaymentProductRepository;
import ru.sapa.gadalka_backend.repository.PaymentRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Догоняющие напоминания о брошенной оплате.
 *
 * <p>Зачем: по данным на июль 2026 пользователей, дошедших до окна оплаты и бросивших
 * его, столько же, сколько заплативших (30 против 22). Особенно это касается
 * Telegram Stars — конверсия окна ~7% (людям в РФ сложно пополнить звёзды), тогда как
 * у карты ~57%. Напоминание с прямой кнопкой «оплатить картой» возвращает часть
 * этих пользователей без затрат на трафик.
 *
 * <p>Как работает: раз в {@code check-interval-ms} сервис ищет платежи в статусе
 * CANCELLED/FAILED возрастом от {@code min-age-minutes} до {@code max-age-minutes}
 * (нижняя граница — чтобы не дёргать человека, который прямо сейчас пробует ещё раз;
 * верхняя — чтобы после деплоя не рассылать по старым платежам) и шлёт одно
 * напоминание. Защита от спама тройная:
 * <ul>
 *   <li>{@code reminder_sent_at} на платеже — по одному платежу шлём максимум один раз;</li>
 *   <li>кулдаун {@code cooldown-days} по пользователю — не чаще одного напоминания в N дней;</li>
 *   <li>если после брошенного платежа пользователь успешно оплатил — молчим.</li>
 * </ul>
 *
 * <p>Тексты выбираются случайно из пула — отдельный пул для Stars (акцент «попробуйте
 * картой») и для карточных платежей. Пулы легко расширять — просто добавить строку.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {

    /**
     * Пул текстов для брошенных Stars-платежей. Плейсхолдер %s — название пакета.
     * Ключевой посыл: не вышло со звёздами — есть карта (МИР/СБП работают).
     */
    private static final List<String> STARS_MESSAGES = List.of(
            "⭐ Не хватило звёзд? Бывает 🙂\n\nПакет *«%s»* можно оплатить обычной картой — МИР, СБП, Visa. Это займёт минуту 💳",
            "✨ Звёзды сегодня капризны — оплата пакета *«%s»* не прошла.\n\nПопробуйте картой: обычно получается с первого раза 💳",
            "🔮 Карты видят незавершённый путь: пакет *«%s»* так и остался неоплаченным.\n\nОплата картой через СБП — быстрее и проще ✨",
            "🌙 Звёзды не сошлись? Ничего страшного.\n\nПакет *«%s»* ждёт вас — оплатите картой, это займёт минуту 💫"
    );

    /** Пул текстов для брошенных карточных платежей. Плейсхолдер %s — название пакета. */
    private static final List<String> CARD_MESSAGES = List.of(
            "💫 Ваш пакет *«%s»* почти был у вас — оплата не завершилась.\n\nВернуться и завершить? Это займёт минуту 💳",
            "🌙 Что-то отвлекло вас от оплаты пакета *«%s»*?\n\nЗнаки уже ждут — попробуйте ещё раз ✨",
            "🔮 Оплата пакета *«%s»* не прошла.\n\nЕсли возникли сложности — попробуйте снова: МИР и СБП работают стабильно 💳",
            "✨ Вселенная подсказывает: пакет *«%s»* остался неоплаченным.\n\nЗавершите оплату — и знаки сразу появятся на балансе 🔮"
    );

    private final PaymentRepository paymentRepository;
    private final PaymentProductRepository paymentProductRepository;
    private final UserRepository userRepository;
    private final GadalkaTelegramBot telegramBot;

    @Value("${payment.recovery.enabled:true}")
    private boolean enabled;

    @Value("${payment.recovery.min-age-minutes:60}")
    private int minAgeMinutes;

    @Value("${payment.recovery.max-age-minutes:180}")
    private int maxAgeMinutes;

    @Value("${payment.recovery.cooldown-days:7}")
    private int cooldownDays;

    @Scheduled(fixedDelayString = "${payment.recovery.check-interval-ms:900000}")
    @Transactional
    public void sendRecoveryReminders() {
        if (!enabled) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<Payment> candidates = paymentRepository.findRecoveryCandidates(
                now.minusMinutes(maxAgeMinutes),
                now.minusMinutes(minAgeMinutes));

        if (candidates.isEmpty()) {
            return;
        }

        // Кандидаты отсортированы по createdAt DESC: у пользователя с несколькими
        // брошенными платежами напоминаем о самом свежем, остальные пропускаем
        Set<Long> processedUsers = new HashSet<>();
        int sent = 0;

        for (Payment payment : candidates) {
            Long userId = payment.getUserId();
            if (!processedUsers.add(userId)) {
                continue;
            }

            // Пользователь уже успешно оплатил после брошенного платежа — не напоминаем
            if (paymentRepository.existsByUserIdAndStatusAndCreatedAtAfter(
                    userId, PaymentStatus.SUCCEEDED, payment.getCreatedAt())) {
                continue;
            }

            // Кулдаун: не чаще одного напоминания на пользователя в cooldownDays
            if (paymentRepository.existsByUserIdAndReminderSentAtAfter(
                    userId, now.minusDays(cooldownDays))) {
                continue;
            }

            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.isBanned()) {
                continue;
            }

            telegramBot.sendPaymentRecoveryMessage(user.getTelegramId(), buildMessage(payment));

            // Помечаем в любом случае (даже если Telegram отклонил отправку — например,
            // бот заблокирован): повторная попытка всё равно не доставит сообщение,
            // а бесконечные ретраи по заблокировавшим бота пользователям не нужны.
            payment.setReminderSentAt(now);
            sent++;
        }

        if (sent > 0) {
            log.info("Напоминания о брошенных оплатах: кандидатов={}, отправлено={}", candidates.size(), sent);
        }
    }

    /** Собирает текст напоминания: случайный вариант из пула по типу провайдера. */
    private String buildMessage(Payment payment) {
        String productName = paymentProductRepository.findByCode(payment.getProductCode())
                .map(p -> p.getName())
                .orElse(payment.getProductCode());

        List<String> pool = payment.getProvider() == PaymentProvider.TELEGRAM_STARS
                ? STARS_MESSAGES
                : CARD_MESSAGES;

        String template = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        return String.format(template, productName);
    }
}
