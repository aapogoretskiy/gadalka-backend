package ru.sapa.gadalka_backend.api.dto.numerology;

import java.time.LocalDate;

/**
 * Превью одной из 4 недель внутри месячного разбора (карточки «Неделя 1..4»).
 * Полный расклад по этой неделе уже создан бесплатно (см. NumerologyWeekService.createIncludedWeek)
 * и открывается по клику через GET /api/numerology/week/by-date.
 */
public record NumerologyMonthWeekPreviewDto(
        int weekIndex,
        LocalDate startDate,
        LocalDate endDate,
        int weekNumber,
        String weekNumberTitle,
        String resonanceLabel
) {
}
