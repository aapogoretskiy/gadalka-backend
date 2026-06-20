package ru.sapa.gadalka_backend.api.dto.numerology;

import java.time.LocalDate;
import java.util.List;

public record NumerologyWeekResponse(
        Long id,
        LocalDate weekStart,
        LocalDate weekEnd,
        int weekNumber,
        String weekNumberTitle,
        String weekDescription,
        List<NumerologyWeekDayDto> days,
        NumerologyWeekDayDto bestDay,
        NumerologyWeekDayDto challengingDay,
        String weeklyAffirmation
) {
}
