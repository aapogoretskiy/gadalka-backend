package ru.sapa.gadalka_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.User;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTelegramId(Long telegramId);

    // ── Source-фильтр ────────────────────────────────────────────────────────
    // Специальный токен "__organic__" означает "пользователи без источника (referral_source IS NULL)".
    // null-значение параметра source = фильтр отключён (показать всех).
    //
    // JPQL-условие:
    //   (:source IS NULL
    //    OR (:source = '__organic__' AND u.referralSource IS NULL)
    //    OR (:source <> '__organic__' AND u.referralSource = :source))
    //
    // SQL-условие (для native-запросов):
    //   (:source IS NULL
    //    OR (:source = '__organic__' AND u.referral_source IS NULL)
    //    OR (:source <> '__organic__' AND u.referral_source = :source))

    /** Список уникальных реферальных источников для дропдауна в админ-панели */
    @Query(value = "SELECT DISTINCT u.referral_source FROM users u " +
            "WHERE u.referral_source IS NOT NULL ORDER BY u.referral_source",
            nativeQuery = true)
    List<String> findDistinctReferralSources();

    // ── Пагинированный список пользователей: базовые случаи ──────────────────

    /**
     * Все пользователи с опциональным фильтром по источнику.
     * Заменяет findAll(pageable) для случаев, когда нужна фильтрация по source.
     */
    @Query("SELECT u FROM User u WHERE " +
            "(:source IS NULL OR (:source = '__organic__' AND u.referralSource IS NULL) OR (:source <> '__organic__' AND u.referralSource = :source))")
    Page<User> findAllWithSourceFilter(@Param("source") String source, Pageable pageable);

    /**
     * Только активные пользователи (totalActionsCount > 0 AND visitCount > 1)
     * с опциональным фильтром по источнику.
     */
    @Query("SELECT u FROM User u WHERE u.totalActionsCount > 0 AND u.visitCount > 1 AND " +
            "(:source IS NULL OR (:source = '__organic__' AND u.referralSource IS NULL) OR (:source <> '__organic__' AND u.referralSource = :source))")
    Page<User> findActiveWithSourceFilter(@Param("source") String source, Pageable pageable);

    // ── Поиск по username с source-фильтром ──────────────────────────────────

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) AND " +
            "(:source IS NULL OR (:source = '__organic__' AND u.referralSource IS NULL) OR (:source <> '__organic__' AND u.referralSource = :source))")
    Page<User> findByUsernameContainingIgnoreCaseWithSource(
            @Param("username") String username, @Param("source") String source, Pageable pageable);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
            "AND u.totalActionsCount > 0 AND u.visitCount > 1 AND " +
            "(:source IS NULL OR (:source = '__organic__' AND u.referralSource IS NULL) OR (:source <> '__organic__' AND u.referralSource = :source))")
    Page<User> findByUsernameActiveWithSource(
            @Param("username") String username, @Param("source") String source, Pageable pageable);

    // ── Сортировка по lastActiveAt с source-фильтром ─────────────────────────

    @Query("SELECT u FROM User u WHERE " +
            "(:source IS NULL OR (:source = '__organic__' AND u.referralSource IS NULL) OR (:source <> '__organic__' AND u.referralSource = :source)) " +
            "ORDER BY u.lastActiveAt DESC NULLS LAST")
    Page<User> findAllOrderByLastActiveAtDesc(@Param("source") String source, Pageable pageable);

    @Query("SELECT u FROM User u WHERE " +
            "(:source IS NULL OR (:source = '__organic__' AND u.referralSource IS NULL) OR (:source <> '__organic__' AND u.referralSource = :source)) " +
            "ORDER BY u.lastActiveAt ASC NULLS LAST")
    Page<User> findAllOrderByLastActiveAtAsc(@Param("source") String source, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.totalActionsCount > 0 AND u.visitCount > 1 AND " +
            "(:source IS NULL OR (:source = '__organic__' AND u.referralSource IS NULL) OR (:source <> '__organic__' AND u.referralSource = :source)) " +
            "ORDER BY u.lastActiveAt DESC NULLS LAST")
    Page<User> findAllActiveOrderByLastActiveAtDesc(@Param("source") String source, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.totalActionsCount > 0 AND u.visitCount > 1 AND " +
            "(:source IS NULL OR (:source = '__organic__' AND u.referralSource IS NULL) OR (:source <> '__organic__' AND u.referralSource = :source)) " +
            "ORDER BY u.lastActiveAt ASC NULLS LAST")
    Page<User> findAllActiveOrderByLastActiveAtAsc(@Param("source") String source, Pageable pageable);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) AND " +
            "(:source IS NULL OR (:source = '__organic__' AND u.referralSource IS NULL) OR (:source <> '__organic__' AND u.referralSource = :source)) " +
            "ORDER BY u.lastActiveAt DESC NULLS LAST")
    Page<User> findByUsernameOrderByLastActiveAtDesc(
            @Param("username") String username, @Param("source") String source, Pageable pageable);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) AND " +
            "(:source IS NULL OR (:source = '__organic__' AND u.referralSource IS NULL) OR (:source <> '__organic__' AND u.referralSource = :source)) " +
            "ORDER BY u.lastActiveAt ASC NULLS LAST")
    Page<User> findByUsernameOrderByLastActiveAtAsc(
            @Param("username") String username, @Param("source") String source, Pageable pageable);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
            "AND u.totalActionsCount > 0 AND u.visitCount > 1 AND " +
            "(:source IS NULL OR (:source = '__organic__' AND u.referralSource IS NULL) OR (:source <> '__organic__' AND u.referralSource = :source)) " +
            "ORDER BY u.lastActiveAt DESC NULLS LAST")
    Page<User> findByUsernameActiveOrderByLastActiveAtDesc(
            @Param("username") String username, @Param("source") String source, Pageable pageable);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
            "AND u.totalActionsCount > 0 AND u.visitCount > 1 AND " +
            "(:source IS NULL OR (:source = '__organic__' AND u.referralSource IS NULL) OR (:source <> '__organic__' AND u.referralSource = :source)) " +
            "ORDER BY u.lastActiveAt ASC NULLS LAST")
    Page<User> findByUsernameActiveOrderByLastActiveAtAsc(
            @Param("username") String username, @Param("source") String source, Pageable pageable);

    // ── Сортировка по totalSpent (native) с source-фильтром ──────────────────

    /** Константа SQL-условия source-фильтра для native-запросов (вставляется текстом в каждый запрос) */

    @Query(value = "SELECT u.* FROM users u " +
            "LEFT JOIN (SELECT user_id, SUM(-delta) AS spent FROM fortune_credit_log WHERE delta < 0 GROUP BY user_id) s " +
            "ON s.user_id = u.id " +
            "WHERE (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source)) " +
            "ORDER BY COALESCE(s.spent, 0) DESC",
            countQuery = "SELECT COUNT(*) FROM users u WHERE (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Page<User> findAllOrderByTotalSpentDesc(@Param("source") String source, Pageable pageable);

    @Query(value = "SELECT u.* FROM users u " +
            "LEFT JOIN (SELECT user_id, SUM(-delta) AS spent FROM fortune_credit_log WHERE delta < 0 GROUP BY user_id) s " +
            "ON s.user_id = u.id " +
            "WHERE (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source)) " +
            "ORDER BY COALESCE(s.spent, 0) ASC",
            countQuery = "SELECT COUNT(*) FROM users u WHERE (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Page<User> findAllOrderByTotalSpentAsc(@Param("source") String source, Pageable pageable);

    @Query(value = "SELECT u.* FROM users u " +
            "LEFT JOIN (SELECT user_id, SUM(-delta) AS spent FROM fortune_credit_log WHERE delta < 0 GROUP BY user_id) s " +
            "ON s.user_id = u.id " +
            "WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source)) " +
            "ORDER BY COALESCE(s.spent, 0) DESC",
            countQuery = "SELECT COUNT(*) FROM users u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
                    "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Page<User> findByUsernameOrderByTotalSpentDesc(
            @Param("username") String username, @Param("source") String source, Pageable pageable);

    @Query(value = "SELECT u.* FROM users u " +
            "LEFT JOIN (SELECT user_id, SUM(-delta) AS spent FROM fortune_credit_log WHERE delta < 0 GROUP BY user_id) s " +
            "ON s.user_id = u.id " +
            "WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source)) " +
            "ORDER BY COALESCE(s.spent, 0) ASC",
            countQuery = "SELECT COUNT(*) FROM users u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
                    "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Page<User> findByUsernameOrderByTotalSpentAsc(
            @Param("username") String username, @Param("source") String source, Pageable pageable);

    @Query(value = "SELECT u.* FROM users u " +
            "LEFT JOIN (SELECT user_id, SUM(-delta) AS spent FROM fortune_credit_log WHERE delta < 0 GROUP BY user_id) s " +
            "ON s.user_id = u.id " +
            "WHERE u.total_actions_count > 0 AND u.visit_count > 1 " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source)) " +
            "ORDER BY COALESCE(s.spent, 0) DESC",
            countQuery = "SELECT COUNT(*) FROM users u WHERE u.total_actions_count > 0 AND u.visit_count > 1 " +
                    "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Page<User> findAllActiveOrderByTotalSpentDesc(@Param("source") String source, Pageable pageable);

    @Query(value = "SELECT u.* FROM users u " +
            "LEFT JOIN (SELECT user_id, SUM(-delta) AS spent FROM fortune_credit_log WHERE delta < 0 GROUP BY user_id) s " +
            "ON s.user_id = u.id " +
            "WHERE u.total_actions_count > 0 AND u.visit_count > 1 " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source)) " +
            "ORDER BY COALESCE(s.spent, 0) ASC",
            countQuery = "SELECT COUNT(*) FROM users u WHERE u.total_actions_count > 0 AND u.visit_count > 1 " +
                    "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Page<User> findAllActiveOrderByTotalSpentAsc(@Param("source") String source, Pageable pageable);

    @Query(value = "SELECT u.* FROM users u " +
            "LEFT JOIN (SELECT user_id, SUM(-delta) AS spent FROM fortune_credit_log WHERE delta < 0 GROUP BY user_id) s " +
            "ON s.user_id = u.id " +
            "WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
            "AND u.total_actions_count > 0 AND u.visit_count > 1 " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source)) " +
            "ORDER BY COALESCE(s.spent, 0) DESC",
            countQuery = "SELECT COUNT(*) FROM users u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
                    "AND u.total_actions_count > 0 AND u.visit_count > 1 " +
                    "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Page<User> findByUsernameActiveOrderByTotalSpentDesc(
            @Param("username") String username, @Param("source") String source, Pageable pageable);

    @Query(value = "SELECT u.* FROM users u " +
            "LEFT JOIN (SELECT user_id, SUM(-delta) AS spent FROM fortune_credit_log WHERE delta < 0 GROUP BY user_id) s " +
            "ON s.user_id = u.id " +
            "WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
            "AND u.total_actions_count > 0 AND u.visit_count > 1 " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source)) " +
            "ORDER BY COALESCE(s.spent, 0) ASC",
            countQuery = "SELECT COUNT(*) FROM users u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')) " +
                    "AND u.total_actions_count > 0 AND u.visit_count > 1 " +
                    "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Page<User> findByUsernameActiveOrderByTotalSpentAsc(
            @Param("username") String username, @Param("source") String source, Pageable pageable);

    // ── Инкремент счётчика действий ──────────────────────────────────────────

    @Modifying
    @Query("UPDATE User u SET u.totalActionsCount = u.totalActionsCount + 1 WHERE u.id = :userId")
    void incrementActionsCount(@Param("userId") Long userId);

    // ── Отчёты — стандартные методы (без source-фильтра) ─────────────────────

    long countByCreatedAtAfter(OffsetDateTime from);
    long countByLastActiveAtAfter(OffsetDateTime from);
    long countByCreatedAtBetween(OffsetDateTime from, OffsetDateTime to);

    // ── Отчёты — source-aware методы ─────────────────────────────────────────

    /** Суммарное количество пользователей с учётом source-фильтра */
    @Query("SELECT COUNT(u) FROM User u WHERE " +
            "(:source IS NULL OR (:source = '__organic__' AND u.referralSource IS NULL) OR (:source <> '__organic__' AND u.referralSource = :source))")
    long countWithSourceFilter(@Param("source") String source);

    /** Новые пользователи после даты с source-фильтром */
    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt > :from AND " +
            "(:source IS NULL OR (:source = '__organic__' AND u.referralSource IS NULL) OR (:source <> '__organic__' AND u.referralSource = :source))")
    long countByCreatedAtAfterWithSource(@Param("from") OffsetDateTime from, @Param("source") String source);

    /** Активные пользователи после даты (по lastActiveAt) с source-фильтром */
    @Query("SELECT COUNT(u) FROM User u WHERE u.lastActiveAt > :from AND " +
            "(:source IS NULL OR (:source = '__organic__' AND u.referralSource IS NULL) OR (:source <> '__organic__' AND u.referralSource = :source))")
    long countByLastActiveAtAfterWithSource(@Param("from") OffsetDateTime from, @Param("source") String source);

    /** Новые пользователи за диапазон дат с source-фильтром */
    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :from AND u.createdAt <= :to AND " +
            "(:source IS NULL OR (:source = '__organic__' AND u.referralSource IS NULL) OR (:source <> '__organic__' AND u.referralSource = :source))")
    long countByCreatedAtBetweenWithSource(
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, @Param("source") String source);

    // ── Органика (вкладка "Рефералы") ────────────────────────────────────────

    @Query("SELECT COUNT(u) FROM User u WHERE u.referralSource IS NULL")
    long countOrganicUsers();

    @Query("SELECT COALESCE(SUM(u.visitCount), 0) FROM User u WHERE u.referralSource IS NULL")
    long sumVisitCountForOrganicUsers();

    /**
     * ID пользователей, чей username содержит подстроку (без учёта регистра).
     * Используется для фильтрации платежей по пользователю — сначала резолвим
     * ID, затем фильтруем payments.user_id IN (...).
     */
    @Query("SELECT u.id FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%'))")
    List<Long> findIdsByUsernameContainingIgnoreCase(@Param("username") String username);

    // ── Сегменты аудитории для рассылок (AdminController#broadcast) ──────────

    /**
     * ID «неактивированных»: зарегистрировались, но не совершили ни одного действия.
     * Забаненные исключены; cutoff отсекает свежих пользователей — они ещё могут
     * дойти до первого действия сами, дёргать их рассылкой рано.
     */
    @Query("SELECT u.id FROM User u WHERE u.totalActionsCount = 0 AND u.banned = false AND u.createdAt < :cutoff")
    List<Long> findInactiveUserIds(@Param("cutoff") OffsetDateTime cutoff);

    /** Счётчик того же сегмента — для отображения числа получателей в админке до отправки */
    @Query("SELECT COUNT(u) FROM User u WHERE u.totalActionsCount = 0 AND u.banned = false AND u.createdAt < :cutoff")
    long countInactiveUsers(@Param("cutoff") OffsetDateTime cutoff);

    /**
     * Сколько пользователей реально достижимы ботом (notificationsAllowed = true).
     * Показывается в админке рядом с сегментами рассылки — см. {@link #countInactiveUsers}.
     */
    long countByNotificationsAllowedTrue();
}
