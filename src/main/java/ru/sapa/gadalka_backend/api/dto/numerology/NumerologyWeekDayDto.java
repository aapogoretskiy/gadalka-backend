package ru.sapa.gadalka_backend.api.dto.numerology;

import java.time.LocalDate;

public record NumerologyWeekDayDto(
        LocalDate date,
        String dayOfWeek,
        int dayCode,
        String dayCodeTitle,
        int resonanceScore,
        String resonanceLabel
) {
}
