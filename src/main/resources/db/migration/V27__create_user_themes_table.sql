-- Таблица покупок тем: какие темы принадлежат каждому пользователю.
CREATE TABLE user_themes
(
    user_id      BIGINT    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    theme_id     BIGINT    NOT NULL REFERENCES card_deck_themes (id) ON DELETE CASCADE,
    purchased_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, theme_id) -- гарантирует отсутствие дублей
);
