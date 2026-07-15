package ru.sapa.gadalka_backend.api.dto.subscription;

import ru.sapa.gadalka_backend.domain.SubscriptionPlanQuota;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.QuotaPeriod;

/**
 * Квота плана для каталога подписок: «5 × Три карты на весь срок», «3 × Сонник в день».
 */
public record SubscriptionPlanQuotaDto(
        DiaryFeatureType featureType,
        int quotaCount,
        QuotaPeriod quotaPeriod
) {
    public static SubscriptionPlanQuotaDto from(SubscriptionPlanQuota quota) {
        return new SubscriptionPlanQuotaDto(quota.getFeatureType(), quota.getQuotaCount(), quota.getQuotaPeriod());
    }
}
