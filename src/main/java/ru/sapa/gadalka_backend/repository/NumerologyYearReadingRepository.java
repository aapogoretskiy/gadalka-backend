package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.NumerologyYearReading;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NumerologyYearReadingRepository extends JpaRepository<NumerologyYearReading, Long> {

    Optional<NumerologyYearReading> findByUserIdAndYearStartDate(Long userId, LocalDate yearStartDate);
    Optional<NumerologyYearReading> findByIdAndUserId(Long id, Long userId);

    // ── История действий пользователя ────────────────────────────────────────
    List<NumerologyYearReading> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // ── Отчёты — стандартные методы ──────────────────────────────────────────
    long countByCreatedAtAfter(OffsetDateTime from);
    long countByCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);

    // ── Отчёты — source-aware методы ─────────────────────────────────────────

    @Query(value = "SELECT COUNT(n.*) FROM numerology_year_readings n " +
            "JOIN users u ON u.id = n.user_id " +
            "WHERE n.created_at > :from " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countByCreatedAtAfterWithSource(@Param("from") OffsetDateTime from, @Param("source") String source);

    @Query(value = "SELECT COUNT(n.*) FROM numerology_year_readings n " +
            "JOIN users u ON u.id = n.user_id " +
            "WHERE n.created_at >= :from AND n.created_at <= :to " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countByCreatedAtBetweenWithSource(
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, @Param("source") String source);
}
