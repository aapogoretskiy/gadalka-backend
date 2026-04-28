-- =============================================================================
-- Реферальная аналитика: дневной тренд по каждому источнику
-- =============================================================================
-- Показывает помедённые активность по дням — удобно для анализа
-- эффективности рекламных кампаний во времени.
-- =============================================================================

SELECT
    re.created_at::DATE                                         AS "Дата",
    re.referral_code                                            AS "Источник",
    COUNT(*) FILTER (WHERE re.event_type = 'BOT_ENTRY')        AS "Кликов",
    COUNT(*) FILTER (WHERE re.event_type = 'APP_OPEN')         AS "Открытий",
    COUNT(*) FILTER (WHERE re.event_type = 'APP_OPEN'
                       AND re.is_new_user = TRUE)              AS "Новых"

FROM referral_events re
GROUP BY re.created_at::DATE, re.referral_code
ORDER BY "Дата" DESC, "Новых" DESC;
