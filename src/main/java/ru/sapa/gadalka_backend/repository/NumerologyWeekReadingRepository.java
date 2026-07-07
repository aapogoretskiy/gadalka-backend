package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.NumerologyWeekReading;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NumerologyWeekReadingRepository extends JpaRepository<NumerologyWeekReading, Long> {

    /**
     * Все недельные расклады пользователя, чей диапазон [weekStartDate; weekEndDate] покрывает
     * заданную дату. В норме такая запись ровно одна, но если у пользователя есть и отдельно
     * купленная неделя (плавающее окно от даты покупки), и недели, включённые в купленный месяц
     * (фиксированные календарные блоки 1-7/8-14/15-21/22-28/29-31) — их диапазоны могут
     * пересекаться, не совпадая по дате начала. Поэтому метод возвращает список, отсортированный
     * по дате начала по убыванию: самая поздняя дата начала — самая «актуальная» на сегодня неделя.
     */
    List<NumerologyWeekReading> findByUserIdAndWeekStartDateLessThanEqualAndWeekEndDateGreaterThanEqualOrderByWeekStartDateDesc(
            Long userId, LocalDate startsBefore, LocalDate endsAfter);
    Optional<NumerologyWeekReading> findByIdAndUserId(Long id, Long userId);

    /**
     * Поиск расклада по точной дате начала недели — используется и для «бесплатных»
     * недель, включённых в купленный месяц (см. NumerologyWeekService.createIncludedWeek/peekByDate),
     * и в принципе для любой недели, которую пользователь уже открывал.
     */
    Optional<NumerologyWeekReading> findByUserIdAndWeekStartDate(Long userId, LocalDate weekStartDate);

    // ── История действий пользователя ────────────────────────────────────────
    List<NumerologyWeekReading> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // ── Отчёты — стандартные методы ──────────────────────────────────────────
    long countByCreatedAtAfter(OffsetDateTime from);
    long countByCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);

    // ── Отчёты — source-aware методы ─────────────────────────────────────────

    @Query(value = "SELECT COUNT(n.*) FROM numerology_week_readings n " +
            "JOIN users u ON u.id = n.user_id " +
            "WHERE n.created_at > :from " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countByCreatedAtAfterWithSource(@Param("from") OffsetDateTime from, @Param("source") String source);

    @Query(value = "SELECT COUNT(n.*) FROM numerology_week_readings n " +
            "JOIN users u ON u.id = n.user_id " +
            "WHERE n.created_at >= :from AND n.created_at <= :to " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    long countByCreatedAtBetweenWithSource(
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, @Param("source") String source);
}
