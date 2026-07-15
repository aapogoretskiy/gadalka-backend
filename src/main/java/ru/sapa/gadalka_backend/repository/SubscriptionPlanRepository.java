package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.SubscriptionPlan;

import java.util.List;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    /** Активные планы для каталога во вкладке «Подписки» */
    List<SubscriptionPlan> findAllByIsActiveTrueOrderBySortOrderAsc();

    /** Все планы для админки (включая неактивные) */
    List<SubscriptionPlan> findAllByOrderBySortOrderAsc();
}
