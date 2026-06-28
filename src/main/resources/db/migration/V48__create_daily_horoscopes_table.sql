-- Гороскоп на день: одна строка на каждый знак зодиака (всего 12 строк),
-- контент перезаписывается каждые сутки (по МСК) — история прошлых дней не хранится.
CREATE TABLE daily_horoscopes
(
    id BIGSERIAL PRIMARY KEY,
    zodiac_sign VARCHAR(20) NOT NULL,
    date DATE NOT NULL,
    general_text TEXT NOT NULL,
    advice_text TEXT NOT NULL,
    love_text TEXT NOT NULL,
    work_text TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_daily_horoscopes_zodiac_sign
    ON daily_horoscopes(zodiac_sign);
