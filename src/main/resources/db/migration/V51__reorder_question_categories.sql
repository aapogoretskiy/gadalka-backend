-- Переупорядочивание категорий вопросов на экране "О чём спросить карты?".
-- Цель: самые "цепляющие" категории показываются первыми, чтобы пользователь
-- видел их без свайпа горизонтального списка чипов.
-- Новый порядок: Любовь, Близость, Бывшие, Деньги, Работа, Ситуация, Здоровье.

UPDATE question_categories SET sort_order = 1 WHERE code = 'love';
UPDATE question_categories SET sort_order = 2 WHERE code = 'intimacy';
UPDATE question_categories SET sort_order = 3 WHERE code = 'ex';
UPDATE question_categories SET sort_order = 4 WHERE code = 'money';
UPDATE question_categories SET sort_order = 5 WHERE code = 'work';
UPDATE question_categories SET sort_order = 6 WHERE code = 'life';
UPDATE question_categories SET sort_order = 7 WHERE code = 'health';
