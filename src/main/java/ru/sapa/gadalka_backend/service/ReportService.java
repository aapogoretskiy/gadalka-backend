package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.api.dto.admin.report.ActionsTodayDto;
import ru.sapa.gadalka_backend.api.dto.admin.report.AdminReportDto;
import ru.sapa.gadalka_backend.api.dto.admin.report.CreditsReportDto;
import ru.sapa.gadalka_backend.api.dto.admin.report.FortunesReportDto;
import ru.sapa.gadalka_backend.api.dto.admin.report.PaymentsReportDto;
import ru.sapa.gadalka_backend.api.dto.admin.report.ReturningUsersDto;
import ru.sapa.gadalka_backend.api.dto.admin.report.UsersReportDto;
import ru.sapa.gadalka_backend.domain.type.SpreadType;
import ru.sapa.gadalka_backend.repository.CompatibilityReadingRepository;
import ru.sapa.gadalka_backend.repository.DailyCardRepository;
import ru.sapa.gadalka_backend.repository.FortuneCreditLogRepository;
import ru.sapa.gadalka_backend.repository.FortuneRepository;
import ru.sapa.gadalka_backend.repository.NumerologyDayReadingRepository;
import ru.sapa.gadalka_backend.repository.PaymentRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.repository.UserVisitRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;

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
    private final CompatibilityReadingRepository compatibilityReadingRepository;
    private final NumerologyDayReadingRepository numerologyDayReadingRepository;
    private final DailyCardRepository dailyCardRepository;
    private final UserVisitRepository userVisitRepository;
    private final PaymentRepository paymentRepository;
    private final FortuneCreditLogRepository creditLogRepository;

    /**
     * Собирает полный снимок метрик и возвращает типизированный DTO.
     */
    @Transactional(readOnly = true)
    public AdminReportDto buildReport() {
        OffsetDateTime now      = OffsetDateTime.now();
        OffsetDateTime minus1d  = now.minusDays(1);
        OffsetDateTime minus7d  = now.minusDays(7);
        OffsetDateTime minus30d = now.minusDays(30);

        return new AdminReportDto(
                buildUsersSection(minus1d, minus7d, minus30d),
                buildFortunesSection(minus7d, minus30d),
                buildActionsTodaySection(minus1d),
                buildReturningUsersSection(minus1d, minus7d, minus30d),
                buildPaymentsSection(minus7d, minus30d),
                buildCreditsSection()
        );
    }

    // ── Пользователи ─────────────────────────────────────────────────────────

    private UsersReportDto buildUsersSection(
            OffsetDateTime minus1d, OffsetDateTime minus7d, OffsetDateTime minus30d) {

        return new UsersReportDto(
                userRepository.count(),
                userRepository.countByCreatedAtAfter(minus1d),
                userRepository.countByCreatedAtAfter(minus7d),
                userRepository.countByCreatedAtAfter(minus30d),
                userRepository.countByLastActiveAtAfter(minus1d),   // DAU: активны за 24ч
                userRepository.countByLastActiveAtAfter(minus7d)    // WAU: активны за 7 дней
        );
    }

    // ── Гадания ──────────────────────────────────────────────────────────────

    private FortunesReportDto buildFortunesSection(
            OffsetDateTime minus7d, OffsetDateTime minus30d) {

        return new FortunesReportDto(
                fortuneRepository.count(),
                fortuneRepository.countByCreatedAtAfter(minus7d),
                fortuneRepository.countByCreatedAtAfter(minus30d)
        );
    }

    // ── Платежи ──────────────────────────────────────────────────────────────

    private PaymentsReportDto buildPaymentsSection(
            OffsetDateTime minus7d, OffsetDateTime minus30d) {

        // Рублёвые платежи (Robokassa) — amount_minor хранится в копейках
        // Stars (Telegram) — amount_minor хранится в штуках (1 Star = 1)
        // Конвертацию kopecks → рубли делаем на фронте
        return new PaymentsReportDto(
                paymentRepository.sumSucceededRub(),
                paymentRepository.sumSucceededRubSince(minus7d),
                paymentRepository.sumSucceededRubSince(minus30d),
                paymentRepository.countPayingUsers(),
                paymentRepository.sumSucceededStars(),
                paymentRepository.sumSucceededStarsSince(minus7d),
                paymentRepository.sumSucceededStarsSince(minus30d),
                paymentRepository.countStarsPayingUsers()
        );
    }

    // ── Детализация действий за сутки ────────────────────────────────────────

    /**
     * Разбивка по типам действий за последние 24 часа.
     * Fortune считаем по SpreadType; старые записи без типа относим к THREE_CARD.
     * NumerologyDayReading и DailyCard хранят LocalDate, поэтому считаем с начала сегодняшнего дня.
     */
    private ActionsTodayDto buildActionsTodaySection(OffsetDateTime minus1d) {
        LocalDate today = LocalDate.now();

        long threeCard   = fortuneRepository.countByCreatedAtAfterAndSpreadType(minus1d, SpreadType.THREE_CARD)
                         + fortuneRepository.countByCreatedAtAfterAndSpreadTypeIsNull(minus1d);
        long horseshoe   = fortuneRepository.countByCreatedAtAfterAndSpreadType(minus1d, SpreadType.HORSESHOE);
        long celticCross = fortuneRepository.countByCreatedAtAfterAndSpreadType(minus1d, SpreadType.CELTIC_CROSS);
        long compatibility = compatibilityReadingRepository.countByCreatedAtAfter(minus1d);
        long numerology  = numerologyDayReadingRepository.countByDateGreaterThanEqual(today);
        long dailyCard   = dailyCardRepository.countByDateGreaterThanEqual(today);
        long total       = threeCard + horseshoe + celticCross + compatibility + numerology + dailyCard;

        return new ActionsTodayDto(total, threeCard, horseshoe, celticCross, compatibility, numerology, dailyCard);
    }

    // ── Повторные посещения ───────────────────────────────────────────────────

    /**
     * Количество пользователей, зашедших более одного раза за период.
     * Данные берутся из таблицы user_visits.
     */
    private ReturningUsersDto buildReturningUsersSection(
            OffsetDateTime minus1d, OffsetDateTime minus7d, OffsetDateTime minus30d) {

        return new ReturningUsersDto(
                userVisitRepository.countUsersWithMultipleVisits(minus1d),
                userVisitRepository.countUsersWithMultipleVisits(minus7d),
                userVisitRepository.countUsersWithMultipleVisits(minus30d)
        );
    }

    // ── Знаки (кредиты) ──────────────────────────────────────────────────────

    private CreditsReportDto buildCreditsSection() {
        long totalGranted = creditLogRepository.sumGranted();
        long totalSpent   = creditLogRepository.sumSpent();

        return new CreditsReportDto(
                totalGranted,
                totalSpent,
                totalGranted - totalSpent,            // currentInCirculation
                creditLogRepository.sumGrantedByPayment(),
                creditLogRepository.sumGrantedByAdmin(),
                creditLogRepository.sumGrantedByBonus()
        );
    }
}
