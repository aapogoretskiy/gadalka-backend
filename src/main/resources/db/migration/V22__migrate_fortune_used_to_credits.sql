-- Переносим логику fortune_used в новую систему кредитов.
--
-- Правило:
--   fortune_used = FALSE → пользователь ещё не тратил бесплатное гадание → баланс 1
--   fortune_used = TRUE  → уже потратил → баланс 0

INSERT INTO user_fortune_credits (user_id, balance)
SELECT id,
       CASE WHEN fortune_used = FALSE THEN 1 ELSE 0 END
FROM users;

-- Фиксируем в истории: кому выдали стартовый кредит
INSERT INTO fortune_credit_log (user_id, delta, reason)
SELECT id, 1, 'FREE_GRANT'
FROM users
WHERE fortune_used = FALSE;
