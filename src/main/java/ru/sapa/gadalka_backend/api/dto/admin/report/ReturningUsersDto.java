package ru.sapa.gadalka_backend.api.dto.admin.report;

public record ReturningUsersDto(
        long returning1Day,
        long returning7Days,
        long returning30Days
) {}
