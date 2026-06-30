package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.type.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@code JpaSpecificationExecutor} подключён ради вкладки "Транзакции" в админке:
 * там до 4 независимых опциональных фильтров (статус, провайдер, пользователь, диапазон дат),
 * которые нужно комбинировать в любом сочетании. Через Specification это собирается
 * декларативно в {@code AdminPaymentService}, без необходимости городить отдельный
 * @Query на каждую комбинацию фильтров, как сделано для отчётов ниже.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);
    List<Payment> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    boolean existsByProviderPaymentIdAndStatus(String providerPaymentId, PaymentStatus status);

    // ── Отчёты — стандартные методы ──────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(p.amountMinor), 0) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'RUB'")
    Long sumSucceededRub();

    @Query("SELECT COALESCE(SUM(p.amountMinor), 0) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'RUB' AND p.createdAt >= :from")
    Long sumSucceededRubSince(OffsetDateTime from);

    @Query("SELECT COUNT(DISTINCT p.userId) FROM Payment p WHERE p.status = 'SUCCEEDED'")
    Long countPayingUsers();

    @Query("SELECT p.productCode, COUNT(p) FROM Payment p WHERE p.status = 'SUCCEEDED' GROUP BY p.productCode ORDER BY COUNT(p) DESC")
    List<Object[]> topProducts();

    @Query(value = """
        SELECT DATE(created_at AT TIME ZONE 'UTC') as day, SUM(amount_minor) as total
        FROM payments
        WHERE status = 'SUCCEEDED' AND currency = 'RUB' AND created_at >= :from
        GROUP BY day ORDER BY day
        """, nativeQuery = true)
    List<Object[]> revenueByDay(OffsetDateTime from);

    @Query("SELECT COALESCE(SUM(p.amountMinor), 0) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'XTR'")
    Long sumSucceededStars();

    @Query("SELECT COALESCE(SUM(p.amountMinor), 0) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'XTR' AND p.createdAt >= :from")
    Long sumSucceededStarsSince(OffsetDateTime from);

    @Query("SELECT COUNT(DISTINCT p.userId) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'XTR'")
    Long countStarsPayingUsers();

    @Query("SELECT COALESCE(SUM(p.amountMinor), 0) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'RUB' AND p.createdAt >= :from AND p.createdAt <= :to")
    Long sumSucceededRubBetween(OffsetDateTime from, OffsetDateTime to);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'RUB' AND p.createdAt >= :from AND p.createdAt <= :to")
    Long countSucceededRubBetween(OffsetDateTime from, OffsetDateTime to);

    @Query("SELECT COALESCE(SUM(p.amountMinor), 0) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'XTR' AND p.createdAt >= :from AND p.createdAt <= :to")
    Long sumSucceededStarsBetween(OffsetDateTime from, OffsetDateTime to);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'XTR' AND p.createdAt >= :from AND p.createdAt <= :to")
    Long countSucceededStarsBetween(OffsetDateTime from, OffsetDateTime to);

    // ── Доход по маркетинговым источникам (вкладка "Рефералы") ────────────────

    @Query(value = """
        SELECT
            u.referral_source,
            COALESCE(SUM(p.amount_minor) FILTER (WHERE p.currency = 'RUB'), 0) AS rub_minor,
            COALESCE(SUM(p.amount_minor) FILTER (WHERE p.currency = 'XTR'), 0) AS stars
        FROM payments p
        JOIN users u ON u.id = p.user_id
        WHERE p.status = 'SUCCEEDED'
          AND u.referral_source IS NOT NULL
          AND u.referral_source NOT LIKE 'ref\\_%' ESCAPE '\\'
        GROUP BY u.referral_source
        """, nativeQuery = true)
    List<Object[]> findRevenueByReferralSource();

    @Query(value = """
        SELECT
            COALESCE(SUM(p.amount_minor) FILTER (WHERE p.currency = 'RUB'), 0) AS rub_minor,
            COALESCE(SUM(p.amount_minor) FILTER (WHERE p.currency = 'XTR'), 0) AS stars
        FROM payments p
        JOIN users u ON u.id = p.user_id
        WHERE p.status = 'SUCCEEDED'
          AND u.referral_source IS NULL
        """, nativeQuery = true)
    List<Object[]> findRevenueForOrganicUsers();

    // ── Отчёты — source-aware методы ─────────────────────────────────────────
    // Токен "__organic__" = referral_source IS NULL. source = null → фильтр отключён.

    @Query(value = "SELECT COALESCE(SUM(p.amount_minor), 0) FROM payments p " +
            "JOIN users u ON u.id = p.user_id " +
            "WHERE p.status = 'SUCCEEDED' AND p.currency = 'RUB' " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Long sumSucceededRubWithSource(@Param("source") String source);

    @Query(value = "SELECT COALESCE(SUM(p.amount_minor), 0) FROM payments p " +
            "JOIN users u ON u.id = p.user_id " +
            "WHERE p.status = 'SUCCEEDED' AND p.currency = 'RUB' AND p.created_at >= :from " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Long sumSucceededRubSinceWithSource(@Param("from") OffsetDateTime from, @Param("source") String source);

    @Query(value = "SELECT COUNT(DISTINCT p.user_id) FROM payments p " +
            "JOIN users u ON u.id = p.user_id " +
            "WHERE p.status = 'SUCCEEDED' " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Long countPayingUsersWithSource(@Param("source") String source);

    @Query(value = "SELECT COALESCE(SUM(p.amount_minor), 0) FROM payments p " +
            "JOIN users u ON u.id = p.user_id " +
            "WHERE p.status = 'SUCCEEDED' AND p.currency = 'XTR' " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Long sumSucceededStarsWithSource(@Param("source") String source);

    @Query(value = "SELECT COALESCE(SUM(p.amount_minor), 0) FROM payments p " +
            "JOIN users u ON u.id = p.user_id " +
            "WHERE p.status = 'SUCCEEDED' AND p.currency = 'XTR' AND p.created_at >= :from " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Long sumSucceededStarsSinceWithSource(@Param("from") OffsetDateTime from, @Param("source") String source);

    @Query(value = "SELECT COUNT(DISTINCT p.user_id) FROM payments p " +
            "JOIN users u ON u.id = p.user_id " +
            "WHERE p.status = 'SUCCEEDED' AND p.currency = 'XTR' " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Long countStarsPayingUsersWithSource(@Param("source") String source);

    @Query(value = "SELECT COALESCE(SUM(p.amount_minor), 0) FROM payments p " +
            "JOIN users u ON u.id = p.user_id " +
            "WHERE p.status = 'SUCCEEDED' AND p.currency = 'RUB' AND p.created_at >= :from AND p.created_at <= :to " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Long sumSucceededRubBetweenWithSource(
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, @Param("source") String source);

    @Query(value = "SELECT COUNT(p.*) FROM payments p " +
            "JOIN users u ON u.id = p.user_id " +
            "WHERE p.status = 'SUCCEEDED' AND p.currency = 'RUB' AND p.created_at >= :from AND p.created_at <= :to " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Long countSucceededRubBetweenWithSource(
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, @Param("source") String source);

    @Query(value = "SELECT COALESCE(SUM(p.amount_minor), 0) FROM payments p " +
            "JOIN users u ON u.id = p.user_id " +
            "WHERE p.status = 'SUCCEEDED' AND p.currency = 'XTR' AND p.created_at >= :from AND p.created_at <= :to " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Long sumSucceededStarsBetweenWithSource(
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, @Param("source") String source);

    @Query(value = "SELECT COUNT(p.*) FROM payments p " +
            "JOIN users u ON u.id = p.user_id " +
            "WHERE p.status = 'SUCCEEDED' AND p.currency = 'XTR' AND p.created_at >= :from AND p.created_at <= :to " +
            "AND (:source IS NULL OR (:source = '__organic__' AND u.referral_source IS NULL) OR (:source <> '__organic__' AND u.referral_source = :source))",
            nativeQuery = true)
    Long countSucceededStarsBetweenWithSource(
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, @Param("source") String source);
}
