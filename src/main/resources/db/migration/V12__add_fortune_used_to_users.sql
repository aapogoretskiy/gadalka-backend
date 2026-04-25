ALTER TABLE users ADD COLUMN fortune_used BOOLEAN NOT NULL DEFAULT FALSE;

-- Помечаем уже существующих пользователей, у которых есть записи в fortunes
UPDATE users SET fortune_used = TRUE WHERE id IN (SELECT DISTINCT user_id FROM fortunes);
