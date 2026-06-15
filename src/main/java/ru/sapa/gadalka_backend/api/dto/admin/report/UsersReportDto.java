package ru.sapa.gadalka_backend.api.dto.admin.report;

public record UsersReportDto(
        long total,
        long newToday,
        long new7Days,
        long new30Days,
        long dau,
        long wau
) {}
