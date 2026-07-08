package ru.sapa.gadalka_backend.api.dto.admin.report;

/**
 * Отчёт за произвольный диапазон дат.
 * Возвращается эндпоинтом GET /api/admin/reports/range?from=YYYY-MM-DD&to=YYYY-MM-DD.
 *
 * <p>Даты трактуются в московском часовом поясе (Europe/Moscow).
 */
public record RangeReportDto(
        String from,
        String to,
        long newUsers,
        FortunesRangeDto fortunes,
        long compatibility,
        ActionsRangeDto actions,
        long returningUsers,
        PaymentsRangeDto payments
) {

    /** Гадания с разбивкой по типам расклада */
    public record FortunesRangeDto(
            long total,
            long threeCard,
            long horseshoe,
            long celticCross
    ) {}

    /** Суммарные действия с разбивкой по типам (только платные функции) */
    public record ActionsRangeDto(
            long total,
            long compatibility,
            long threeCard,
            long horseshoe,
            long celticCross,
            long numerologyWeek,
            long numerologyMonth,
            long numerologyYear,
            long dream
    ) {}

    /** Платежи с разбивкой по валюте */
    public record PaymentsRangeDto(
            long rubKopecks,
            long rubTransactions,
            long stars,
            long starsTransactions
    ) {}
}
