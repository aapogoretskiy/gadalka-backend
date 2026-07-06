-- Фича "Сонник": AI-разбор снов с учётом знака зодиака и числа жизни.
--
-- dream_symbols — справочник "частых символов во снах" (чипы на экране ввода).
-- Хранится в БД, а не хардкодом на фронте, чтобы список можно было расширять
-- через админ-панель без деплоя (по аналогии с question_presets).
--
-- dream_readings — сами разборы снов. Полный ответ AI хранится снимком в payload
-- (JSON) — как в numerology_week_readings: разбор генерируется один раз, повторное
-- открытие из истории бесплатно и не зависит от доступности AI.

CREATE TABLE dream_symbols
(
    id          BIGSERIAL PRIMARY KEY,
    emoji       VARCHAR(16)  NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    -- Подсказка для промпта: что этот символ классически означает во сне.
    -- Может быть NULL — тогда AI трактует символ самостоятельно.
    prompt_hint VARCHAR(300),
    sort_order  INT          NOT NULL DEFAULT 0,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_dream_symbols_name UNIQUE (name)
);

CREATE TABLE dream_readings
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES users (id),
    -- Текст сна от пользователя (может быть NULL, если выбраны только символы)
    dream_text       VARCHAR(1000),
    -- Снимок выбранных пользователем символов на момент разбора (JSON-массив строк).
    -- Именно снимок, а не FK: админ может переименовать/удалить символ,
    -- а старый разбор должен показываться как был.
    selected_symbols TEXT,
    -- Полный ответ AI (DreamResponse) в JSON — источник данных для истории
    payload          TEXT        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dream_readings_user_created ON dream_readings (user_id, created_at DESC);

-- Сид чипов с дизайн-макета (12 символов)
INSERT INTO dream_symbols (emoji, name, prompt_hint, sort_order)
VALUES ('💧', 'Вода',       'эмоции, подсознание, очищение',                      10),
       ('🌊', 'Море',       'глубина чувств, неизведанное, масштаб перемен',      20),
       ('✈️', 'Полёт',      'свобода, амбиции, желание вырваться за пределы',     30),
       ('🏠', 'Дом',        'внутренний мир, семья, ощущение безопасности',       40),
       ('🌚', 'Незнакомец', 'скрытая сторона личности, новые обстоятельства',     50),
       ('🐍', 'Змея',       'трансформация, скрытая угроза, исцеление',           60),
       ('🔑', 'Ключ',       'решение проблемы, доступ к новому, тайна',           70),
       ('🌙', 'Луна',       'интуиция, цикличность, скрытые чувства',             80),
       ('🔥', 'Огонь',      'страсть, разрушение старого, энергия',               90),
       ('💀', 'Смерть',     'завершение этапа, обновление (не буквальная смерть)', 100),
       ('👶', 'Ребёнок',    'новое начинание, уязвимость, внутренний ребёнок',    110),
       ('🐱', 'Кошка',      'независимость, женская энергия, интуиция',           120);

-- Стоимость разбора сна в знаках — конфигурируется через админ-панель
INSERT INTO system_config (config_key, config_value, description, created_at, updated_at)
VALUES ('FEATURE_COST_DREAM',
        '3',
        'Стоимость разбора сна (Сонник) в знаках',
        NOW(),
        NOW());
