package ru.sapa.gadalka_backend.api.dto.admin;

/**
 * Стоимость платных функций в знаках.
 * Используется и для чтения (GET /api/admin/feature-costs),
 * и для записи (PUT /api/admin/feature-costs).
 */
public record FeatureCostsDto(
        int threeCard,
        int horseshoe,
        int celticCross,
        int compatibilityUnlock,
        int numerologyWeek
) {}
