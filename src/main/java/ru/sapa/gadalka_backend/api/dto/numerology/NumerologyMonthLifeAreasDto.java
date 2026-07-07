package ru.sapa.gadalka_backend.api.dto.numerology;

/** 4 сферы жизни месячного разбора. */
public record NumerologyMonthLifeAreasDto(
        NumerologyMonthLifeAreaDto relationships,
        NumerologyMonthLifeAreaDto career,
        NumerologyMonthLifeAreaDto finance,
        NumerologyMonthLifeAreaDto health
) {
}
