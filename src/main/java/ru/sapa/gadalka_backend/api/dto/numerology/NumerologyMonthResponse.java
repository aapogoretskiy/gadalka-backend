package ru.sapa.gadalka_backend.api.dto.numerology;

import java.time.LocalDate;
import java.util.List;

public record NumerologyMonthResponse(
        Long id,
        LocalDate monthStart,
        LocalDate monthEnd,
        int monthNumber,
        String monthNumberTitle,
        String mainTheme,
        NumerologyMonthLifeAreasDto lifeAreas,
        List<NumerologyMonthKeyDateDto> keyDates,
        String whatToAvoid,
        String advice,
        List<NumerologyMonthWeekPreviewDto> weekPreviews
) {
}
