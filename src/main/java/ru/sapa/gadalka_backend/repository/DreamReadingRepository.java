package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.DreamReading;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface DreamReadingRepository extends JpaRepository<DreamReading, Long> {

    /** Проверка принадлежности разбора пользователю при открытии из истории. */
    Optional<DreamReading> findByIdAndUserId(Long id, Long userId);

    /** «Недавние сны» на экране Сонника + история действий в админке. */
    List<DreamReading> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // ── Отчёты — стандартные методы ──────────────────────────────────────────
    long countByCreatedAtAfter(OffsetDateTime from);
    long countByCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);

    // ── Отчёты — source-aware методы (по аналогии с NumerologyWeekReadingRepository) ──

    @Query(value = "SELECT COUNT(d.*) FROM dream_readings d " +
            "JOIN users u ON u.id = d.user_id " +
            "WHERE d.created_at > :from " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countByCreatedAtAfterWithSource(@Param("from") OffsetDateTime from, @Param("source") String source);

    @Query(value = "SELECT COUNT(d.*) FROM dream_readings d " +
            "JOIN users u ON u.id = d.user_id " +
            "WHERE d.created_at >= :from AND d.created_at <= :to " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countByCreatedAtBetweenWithSource(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, @Param("source") String source);
}
