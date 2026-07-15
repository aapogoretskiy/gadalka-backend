package ru.sapa.gadalka_backend.api.dto.subscription;

import ru.sapa.gadalka_backend.domain.SubscriptionPlan;

import java.util.List;

/**
 * План подписки для каталога во вкладке «Подписки».
 * priceRub — в рублях (не копейках), как в PaymentProductDto.
 */
public record SubscriptionPlanDto(
        Long id,
        String name,
        double priceRub,
        int priceStars,
        int durationDays,
        List<SubscriptionPlanQuotaDto> quotas
) {
    public static SubscriptionPlanDto from(SubscriptionPlan plan, List<SubscriptionPlanQuotaDto> quotas) {
        return new SubscriptionPlanDto(
                plan.getId(),
                plan.getName(),
                plan.getPriceRub() / 100.0,
                plan.getPriceStars(),
                plan.getDurationDays(),
                quotas
        );
    }
}
