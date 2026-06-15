-- Добавляем признак Telegram Premium и счётчик посещений в таблицу users.
-- is_premium обновляется при каждом логине из initData.user.is_premium.
-- visit_count инкрементируется при каждом новом "сеансе" (вместе с lastActiveAt).

ALTER TABLE users
    ADD COLUMN is_premium  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN visit_count INT     NOT NULL DEFAULT 0;
