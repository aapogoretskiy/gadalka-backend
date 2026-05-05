CREATE TABLE payments
(
    id                  BIGSERIAL    PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    product_code        VARCHAR(50)  NOT NULL,
    provider            VARCHAR(50)  NOT NULL, -- YOOKASSA, TELEGRAM_STARS
    provider_payment_id VARCHAR(255) UNIQUE,   -- id платежа на стороне провайдера (UNIQUE = идемпотентность)
    status              VARCHAR(50)  NOT NULL, -- PENDING, SUCCEEDED, FAILED, CANCELLED
    amount_minor        INTEGER      NOT NULL, -- копейки для RUB, штуки для XTR (Stars)
    currency            VARCHAR(10)  NOT NULL, -- RUB, XTR
    credits_to_grant    INTEGER      NOT NULL, -- зафиксировано в момент создания платежа
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_payments_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_payments_user_id ON payments (user_id);
CREATE INDEX idx_payments_status  ON payments (status);
