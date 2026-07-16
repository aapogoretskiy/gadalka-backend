package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.SubscriptionPlanQuota;

import java.util.List;

public interface SubscriptionPlanQuotaRepository extends JpaRepository<SubscriptionPlanQuota, Long> {

    List<SubscriptionPlanQuota> findAllByPlanId(Long planId);

    /** Квоты сразу нескольких планов — чтобы каталог не делал N+1 запросов */
    List<SubscriptionPlanQuota> findAllByPlanIdIn(List<Long> planIds);

    /**
     * Удаляет все квоты плана НЕМЕДЛЕННО (bulk delete).
     * <p>
     * ВАЖНО: именно @Modifying @Query, а не производный deleteAllByPlanId —
     * производный метод откладывает удаления до flush, а Hibernate выполняет
     * INSERT раньше DELETE. При обновлении плана (updatePlan: удалить старые
     * квоты → вставить новые) это ломало уникальный индекс
     * uq_plan_quotas_plan_feature: новая квота вставлялась, пока старая
     * с той же (plan_id, feature_type) ещё была в таблице.
     * flushAutomatically — на случай несброшенных изменений перед удалением,
     * clearAutomatically — чтобы в persistence context не остались «призраки»
     * удалённых строк.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SubscriptionPlanQuota q WHERE q.planId = :planId")
    void deleteAllByPlanId(@Param("planId") Long planId);
}
