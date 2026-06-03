-- Добавляем поле referrer_user_id в referral_events.
-- Заполняется только для событий USER_REFERRAL — хранит ID пользователя, который пригласил.
-- Nullable: маркетинговые рефералы (BOT_ENTRY/APP_OPEN) это поле не используют.

ALTER TABLE referral_events
    ADD COLUMN referrer_user_id BIGINT,
    ADD CONSTRAINT fk_referral_events_referrer
        FOREIGN KEY (referrer_user_id) REFERENCES users (id);

CREATE INDEX idx_referral_events_referrer_user_id ON referral_events (referrer_user_id);
