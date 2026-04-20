CREATE TABLE diary_entries
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id),
    feature_type VARCHAR(50)  NOT NULL,
    reference_id BIGINT,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_diary_entries_user_feature ON diary_entries (user_id, feature_type);
CREATE INDEX idx_diary_entries_created_at ON diary_entries (created_at);
