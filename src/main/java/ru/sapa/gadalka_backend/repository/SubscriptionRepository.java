package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.Subscription;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @Query("""
            SELECT s FROM Subscription s
            WHERE s.userId = :userId
              AND s.status = 'ACTIVE'
              AND s.expiresAt > :now
            ORDER BY s.expiresAt DESC
            LIMIT 1
            """)
    Optional<Subscription> findActiveByUserId(@Param("userId") Long userId,
                                              @Param("now") OffsetDateTime now);
}
