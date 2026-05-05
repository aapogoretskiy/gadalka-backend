CREATE TABLE user_fortune_credits
(
    user_id    BIGINT      NOT NULL PRIMARY KEY,
    balance    INTEGER     NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_user_fortune_credits_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_user_fortune_credits_balance_non_negative
        CHECK (balance >= 0)
);
