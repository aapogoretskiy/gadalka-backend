package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.sapa.gadalka_backend.domain.FortuneCreditLogEntry;

import java.util.List;

public interface FortuneCreditLogRepository extends JpaRepository<FortuneCreditLogEntry, Long> {

    List<FortuneCreditLogEntry> findAllByUserIdOrderByCreatedAtDesc(Long userId);

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
