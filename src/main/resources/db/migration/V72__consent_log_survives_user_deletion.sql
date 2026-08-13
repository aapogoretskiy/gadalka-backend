-- Журнал согласий на автопродление должен переживать удаление пользователя.
--
-- Зачем: subscription_autorenew_consent_log — наша доказательная база правомерности
-- рекуррентных списаний (прямое требование Robokassa — хранить историю согласий).
-- До этой миграции FK на users был без каскада, поэтому удаление пользователя
-- физически требовало снести журнал вместе с ним — и от согласий на списания
-- не оставалось никаких следов (payments и subscriptions удаляются там же).
--
-- Решение: user_id становится необязательной «живой» ссылкой (ON DELETE SET NULL),
-- а telegram_id — денормализованным вечным идентификатором, по которому строка
-- остаётся осмысленной и после удаления аккаунта.
--
-- telegram_id намеренно оставлен NULLABLE: если бэкфилл ниже вдруг не покроет
-- какую-то строку, NOT NULL уронил бы всю миграцию и остановил деплой.

ALTER TABLE subscription_autorenew_consent_log ADD COLUMN telegram_id BIGINT;

UPDATE subscription_autorenew_consent_log l
   SET telegram_id = u.telegram_id
  FROM users u
 WHERE u.id = l.user_id;

ALTER TABLE subscription_autorenew_consent_log
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE subscription_autorenew_consent_log
    DROP CONSTRAINT fk_consent_log_user;

ALTER TABLE subscription_autorenew_consent_log
    ADD CONSTRAINT fk_consent_log_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL;

CREATE INDEX idx_consent_log_telegram_id ON subscription_autorenew_consent_log (telegram_id);
