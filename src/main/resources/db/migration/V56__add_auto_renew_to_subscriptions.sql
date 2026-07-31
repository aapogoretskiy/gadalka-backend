-- Автопродление подписки: рекуррентные списания через Robokassa (самостоятельная
-- интеграция, Recurring=true / PreviousInvoiceID — см. RobokassaClient).
--
-- root_payment_id — id самого первого успешного платежа цепочки подписки.
-- Именно его нужно передавать Robokassa как PreviousInvoiceID при каждом
-- следующем списании (сама Robokassa связывает дочерние платежи с материнским
-- через этот номер, а не через последний по времени).
--
-- renewal_notice_sent_at — когда пользователю отправлено обязательное по 376-ФЗ
-- (ст. 16.1 ЗоЗПП) уведомление за сутки до списания. NULL — не отправлялось.
-- Защищает от двух вещей сразу: от повторной отправки уведомления и от попытки
-- списания раньше, чем пройдут положенные 24 часа с момента уведомления.
ALTER TABLE subscriptions
    ADD COLUMN auto_renew_enabled     BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN root_payment_id        BIGINT,
    ADD COLUMN renewal_notice_sent_at TIMESTAMPTZ;

-- Журнал согласий/отзывов на автопродление.
-- Robokassa прямо требует хранить ИСТОРИЮ согласий пользователя на автосписания,
-- а не только текущее состояние — поэтому это отдельная append-only таблица
--
-- payment_id — платёж, в рамках оформления которого дано согласие (NULL при простом отзыве вне момента оплаты).
-- subscription_id — подписка, к которой относится согласие (NULL при самой первой покупке — подписки ещё не существует. На момент клика по чекбоксу, она появится только после webhook).
CREATE TABLE subscription_autorenew_consent_log
(
    id                BIGSERIAL   PRIMARY KEY,
    user_id           BIGINT      NOT NULL,
    payment_id        BIGINT,
    subscription_id   BIGINT,
    action            VARCHAR(20) NOT NULL,  -- GRANTED / REVOKED
    agreement_version VARCHAR(50),           -- версия оферты, действовавшая на момент согласия
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_consent_log_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_consent_log_user_id         ON subscription_autorenew_consent_log (user_id);
CREATE INDEX idx_consent_log_subscription_id ON subscription_autorenew_consent_log (subscription_id);
