package ru.sapa.gadalka_backend.api.dto.admin.report;

public record ActionsTodayDto(
        long total,
        long threeCard,
        long horseshoe,
        long celticCross,
        long compatibility,
        long numerology,
        long dailyCard,
        long numerologyWeek,
        long numerologyMonth,
        long dream
) {}
