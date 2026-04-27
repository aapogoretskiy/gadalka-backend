CREATE TABLE numerology_day_readings
(
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT  NOT NULL,
    date                  DATE    NOT NULL,
    day_code              INTEGER NOT NULL,
    personal_year_number  INTEGER NOT NULL,
    personal_month_number INTEGER NOT NULL,
    affirmation           TEXT    NOT NULL,
    payload               TEXT    NOT NULL,

    CONSTRAINT uk_numerology_day_user_date UNIQUE (user_id, date)
);
