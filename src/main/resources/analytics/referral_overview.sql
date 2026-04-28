-- =============================================================================
-- Реферальная аналитика: сводная таблица по источникам
-- =============================================================================
-- Запуск: psql -U <user> -d <db> -f referral_overview.sql
-- =============================================================================

SELECT
    re.referral_code                                            AS "Источник",

    -- Сколько раз пользователи кликали по ссылке (бот получил /start CODE)
    COUNT(*) FILTER (WHERE re.event_type = 'BOT_ENTRY')        AS "Кликов по ссылке",

    -- Сколько из них реально открыли Mini App
    COUNT(*) FILTER (WHERE re.event_type = 'APP_OPEN')         AS "Открытий приложения",

    -- Сколько из открывших были новыми пользователями
    COUNT(*) FILTER (WHERE re.event_type = 'APP_OPEN'
                       AND re.is_new_user = TRUE)              AS "Новых пользователей",

    -- Конверсия: новые / клики * 100
    ROUND(
        COUNT(*) FILTER (WHERE re.event_type = 'APP_OPEN' AND re.is_new_user = TRUE)::NUMERIC
        / NULLIF(COUNT(*) FILTER (WHERE re.event_type = 'BOT_ENTRY'), 0) * 100,
        1
    )                                                           AS "Конверсия %",

    -- Конверсия клик → открытие
    ROUND(
        COUNT(*) FILTER (WHERE re.event_type = 'APP_OPEN')::NUMERIC
        / NULLIF(COUNT(*) FILTER (WHERE re.event_type = 'BOT_ENTRY'), 0) * 100,
        1
    )                                                           AS "Клик→Открытие %",

    MIN(re.created_at)::DATE                                    AS "Первое событие",
    MAX(re.created_at)::DATE                                    AS "Последнее событие"

FROM referral_events re
GROUP BY re.referral_code
ORDER BY "Новых пользователей" DESC, "Кликов по ссылке" DESC;
