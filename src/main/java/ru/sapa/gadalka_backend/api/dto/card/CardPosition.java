package ru.sapa.gadalka_backend.api.dto.card;

public enum CardPosition {

    // ── Три карты (прошлое · настоящее · будущее) ──────────────────────────
    PAST,
    PRESENT,
    FUTURE,

    // ── Подкова (7 карт) ───────────────────────────────────────────────────
    HORSESHOE_PAST,           // 1. Прошлое
    HORSESHOE_PRESENT,        // 2. Настоящее
    HORSESHOE_HIDDEN,         // 3. Скрытые влияния
    HORSESHOE_OBSTACLES,      // 4. Препятствия
    HORSESHOE_EXTERNAL,       // 5. Внешние влияния
    HORSESHOE_ADVICE,         // 6. Совет
    HORSESHOE_OUTCOME,        // 7. Итог

    // ── Кельтский крест (10 карт) ──────────────────────────────────────────
    CELTIC_HEART,             // 1. Суть вопроса
    CELTIC_CROSS,             // 2. Что мешает / перекрещивает
    CELTIC_FOUNDATION,        // 3. Основа / подсознание
    CELTIC_PAST,              // 4. Недавнее прошлое
    CELTIC_POSSIBLE_FUTURE,   // 5. Возможное будущее
    CELTIC_NEAR_FUTURE,       // 6. Ближайшее будущее
    CELTIC_SELF,              // 7. Отношение к себе / позиция
    CELTIC_EXTERNAL,          // 8. Внешние влияния / окружение
    CELTIC_HOPES_FEARS,       // 9. Надежды и страхи
    CELTIC_OUTCOME            // 10. Итог
}
