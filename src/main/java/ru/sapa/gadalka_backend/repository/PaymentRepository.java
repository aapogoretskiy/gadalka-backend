package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.sapa.gadalka_backend.domain.Payment;
import ru.sapa.gadalka_backend.domain.type.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    List<Payment> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByProviderPaymentIdAndStatus(String providerPaymentId, PaymentStatus status);

    // ── Отчёты ──────────────────────────────────────────────────────────────

    /** Суммарная выручка в рублях (amount_minor / 100) по успешным RUB-платежам */
    @Query("SELECT COALESCE(SUM(p.amountMinor), 0) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'RUB'")
    Long sumSucceededRub();

    /** Выручка в рублях за период */
    @Query("SELECT COALESCE(SUM(p.amountMinor), 0) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'RUB' AND p.createdAt >= :from")
    Long sumSucceededRubSince(OffsetDateTime from);

    /** Количество уникальных платящих пользователей */
    @Query("SELECT COUNT(DISTINCT p.userId) FROM Payment p WHERE p.status = 'SUCCEEDED'")
    Long countPayingUsers();

    /** Топ продуктов: [productCode, count] по убыванию продаж */
    @Query("SELECT p.productCode, COUNT(p) FROM Payment p WHERE p.status = 'SUCCEEDED' GROUP BY p.productCode ORDER BY COUNT(p) DESC")
    List<Object[]> topProducts();

    /** Выручка по дням за последние N дней: [date, sumMinor] */
    @Query(value = """
        SELECT DATE(created_at AT TIME ZONE 'UTC') as day, SUM(amount_minor) as total
        FROM payments
        WHERE status = 'SUCCEEDED' AND currency = 'RUB' AND created_at >= :from
        GROUP BY day ORDER BY day
        """, nativeQuery = true)
    List<Object[]> revenueByDay(OffsetDateTime from);

    // ── Stars (Telegram) ──────────────────────────────────────────────────

    /** Суммарно Stars потрачено за всё время */
    @Query("SELECT COALESCE(SUM(p.amountMinor), 0) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'XTR'")
    Long sumSucceededStars();

    /** Stars за период */
    @Query("SELECT COALESCE(SUM(p.amountMinor), 0) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'XTR' AND p.createdAt >= :from")
    Long sumSucceededStarsSince(OffsetDateTime from);

    /** Количество уникальных Stars-плательщиков */
    @Query("SELECT COUNT(DISTINCT p.userId) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'XTR'")
    Long countStarsPayingUsers();

    // ── Отчёты за диапазон ──────────────────────────────────────────────────

    /** Выручка в рублях (копейки) за диапазон дат */
    @Query("SELECT COALESCE(SUM(p.amountMinor), 0) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'RUB' AND p.createdAt >= :from AND p.createdAt <= :to")
    Long sumSucceededRubBetween(OffsetDateTime from, OffsetDateTime to);

    /** Количество успешных рублёвых транзакций за диапазон дат */
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'RUB' AND p.createdAt >= :from AND p.createdAt <= :to")
    Long countSucceededRubBetween(OffsetDateTime from, OffsetDateTime to);

    /** Stars за диапазон дат */
    @Query("SELECT COALESCE(SUM(p.amountMinor), 0) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'XTR' AND p.createdAt >= :from AND p.createdAt <= :to")
    Long sumSucceededStarsBetween(OffsetDateTime from, OffsetDateTime to);

    /** Количество успешных Stars-транзакций за диапазон дат */
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.currency = 'XTR' AND p.createdAt >= :from AND p.createdAt <= :to")
    Long countSucceededStarsBetween(OffsetDateTime from, OffsetDateTime to);

    // ── Доход по маркетинговым источникам (вкладка "Рефералы") ────────────────

    /**
     * Доход по успешным платежам, сгруппированный по источнику регистрации пользователя
     * ({@code users.referral_source}). Исключает пользовательские коды вида "ref_*" —
     * там нужна модель "топ рефереров", а не маркетинговый разрез.
     * <p>
     * Возвращает: [referral_source, rub_minor, stars]
     */
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
}
