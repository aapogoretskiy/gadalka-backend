package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.sapa.gadalka_backend.domain.NumerologyWeekReading;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NumerologyWeekReadingRepository extends JpaRepository<NumerologyWeekReading, Long> {

    Optional<NumerologyWeekReading> findByUserIdAndWeekStartDateLessThanEqualAndWeekEndDateGreaterThanEqual(Long userId, LocalDate startsBefore, LocalDate endsAfter);

    Optional<NumerologyWeekReading> findByIdAndUserId(Long id, Long userId);

    // ── История действий пользователя ────────────────────────────────────────
    List<NumerologyWeekReading> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // ── Отчёты ──────────────────────────────────────────────────────────────
    long countByCreatedAtAfter(OffsetDateTime from);

    long countByCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);
}
