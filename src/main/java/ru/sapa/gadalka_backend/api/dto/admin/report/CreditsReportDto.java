package ru.sapa.gadalka_backend.api.dto.admin.report;

public record CreditsReportDto(
        long totalGranted,
        long totalSpent,
        long currentInCirculation,
        long grantedByPayment,
        long grantedByAdmin,
        long grantedByBonus
) {}
