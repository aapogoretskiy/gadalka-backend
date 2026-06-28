-- Гороскоп на день: разделяем общий work_text на career_text (карьера) и money_text (финансы),
-- добавляем числовые рейтинги 1..5 для общего/любви/карьеры/денег.
-- DEFAULT-значения нужны только для прохождения NOT NULL на момент миграции —
-- реальные данные перезапишутся при следующей генерации гороскопа (контент кэшируется на сутки).
ALTER TABLE daily_horoscopes
    RENAME COLUMN work_text TO career_text;

ALTER TABLE daily_horoscopes
    ADD COLUMN money_text TEXT NOT NULL DEFAULT '',
    ADD COLUMN general_score INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN love_score INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN career_score INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN money_score INTEGER NOT NULL DEFAULT 3;

ALTER TABLE daily_horoscopes
    ALTER COLUMN money_text DROP DEFAULT,
    ALTER COLUMN general_score DROP DEFAULT,
    ALTER COLUMN love_score DROP DEFAULT,
    ALTER COLUMN career_score DROP DEFAULT,
    ALTER COLUMN money_score DROP DEFAULT;
