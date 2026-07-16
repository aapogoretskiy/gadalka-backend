package ru.sapa.gadalka_backend.api.dto.admin;

import ru.sapa.gadalka_backend.domain.SubscriptionPlan;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.QuotaPeriod;

import java.util.List;

/**
 * План подписки в админ-панели. Используется и для чтения, и как тело
 * create/update запроса (id при создании игнорируется).
 * <p>
 * priceRub — в КОПЕЙКАХ (в отличие от публичного SubscriptionPlanDto):
 * админ работает с точными значениями, конвертацию в рубли делает фронт админки.
 */
public record AdminSubscriptionPlanDto(
        Long id,
        String name,
        int priceRub,
        int priceStars,
        int durationDays,
        boolean isActive,
        int sortOrder,
        List<QuotaDto> quotas
) {
    /**
     * @param quotaCount для безлимита (unlimited = true) — скрытый дневной
     *                   анти-абьюз лимит (админ видит и настраивает его)
     */
    public record QuotaDto(
            DiaryFeatureType featureType,
            int quotaCount,
            QuotaPeriod quotaPeriod,
            boolean unlimited
    ) {
    }

    public static AdminSubscriptionPlanDto from(SubscriptionPlan plan, List<QuotaDto> quotas) {
        return new AdminSubscriptionPlanDto(
                plan.getId(),
                plan.getName(),
                plan.getPriceRub(),
                plan.getPriceStars(),
                plan.getDurationDays(),
                Boolean.TRUE.equals(plan.getIsActive()),
                plan.getSortOrder(),
                quotas
        );
    }
}
