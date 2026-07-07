package ru.sapa.gadalka_backend.api.dto.numerology;

/** Одна сфера жизни в месячном разборе: оценка 1–5 и текстовый комментарий. */
public record NumerologyMonthLifeAreaDto(
        int score,
        String text
) {
}
