package ru.sapa.gadalka_backend.api.dto.numerology;

/**
 * Лёгкое превью одного из 12 месяцев года (карточки на экране года) — считается на лету
 * из формулы личного месяца, БЕЗ создания записи месячного разбора и без каскада недель.
 * Полный разбор месяца создаётся лениво, только по клику, через
 * {@link ru.sapa.gadalka_backend.service.NumerologyMonthService#createIncludedMonth}.
 */
public record NumerologyYearMonthPreviewDto(
        int calendarMonth,
        String monthName,
        int monthNumber,
        String monthNumberTitle,
        String resonanceLabel
) {
}
