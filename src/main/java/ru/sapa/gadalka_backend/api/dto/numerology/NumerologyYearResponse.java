package ru.sapa.gadalka_backend.api.dto.numerology;

import java.time.LocalDate;
import java.util.List;

public record NumerologyYearResponse(
        Long id,
        LocalDate yearStart,
        LocalDate yearEnd,
        int yearNumber,
        String yearTitle,
        String mainTheme,
        NumerologyMonthLifeAreasDto lifeAreas,
        List<NumerologyYearKeyPeriodDto> keyPeriods,
        String whatToAvoid,
        String advice,
        List<NumerologyYearMonthPreviewDto> monthPreviews
) {
}
