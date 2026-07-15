package ru.sapa.gadalka_backend.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.SubscriptionQuota;
import ru.sapa.gadalka_backend.domain.type.DiaryFeatureType;

import java.util.List;
import java.util.Optional;

public interface SubscriptionQuotaRepository extends JpaRepository<SubscriptionQuota, Long> {

    List<SubscriptionQuota> findAllBySubscriptionId(Long subscriptionId);

    Optional<SubscriptionQuota> findBySubscriptionIdAndFeatureType(Long subscriptionId, DiaryFeatureType featureType);

    /**
     * Та же выборка, но с PESSIMISTIC_WRITE — для списания квоты.
     * Защита от гонки: два одновременных запроса не потратят одну квоту дважды
     * (тот же приём, что в UserFortuneCreditRepository.findByUserIdForUpdate).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT q FROM SubscriptionQuota q
            WHERE q.subscriptionId = :subscriptionId AND q.featureType = :featureType
            """)
    Optional<SubscriptionQuota> findForUpdate(@Param("subscriptionId") Long subscriptionId,
                                              @Param("featureType") DiaryFeatureType featureType);
}
