package ru.sapa.gadalka_backend.api.dto.numerology;

import java.time.LocalDate;

public record NumerologyWeekPeakDayDto(
        LocalDate date,
        String dayOfWeek,
        String label,
        String advice
) {
}
