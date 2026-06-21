-- Категории вопросов на экране "О чём спросить карты?" (Любовь, Деньги, Работа, Ситуация, Здоровье)
-- и заготовленные вопросы-пресеты для каждой категории.
-- Раньше категории были захардкожены на фронте и валидировались regex'ом в FortuneRequest.
-- Теперь сами тексты вопросов-подсказок хранятся в БД, а пользователь может либо
-- выбрать готовый вопрос, либо ввести свой — обработка вопроса на бэке не меняется.

CREATE TABLE question_categories
(
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(20) NOT NULL,
    name       VARCHAR(50) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    is_active  BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_question_categories_code UNIQUE (code)
);

CREATE TABLE question_presets
(
    id            BIGSERIAL PRIMARY KEY,
    category_id   BIGINT      NOT NULL REFERENCES question_categories (id),
    question_text VARCHAR(300) NOT NULL,
    sort_order    INT         NOT NULL DEFAULT 0,
    is_active     BOOLEAN     NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_question_presets_category_id ON question_presets (category_id);
