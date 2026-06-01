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
}
