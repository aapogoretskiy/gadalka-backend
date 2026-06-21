-- Seed-данные для категорий и пресетов вопросов.
-- code должен совпадать со значениями, разрешёнными в FortuneRequest.category
-- (regexp = "^(love|money|work|life|health)$"). Это сознательное дублирование —
-- категорий всего 5 и они меняются редко, поэтому динамическая валидация по БД избыточна.

INSERT INTO question_categories (code, name, sort_order, is_active)
VALUES
    ('love', 'Любовь', 1, true),
    ('money', 'Деньги', 2, true),
    ('work', 'Работа', 3, true),
    ('life', 'Ситуация', 4, true),
    ('health', 'Здоровье', 5, true);

INSERT INTO question_presets (category_id, question_text, sort_order, is_active)
SELECT id, preset.question_text, preset.sort_order, true
FROM question_categories,
     (VALUES
         ('Что сейчас происходит в наших отношениях и куда они движутся?', 1),
         ('Какие чувства он испытывает ко мне на самом деле?', 2),
         ('Есть ли у нас будущее вместе?', 3),
         ('Что мешает нам сблизиться и как это преодолеть?', 4),
         ('Стоит ли мне сделать первый шаг?', 5),
         ('Почему мы расстались и есть ли шанс на воссоединение?', 6),
         ('Что мне важно понять о себе в этих отношениях?', 7),
         ('Когда и при каких обстоятельствах я встречу своего человека?', 8)
     ) AS preset(question_text, sort_order)
WHERE code = 'love';

INSERT INTO question_presets (category_id, question_text, sort_order, is_active)
SELECT id, preset.question_text, preset.sort_order, true
FROM question_categories,
     (VALUES
         ('Что сейчас блокирует мой финансовый рост?', 1),
         ('Стоит ли мне сейчас совершать крупную покупку или инвестицию?', 2),
         ('Как улучшится моё материальное положение в ближайшие 3 месяца?', 3),
         ('Какой источник дохода принесёт мне наибольший результат?', 4),
         ('Что мне мешает зарабатывать больше?', 5),
         ('Стоит ли браться за этот проект или договор?', 6),
         ('Как мне выйти из текущих финансовых трудностей?', 7),
         ('Что карты говорят о моём отношении к деньгам?', 8)
     ) AS preset(question_text, sort_order)
WHERE code = 'money';

INSERT INTO question_presets (category_id, question_text, sort_order, is_active)
SELECT id, preset.question_text, preset.sort_order, true
FROM question_categories,
     (VALUES
         ('Что сейчас происходит в моей карьере и куда она движется?', 1),
         ('Стоит ли мне сейчас менять работу?', 2),
         ('Как улучшится моё положение на работе в ближайшие 3 месяца?', 3),
         ('Какой проект или направление принесёт мне наибольший результат?', 4),
         ('Что мешает мне реализовать себя в профессии?', 5),
         ('Стоит ли мне начинать своё дело?', 6),
         ('Как мне выйти из текущего конфликта на работе?', 7),
         ('Что карты говорят о моём призвании?', 8)
     ) AS preset(question_text, sort_order)
WHERE code = 'work';

INSERT INTO question_presets (category_id, question_text, sort_order, is_active)
SELECT id, preset.question_text, preset.sort_order, true
FROM question_categories,
     (VALUES
         ('Что сейчас происходит в моей жизни и к чему это ведёт?', 1),
         ('Что мне важно отпустить прямо сейчас?', 2),
         ('Что ждёт меня в ближайшие 3 месяца?', 3),
         ('На что мне стоит обратить особое внимание?', 4),
         ('Как мне принять правильное решение в текущей ситуации?', 5),
         ('Что я не замечаю или от чего убегаю?', 6),
         ('В чём моя сила в этой ситуации?', 7),
         ('Какой урок несёт мне происходящее?', 8)
     ) AS preset(question_text, sort_order)
WHERE code = 'life';

INSERT INTO question_presets (category_id, question_text, sort_order, is_active)
SELECT id, preset.question_text, preset.sort_order, true
FROM question_categories,
     (VALUES
         ('Что сейчас происходит с моим здоровьем и самочувствием?', 1),
         ('На что мне стоит обратить внимание в заботе о себе?', 2),
         ('Как улучшится моё самочувствие в ближайшие 3 месяца?', 3),
         ('Что мешает мне восстановить силы?', 4),
         ('Стоит ли мне сейчас заняться новой практикой или спортом?', 5),
         ('Что я не замечаю в своём состоянии?', 6),
         ('В чём источник моей усталости?', 7),
         ('Какой урок несёт мне это состояние?', 8)
     ) AS preset(question_text, sort_order)
WHERE code = 'health';
