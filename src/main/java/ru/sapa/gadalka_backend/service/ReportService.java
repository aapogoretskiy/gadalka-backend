package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.api.dto.admin.report.ActionsTodayDto;
import ru.sapa.gadalka_backend.api.dto.admin.report.AdminReportDto;
import ru.sapa.gadalka_backend.api.dto.admin.report.CompatibilityReportDto;
import ru.sapa.gadalka_backend.api.dto.admin.report.CreditsReportDto;
import ru.sapa.gadalka_backend.api.dto.admin.report.FortunesReportDto;
import ru.sapa.gadalka_backend.api.dto.admin.report.PaymentsReportDto;
import ru.sapa.gadalka_backend.api.dto.admin.report.RangeReportDto;
import ru.sapa.gadalka_backend.api.dto.admin.report.ReturningUsersDto;
import ru.sapa.gadalka_backend.api.dto.admin.report.UsersReportDto;
import ru.sapa.gadalka_backend.domain.type.SpreadType;
import ru.sapa.gadalka_backend.repository.CompatibilityReadingRepository;
import ru.sapa.gadalka_backend.repository.DailyCardRepository;
import ru.sapa.gadalka_backend.repository.DreamReadingRepository;
import ru.sapa.gadalka_backend.repository.FortuneCreditLogRepository;
import ru.sapa.gadalka_backend.repository.FortuneRepository;
import ru.sapa.gadalka_backend.repository.NumerologyDayReadingRepository;
import ru.sapa.gadalka_backend.repository.NumerologyMonthReadingRepository;
import ru.sapa.gadalka_backend.repository.NumerologyWeekReadingRepository;
import ru.sapa.gadalka_backend.repository.PaymentRepository;
import ru.sapa.gadalka_backend.repository.UserRepository;
import ru.sapa.gadalka_backend.repository.UserVisitRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Агрегирует статистику для страницы отчётов в админ-панели.
 *
 * <p>Все методы принимают опциональный параметр {@code source}:
 * <ul>
 *   <li>{@code null} — без фильтра (все пользователи)</li>
 *   <li>{@code "__organic__"} — пользователи без источника (referral_source IS NULL)</li>
 *   <li>любая другая строка — пользователи с конкретным referral_source</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final UserRepository userRepository;
    private final FortuneRepository fortuneRepository;
    private final CompatibilityReadingRepository compatibilityReadingRepository;
    private final NumerologyDayReadingRepository numerologyDayReadingRepository;
    private final NumerologyWeekReadingRepository numerologyWeekReadingRepository;
    private final NumerologyMonthReadingRepository numerologyMonthReadingRepository;
    private final DreamReadingRepository dreamReadingRepository;
    private final DailyCardRepository dailyCardRepository;
    private final UserVisitRepository userVisitRepository;
    private final PaymentRepository paymentRepository;
    private final FortuneCreditLogRepository creditLogRepository;

    /**
     * Полный снимок метрик. Если source != null — все счётчики фильтруются по источнику.
     */
    @Transactional(readOnly = true)
    public AdminReportDto buildReport(@Nullable String source) {
        OffsetDateTime now      = OffsetDateTime.now();
        OffsetDateTime minus1d  = now.minusDays(1);
        OffsetDateTime minus7d  = now.minusDays(7);
        OffsetDateTime minus30d = now.minusDays(30);

        return new AdminReportDto(
                buildUsersSection(minus1d, minus7d, minus30d, source),
                buildFortunesSection(minus7d, minus30d, source),
                buildCompatibilitySection(minus7d, minus30d, source),
                buildActionsTodaySection(minus1d, source),
                buildReturningUsersSection(minus1d, minus7d, minus30d, source),
                buildPaymentsSection(minus7d, minus30d, source),
                buildCreditsSection()  // кредиты не фильтруются по источнику
        );
    }

    // ── Пользователи ─────────────────────────────────────────────────────────

    private UsersReportDto buildUsersSection(
            OffsetDateTime minus1d, OffsetDateTime minus7d, OffsetDateTime minus30d,
            @Nullable String source) {

        return new UsersReportDto(
                userRepository.countWithSourceFilter(source),
                userRepository.countByCreatedAtAfterWithSource(minus1d, source),
                userRepository.countByCreatedAtAfterWithSource(minus7d, source),
                userRepository.countByCreatedAtAfterWithSource(minus30d, source),
                userRepository.countByLastActiveAtAfterWithSource(minus1d, source),
                userRepository.countByLastActiveAtAfterWithSource(minus7d, source)
        );
    }

    // ── Гадания ──────────────────────────────────────────────────────────────

    private FortunesReportDto buildFortunesSection(
            OffsetDateTime minus7d, OffsetDateTime minus30d, @Nullable String source) {

        return new FortunesReportDto(
                fortuneRepository.countWithSourceFilter(source),
                fortuneRepository.countByCreatedAtAfterWithSource(minus7d, source),
                fortuneRepository.countByCreatedAtAfterWithSource(minus30d, source)
        );
    }

    // ── Совместимость ────────────────────────────────────────────────────────

    private CompatibilityReportDto buildCompatibilitySection(
            OffsetDateTime minus7d, OffsetDateTime minus30d, @Nullable String source) {

        return new CompatibilityReportDto(
                compatibilityReadingRepository.countWithSourceFilter(source),
                compatibilityReadingRepository.countByCreatedAtAfterWithSource(minus7d, source),
                compatibilityReadingRepository.countByCreatedAtAfterWithSource(minus30d, source)
        );
    }

    // ── Платежи ──────────────────────────────────────────────────────────────

    private PaymentsReportDto buildPaymentsSection(
            OffsetDateTime minus7d, OffsetDateTime minus30d, @Nullable String source) {

        return new PaymentsReportDto(
                paymentRepository.sumSucceededRubWithSource(source),
                paymentRepository.sumSucceededRubSinceWithSource(minus7d, source),
                paymentRepository.sumSucceededRubSinceWithSource(minus30d, source),
                paymentRepository.countPayingUsersWithSource(source),
                paymentRepository.sumSucceededStarsWithSource(source),
                paymentRepository.sumSucceededStarsSinceWithSource(minus7d, source),
                paymentRepository.sumSucceededStarsSinceWithSource(minus30d, source),
                paymentRepository.countStarsPayingUsersWithSource(source)
        );
    }

    // ── Детализация действий за сутки ────────────────────────────────────────

    private ActionsTodayDto buildActionsTodaySection(OffsetDateTime minus1d, @Nullable String source) {
        LocalDate today = LocalDate.now();

        long threeCard   = fortuneRepository.countByCreatedAtAfterAndSpreadTypeWithSource(minus1d, SpreadType.THREE_CARD.name(), source)
                         + fortuneRepository.countByCreatedAtAfterAndSpreadTypeIsNullWithSource(minus1d, source);
        long horseshoe   = fortuneRepository.countByCreatedAtAfterAndSpreadTypeWithSource(minus1d, SpreadType.HORSESHOE.name(), source);
        long celticCross = fortuneRepository.countByCreatedAtAfterAndSpreadTypeWithSource(minus1d, SpreadType.CELTIC_CROSS.name(), source);
        long compatibility = compatibilityReadingRepository.countByCreatedAtAfterWithSource(minus1d, source);
        long numerology  = numerologyDayReadingRepository.countByDateGreaterThanEqualWithSource(today, source);
        long dailyCard   = dailyCardRepository.countByDateGreaterThanEqualWithSource(today, source);
        long numerologyWeek = numerologyWeekReadingRepository.countByCreatedAtAfterWithSource(minus1d, source);
        long numerologyMonth = numerologyMonthReadingRepository.countByCreatedAtAfterWithSource(minus1d, source);
        long dream       = dreamReadingRepository.countByCreatedAtAfterWithSource(minus1d, source);
        long total       = threeCard + horseshoe + celticCross + compatibility + numerology + dailyCard + numerologyWeek + numerologyMonth + dream;

        return new ActionsTodayDto(total, threeCard, horseshoe, celticCross, compatibility, numerology, dailyCard, numerologyWeek, numerologyMonth, dream);
    }

    // ── Повторные посещения ───────────────────────────────────────────────────

    private ReturningUsersDto buildReturningUsersSection(
            OffsetDateTime minus1d, OffsetDateTime minus7d, OffsetDateTime minus30d,
            @Nullable String source) {

        return new ReturningUsersDto(
                userVisitRepository.countUsersWithMultipleVisitsWithSource(minus1d, source),
                userVisitRepository.countUsersWithMultipleVisitsWithSource(minus7d, source),
                userVisitRepository.countUsersWithMultipleVisitsWithSource(minus30d, source)
        );
    }

    // ── Отчёт за произвольный диапазон ──────────────────────────────────────

    @Transactional(readOnly = true)
    public RangeReportDto buildRangeReport(LocalDateTime fromDate, LocalDateTime toDate, @Nullable String source) {
        ZoneId moscow = ZoneId.of("Europe/Moscow");

        OffsetDateTime from = fromDate.atZone(moscow).toOffsetDateTime();
        OffsetDateTime to   = toDate.atZone(moscow).toOffsetDateTime();

        long newUsers = userRepository.countByCreatedAtBetweenWithSource(from, to, source);

        long threeCard   = fortuneRepository.countByCreatedAtBetweenAndSpreadTypeWithSource(from, to, SpreadType.THREE_CARD.name(), source)
                         + fortuneRepository.countByCreatedAtBetweenAndSpreadTypeIsNullWithSource(from, to, source);
        long horseshoe   = fortuneRepository.countByCreatedAtBetweenAndSpreadTypeWithSource(from, to, SpreadType.HORSESHOE.name(), source);
        long celticCross = fortuneRepository.countByCreatedAtBetweenAndSpreadTypeWithSource(from, to, SpreadType.CELTIC_CROSS.name(), source);
        long fortunesTotal = threeCard + horseshoe + celticCross;

        long compatibility = compatibilityReadingRepository.countByCreatedAtBetweenWithSource(from, to, source);
        long numerologyWeek = numerologyWeekReadingRepository.countByCreatedAtBetweenWithSource(from, to, source);
        long numerologyMonth = numerologyMonthReadingRepository.countByCreatedAtBetweenWithSource(from, to, source);
        long dream = dreamReadingRepository.countByCreatedAtBetweenWithSource(from, to, source);

        long actionsTotal = fortunesTotal + compatibility + numerologyWeek + numerologyMonth + dream;

        long returningUsers = userVisitRepository.countUsersWithMultipleVisitsBetweenWithSource(from, to, source);

        long rubKopecks      = paymentRepository.sumSucceededRubBetweenWithSource(from, to, source);
        long rubTransactions = paymentRepository.countSucceededRubBetweenWithSource(from, to, source);
        long stars           = paymentRepository.sumSucceededStarsBetweenWithSource(from, to, source);
        long starsTransactions = paymentRepository.countSucceededStarsBetweenWithSource(from, to, source);

        return new RangeReportDto(
                from.toString(),
                to.toString(),
                newUsers,
                new RangeReportDto.FortunesRangeDto(fortunesTotal, threeCard, horseshoe, celticCross),
                compatibility,
                new RangeReportDto.ActionsRangeDto(actionsTotal, compatibility, threeCard, horseshoe, celticCross, numerologyWeek, numerologyMonth, dream),
                returningUsers,
                new RangeReportDto.PaymentsRangeDto(rubKopecks, rubTransactions, stars, starsTransactions)
        );
    }

    // ── Знаки (кредиты) — не фильтруются по источнику ────────────────────────

    private CreditsReportDto buildCreditsSection() {
        long totalGranted = creditLogRepository.sumGranted();
        long totalSpent   = creditLogRepository.sumSpent();

        return new CreditsReportDto(
                totalGranted,
                totalSpent,
                totalGranted - totalSpent,
                creditLogRepository.sumGrantedByPayment(),
                creditLogRepository.sumGrantedByAdmin(),
                creditLogRepository.sumGrantedByBonus()
        );
    }
}
