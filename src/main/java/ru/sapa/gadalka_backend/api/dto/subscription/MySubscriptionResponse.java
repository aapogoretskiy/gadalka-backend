package ru.sapa.gadalka_backend.api.dto.subscription;

import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;
import ru.sapa.gadalka_backend.domain.type.QuotaPeriod;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Активная подписка пользователя + остатки квот (блок «Моя подписка» в профиле).
 */
public record MySubscriptionResponse(
        Long subscriptionId,
        String planName,
        OffsetDateTime startedAt,
        OffsetDateTime expiresAt,
        List<QuotaStateDto> quotas
) {
    /**
     * Остаток квоты: «Сонник — осталось 2 из 3 (в день)».
     * Для безлимитных (unlimited = true) total/remaining = 0 — скрытый дневной
     * лимит не раскрывается, фронт показывает «Безлимит».
     */
    public record QuotaStateDto(
            DiaryFeatureType featureType,
            QuotaPeriod quotaPeriod,
            int total,
            int remaining,
            boolean unlimited
    ) {
    }
}
