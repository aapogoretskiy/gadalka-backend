package ru.sapa.gadalka_backend.api.dto.numerology;

import java.util.List;

public record NumerologyPortraitResponse(

        // Число жизни (всегда присутствует)
        int lifePathNumber,
        String lifePathArchetype,
        String lifePathDescription,
        String lifePathStrengths,
        String lifePathGrowthPoints,
        String lifePathCalling,
        String lifePathFamousPeople,

        // Число дня рождения (всегда присутствует)
        int birthdayNumber,
        String birthdayArchetype,
        String birthdayDescription,

        // Число души
        int soulNumber,
        String soulArchetype,
        String soulDescription,

        // Число имени
        int nameNumber,
        String nameArchetype,
        String nameDescription,

        // Источник имени для расчёта души и имени
        String nameUsed,
        String nameSource,   // "custom" | "telegram"

        // Совместимость числа жизни со всеми 9 архетипами, по убыванию
        List<NumerologyPortraitCompatibilityItem> compatibility
) {
}
