-- Добавляем slug на карты.
ALTER TABLE cards ADD COLUMN slug VARCHAR(100);

-- Добавляем ссылку на активную тему пользователя.
ALTER TABLE users
    ADD COLUMN active_theme_id BIGINT REFERENCES card_deck_themes (id) ON DELETE SET NULL;
