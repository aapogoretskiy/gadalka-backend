CREATE TABLE payment_webhook_log
(
    id            BIGSERIAL    PRIMARY KEY,
    provider      VARCHAR(50)  NOT NULL,
    raw_payload   TEXT         NOT NULL,       -- сырой JSON как пришёл от провайдера
    status        VARCHAR(50)  NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSED, FAILED
    error_message TEXT,                        -- заполняется при статусе FAILED
    received_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at  TIMESTAMPTZ
);

-- Индекс по статусу: scheduler каждые 30с ищет PENDING записи
CREATE INDEX idx_payment_webhook_log_status ON payment_webhook_log (status);
