CREATE TABLE numerology_year_readings
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT  NOT NULL,
    year_start_date DATE    NOT NULL,
    year_end_date   DATE    NOT NULL,
    year_number     INTEGER NOT NULL,
    payload         TEXT    NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_numerology_year_user_start UNIQUE (user_id, year_start_date)
);

CREATE INDEX idx_numerology_year_readings_user_range
    ON numerology_year_readings (user_id, year_start_date, year_end_date);

-- Стоимость годового нумерологического разбора (в знаках) — редактируется в админ-панели.
INSERT INTO system_config (config_key, config_value, description, created_at, updated_at)
VALUES
    ('FEATURE_COST_NUMEROLOGY_YEAR',
     '18',
     'Стоимость разбора на год (нумерология) в знаках',
     NOW(),
     NOW());
