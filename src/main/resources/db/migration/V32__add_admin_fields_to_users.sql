-- Поле для бана пользователя администратором.
-- При is_banned = true бэкенд возвращает 403 на все защищённые запросы.
-- Поле last_active_at обновляется JwtAuthFilter не чаще раза в 5 минут —
-- используется в админ-панели для мониторинга активности пользователей.

ALTER TABLE users
    ADD COLUMN is_banned     BOOLEAN                  NOT NULL DEFAULT FALSE,
    ADD COLUMN last_active_at TIMESTAMPTZ;
