package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.FortuneCreditLogEntry;

import java.util.List;

public interface FortuneCreditLogRepository extends JpaRepository<FortuneCreditLogEntry, Long> {

    List<FortuneCreditLogEntry> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Батч-подсчёт потраченных знаков для списка пользователей (например, для одной страницы
     * таблицы в админке) — один запрос вместо N отдельных по каждому пользователю.
     * Возвращает только пользователей, у которых есть хотя бы одно списание.
     */
    @Query("SELECT l.userId AS userId, COALESCE(SUM(-l.delta), 0) AS spent " +
            "FROM FortuneCreditLogEntry l " +
            "WHERE l.userId IN :userIds AND l.delta < 0 " +
            "GROUP BY l.userId")
    List<UserSpentRow> sumSpentByUserIds(@Param("userIds") List<Long> userIds);

    interface UserSpentRow {
        Long getUserId();
        long getSpent();
    }

    // ── Отчёты ──────────────────────────────────────────────────────────────

    /** Суммарно начислено знаков за всё время (delta > 0) */
    @Query("SELECT COALESCE(SUM(l.delta), 0) FROM FortuneCreditLogEntry l WHERE l.delta > 0")
    long sumGranted();

    /** Суммарно потрачено знаков за всё время (|delta| где delta < 0) */
    @Query("SELECT COALESCE(SUM(-l.delta), 0) FROM FortuneCreditLogEntry l WHERE l.delta < 0")
    long sumSpent();

    /** Начислено через покупку (reason = PAYMENT) */
    @Query("SELECT COALESCE(SUM(l.delta), 0) FROM FortuneCreditLogEntry l WHERE l.reason = 'PAYMENT'")
    long sumGrantedByPayment();

    /** Начислено подарками (ADMIN_GIFT + ADMIN_BROADCAST) */
    @Query("SELECT COALESCE(SUM(l.delta), 0) FROM FortuneCreditLogEntry l WHERE l.reason IN ('ADMIN_GIFT', 'ADMIN_BROADCAST')")
    long sumGrantedByAdmin();

    /** Начислено бонусами (FREE_GRANT, REFUND) */
    @Query("SELECT COALESCE(SUM(l.delta), 0) FROM FortuneCreditLogEntry l WHERE l.reason IN ('FREE_GRANT', 'REFUND')")
    long sumGrantedByBonus();
}
