-- Настройка времени уведомлений для пользователя.
-- MORNING  — уведомление в 9:00 по Москве
-- EVENING  — уведомление в 20:00 по Москве
-- DISABLED — уведомления отключены
--
-- DEFAULT 'EVENING': все существующие пользователи (фокус-группа) сразу получат вечерние уведомления.
-- Новые пользователи выбирают время явно на экране онбординга.

ALTER TABLE user_profile
    ADD COLUMN notification_time VARCHAR(20) NOT NULL DEFAULT 'EVENING'
        CONSTRAINT chk_notification_time CHECK (notification_time IN ('MORNING', 'EVENING', 'DISABLED'));
