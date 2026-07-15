package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.SubscriptionPlanQuota;

import java.util.List;

public interface SubscriptionPlanQuotaRepository extends JpaRepository<SubscriptionPlanQuota, Long> {

    List<SubscriptionPlanQuota> findAllByPlanId(Long planId);

    /** Квоты сразу нескольких планов — чтобы каталог не делал N+1 запросов */
    List<SubscriptionPlanQuota> findAllByPlanIdIn(List<Long> planIds);

    void deleteAllByPlanId(Long planId);
}
