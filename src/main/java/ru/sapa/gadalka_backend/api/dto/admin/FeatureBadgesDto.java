package ru.sapa.gadalka_backend.api.dto.admin;

/**
 * Отметки «Новинка» / «Хит» по всем платным функциям сразу.
 * Набор полей зеркалит {@link FeatureCostsDto} — те же 8 функций,
 * только вместо цены у каждой пара флагов + служебная дата.
 *
 * <p>Используется и для чтения (GET /api/admin/feature-badges, GET /api/feature-badges),
 * и для записи (PUT /api/admin/feature-badges).
 */
public record FeatureBadgesDto(
        FeatureBadgeDto threeCard,
        FeatureBadgeDto horseshoe,
        FeatureBadgeDto celticCross,
        FeatureBadgeDto compatibilityUnlock,
        FeatureBadgeDto numerologyWeek,
        FeatureBadgeDto numerologyMonth,
        FeatureBadgeDto numerologyYear,
        FeatureBadgeDto dream
) {}
