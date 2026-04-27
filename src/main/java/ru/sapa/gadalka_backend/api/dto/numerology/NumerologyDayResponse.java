package ru.sapa.gadalka_backend.api.dto.numerology;

import java.time.LocalDate;

public record NumerologyDayResponse(
        Long id,
        LocalDate date,
        int dayCode,
        String dayCodeTitle,
        int lifePathNumber,
        String lifePathTitle,
        String moonPhase,
        String zodiacSign,
        String bestTime,
        String energyOfDay,
        String whatToDo,
        String whatToAvoid,
        String astroEvent,
        String affirmation,
        int personalYearNumber,
        int personalMonthNumber
) {
}
