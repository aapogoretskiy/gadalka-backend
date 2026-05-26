-- Фиксация факта принятия пользователем юридических документов (152-ФЗ)
-- terms_accepted_at — серверный timestamp момента завершения онбординга (принятие соглашений)
-- terms_version — версия документов, которую видел пользователь (формат YYYY-MM-DD)
-- Оба поля NULL-able: у существующих пользователей, зарегистрированных до этой миграции, значения отсутствуют

ALTER TABLE users
    ADD COLUMN terms_accepted_at TIMESTAMPTZ,
    ADD COLUMN terms_version     VARCHAR(50);
