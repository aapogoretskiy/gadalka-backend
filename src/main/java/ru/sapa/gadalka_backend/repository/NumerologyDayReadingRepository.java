package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.NumerologyDayReading;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NumerologyDayReadingRepository extends JpaRepository<NumerologyDayReading, Long> {

    Optional<NumerologyDayReading> findByUserIdAndDate(Long userId, LocalDate date);
    Optional<NumerologyDayReading> findByIdAndUserId(Long id, Long userId);

    // ── История действий пользователя ────────────────────────────────────────
    List<NumerologyDayReading> findByUserIdOrderByDateDesc(Long userId, Pageable pageable);

    // ── Отчёты — стандартный метод ───────────────────────────────────────────
    long countByDateGreaterThanEqual(LocalDate from);

    // ── Отчёты — source-aware метод ──────────────────────────────────────────
    // Используем CAST(:from AS date) для передачи LocalDate в native-запросе

    @Query(value = "SELECT COUNT(n.*) FROM numerology_day_readings n " +
            "JOIN users u ON u.id = n.user_id " +
            "WHERE n.date >= CAST(:from AS date) " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countByDateGreaterThanEqualWithSource(@Param("from") LocalDate from, @Param("source") String source);
}
