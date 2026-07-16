package ru.sapa.gadalka_backend.api.dto.subscription;

import ru.sapa.gadalka_backend.domain.SubscriptionPlanQuota;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.QuotaPeriod;

/**
 * Квота плана для каталога подписок: «5 × Три карты на весь срок», «3 × Сонник в день».
 * <p>
 * Для безлимитных квот (unlimited = true) quotaCount = 0: скрытый дневной
 * анти-абьюз лимит наружу не раскрывается, фронт показывает «Безлимит».
 */
public record SubscriptionPlanQuotaDto(
        DiaryFeatureType featureType,
        int quotaCount,
        QuotaPeriod quotaPeriod,
        boolean unlimited
) {
    public static SubscriptionPlanQuotaDto from(SubscriptionPlanQuota quota) {
        boolean unlimited = Boolean.TRUE.equals(quota.getIsUnlimited());
        return new SubscriptionPlanQuotaDto(
                quota.getFeatureType(),
                unlimited ? 0 : quota.getQuotaCount(),
                quota.getQuotaPeriod(),
                unlimited
        );
    }
}
