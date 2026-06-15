package ru.sapa.gadalka_backend.api.dto.admin.report;

public record CompatibilityReportDto(
        long total,
        long last7Days,
        long last30Days
) {}
