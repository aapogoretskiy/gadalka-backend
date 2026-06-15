package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.sapa.gadalka_backend.domain.UserVisit;

import java.time.OffsetDateTime;

public interface UserVisitRepository extends JpaRepository<UserVisit, Long> {

    /**
     * Количество пользователей, у которых более одного посещения за указанный период.
     * Используется в отчёте "повторные посещения за сутки/7 дней/30 дней".
     * Нативный SQL: JPQL не поддерживает COUNT(*) над подзапросом.
     */
    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT user_id
                FROM user_visits
                WHERE visited_at >= :from
                GROUP BY user_id
                HAVING COUNT(id) > 1
            ) sub
            """, nativeQuery = true)
    long countUsersWithMultipleVisits(OffsetDateTime from);

    /**
     * Количество пользователей с более чем одним посещением за диапазон дат.
     * Используется в отчёте за произвольный диапазон.
     */
    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT user_id
                FROM user_visits
                WHERE visited_at >= :from AND visited_at <= :to
                GROUP BY user_id
                HAVING COUNT(id) > 1
            ) sub
            """, nativeQuery = true)
    long countUsersWithMultipleVisitsBetween(OffsetDateTime from, OffsetDateTime to);
}
