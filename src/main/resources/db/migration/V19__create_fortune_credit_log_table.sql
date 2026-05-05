CREATE TABLE fortune_credit_log
(
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    delta        INTEGER      NOT NULL,        -- >0 начисление, <0 списание
    reason       VARCHAR(100) NOT NULL,        -- PAYMENT, FEATURE_SPEND, FREE_GRANT, REFUND
    payment_id   BIGINT,                       -- ссылка на payments если начислено за платёж
    feature_type VARCHAR(100),                 -- FORTUNE, COMPATIBILITY, NUMEROLOGY и т.д.
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_fortune_credit_log_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_fortune_credit_log_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id)
);

CREATE INDEX idx_fortune_credit_log_user_id ON fortune_credit_log (user_id);
