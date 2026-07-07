package ru.sapa.gadalka_backend.api.dto.numerology;

import java.time.LocalDate;

/**
 * Ключевая дата месяца — примечательный по резонансу день с короткой подписью.
 * badge: "Пик" | "Осторожно" | "Решения" | "Встреча"
 */
public record NumerologyMonthKeyDateDto(
        LocalDate date,
        String badge,
        String description
) {
}
