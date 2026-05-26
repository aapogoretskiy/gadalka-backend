-- Таблица тем (колод) карт Таро
CREATE TABLE card_deck_themes
(
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(50)  NOT NULL UNIQUE,  -- Внутренний идентификатор: 'classic', 'cosmic' и т.д.
    name        VARCHAR(100) NOT NULL,          -- Отображаемое название
    description TEXT,                           -- Описание для магазина
    base_url         VARCHAR(512),
    image_extension  VARCHAR(10)  NOT NULL DEFAULT 'jpg', -- Расширение файлов картинок для данной темы: 'jpg', 'png', 'webp' и т.д.
    price       INT          NOT NULL DEFAULT 0, -- Стоимость в кредитах (знаках)
    is_free     BOOLEAN      NOT NULL DEFAULT false, -- true = классическая/бесплатная, покупка не нужна
    is_enabled  BOOLEAN      NOT NULL DEFAULT true,  -- false = "скоро", кнопки покупки нет
    sort_order  INT          NOT NULL DEFAULT 0      -- Порядок отображения в магазине
);

INSERT INTO card_deck_themes (slug, name, description, base_url, image_extension, price, is_free, is_enabled, sort_order)
VALUES ('classic', 'Классическая', 'Традиционная колода Райдера-Уэйта', NULL, 'jpg', 0, true, true, 0);
