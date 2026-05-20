-- Проставляем slug для каждой карты.
-- Slug = имя файла без расширения (maj00, cups01, wands14 и т.д.)
-- Используется для сборки imageUrl: base_url + slug + '.' + image_extension

-- ── Старшие Арканы (Major Arcana) ────────────────────────────────────────────
UPDATE cards SET slug = 'maj00' WHERE name = 'Шут';
UPDATE cards SET slug = 'maj01' WHERE name = 'Маг';
UPDATE cards SET slug = 'maj02' WHERE name = 'Жрица';
UPDATE cards SET slug = 'maj03' WHERE name = 'Императрица';
UPDATE cards SET slug = 'maj04' WHERE name = 'Император';
UPDATE cards SET slug = 'maj05' WHERE name = 'Иерофант';
UPDATE cards SET slug = 'maj06' WHERE name = 'Влюблённые';
UPDATE cards SET slug = 'maj07' WHERE name = 'Колесница';
UPDATE cards SET slug = 'maj08' WHERE name = 'Сила';
UPDATE cards SET slug = 'maj09' WHERE name = 'Отшельник';
UPDATE cards SET slug = 'maj10' WHERE name = 'Колесо Фортуны';
UPDATE cards SET slug = 'maj11' WHERE name = 'Справедливость';
UPDATE cards SET slug = 'maj12' WHERE name = 'Повешенный';
UPDATE cards SET slug = 'maj13' WHERE name = 'Смерть';
UPDATE cards SET slug = 'maj14' WHERE name = 'Умеренность';
UPDATE cards SET slug = 'maj15' WHERE name = 'Дьявол';
UPDATE cards SET slug = 'maj16' WHERE name = 'Башня';
UPDATE cards SET slug = 'maj17' WHERE name = 'Звезда';
UPDATE cards SET slug = 'maj18' WHERE name = 'Луна';
UPDATE cards SET slug = 'maj19' WHERE name = 'Солнце';
UPDATE cards SET slug = 'maj20' WHERE name = 'Суд';
UPDATE cards SET slug = 'maj21' WHERE name = 'Мир';

-- ── Младшие Арканы: слаг = масть + номер ─────────────────────────────────────
-- Для Minor Arcana выводим slug из suit и rank — надёжнее чем по русскому name
UPDATE cards SET slug =
    LOWER(
        CASE suit
            WHEN 'WANDS'     THEN 'wands'
            WHEN 'CUPS'      THEN 'cups'
            WHEN 'SWORDS'    THEN 'swords'
            WHEN 'PENTACLES' THEN 'pents'
        END
    ) ||
    LPAD(
        CASE rank
            WHEN 'ACE'    THEN '1'
            WHEN 'TWO'    THEN '2'
            WHEN 'THREE'  THEN '3'
            WHEN 'FOUR'   THEN '4'
            WHEN 'FIVE'   THEN '5'
            WHEN 'SIX'    THEN '6'
            WHEN 'SEVEN'  THEN '7'
            WHEN 'EIGHT'  THEN '8'
            WHEN 'NINE'   THEN '9'
            WHEN 'TEN'    THEN '10'
            WHEN 'PAGE'   THEN '11'
            WHEN 'KNIGHT' THEN '12'
            WHEN 'QUEEN'  THEN '13'
            WHEN 'KING'   THEN '14'
        END,
        2, '0'
    )
WHERE arcana_type = 'MINOR';

-- ── base_url для классической темы ───────────────────────────────────────────
-- Картинки лежат в MinIO: gadalka-cards/classic/maj00.jpg, cups01.jpg и т.д.
UPDATE card_deck_themes
SET base_url = 'https://magicliora.com/storage/gadalka-cards/classic/'
WHERE slug = 'classic';
