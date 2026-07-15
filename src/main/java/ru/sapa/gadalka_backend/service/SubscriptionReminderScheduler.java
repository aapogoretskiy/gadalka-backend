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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Напоминания об истечении подписки: за 3 дня, за 2 дня и в день истечения.
 * <p>
 * v1 без автопродления — пользователь продлевает вручную, поэтому напоминания
 * критичны для удержания. Дни считаются по МСК-датам (не по «полным суткам»):
 * если подписка истекает 18-го, то 15-го придёт «осталось 3 дня», 16-го — «2 дня»,
 * 18-го — «истекает сегодня».
 * <p>
 * Защита от дублей: {@code lastReminderDaysLeft} на подписке хранит последний
 * отправленный рубеж. Шлём только если текущий рубеж МЕНЬШЕ отправленного
 * (движемся 3 → 2 → 0). Пропущенные рубежи (бэкенд лежал) просто перескакиваются.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionReminderScheduler {

    /** Рубежи напоминаний: дней до истечения */
    private static final List<Integer> REMINDER_THRESHOLDS = List.of(3, 2, 0);

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final GadalkaTelegramBot telegramBot;

    @Value("${subscription.reminder.enabled:true}")
    private boolean enabled;

    /** Раз в час: напоминания уходят в первый прогон после полуночи МСК нового рубежа */
    @Scheduled(fixedDelayString = "${subscription.reminder.check-interval-ms:3600000}")
    @Transactional
    public void sendExpiryReminders() {
        if (!enabled) return;

        OffsetDateTime now = OffsetDateTime.now();
        // +4 дня покрывает рубеж «3 дня» с запасом на границы дат
        List<Subscription> expiring = subscriptionRepository.findActiveExpiringBefore(now, now.plusDays(4));
        if (expiring.isEmpty()) return;

        LocalDate todayMsk = LocalDate.now(SubscriptionQuotaService.MSK);
        int sent = 0;

        for (Subscription subscription : expiring) {
            LocalDate expiryDateMsk = subscription.getExpiresAt()
                    .atZoneSameInstant(SubscriptionQuotaService.MSK)
                    .toLocalDate();
            int daysLeft = (int) ChronoUnit.DAYS.between(todayMsk, expiryDateMsk);

            if (!REMINDER_THRESHOLDS.contains(daysLeft)) continue;

            // Уже слали этот (или более поздний) рубеж — пропускаем
            Integer lastSent = subscription.getLastReminderDaysLeft();
            if (lastSent != null && lastSent <= daysLeft) continue;

            User user = userRepository.findById(subscription.getUserId()).orElse(null);
            if (user == null || user.isBanned()) continue;

            telegramBot.sendSubscriptionExpiryReminder(user.getTelegramId(),
                    buildMessage(subscription, daysLeft));

            // Помечаем даже при неудачной отправке (бот заблокирован) — ретраи бессмысленны
            subscription.setLastReminderDaysLeft(daysLeft);
            sent++;
        }

        if (sent > 0) {
            log.info("Напоминания об истечении подписок: кандидатов={}, отправлено={}", expiring.size(), sent);
        }
    }

    private String buildMessage(Subscription subscription, int daysLeft) {
        String planName = subscription.getPlanName() != null ? subscription.getPlanName() : "подписка";
        return switch (daysLeft) {
            case 3 -> String.format(
                    "🌙 Ваша подписка *«%s»* действует ещё 3 дня.\n\nКвоты, которые вы не успеете использовать, сгорят — самое время заглянуть к картам ✨",
                    planName);
            case 2 -> String.format(
                    "🔮 Подписка *«%s»* истекает через 2 дня.\n\nПродлите заранее, чтобы не остаться без предсказаний 💫",
                    planName);
            default -> String.format(
                    "⏳ Сегодня — последний день подписки *«%s»*.\n\nПродлите её, чтобы квоты и предсказания остались с вами ✨",
                    planName);
        };
    }
}
