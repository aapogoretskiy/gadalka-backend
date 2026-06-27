package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.DailyCard;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyCardRepository extends JpaRepository<DailyCard, Long> {

    @Query("SELECT dc FROM DailyCard dc JOIN FETCH dc.card WHERE dc.userId = :userId AND dc.date = :date")
    Optional<DailyCard> findByUserIdAndDate(Long userId, LocalDate date);

    @Query("SELECT dc FROM DailyCard dc JOIN FETCH dc.card WHERE dc.id = :id AND dc.userId = :userId")
    Optional<DailyCard> findByIdAndUserId(Long id, Long userId);

    // ── История действий пользователя ────────────────────────────────────────
    List<DailyCard> findByUserIdOrderByDateDesc(Long userId, Pageable pageable);

    // ── Отчёты — стандартный метод ───────────────────────────────────────────
    long countByDateGreaterThanEqual(LocalDate from);

    // ── Отчёты — source-aware метод ──────────────────────────────────────────

    @Query(value = "SELECT COUNT(dc.*) FROM daily_cards dc " +
            "JOIN users u ON u.id = dc.user_id " +
            "WHERE dc.date >= CAST(:from AS date) " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countByDateGreaterThanEqualWithSource(@Param("from") LocalDate from, @Param("source") String source);
}
