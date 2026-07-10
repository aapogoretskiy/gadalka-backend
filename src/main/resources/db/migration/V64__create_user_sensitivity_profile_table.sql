-- Агрегированный рейтинг "склонности к чувствительным вопросам" на пользователя.
-- Отдельная таблица (не поле в users) — чтобы доступ к ней можно было ограничить
-- отдельно от остальных данных пользователя: это фактически обработка спецкатегорий
-- персональных данных (здоровье/суицид/политика/религия), см. напоминание про 152-ФЗ.

CREATE TABLE user_sensitivity_profile
(
    user_id               BIGINT PRIMARY KEY,
    total_text_questions  INTEGER        NOT NULL DEFAULT 0,
    total_sensitive_count INTEGER        NOT NULL DEFAULT 0,
    category_counts       TEXT           NOT NULL DEFAULT '{}',
    sensitive_percentage  NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    dominant_category     VARCHAR(100),
    risk_level            VARCHAR(20)    NOT NULL DEFAULT 'GREEN',
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_user_sensitivity_profile_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_user_sensitivity_profile_risk_level ON user_sensitivity_profile (risk_level);
