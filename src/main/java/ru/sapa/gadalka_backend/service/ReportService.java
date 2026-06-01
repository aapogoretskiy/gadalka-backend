package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.repository.FortuneCreditLogRepository;
import ru.sapa.gadalka_backend.repository.FortuneRepository;
import ru.sapa.gadalka_backend.repository.PaymentRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Агрегирует статистику для страницы отчётов в админ-панели.
 *
 * <p>Все запросы — read-only, данные собираются в один проход по репозиториям.
 * Для MVP достаточно прямых запросов без кеша — страница отчётов не нагружена.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final UserRepository userRepository;
    private final FortuneRepository fortuneRepository;
    private final PaymentRepository paymentRepository;
    private final FortuneCreditLogRepository creditLogRepository;

    /**
     * Собирает полный снимок метрик.
     * Возвращает вложенный Map, который Jackson сериализует в JSON.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildReport() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime minus1d  = now.minusDays(1);
        OffsetDateTime minus7d  = now.minusDays(7);
        OffsetDateTime minus30d = now.minusDays(30);

        return Map.of(
                "users",    buildUsersSection(minus1d, minus7d, minus30d),
                "fortunes", buildFortunesSection(minus7d, minus30d),
                "payments", buildPaymentsSection(minus7d, minus30d),
                "credits",  buildCreditsSection()
        );
    }

    // ── Пользователи ─────────────────────────────────────────────────────────

    private Map<String, Object> buildUsersSection(
            OffsetDateTime minus1d, OffsetDateTime minus7d, OffsetDateTime minus30d) {

        long total       = userRepository.count();
        long newToday    = userRepository.countByCreatedAtAfter(minus1d);
        long new7d       = userRepository.countByCreatedAtAfter(minus7d);
        long new30d      = userRepository.countByCreatedAtAfter(minus30d);
        long dau         = userRepository.countByLastActiveAtAfter(minus1d);   // активны за 24ч
        long wau         = userRepository.countByLastActiveAtAfter(minus7d);   // активны за 7 дней

        return Map.of(
                "total",       total,
                "newToday",    newToday,
                "new7Days",    new7d,
                "new30Days",   new30d,
                "dau",         dau,
                "wau",         wau
        );
    }

    // ── Гадания ──────────────────────────────────────────────────────────────

    private Map<String, Object> buildFortunesSection(
            OffsetDateTime minus7d, OffsetDateTime minus30d) {

        long total   = fortuneRepository.count();
        long last7d  = fortuneRepository.countByCreatedAtAfter(minus7d);
        long last30d = fortuneRepository.countByCreatedAtAfter(minus30d);

        return Map.of(
                "total",     total,
                "last7Days", last7d,
                "last30Days",last30d
        );
    }

    // ── Платежи ──────────────────────────────────────────────────────────────

    private Map<String, Object> buildPaymentsSection(
            OffsetDateTime minus7d, OffsetDateTime minus30d) {

        // Рублёвые платежи (Robokassa) — amount_minor хранится в копейках
        long rubTotal    = paymentRepository.sumSucceededRub();
        long rub7d       = paymentRepository.sumSucceededRubSince(minus7d);
        long rub30d      = paymentRepository.sumSucceededRubSince(minus30d);
        long rubUsers    = paymentRepository.countPayingUsers();

        // Stars (Telegram) — amount_minor хранится в штуках (1 Star = 1)
        long starsTotal  = paymentRepository.sumSucceededStars();
        long stars7d     = paymentRepository.sumSucceededStarsSince(minus7d);
        long stars30d    = paymentRepository.sumSucceededStarsSince(minus30d);
        long starsUsers  = paymentRepository.countStarsPayingUsers();

        return Map.of(
                // Рубли (копейки → рубли делаем на фронте, передаём kopecks)
                "rubKopecksTotal",   rubTotal,
                "rubKopecks7Days",   rub7d,
                "rubKopecks30Days",  rub30d,
                "rubPayingUsers",    rubUsers,
                // Stars (штуки)
                "starsTotal",        starsTotal,
                "stars7Days",        stars7d,
                "stars30Days",       stars30d,
                "starsPayingUsers",  starsUsers
        );
    }

    // ── Знаки (кредиты) ──────────────────────────────────────────────────────

    private Map<String, Object> buildCreditsSection() {
        long totalGranted       = creditLogRepository.sumGranted();
        long totalSpent         = creditLogRepository.sumSpent();
        long grantedByPayment   = creditLogRepository.sumGrantedByPayment();
        long grantedByAdmin     = creditLogRepository.sumGrantedByAdmin();
        long grantedByBonus     = creditLogRepository.sumGrantedByBonus();

        // Текущий суммарный баланс у всех пользователей = начислено − потрачено
        long currentInCirculation = totalGranted - totalSpent;

        return Map.of(
                "totalGranted",         totalGranted,
                "totalSpent",           totalSpent,
                "currentInCirculation", currentInCirculation,
                "grantedByPayment",     grantedByPayment,
                "grantedByAdmin",       grantedByAdmin,
                "grantedByBonus",       grantedByBonus
        );
    }
}
