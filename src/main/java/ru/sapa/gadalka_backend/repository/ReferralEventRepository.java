package ru.sapa.gadalka_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sapa.gadalka_backend.domain.ReferralEvent;
import ru.sapa.gadalka_backend.domain.type.ReferralEventType;

import java.util.List;
import java.util.Optional;

public interface ReferralEventRepository extends JpaRepository<ReferralEvent, Long> {

    /** Найти последнее BOT_ENTRY событие для данного telegram_id (для связки с APP_OPEN). */
    Optional<ReferralEvent> findTopByTelegramIdAndEventTypeOrderByCreatedAtDesc(
            Long telegramId, ReferralEventType eventType);

    /** Все события по реферальному коду (для отладки). */
    List<ReferralEvent> findAllByReferralCodeOrderByCreatedAtDesc(String referralCode);

    // ── Аналитика ────────────────────────────────────────────────────────────

    /**
     * Сводная таблица по маркетинговым источникам (исключает коды вида "ref_*").
     * Возвращает: [referral_code, clicks, app_opens, new_users]
     */
    @Query(value = """
        SELECT
            re.referral_code,
            COUNT(*) FILTER (WHERE re.event_type = 'BOT_ENTRY')                          AS clicks,
            COUNT(*) FILTER (WHERE re.event_type = 'APP_OPEN')                           AS app_opens,
            COUNT(*) FILTER (WHERE re.event_type = 'APP_OPEN' AND re.is_new_user = TRUE) AS new_users
        FROM referral_events re
        WHERE re.referral_code NOT LIKE 'ref\\_%' ESCAPE '\\'
        GROUP BY re.referral_code
        ORDER BY new_users DESC, clicks DESC
        """, nativeQuery = true)
    List<Object[]> findMarketingSourceStats();

    /**
     * Топ пользователей по количеству приглашённых (из событий USER_REFERRAL).
     * Возвращает: [referrer_user_id, invited_count]
     */
    @Query(value = """
        SELECT re.referrer_user_id, COUNT(*) AS invited_count
        FROM referral_events re
        WHERE re.event_type = 'USER_REFERRAL'
          AND re.referrer_user_id IS NOT NULL
        GROUP BY re.referrer_user_id
        ORDER BY invited_count DESC
        LIMIT 50
        """, nativeQuery = true)
    List<Object[]> findTopUserReferrers();

    /**
     * Список пользователей, приглашённых конкретным реферером.
     * Возвращает: [user_id, telegram_id] — дальше джойним с users на уровне сервиса.
     */
    @Query(value = """
        SELECT re.user_id
        FROM referral_events re
        WHERE re.event_type = 'USER_REFERRAL'
          AND re.referrer_user_id = :referrerUserId
        ORDER BY re.created_at DESC
        """, nativeQuery = true)
    List<Long> findInvitedUserIdsByReferrer(@Param("referrerUserId") Long referrerUserId);
}
