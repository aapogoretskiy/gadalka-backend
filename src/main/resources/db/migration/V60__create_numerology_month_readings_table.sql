CREATE TABLE numerology_month_readings
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT  NOT NULL,
    month_start_date DATE    NOT NULL,
    month_end_date   DATE    NOT NULL,
    month_number     INTEGER NOT NULL,
    payload          TEXT    NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_numerology_month_user_start UNIQUE (user_id, month_start_date)
);

CREATE INDEX idx_numerology_month_readings_user_range
    ON numerology_month_readings (user_id, month_start_date, month_end_date);

-- Стоимость месячного нумерологического разбора (в знаках) — редактируется в админ-панели.
INSERT INTO system_config (config_key, config_value, description, created_at, updated_at)
VALUES
    ('FEATURE_COST_NUMEROLOGY_MONTH',
     '10',
     'Стоимость разбора на месяц (нумерология) в знаках',
     NOW(),
     NOW());
