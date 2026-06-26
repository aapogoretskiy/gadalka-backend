package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.User;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTelegramId(Long telegramId);

    /** Поиск по username (без учёта регистра, частичное совпадение) для админки */
    Page<User> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    /**
     * Те же выборки, но с отсечением "неактивных" пользователей —
     * totalActionsCount = 0 ИЛИ visitCount <= 1 (зарегистрировался и не вернулся / ничего не сделал).
     * Условие "оставить активного" эквивалентно totalActionsCount > 0 AND visitCount > 1.
     */
    Page<User> findByTotalActionsCountGreaterThanAndVisitCountGreaterThan(int actions, int visits, Pageable pageable);

    Page<User> findByUsernameContainingIgnoreCaseAndTotalActionsCountGreaterThanAndVisitCountGreaterThan(
            String username, int actions, int visits, Pageable pageable);

    @Query("SELECT u FROM User u ORDER BY u.lastActiveAt DESC NULLS LAST")
    Page<User> findAllOrderByLastActiveAtDesc(Pageable pageable);

    @Query("SELECT u FROM User u ORDER BY u.lastActiveAt ASC NULLS LAST")
    Page<User> findAllOrderByLastActiveAtAsc(Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.totalActionsCount > 0 AND u.visitCount > 1 ORDER BY u.lastActiveAt DESC NULLS LAST")
    Page<User> findAllActiveOrderByLastActiveAtDesc(Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.totalActionsCount > 0 AND u.visitCount > 1 ORDER BY u.lastActiveAt ASC NULLS LAST")
    Page<User> findAllActiveOrderByLastActiveAtAsc(Pageable pageable);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) ORDER BY u.lastActiveAt DESC NULLS LAST")
    Page<User> findByUsernameOrderByLastActiveAtDesc(@Param("username") String username, Pageable pageable);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) ORDER BY u.lastActiveAt ASC NULLS LAST")
    Page<User> findByUsernameOrderByLastActiveAtAsc(@Param("username") String username, Pageable pageable);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
            "AND u.totalActionsCount > 0 AND u.visitCount > 1 ORDER BY u.lastActiveAt DESC NULLS LAST")
    Page<User> findByUsernameActiveOrderByLastActiveAtDesc(@Param("username") String username, Pageable pageable);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
            "AND u.totalActionsCount > 0 AND u.visitCount > 1 ORDER BY u.lastActiveAt ASC NULLS LAST")
    Page<User> findByUsernameActiveOrderByLastActiveAtAsc(@Param("username") String username, Pageable pageable);

    /**
     * Сортировка по сумме потраченных знаков (агрегат из fortune_credit_log, не хранится на User).
     * Native-запрос с LEFT JOIN на подзапрос-агрегат — обычный @Query (JPQL) не умеет
     * сортировать по вычисляемой сумме из другой таблицы без отдельного DTO-проекта.
     */
    @Query(value = "SELECT u.* FROM users u " +
            "LEFT JOIN (SELECT user_id, SUM(-delta) AS spent FROM fortune_credit_log WHERE delta < 0 GROUP BY user_id) s " +
            "ON s.user_id = u.id " +
            "ORDER BY COALESCE(s.spent, 0) DESC",
            countQuery = "SELECT COUNT(*) FROM users",
            nativeQuery = true)
    Page<User> findAllOrderByTotalSpentDesc(Pageable pageable);

    @Query(value = "SELECT u.* FROM users u " +
            "LEFT JOIN (SELECT user_id, SUM(-delta) AS spent FROM fortune_credit_log WHERE delta < 0 GROUP BY user_id) s " +
            "ON s.user_id = u.id " +
            "ORDER BY COALESCE(s.spent, 0) ASC",
            countQuery = "SELECT COUNT(*) FROM users",
            nativeQuery = true)
    Page<User> findAllOrderByTotalSpentAsc(Pageable pageable);

    @Query(value = "SELECT u.* FROM users u " +
            "LEFT JOIN (SELECT user_id, SUM(-delta) AS spent FROM fortune_credit_log WHERE delta < 0 GROUP BY user_id) s " +
            "ON s.user_id = u.id " +
            "WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
            "ORDER BY COALESCE(s.spent, 0) DESC",
            countQuery = "SELECT COUNT(*) FROM users u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%'))",
            nativeQuery = true)
    Page<User> findByUsernameOrderByTotalSpentDesc(@Param("username") String username, Pageable pageable);

    @Query(value = "SELECT u.* FROM users u " +
            "LEFT JOIN (SELECT user_id, SUM(-delta) AS spent FROM fortune_credit_log WHERE delta < 0 GROUP BY user_id) s " +
            "ON s.user_id = u.id " +
            "WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
            "ORDER BY COALESCE(s.spent, 0) ASC",
            countQuery = "SELECT COUNT(*) FROM users u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%'))",
            nativeQuery = true)
    Page<User> findByUsernameOrderByTotalSpentAsc(@Param("username") String username, Pageable pageable);

    @Query(value = "SELECT u.* FROM users u " +
            "LEFT JOIN (SELECT user_id, SUM(-delta) AS spent FROM fortune_credit_log WHERE delta < 0 GROUP BY user_id) s " +
            "ON s.user_id = u.id " +
            "WHERE u.total_actions_count > 0 AND u.visit_count > 1 " +
            "ORDER BY COALESCE(s.spent, 0) DESC",
            countQuery = "SELECT COUNT(*) FROM users u WHERE u.total_actions_count > 0 AND u.visit_count > 1",
            nativeQuery = true)
    Page<User> findAllActiveOrderByTotalSpentDesc(Pageable pageable);

    @Query(value = "SELECT u.* FROM users u " +
            "LEFT JOIN (SELECT user_id, SUM(-delta) AS spent FROM fortune_credit_log WHERE delta < 0 GROUP BY user_id) s " +
            "ON s.user_id = u.id " +
            "WHERE u.total_actions_count > 0 AND u.visit_count > 1 " +
            "ORDER BY COALESCE(s.spent, 0) ASC",
            countQuery = "SELECT COUNT(*) FROM users u WHERE u.total_actions_count > 0 AND u.visit_count > 1",
            nativeQuery = true)
    Page<User> findAllActiveOrderByTotalSpentAsc(Pageable pageable);

    @Query(value = "SELECT u.* FROM users u " +
            "LEFT JOIN (SELECT user_id, SUM(-delta) AS spent FROM fortune_credit_log WHERE delta < 0 GROUP BY user_id) s " +
            "ON s.user_id = u.id " +
            "WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
            "AND u.total_actions_count > 0 AND u.visit_count > 1 " +
            "ORDER BY COALESCE(s.spent, 0) DESC",
            countQuery = "SELECT COUNT(*) FROM users u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
                    "AND u.total_actions_count > 0 AND u.visit_count > 1",
            nativeQuery = true)
    Page<User> findByUsernameActiveOrderByTotalSpentDesc(@Param("username") String username, Pageable pageable);

    @Query(value = "SELECT u.* FROM users u " +
            "LEFT JOIN (SELECT user_id, SUM(-delta) AS spent FROM fortune_credit_log WHERE delta < 0 GROUP BY user_id) s " +
            "ON s.user_id = u.id " +
            "WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
            "AND u.total_actions_count > 0 AND u.visit_count > 1 " +
            "ORDER BY COALESCE(s.spent, 0) ASC",
            countQuery = "SELECT COUNT(*) FROM users u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
                    "AND u.total_actions_count > 0 AND u.visit_count > 1",
            nativeQuery = true)
    Page<User> findByUsernameActiveOrderByTotalSpentAsc(@Param("username") String username, Pageable pageable);

    /**
     * Атомарный инкремент счётчика действий пользователя.
     * Вызывается из сервисов при создании новой записи активности.
     * Требует @Transactional на вызывающем методе.
     */
    @Modifying
    @Query("UPDATE User u SET u.totalActionsCount = u.totalActionsCount + 1 WHERE u.id = :userId")
    void incrementActionsCount(@Param("userId") Long userId);

    // ── Отчёты ──────────────────────────────────────────────────────────────

    /** Количество новых пользователей после указанной даты */
    long countByCreatedAtAfter(OffsetDateTime from);

    /** Количество активных пользователей после указанной даты (по lastActiveAt) */
    long countByLastActiveAtAfter(OffsetDateTime from);

    /** Количество новых пользователей за диапазон дат */
    long countByCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);

    // ── Органика (вкладка "Рефералы" → "Маркетинговые источники") ────────────

    /**
     * Количество пользователей без источника регистрации (referral_source IS NULL) —
     * это "органика": пришли в бота сами, без реферального кода.
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.referralSource IS NULL")
    long countOrganicUsers();

    /**
     * Суммарное количество посещений (visit_count) органических пользователей.
     * Используется как замена счётчику "Открытий" — для органики событий APP_OPEN
     * в referral_events не существует (они создаются только при наличии реферального кода).
     */
    @Query("SELECT COALESCE(SUM(u.visitCount), 0) FROM User u WHERE u.referralSource IS NULL")
    long sumVisitCountForOrganicUsers();
}
