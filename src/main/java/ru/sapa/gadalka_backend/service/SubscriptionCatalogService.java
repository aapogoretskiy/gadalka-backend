package ru.sapa.gadalka_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sapa.gadalka_backend.api.dto.subscription.MySubscriptionResponse;
import ru.sapa.gadalka_backend.api.dto.subscription.SubscriptionPlanDto;
import ru.sapa.gadalka_backend.api.dto.subscription.SubscriptionPlanQuotaDto;
import ru.sapa.gadalka_backend.domain.SubscriptionPlan;
import ru.sapa.gadalka_backend.domain.SubscriptionPlanQuota;
import ru.sapa.gadalka_backend.repository.SubscriptionPlanQuotaRepository;
import ru.sapa.gadalka_backend.repository.SubscriptionPlanRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Каталог планов подписки для фронта (вкладка «Подписки»)
 * и сборка ответа «Моя подписка».
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionCatalogService {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionPlanQuotaRepository planQuotaRepository;
    private final SubscriptionQuotaService subscriptionQuotaService;

    /** Активные планы с квотами, отсортированные по sort_order. Одним запросом на квоты (без N+1). */
    @Transactional(readOnly = true)
    public List<SubscriptionPlanDto> getActivePlans() {
        List<SubscriptionPlan> plans = planRepository.findAllByIsActiveTrueOrderBySortOrderAsc();
        if (plans.isEmpty()) return List.of();

        Map<Long, List<SubscriptionPlanQuota>> quotasByPlan = planQuotaRepository
                .findAllByPlanIdIn(plans.stream().map(SubscriptionPlan::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(SubscriptionPlanQuota::getPlanId));

        return plans.stream()
                .map(plan -> SubscriptionPlanDto.from(plan,
                        quotasByPlan.getOrDefault(plan.getId(), List.of()).stream()
                                .map(SubscriptionPlanQuotaDto::from)
                                .toList()))
                .toList();
    }

    /** План по ID — только активный (для покупки). */
    @Transactional(readOnly = true)
    public Optional<SubscriptionPlan> getActivePlan(Long planId) {
        return planRepository.findById(planId)
                .filter(SubscriptionPlan::getIsActive);
    }

    /** Активная подписка пользователя с остатками квот. Empty — подписки нет. */
    @Transactional(readOnly = true)
    public Optional<MySubscriptionResponse> getMySubscription(Long userId) {
        return subscriptionQuotaService.findActiveSubscription(userId)
                .map(sub -> new MySubscriptionResponse(
                        sub.getId(),
                        sub.getPlanName() != null ? sub.getPlanName() : sub.getPlan(),
                        sub.getStartedAt(),
                        sub.getExpiresAt(),
                        subscriptionQuotaService.getAllQuotaStates(sub.getId()).stream()
                                .map(q -> new MySubscriptionResponse.QuotaStateDto(
                                        q.featureType(), q.period(),
                                        q.unlimited() ? 0 : q.total(),
                                        q.unlimited() ? 0 : q.remaining(),
                                        q.unlimited()))
                                .toList()));
    }
}
