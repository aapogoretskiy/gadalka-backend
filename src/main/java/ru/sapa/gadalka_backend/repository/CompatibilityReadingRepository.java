package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.CompatibilityReading;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CompatibilityReadingRepository extends JpaRepository<CompatibilityReading, Long> {
    Optional<CompatibilityReading> findByUserIdAndPersonsHash(Long userId, String personsHash);
    Optional<CompatibilityReading> findByIdAndUserId(Long id, Long userId);

    /** Проверка владельца — используется CompatibilityFeedbackValidator */
    boolean existsByIdAndUserId(Long id, Long userId);

    // ── История действий пользователя ────────────────────────────────────────
    List<CompatibilityReading> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // ── Отчёты ──────────────────────────────────────────────────────────────
    long countByCreatedAtAfter(OffsetDateTime from);
}
