package ru.sapa.gadalka_backend.api.dto.admin.report;

public record FortunesReportDto(
        long total,
        long last7Days,
        long last30Days
) {}
