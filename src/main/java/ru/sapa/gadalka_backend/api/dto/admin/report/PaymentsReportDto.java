package ru.sapa.gadalka_backend.api.dto.admin.report;

public record PaymentsReportDto(
        long rubKopecksTotal,
        long rubKopecks7Days,
        long rubKopecks30Days,
        long rubPayingUsers,
        long starsTotal,
        long stars7Days,
        long stars30Days,
        long starsPayingUsers
) {}
