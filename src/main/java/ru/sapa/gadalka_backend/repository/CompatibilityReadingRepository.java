package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.CompatibilityReading;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CompatibilityReadingRepository extends JpaRepository<CompatibilityReading, Long> {
    Optional<CompatibilityReading> findByUserIdAndPersonsHash(Long userId, String personsHash);
    Optional<CompatibilityReading> findByIdAndUserId(Long id, Long userId);
    boolean existsByIdAndUserId(Long id, Long userId);

    // ── История действий пользователя ────────────────────────────────────────
    List<CompatibilityReading> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // ── Отчёты — стандартные методы ──────────────────────────────────────────
    long countByCreatedAtAfter(OffsetDateTime from);
    long countByCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);

    // ── Отчёты — source-aware методы ─────────────────────────────────────────

    @Query(value = "SELECT COUNT(c.*) FROM compatibility_readings c " +
            "JOIN users u ON u.id = c.user_id " +
            "WHERE (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countWithSourceFilter(@Param("source") String source);

    @Query(value = "SELECT COUNT(c.*) FROM compatibility_readings c " +
            "JOIN users u ON u.id = c.user_id " +
            "WHERE c.created_at > :from " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countByCreatedAtAfterWithSource(@Param("from") OffsetDateTime from, @Param("source") String source);

    @Query(value = "SELECT COUNT(c.*) FROM compatibility_readings c " +
            "JOIN users u ON u.id = c.user_id " +
            "WHERE c.created_at >= :from AND c.created_at <= :to " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countByCreatedAtBetweenWithSource(
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, @Param("source") String source);
}
