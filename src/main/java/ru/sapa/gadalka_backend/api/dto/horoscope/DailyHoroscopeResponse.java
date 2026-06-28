package ru.sapa.gadalka_backend.api.dto.horoscope;

import java.time.LocalDate;
import java.util.List;

public record DailyHoroscopeResponse(
        LocalDate date,
        String zodiacSign,
        String periodLabel,
        int generalScore,
        int loveScore,
        int careerScore,
        int moneyScore,
        String general,
        String advice,
        String love,
        String career,
        String money,
        List<Integer> luckyNumbers,
        List<String> luckyColors,
        String stone
) {
}
