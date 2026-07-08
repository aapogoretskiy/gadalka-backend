package ru.sapa.gadalka_backend.api.dto.numerology;

/**
 * Один из 4 ключевых периодов года — квартал, представленный своим самым резонансным месяцем.
 * badge: "Старт" (январь-март) | "Пауза" (апрель-июнь) | "Пик" (июль-сентябрь) | "Итоги" (октябрь-декабрь).
 * Позиция бейджа за кварталом закреплена всегда, а вот КАКОЙ месяц внутри квартала его получит —
 * определяется резонансом (числовое сродство личного числа жизни и личного числа месяца), см.
 * {@link ru.sapa.gadalka_backend.service.NumerologyYearService}.
 */
public record NumerologyYearKeyPeriodDto(
        String badge,
        int calendarMonth,
        String monthName,
        int monthNumber,
        String monthNumberTitle,
        String description
) {
}
