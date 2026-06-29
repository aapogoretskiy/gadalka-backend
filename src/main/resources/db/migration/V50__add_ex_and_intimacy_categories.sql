-- Добавление двух новых категорий вопросов на экране "О чём спросить карты?":
-- "Бывшие" (ex) и "Близость" (intimacy). Механизм идентичен V44/V45 —
-- code должен совпадать со значениями, разрешёнными в FortuneRequest.category.

INSERT INTO question_categories (code, name, sort_order, is_active)
VALUES
    ('ex', 'Бывшие', 6, true),
    ('intimacy', 'Близость', 7, true);

INSERT INTO question_presets (category_id, question_text, sort_order, is_active)
SELECT id, preset.question_text, preset.sort_order, true
FROM question_categories,
     (VALUES
         ('Скучает ли он/она обо мне и что чувствует сейчас?', 1),
         ('Есть ли у него/неё новый человек?', 2),
         ('Почему мы расстались и есть ли шанс на новый виток?', 3),
         ('Стоит ли мне написать первой/первым?', 4),
         ('О чём он/она думает, когда вспоминает меня?', 5),
         ('Что мешает нам помириться или возобновить общение?', 6),
         ('Как он/она отнесётся, если я выйду на контакт?', 7),
         ('Какой урок мне важно извлечь из этих отношений?', 8)
     ) AS preset(question_text, sort_order)
WHERE code = 'ex';

INSERT INTO question_presets (category_id, question_text, sort_order, is_active)
SELECT id, preset.question_text, preset.sort_order, true
FROM question_categories,
     (VALUES
         ('Что притягивает партнёра ко мне на физическом уровне?', 1),
         ('Доволен(а) ли партнёр нашей близостью?', 2),
         ('Что мешает нам стать ближе друг к другу?', 3),
         ('О чём партнёр стесняется сказать мне?', 4),
         ('Как сделать наши отношения более гармоничными в этой сфере?', 5),
         ('Что партнёр на самом деле чувствует ко мне?', 6),
         ('Подходим ли мы друг другу в этом смысле?', 7),
         ('Какой совет дают карты для гармонии в близости?', 8)
     ) AS preset(question_text, sort_order)
WHERE code = 'intimacy';
