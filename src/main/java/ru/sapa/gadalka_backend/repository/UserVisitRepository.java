package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.UserVisit;

import java.time.OffsetDateTime;

public interface UserVisitRepository extends JpaRepository<UserVisit, Long> {

    // ── Отчёты — стандартные методы ──────────────────────────────────────────

    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT user_id FROM user_visits
                WHERE visited_at >= :from
                GROUP BY user_id HAVING COUNT(id) > 1
            ) sub
            """, nativeQuery = true)
    long countUsersWithMultipleVisits(OffsetDateTime from);

    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT user_id FROM user_visits
                WHERE visited_at >= :from AND visited_at <= :to
                GROUP BY user_id HAVING COUNT(id) > 1
            ) sub
            """, nativeQuery = true)
    long countUsersWithMultipleVisitsBetween(OffsetDateTime from, OffsetDateTime to);

    // ── Отчёты — source-aware методы ─────────────────────────────────────────

    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT v.user_id FROM user_visits v
                JOIN users u ON u.id = v.user_id
                WHERE v.visited_at >= :from
                  AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))
                GROUP BY v.user_id HAVING COUNT(v.id) > 1
            ) sub
            """, nativeQuery = true)
    long countUsersWithMultipleVisitsWithSource(@Param("from") OffsetDateTime from, @Param("source") String source);

    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT v.user_id FROM user_visits v
                JOIN users u ON u.id = v.user_id
                WHERE v.visited_at >= :from AND v.visited_at <= :to
                  AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))
                GROUP BY v.user_id HAVING COUNT(v.id) > 1
            ) sub
            """, nativeQuery = true)
    long countUsersWithMultipleVisitsBetweenWithSource(
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, @Param("source") String source);
}
