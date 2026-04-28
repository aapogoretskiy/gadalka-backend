CREATE TABLE referral_events
(
    id            BIGSERIAL PRIMARY KEY,
    referral_code VARCHAR(255)             NOT NULL,
    telegram_id   BIGINT                   NOT NULL,
    user_id       BIGINT,
    is_new_user   BOOLEAN,
    event_type    VARCHAR(50)              NOT NULL,
    created_at    TIMESTAMPTZ              NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_referral_events_code        ON referral_events (referral_code);
CREATE INDEX idx_referral_events_telegram_id ON referral_events (telegram_id);
CREATE INDEX idx_referral_events_created_at  ON referral_events (created_at);
