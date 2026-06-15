package ru.sapa.gadalka_backend.api.dto.admin.report;

/**
 * Верхнеуровневый DTO отчёта для GET /api/admin/reports.
 * Каждая секция — отдельный типизированный record.
 */
public record AdminReportDto(
        UsersReportDto users,
        FortunesReportDto fortunes,
        CompatibilityReportDto compatibility,
        ActionsTodayDto actionsToday,
        ReturningUsersDto returningUsers,
        PaymentsReportDto payments,
        CreditsReportDto credits
) {}
