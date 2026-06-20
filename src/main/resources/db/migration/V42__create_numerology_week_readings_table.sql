CREATE TABLE numerology_week_readings
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT  NOT NULL,
    week_start_date DATE    NOT NULL,
    week_end_date   DATE    NOT NULL,
    week_number     INTEGER NOT NULL,
    payload         TEXT    NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_numerology_week_user_start UNIQUE (user_id, week_start_date)
);

CREATE INDEX idx_numerology_week_readings_user_range
    ON numerology_week_readings (user_id, week_start_date, week_end_date);
